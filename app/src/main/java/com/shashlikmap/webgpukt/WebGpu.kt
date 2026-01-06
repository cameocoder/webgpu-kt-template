package com.shashlikmap.webgpukt

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webgpu.*
import androidx.webgpu.GPU.createInstance
import androidx.webgpu.LoadOp.Companion.Clear
import androidx.webgpu.StoreOp.Companion.Store
import androidx.webgpu.helper.Util
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Float4
import dev.romainguy.kotlin.math.rotation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Vector

var dragX = 0.0f

class WebGPURenderer {
    companion object {
        init {
            try {
                System.loadLibrary("webgpu_c_bundled")
                Log.d("WebGPU", "Library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("WebGPU", "Failed to load WebGPU library", e)
            }
        }
    }

    private var gpuInstance: GPUInstance? = null
    private var adapter: GPUAdapter? = null
    private var device: GPUDevice? = null
    private var surface: GPUSurface? = null
    private var renderPipeline: GPURenderPipeline? = null
    private var isInitialized = false

    private var globalUniformBuffer: GPUBuffer? = null
    private var globalUniformBindGroupLayout: GPUBindGroupLayout? = null
    private var globalUniformBindGroup: GPUBindGroup? = null

    private val perspectiveMatrix = FloatArray(16)
    private val viewProj = FloatArray(16)


    suspend fun initialize() {

        if (isInitialized) return

        try {
            Log.d("WebGPU", "Starting initialization...")
            val instanceLimits = GPUInstanceLimits()
            val instanceDescriptor = GPUInstanceDescriptor(intArrayOf(1), instanceLimits)
            gpuInstance = createInstance(instanceDescriptor)
            Log.d("WebGPU", "GPU Instance created: ${gpuInstance != null}")

            adapter = gpuInstance?.requestAdapter()
            Log.d("WebGPU", "Adapter created: ${adapter != null}")

            device = adapter?.requestDevice()
            Log.d("WebGPU", "Device created: ${device != null}")

            isInitialized = true
            Log.d("WebGPU", "Initialization complete!")
        } catch (e: Exception) {
            Log.e("WebGPU", "Initialization failed", e)
            e.printStackTrace()
        }
    }

    fun createSurface(context: Context, nativeSurface: Surface, width: Int, height: Int) {
        if (!isInitialized) {
            Log.e("WebGPU", "Cannot create surface - not initialized!")
            return
        }

        if (width <= 0 || height <= 0) {
            Log.e("WebGPU", "Invalid surface dimensions: ${width}x${height}")
            return
        }

        try {
            Log.d("WebGPU", "Creating surface with dimensions: ${width}x${height}")

            val nativeWindow = Util.windowFromSurface(nativeSurface)
            Log.d("WebGPU", "Native window pointer: $nativeWindow")

            val surfaceDescriptor = GPUSurfaceDescriptor(
                surfaceSourceAndroidNativeWindow = GPUSurfaceSourceAndroidNativeWindow(nativeWindow)
            )
            surface = gpuInstance?.createSurface(surfaceDescriptor)
            Log.d("WebGPU", "Surface created: ${surface != null}")

            configureSurface(width, height)
            createRenderPipeline(context)

            Log.d("WebGPU", "Surface setup complete!")
        } catch (e: Exception) {
            Log.e("WebGPU", "Failed to create surface", e)
            e.printStackTrace()
        }
    }

    private fun configureSurface(width: Int, height: Int) {
        val currentSurface = surface ?: return
        val currentAdapter = adapter ?: return
        val currentDevice = device ?: return

        Matrix.perspectiveM(
            perspectiveMatrix,
            0,
            45f,
            width.toFloat() / height.toFloat(),
            0.1f,
            100f
        )

        try {
            val capabilities = currentSurface.getCapabilities(currentAdapter)
            Log.d("WebGPU", "Surface capabilities - formats: ${capabilities.formats.size}")

            val textureFormat = capabilities.formats.firstOrNull()
            if (textureFormat == null) {
                Log.e("WebGPU", "No texture formats available!")
                return
            }
            Log.d("WebGPU", "Using texture format: $textureFormat")

            val surfaceConfiguration = GPUSurfaceConfiguration(
                device = currentDevice,
                format = textureFormat,
                width = width,
                height = height
            )
            currentSurface.configure(surfaceConfiguration)
            Log.d("WebGPU", "Surface configured successfully")
        } catch (e: Exception) {
            Log.e("WebGPU", "Failed to configure surface", e)
            e.printStackTrace()
        }
    }

    @SuppressLint("RestrictedApi")
    private fun createRenderPipeline(context: Context) {
        val currentDevice = device ?: return
        val currentSurface = surface ?: return
        val currentAdapter = adapter ?: return

        try {
            Log.d("WebGPU", "Creating render pipeline...")

            val shader = context.resources.openRawResource(R.raw.shader).bufferedReader().readText()
            val shaderModuleDescriptor = GPUShaderModuleDescriptor().apply {
                shaderSourceWGSL = GPUShaderSourceWGSL(shader)
            }

            val module = currentDevice.createShaderModule(shaderModuleDescriptor)
            Log.d("WebGPU", "Shader module created")

            val capabilities = currentSurface.getCapabilities(currentAdapter)
            val textureFormat = capabilities.formats.firstOrNull() ?: return

            val colorTargetState = GPUColorTargetState(format = textureFormat)
            val fragmentState = GPUFragmentState(
                module = module,
                targets = arrayOf(colorTargetState),
                entryPoint = "fragmentMain"
            )


            val renderPipelineDescriptor = GPURenderPipelineDescriptor(
                vertex = GPUVertexState(
                    module = module,
                    entryPoint = "vertexMain"
                ),
                fragment = fragmentState,
                layout = currentDevice.createPipelineLayout(
                    GPUPipelineLayoutDescriptor(
                        bindGroupLayouts = arrayOf(globalUniformBindGroupLayout!!)
                    )
                )
            )

            renderPipeline = currentDevice.createRenderPipeline(renderPipelineDescriptor)
            Log.d("WebGPU", "Render pipeline created: ${renderPipeline != null}")
        } catch (e: Exception) {
            Log.e("WebGPU", "Failed to create render pipeline", e)
            e.printStackTrace()
        }
    }

    @SuppressLint("RestrictedApi")
    fun createGlobalUniform() {
        val currentDevice = device ?: return

        val buffer =
            currentDevice.createBuffer(
                GPUBufferDescriptor(size = 64, usage = BufferUsage.CopyDst or BufferUsage.Uniform)
            )
        globalUniformBuffer = buffer

        val bindGroupLayout = currentDevice.createBindGroupLayout(
            GPUBindGroupLayoutDescriptor(
                entries = arrayOf(
                    GPUBindGroupLayoutEntry(
                        binding = 0,
                        visibility = ShaderStage.Vertex,
                        buffer = GPUBufferBindingLayout(type = BufferBindingType.Uniform)
                    )
                )
            )
        )
        globalUniformBindGroupLayout = bindGroupLayout

        globalUniformBindGroup = currentDevice.createBindGroup(
            descriptor = GPUBindGroupDescriptor(
                layout = bindGroupLayout,
                entries = arrayOf(GPUBindGroupEntry(binding = 0, buffer = buffer))
            )
        )
    }

    fun floatArrayToByteBuffer(floatArray: FloatArray): ByteBuffer {
        val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(floatArray.size * Float.SIZE_BYTES)
        byteBuffer.order(ByteOrder.nativeOrder())
        val floatBuffer: FloatBuffer = byteBuffer.asFloatBuffer()
        floatBuffer.put(floatArray)
        byteBuffer.rewind()
        return byteBuffer
    }

    fun update() {
        val currentDevice = device ?: return

        val ccc = floatArrayOf(
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.5f,
            0.5f,
            0.0f,
            0.0f,
            0.0f,
            1.0f
        )

        val v = Float4(0.0f, 0.0f, 5.0f, 1.0f)
        val rotationMatrix = rotation(d = Float3(y = dragX))
        val bb = rotationMatrix*v
        Matrix.setLookAtM(viewProj, 0, bb.x, bb.y, bb.z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f)
        Matrix.multiplyMM(viewProj, 0, perspectiveMatrix, 0, viewProj, 0)
        Matrix.multiplyMM(viewProj, 0, ccc, 0, viewProj, 0)

        currentDevice.queue.writeBuffer(
            globalUniformBuffer!!,
            0,
            floatArrayToByteBuffer(viewProj)
        )
    }

    fun render() {
        val currentSurface = surface ?: return
        val currentDevice = device ?: return
        val currentRenderPipeline = renderPipeline ?: return

        try {
            val surfaceTexture = currentSurface.getCurrentTexture()
            val renderPassColorAttachment = GPURenderPassColorAttachment(
                view = surfaceTexture.texture.createView(),
                loadOp = Clear,
                storeOp = Store,
                clearValue = GPUColor(0.0, 0.0, 0.5, 1.0) // Blue background for visibility
            )

            val renderPassDescriptor = GPURenderPassDescriptor(
                colorAttachments = arrayOf(renderPassColorAttachment)
            )

            val commandEncoder = currentDevice.createCommandEncoder()
            val renderPassEncoder = commandEncoder.beginRenderPass(renderPassDescriptor)

            renderPassEncoder.apply {
                setPipeline(currentRenderPipeline)
                setBindGroup(0, globalUniformBindGroup!!)
                draw(vertexCount = 6)
                end()
            }

            val commands = commandEncoder.finish()
            currentDevice.queue.submit(arrayOf(commands))

            currentSurface.present()
            gpuInstance?.processEvents()
        } catch (e: Exception) {
            Log.e("WebGPU", "Render error", e)
            e.printStackTrace()
        }
    }

    fun cleanup() {
        Log.d("WebGPU", "Cleaning up...")
        surface = null
        renderPipeline = null
        device = null
        adapter = null
        gpuInstance = null
        isInitialized = false
    }
}

@Composable
fun WebGPUView() {
    val renderer = remember { WebGPURenderer() }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var renderJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                renderer.cleanup()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            renderJob?.cancel()
            renderer.cleanup()
        }
    }

    LaunchedEffect(Unit) {
        Log.d("WebGPU", "LaunchedEffect: Initializing renderer")
        renderer.initialize()
    }

    Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            dragX -= dragAmount.x / 5.0f
        }
    }) {
        AndroidView(
            factory = { context ->
                Log.d("WebGPU", "Creating SurfaceView")
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            Log.d("WebGPU", "Surface created")

                            // Wait a bit for dimensions to be available
                            coroutineScope.launch {
                                delay(100) // Small delay to ensure dimensions are ready

                                val width = holder.surfaceFrame.width()
                                val height = holder.surfaceFrame.height()
                                Log.d("WebGPU", "Surface dimensions: ${width}x${height}")

                                if (width > 0 && height > 0) {
                                    renderer.createGlobalUniform()

                                    renderer.createSurface(context, holder.surface, width, height)
                                    renderJob = launch {
                                        Log.d("WebGPU", "Starting render loop")
                                        var frameCount = 0
                                        while (isActive) {
                                            renderer.update()
                                            renderer.render()
                                            delay(16)
                                            frameCount++
                                            if (frameCount % 60 == 0) {
                                                Log.d("WebGPU", "Rendered $frameCount frames")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                            Log.d("WebGPU", "Surface changed: ${width}x${height}")
                            if (width > 0 && height > 0) {
                                renderer.createSurface(context, holder.surface, width, height)
                            }
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            Log.d("WebGPU", "Surface destroyed")
                            renderJob?.cancel()
                            renderJob = null
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun WebGPUScreen() {
    WebGPUView()
}
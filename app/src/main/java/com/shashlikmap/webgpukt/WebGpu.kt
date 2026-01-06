package com.shashlikmap.webgpukt

import android.util.Log
import android.view.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webgpu.*
import androidx.webgpu.GPU.createInstance
import androidx.webgpu.LoadOp.Companion.Clear
import androidx.webgpu.StoreOp.Companion.Store
import androidx.webgpu.helper.Util
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    private val shaderCode = """
        @vertex fn vertexMain(@builtin(vertex_index) i : u32) ->
              @builtin(position) vec4f {
                const pos = array(vec2f(0, 1), vec2f(-1, -1), vec2f(1, -1));
                return vec4f(pos[i], 0, 1);
            }
        @fragment fn fragmentMain() -> @location(0) vec4f {
            return vec4f(1, 0, 0, 1);
        }
    """.trimIndent()

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

    fun createSurface(nativeSurface: Surface, width: Int, height: Int) {
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
            createRenderPipeline()

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

    private fun createRenderPipeline() {
        val currentDevice = device ?: return
        val currentSurface = surface ?: return
        val currentAdapter = adapter ?: return

        try {
            Log.d("WebGPU", "Creating render pipeline...")

            val shaderModuleDescriptor = GPUShaderModuleDescriptor().apply {
                shaderSourceWGSL = GPUShaderSourceWGSL(shaderCode)
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
                fragment = fragmentState
            )

            renderPipeline = currentDevice.createRenderPipeline(renderPipelineDescriptor)
            Log.d("WebGPU", "Render pipeline created: ${renderPipeline != null}")
        } catch (e: Exception) {
            Log.e("WebGPU", "Failed to create render pipeline", e)
            e.printStackTrace()
        }
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
                clearValue = GPUColor(0.2, 0.3, 0.5, 1.0) // Blue background for visibility
            )

            val renderPassDescriptor = GPURenderPassDescriptor(
                colorAttachments = arrayOf(renderPassColorAttachment)
            )

            val commandEncoder = currentDevice.createCommandEncoder()
            val renderPassEncoder = commandEncoder.beginRenderPass(renderPassDescriptor)

            renderPassEncoder.apply {
                setPipeline(currentRenderPipeline)
                draw(vertexCount = 3)
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

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                Log.d("WebGPU", "Creating SurfaceView")
                android.view.SurfaceView(context).apply {
                    holder.addCallback(object : android.view.SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                            Log.d("WebGPU", "Surface created")

                            // Wait a bit for dimensions to be available
                            coroutineScope.launch {
                                delay(100) // Small delay to ensure dimensions are ready

                                val width = holder.surfaceFrame.width()
                                val height = holder.surfaceFrame.height()
                                Log.d("WebGPU", "Surface dimensions: ${width}x${height}")

                                if (width > 0 && height > 0) {
                                    renderer.createSurface(holder.surface, width, height)

                                    renderJob = launch {
                                        Log.d("WebGPU", "Starting render loop")
                                        var frameCount = 0
                                        while (isActive) {
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
                            holder: android.view.SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                            Log.d("WebGPU", "Surface changed: ${width}x${height}")
                            if (width > 0 && height > 0) {
                                renderer.createSurface(holder.surface, width, height)
                            }
                        }

                        override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
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
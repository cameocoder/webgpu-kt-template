package com.shashlikmap.webgpukt

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.shashlikmap.webgpukt.webgpu.WebGPUSurfaceView
import com.shashlikmap.webgpukt.webgpu.WebGpuAPI

@Composable
fun WebGPUView() {
    val renderer = remember { WebGpuAPI() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event >= Lifecycle.Event.ON_STOP) {
                renderer.reset()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            renderer.reset()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    renderer.rotationAngle -= dragAmount.x / 5.0f
                }
            }) {
        AndroidView(
            factory = { context ->
                WebGPUSurfaceView(context, renderer)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
package com.shashlikmap.webgpukt.webgpu

import android.content.Context
import android.graphics.Canvas
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WebGPUSurfaceView(context: Context, private val api: WebGpuAPI) : SurfaceView(context) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        setWillNotDraw(false)
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                val width = holder.surfaceFrame.width()
                val height = holder.surfaceFrame.height()
                coroutineScope.launch {
                    api.setSurface(context, holder.surface, width, height)
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                //no-op
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                // no-op
            }
        })
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        api.update()
        api.render()

        invalidate()
    }
}
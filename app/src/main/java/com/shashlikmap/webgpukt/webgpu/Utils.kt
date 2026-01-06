package com.shashlikmap.webgpukt.webgpu

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

fun FloatArray.toByteBuffer(): ByteBuffer {
    val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(size * Float.SIZE_BYTES)
    byteBuffer.order(ByteOrder.nativeOrder())
    val floatBuffer: FloatBuffer = byteBuffer.asFloatBuffer()
    floatBuffer.put(this)
    byteBuffer.rewind()
    return byteBuffer
}
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

private const val ALIGNMENT = 4L

val FloatArray.paddedSize: Long
    get() = padded((size * Float.SIZE_BYTES).toLong())

private fun padded(dataSize: Long) = (dataSize + 3) and -ALIGNMENT
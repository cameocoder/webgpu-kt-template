package com.shashlikmap.webgpukt.webgpu.utils

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.nio.ByteBuffer

@Parcelize
data class Vec3(val a: Float, val b: Float, val c: Float) : Parcelable

@Parcelize
data class Vec4(val a: Float, val b: Float, val c: Float, val w: Float) : Parcelable

/**
 * Converts Parcelables to [ByteBuffer]
 */
fun Iterable<Parcelable>.toByteBuffer(): ByteBuffer {
    val parcel = Parcel.obtain()
    forEach { data ->
        data.writeToParcel(parcel, 0)
    }
    val bytes = parcel.marshall()
    parcel.recycle()
    return ByteBuffer.wrap(bytes)
}
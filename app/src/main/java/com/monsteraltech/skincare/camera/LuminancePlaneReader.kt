package com.monsteraltech.skincare.camera

import java.nio.ByteBuffer

/** Copies Y samples without consuming the sensor buffer or including row/pixel padding. */
internal object LuminancePlaneReader {
    fun read(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): ByteArray {
        require(width > 0 && height > 0 && pixelStride > 0)
        val rowSize = (width - 1L) * pixelStride + 1L
        require(rowStride >= rowSize) { "Invalid luminance row stride" }
        val source = buffer.duplicate().apply { rewind() }
        val required = (height - 1L) * rowStride + rowSize
        require(required <= source.limit()) { "Incomplete luminance plane" }
        val outputSize = width.toLong() * height
        require(outputSize <= Int.MAX_VALUE)
        return ByteArray(outputSize.toInt()).also { output ->
            if (pixelStride == 1) {
                for (y in 0 until height) {
                    source.position(y * rowStride)
                    source.get(output, y * width, width)
                }
            } else {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        output[y * width + x] = source.get(y * rowStride + x * pixelStride)
                    }
                }
            }
        }
    }
}

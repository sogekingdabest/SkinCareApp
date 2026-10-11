package com.monsteraltech.skincare.camera

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class LuminancePlaneReaderTest {
    @Test fun `row padding is discarded and the sensor buffer position is preserved`() {
        val buffer = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 99, 4, 5, 6)).apply { position(2) }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), LuminancePlaneReader.read(buffer, 3, 2, 4, 1))
        assertEquals(2, buffer.position())
    }

    @Test fun `pixel padding is discarded without requiring final row padding`() {
        val buffer = ByteBuffer.wrap(byteArrayOf(1, 90, 2, 90, 3, 99, 4, 90, 5, 90, 6))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), LuminancePlaneReader.read(buffer, 3, 2, 6, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a truncated plane is rejected before allocation`() {
        LuminancePlaneReader.read(ByteBuffer.wrap(byteArrayOf(1, 2, 3)), 2, 2, 2, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid strides are rejected`() {
        LuminancePlaneReader.read(ByteBuffer.allocate(32), 3, 2, 3, 2)
    }
}

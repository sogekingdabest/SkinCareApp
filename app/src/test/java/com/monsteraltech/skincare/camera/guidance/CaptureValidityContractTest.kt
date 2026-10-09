package com.monsteraltech.skincare.camera.guidance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureValidityContractTest {
    @Test
    fun `class order matches the exported model`() {
        assertEquals(
            CaptureValidityContract.SemanticClass.TARGET_LESION,
            CaptureValidityContract.prediction(
                floatArrayOf(0.9f, 0.08f, 0.02f)
            ).predictedClass
        )
        assertEquals(
            CaptureValidityContract.SemanticClass.SKIN_WITHOUT_TARGET,
            CaptureValidityContract.prediction(
                floatArrayOf(0.1f, 0.8f, 0.1f)
            ).predictedClass
        )
        assertEquals(
            CaptureValidityContract.SemanticClass.NO_SKIN,
            CaptureValidityContract.prediction(
                floatArrayOf(0.1f, 0.2f, 0.7f)
            ).predictedClass
        )
    }

    @Test
    fun `READY uses the frozen validation threshold`() {
        assertFalse(
            CaptureValidityContract.prediction(
                floatArrayOf(0.7959f, 0.2f, 0.0041f)
            ).isReady
        )
        assertTrue(
            CaptureValidityContract.prediction(
                floatArrayOf(0.796f, 0.2f, 0.004f)
            ).isReady
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid output size is rejected`() {
        CaptureValidityContract.prediction(floatArrayOf(0.5f, 0.5f))
    }
}

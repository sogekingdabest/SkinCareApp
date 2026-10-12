package com.monsteraltech.skincare.analysis.model

import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LesionModelDescriptorTest {
    private val bytes = byteArrayOf(1, 2, 3)
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun descriptor() = LesionModelDescriptor(1, "model", "v1", "model.tflite", hash,
        "known", "rgb-v1", ImageNormalization.NEGATIVE_ONE_TO_ONE,
        listOf(ModelTensor(0, "image", listOf(1, 224, 224, 3), "FLOAT32", "image")),
        listOf(ModelTensor(0, "score", listOf(1, 1), "FLOAT32", "score")))

    @Test fun `changed artifact is rejected without consuming model buffer`() {
        val model = ByteBuffer.wrap(bytes)
        model.position(1)
        descriptor().verifyArtifact(model)
        assertEquals(1, model.position())
        assertThrows(IllegalArgumentException::class.java) { descriptor().verifyArtifact(ByteBuffer.wrap(byteArrayOf(9))) }
    }

    @Test fun `incorrect image size dtype and ambiguous output fail before inference`() {
        val contract = descriptor()
        val variants = listOf(
            contract.copy(inputs = listOf(contract.image.copy(shape = listOf(1, 0, 224, 3)))),
            contract.copy(inputs = listOf(contract.image.copy(dtype = "INT8"))),
            contract.copy(outputs = listOf(contract.outputs.single().copy(shape = listOf(1, 2)))),
            contract.copy(inputs = listOf(contract.image.copy(index = 1))))
        variants.forEach { invalid -> assertThrows(IllegalArgumentException::class.java) { invalid.validate() } }
    }

    @Test fun `multimodal input is explicit and normalized independently`() {
        val base = descriptor()
        val candidate = base.copy(imageNormalization = ImageNormalization.ZERO_TO_ONE,
            inputs = listOf(base.image.copy(shape = listOf(1, 256, 256, 3)),
                ModelTensor(1, "context", listOf(1, 3), "FLOAT32", "metadata")))
        candidate.validate()
        assertEquals(1, candidate.metadata!!.index)
        assertEquals(-1f, base.imageNormalization.channel(0))
        assertEquals(0f, candidate.imageNormalization.channel(0))
        assertEquals(1f, candidate.imageNormalization.channel(255))
    }

    @Test fun `RGB channel order and normalization preserve the current contract`() {
        val input = LiteRtLesionClassifier.rgbInput(intArrayOf(0xff0080ff.toInt()), ImageNormalization.NEGATIVE_ONE_TO_ONE)
        assertEquals(-1f, input.float)
        assertEquals(128f / 127.5f - 1f, input.float)
        assertEquals(1f, input.float)
        assertFalse(input.hasRemaining())
    }

    @Test fun `nonfinite and out of range predictions are rejected`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -0.1f, 1.1f).forEach { score ->
            assertThrows(IllegalArgumentException::class.java) { LesionPrediction(score, descriptor().identity()) }
        }
    }
}

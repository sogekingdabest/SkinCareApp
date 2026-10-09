package com.monsteraltech.skincare.body.mole.domain.usecase

import com.monsteraltech.skincare.body.mole.domain.model.LesionRegistrationMetrics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateLesionRegistrationUseCaseTest {

    private val evaluate = EvaluateLesionRegistrationUseCase()

    @Test
    fun `acepta un registro geometrico consistente`() {
        assertTrue(evaluate(validMetrics()))
    }

    @Test
    fun `rechaza poco solapamiento entre mascaras alineadas`() {
        assertFalse(
            evaluate(validMetrics(alignedMaskIou = 0.20))
        )
    }

    @Test
    fun `rechaza una diferencia de escala extrema`() {
        assertFalse(
            evaluate(validMetrics(appliedScale = 3.5))
        )
    }

    @Test
    fun `rechaza un lunar demasiado desplazado en la captura`() {
        assertFalse(
            evaluate(validMetrics(originalCentroidDistance = 0.60))
        )
    }

    @Test
    fun `rechaza una segmentacion que ocupa casi toda la imagen`() {
        assertFalse(
            evaluate(validMetrics(currentAreaFraction = 0.80))
        )
    }

    private fun validMetrics(
        baselineAreaFraction: Double = 0.12,
        currentAreaFraction: Double = 0.13,
        originalCentroidDistance: Double = 0.05,
        appliedScale: Double = 1.04,
        alignedMaskIou: Double = 0.80
    ) = LesionRegistrationMetrics(
        algorithmVersion = "test-v1",
        baselineAreaFraction = baselineAreaFraction,
        currentAreaFraction = currentAreaFraction,
        originalCentroidDistance = originalCentroidDistance,
        appliedScale = appliedScale,
        appliedRotationDegrees = 3.0,
        alignedMaskIou = alignedMaskIou
    )
}

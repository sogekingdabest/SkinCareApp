package com.monsteraltech.skincare.body.mole.domain.usecase

import com.monsteraltech.skincare.body.mole.domain.model.ObservationComparability
import com.monsteraltech.skincare.body.mole.domain.model.ObservationImageMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateObservationComparabilityUseCaseTest {

    private val evaluate = EvaluateObservationComparabilityUseCase()

    @Test
    fun `acepta dos capturas con calidad y geometria compatibles`() {
        val result = evaluate(
            baselineObservationId = "obs-1",
            baseline = validMetrics(),
            current = validMetrics(width = 1200, height = 900)
        )

        assertTrue(result is ObservationComparability.Comparable)
        assertEquals(
            "obs-1",
            (result as ObservationComparability.Comparable)
                .baselineObservationId
        )
    }

    @Test
    fun `rechaza una captura base borrosa`() {
        val result = evaluate(
            baselineObservationId = "obs-1",
            baseline = validMetrics(isBlurry = true),
            current = validMetrics()
        )

        assertReason(
            ObservationComparability.Reason.BASELINE_IMAGE_BLURRY,
            result
        )
    }

    @Test
    fun `rechaza iluminacion actual insuficiente`() {
        val result = evaluate(
            baselineObservationId = "obs-1",
            baseline = validMetrics(),
            current = validMetrics(
                isOptimalLighting = false,
                isUnderexposed = true
            )
        )

        assertReason(
            ObservationComparability.Reason.CURRENT_LIGHTING_INSUFFICIENT,
            result
        )
    }

    @Test
    fun `rechaza una imagen demasiado pequena`() {
        val result = evaluate(
            baselineObservationId = "obs-1",
            baseline = validMetrics(width = 200, height = 200),
            current = validMetrics()
        )

        assertReason(
            ObservationComparability.Reason.BASELINE_IMAGE_TOO_SMALL,
            result
        )
    }

    @Test
    fun `rechaza relaciones de aspecto muy distintas`() {
        val result = evaluate(
            baselineObservationId = "obs-1",
            baseline = validMetrics(width = 1024, height = 768),
            current = validMetrics(width = 1024, height = 1024)
        )

        assertReason(
            ObservationComparability.Reason.ASPECT_RATIO_MISMATCH,
            result
        )
    }

    private fun assertReason(
        expected: ObservationComparability.Reason,
        actual: ObservationComparability
    ) {
        assertTrue(actual is ObservationComparability.NotComparable)
        assertEquals(
            expected,
            (actual as ObservationComparability.NotComparable).reason
        )
    }

    private fun validMetrics(
        width: Int = 1024,
        height: Int = 768,
        isOptimalLighting: Boolean = true,
        isUnderexposed: Boolean = false,
        isBlurry: Boolean = false
    ) = ObservationImageMetrics(
        width = width,
        height = height,
        brightness = 120f,
        isOptimalLighting = isOptimalLighting,
        isOverexposed = false,
        isUnderexposed = isUnderexposed,
        sharpness = if (isBlurry) 20.0 else 100.0,
        isBlurry = isBlurry
    )
}

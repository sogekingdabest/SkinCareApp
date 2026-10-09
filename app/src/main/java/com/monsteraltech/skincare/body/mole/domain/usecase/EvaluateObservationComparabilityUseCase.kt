package com.monsteraltech.skincare.body.mole.domain.usecase

import com.monsteraltech.skincare.body.mole.domain.model.ObservationComparability
import com.monsteraltech.skincare.body.mole.domain.model.ObservationImageMetrics
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max

/**
 * Política determinista que decide si dos capturas superan la calidad mínima
 * necesaria para un futuro análisis longitudinal.
 */
class EvaluateObservationComparabilityUseCase @Inject constructor() {

    operator fun invoke(
        baselineObservationId: String,
        baseline: ObservationImageMetrics,
        current: ObservationImageMetrics
    ): ObservationComparability {
        val baselineFailure = validateImage(
            metrics = baseline,
            tooSmall = ObservationComparability.Reason.BASELINE_IMAGE_TOO_SMALL,
            badLighting = ObservationComparability.Reason.BASELINE_LIGHTING_INSUFFICIENT,
            blurry = ObservationComparability.Reason.BASELINE_IMAGE_BLURRY
        )
        if (baselineFailure != null) {
            return notComparable(baselineFailure, baselineObservationId)
        }

        val currentFailure = validateImage(
            metrics = current,
            tooSmall = ObservationComparability.Reason.CURRENT_IMAGE_TOO_SMALL,
            badLighting = ObservationComparability.Reason.CURRENT_LIGHTING_INSUFFICIENT,
            blurry = ObservationComparability.Reason.CURRENT_IMAGE_BLURRY
        )
        if (currentFailure != null) {
            return notComparable(currentFailure, baselineObservationId)
        }

        val baselineRatio = baseline.width.toDouble() / baseline.height
        val currentRatio = current.width.toDouble() / current.height
        val relativeRatioDifference =
            abs(baselineRatio - currentRatio) / max(baselineRatio, currentRatio)
        if (relativeRatioDifference > MAX_ASPECT_RATIO_DIFFERENCE) {
            return notComparable(
                reason = ObservationComparability.Reason.ASPECT_RATIO_MISMATCH,
                baselineObservationId = baselineObservationId,
                detail = "relativeDifference=$relativeRatioDifference"
            )
        }

        return ObservationComparability.Comparable(
            baselineObservationId = baselineObservationId,
            baselineMetrics = baseline,
            currentMetrics = current
        )
    }

    private fun validateImage(
        metrics: ObservationImageMetrics,
        tooSmall: ObservationComparability.Reason,
        badLighting: ObservationComparability.Reason,
        blurry: ObservationComparability.Reason
    ): ObservationComparability.Reason? = when {
        minOf(metrics.width, metrics.height) < MIN_IMAGE_SIDE_PIXELS -> tooSmall
        !metrics.isOptimalLighting ||
            metrics.isOverexposed ||
            metrics.isUnderexposed -> badLighting
        metrics.isBlurry -> blurry
        else -> null
    }

    private fun notComparable(
        reason: ObservationComparability.Reason,
        baselineObservationId: String,
        detail: String? = null
    ) = ObservationComparability.NotComparable(
        reason = reason,
        baselineObservationId = baselineObservationId,
        detail = detail
    )

    private companion object {
        const val MIN_IMAGE_SIDE_PIXELS = 256
        const val MAX_ASPECT_RATIO_DIFFERENCE = 0.20
    }
}

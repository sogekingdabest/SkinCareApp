package com.monsteraltech.skincare.body.mole.domain.model

/**
 * Resultado explícito de la compuerta previa a cualquier cálculo temporal.
 */
sealed interface ObservationComparability {
    data class Comparable(
        val baselineObservationId: String,
        val baselineMetrics: ObservationImageMetrics,
        val currentMetrics: ObservationImageMetrics
    ) : ObservationComparability

    data class NotComparable(
        val reason: Reason,
        val baselineObservationId: String? = null,
        val detail: String? = null
    ) : ObservationComparability

    enum class Reason {
        NO_BASELINE,
        BASELINE_LOOKUP_FAILED,
        BASELINE_IMAGE_UNREADABLE,
        BASELINE_IMAGE_TOO_SMALL,
        CURRENT_IMAGE_TOO_SMALL,
        BASELINE_LIGHTING_INSUFFICIENT,
        CURRENT_LIGHTING_INSUFFICIENT,
        BASELINE_IMAGE_BLURRY,
        CURRENT_IMAGE_BLURRY,
        ASPECT_RATIO_MISMATCH,
        BASELINE_SEGMENTATION_FAILED,
        CURRENT_SEGMENTATION_FAILED,
        REGISTRATION_UNRELIABLE
    }
}

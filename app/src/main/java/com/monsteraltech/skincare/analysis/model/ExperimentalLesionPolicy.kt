package com.monsteraltech.skincare.analysis.model

enum class MetadataScenario { RECORDED_CONTEXT, SITE_ONLY, NO_CONTEXT }

/** A frozen research operating point; no clinical urgency or calibrated confidence is inferred. */
data class ExperimentalOperatingPoint(
    val modelSha256: String,
    val scenario: MetadataScenario,
    val threshold: Float,
    val minimumValidationSensitivity: Float,
    val validationPolicySha256: String
) {
    init {
        require(modelSha256.matches(Regex("[a-f0-9]{64}")))
        require(validationPolicySha256.matches(Regex("[a-f0-9]{64}")))
        require(threshold.isFinite() && threshold in 0f..1f)
        require(minimumValidationSensitivity.isFinite() && minimumValidationSensitivity > 0f && minimumValidationSensitivity <= 1f)
    }
}

enum class ExperimentalThresholdResult { ABOVE_THRESHOLD, BELOW_THRESHOLD, NOT_EVALUATED }

object ExperimentalLesionPolicy {
    fun scenario(metadata: LesionMetadata): MetadataScenario? = when {
        metadata.ageYears != null && metadata.sex != null && metadata.site != null -> MetadataScenario.RECORDED_CONTEXT
        metadata.ageYears == null && metadata.sex == null && metadata.site != null -> MetadataScenario.SITE_ONLY
        metadata.ageYears == null && metadata.sex == null && metadata.site == null -> MetadataScenario.NO_CONTEXT
        else -> null
    }

    fun evaluate(score: Float, actualModelSha256: String, metadata: LesionMetadata, point: ExperimentalOperatingPoint): ExperimentalThresholdResult {
        if (!score.isFinite() || score !in 0f..1f || point.modelSha256 != actualModelSha256 || scenario(metadata) != point.scenario) {
            return ExperimentalThresholdResult.NOT_EVALUATED
        }
        return if (score >= point.threshold) ExperimentalThresholdResult.ABOVE_THRESHOLD else ExperimentalThresholdResult.BELOW_THRESHOLD
    }
}

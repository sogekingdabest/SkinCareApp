package com.monsteraltech.skincare.camera.guidance

/** Immutable Android contract for the private capture-validity V6 model. */
object CaptureValidityContract {
    const val MODEL_VERSION = "capture-validity-v6"
    const val MODEL_FILENAME = "capture_validity_mobilenetv3_small_float16.tflite"
    const val MODEL_SHA256 =
        "af150991b37d9efefd6eb5db8f1a868cfb564f02eaaabbb87d4a8aabe2146d5e"
    const val IMAGE_SIZE = 192
    const val READY_THRESHOLD = 0.796f
    const val OUTPUT_CLASSES = 3

    enum class SemanticClass(val index: Int) {
        TARGET_LESION(0),
        SKIN_WITHOUT_TARGET(1),
        NO_SKIN(2)
    }

    data class Prediction(
        val probabilities: List<Float>,
        val predictedClass: SemanticClass,
        val targetProbability: Float,
        val isReady: Boolean
    )

    fun prediction(probabilities: FloatArray): Prediction {
        require(probabilities.size == OUTPUT_CLASSES) {
            "Expected $OUTPUT_CLASSES probabilities, found ${probabilities.size}"
        }
        require(probabilities.all { it.isFinite() && it in 0f..1f }) {
            "Capture-validity probabilities must be finite values in [0, 1]"
        }

        val predictedIndex = probabilities.indices.maxBy { probabilities[it] }
        val predictedClass = SemanticClass.entries.first { it.index == predictedIndex }
        val targetProbability = probabilities[SemanticClass.TARGET_LESION.index]
        return Prediction(
            probabilities = probabilities.toList(),
            predictedClass = predictedClass,
            targetProbability = targetProbability,
            isReady = targetProbability >= READY_THRESHOLD
        )
    }
}

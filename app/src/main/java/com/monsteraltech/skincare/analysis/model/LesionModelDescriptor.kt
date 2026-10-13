package com.monsteraltech.skincare.analysis.model

import androidx.annotation.Keep
import java.nio.ByteBuffer
import java.security.MessageDigest

@Keep
data class ModelTensor(
    val index: Int,
    val name: String,
    val shape: List<Int>,
    val dtype: String,
    val role: String
)

@Keep
enum class ImageNormalization {
    NEGATIVE_ONE_TO_ONE, ZERO_TO_ONE;

    fun channel(value: Int): Float {
        require(value in 0..255)
        return when (this) {
            NEGATIVE_ONE_TO_ONE -> value / 127.5f - 1f
            ZERO_TO_ONE -> value / 255f
        }
    }
}

/** Exact artifact and input semantics; shapes describe one inference, not dynamic batches. */
@Keep
data class LesionModelDescriptor(
    val schemaVersion: Int,
    val modelId: String,
    val modelVersion: String,
    val filename: String,
    val sha256: String,
    val sourceStatus: String,
    val preprocessingVersion: String,
    val imageNormalization: ImageNormalization,
    val inputs: List<ModelTensor>,
    val outputs: List<ModelTensor>
) {
    val image: ModelTensor get() = inputs.single { it.role == "image" }
    val metadata: ModelTensor? get() = inputs.singleOrNull { it.role == "metadata" }

    fun validate() {
        require(schemaVersion == 1) { "Unsupported model descriptor version" }
        require(modelId.isNotBlank() && modelVersion.isNotBlank() && preprocessingVersion.isNotBlank())
        require(sourceStatus.isNotBlank())
        require(filename.matches(Regex("[A-Za-z0-9_.-]+\\.tflite")))
        require(sha256.matches(Regex("[a-f0-9]{64}"))) { "Invalid model SHA-256" }
        require(imageNormalization in ImageNormalization.entries)
        require(inputs.size in 1..2 && inputs.map { it.index } == inputs.indices.toList())
        require(inputs.count { it.role == "image" } == 1)
        require(inputs.all { it.role in setOf("image", "metadata") })
        require(inputs.count { it.role == "metadata" } == inputs.size - 1)
        require(image.shape.size == 4 && image.shape[0] == 1 && image.shape[3] == 3)
        require(image.shape[1] in 1..1024 && image.shape[2] in 1..1024)
        metadata?.let { require(it.shape == listOf(1, 3)) }
        require(outputs.size == 1 && outputs[0].index == 0 && outputs[0].role == "score")
        require(outputs[0].shape == listOf(1, 1)) { "Expected a scalar score" }
        require((inputs + outputs).all { it.dtype == "FLOAT32" && it.name.isNotBlank() })
    }

    fun verifyArtifact(model: ByteBuffer) {
        validate()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(model.duplicate().apply { position(0) })
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(actual == sha256) { "Model artifact does not match its descriptor" }
    }

    fun identity() = ModelIdentity(modelId, modelVersion, sha256, preprocessingVersion, sourceStatus)
}

data class ModelIdentity(
    val id: String,
    val version: String,
    val sha256: String,
    val preprocessingVersion: String,
    val sourceStatus: String
) {
    fun observationMetadata(): Map<String, Any> = mapOf(
        "aiModelId" to id,
        "aiModelVersion" to version,
        "aiModelSha256" to sha256,
        "aiPreprocessingVersion" to preprocessingVersion,
        "aiModelSourceStatus" to sourceStatus,
        "analysisPolicyVersion" to "legacy-ai-abcde-v1",
        "aiConfidenceMethod" to "legacy-heuristic-uncalibrated",
        "abcdeAlgorithmVersion" to "opencv-abcde-v1"
    )
}

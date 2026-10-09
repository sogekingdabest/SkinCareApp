package com.monsteraltech.skincare.analysis
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs

class MelanomaAIDetector(private val context: Context) {
    companion object {
        private const val TAG = "MelanomaAIDetector"
        private const val MODEL_PATH = "melanoma_efficientnet_dynamic.tflite"
        private const val IMAGE_SIZE = 224
        private const val AI_THRESHOLD_HIGH = 0.5f
        private const val OPTIMAL_THRESHOLD = 0.327f
        private const val AI_WEIGHT = 0.6f
        private const val ABCDE_WEIGHT = 0.4f
    }

    @Volatile
    private var interpreter: Interpreter? = null
    private val inferenceLock = Any()
    private val abcdeAnalyzer = ABCDEAnalyzerOpenCV(context)

    init {
        loadModel()
    }

    data class CombinedAnalysisResult(
        val aiProbability: Float,
        val aiRiskLevel: RiskLevel,
        val aiConfidence: Float,
        val abcdeResult: ABCDEAnalyzerOpenCV.ABCDEResult,
        val combinedScore: Float,
        val combinedRiskLevel: RiskLevel,
        val recommendation: String,
        val shouldMonitor: Boolean,
        val urgencyLevel: UrgencyLevel,
        val explanations: List<String>
    )

    /** Resultado de la inferencia del modelo LiteRT, sin combinar con ABCDE. */
    data class AIInferenceResult(
        val probability: Float,
        val riskLevel: RiskLevel,
        val confidence: Float
    )

    enum class RiskLevel {
        VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH
    }

    enum class UrgencyLevel {
        ROUTINE, MONITOR, CONSULT, URGENT
    }

    /**
     * Análisis completo: inferencia IA + criterios ABCDE combinados.
     *
     * @throws AnalysisError.AIModelError si el modelo no está disponible o la
     * inferencia falla. En una app médica un fallo técnico nunca debe
     * presentarse como un resultado de bajo riesgo: el llamador decide cómo
     * degradar (p. ej. continuar solo con ABCDE informándolo al usuario).
     */
    fun analyzeMole(
        bitmap: Bitmap,
        previousBitmap: Bitmap? = null,
        pixelDensity: Float = 1.0f
    ): CombinedAnalysisResult {
        val aiResult = runInference(bitmap)
        val abcdeResult = abcdeAnalyzer.analyzeMole(bitmap, previousBitmap, pixelDensity)
        return combineResults(aiResult.probability, aiResult.riskLevel, aiResult.confidence, abcdeResult)
    }

    /**
     * Ejecuta únicamente la inferencia del modelo LiteRT sobre la imagen.
     *
     * @throws AnalysisError.AIModelError si el modelo no cargó o la inferencia
     * falla. Nunca devuelve valores inventados.
     */
    fun runInference(bitmap: Bitmap): AIInferenceResult {
        val interpreter = this.interpreter
            ?: throw AnalysisError.AIModelError("Modelo no disponible (no se pudo cargar)")

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)
        try {
            val inputBuffer = ByteBuffer.allocateDirect(4 * IMAGE_SIZE * IMAGE_SIZE * 3)
            inputBuffer.order(ByteOrder.nativeOrder())
            val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
            resizedBitmap.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                // Normalización a [-1, 1], como en el entrenamiento (EfficientNetV2)
                inputBuffer.putFloat((r / 127.5f) - 1.0f)
                inputBuffer.putFloat((g / 127.5f) - 1.0f)
                inputBuffer.putFloat((b / 127.5f) - 1.0f)
            }
            inputBuffer.rewind()

            val probability = try {
                synchronized(inferenceLock) {
                    val outputTensor = interpreter.getOutputTensor(0)
                    val outputBuffer = ByteBuffer
                        .allocateDirect(outputTensor.numBytes())
                        .order(ByteOrder.nativeOrder())
                    interpreter.run(inputBuffer, outputBuffer)
                    outputBuffer.rewind()
                    val floatOutput = outputBuffer.asFloatBuffer()
                    val outputArray = FloatArray(floatOutput.remaining())
                    floatOutput.get(outputArray)
                    when (outputArray.size) {
                        1 -> outputArray[0]
                        2 -> outputArray[1]
                        else -> outputArray.last()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error durante la inferencia del modelo: ${e.message}", e)
                throw AnalysisError.AIModelError("Fallo en la inferencia: ${e.message}")
            }

            if (!probability.isFinite() || probability < 0f || probability > 1f) {
                throw AnalysisError.AIModelError("Salida del modelo fuera de rango: $probability")
            }

            return AIInferenceResult(
                probability = probability,
                riskLevel = riskLevelForScore(probability),
                confidence = calculateConfidence(probability)
            )
        } finally {
            if (resizedBitmap !== bitmap) {
                resizedBitmap.recycle()
            }
        }
    }

    fun combineResults(
        probability: Float,
        riskLevel: RiskLevel,
        confidence: Float,
        abcdeResult: ABCDEAnalyzerOpenCV.ABCDEResult
    ): CombinedAnalysisResult {
        val aiResult = AIInferenceResult(probability, riskLevel, confidence)
        val combinedScore = calculateCombinedScore(aiResult, abcdeResult)
        val combinedRiskLevel = riskLevelForScore(combinedScore)
        val recommendation = generateRecommendation(combinedRiskLevel, aiResult, abcdeResult)
        val urgencyLevel = determineUrgency(
            combinedRiskLevel,
            abcdeResult,
            probability > AI_THRESHOLD_HIGH
        )
        val explanations = generateExplanations(aiResult, abcdeResult)
        return CombinedAnalysisResult(
            aiProbability = probability,
            aiRiskLevel = riskLevel,
            aiConfidence = confidence,
            abcdeResult = abcdeResult,
            combinedScore = combinedScore,
            combinedRiskLevel = combinedRiskLevel,
            recommendation = recommendation,
            shouldMonitor = combinedScore > 0.2f || abcdeResult.evolutionScore != null,
            urgencyLevel = urgencyLevel,
            explanations = explanations
        )
    }

    private fun calculateCombinedScore(
        aiResult: AIInferenceResult,
        abcdeResult: ABCDEAnalyzerOpenCV.ABCDEResult
    ): Float {
        val normalizedAbcdeScore = minOf(1f, abcdeResult.totalScore / 11.9f)
        var combinedScore = (aiResult.probability * AI_WEIGHT) +
                (normalizedAbcdeScore * ABCDE_WEIGHT)
        if (abcdeResult.details.colorDetails.hasBlueWhite) {
            combinedScore *= 1.2f
        }
        if (abcdeResult.diameterScore >= 3f) {
            combinedScore *= 1.1f
        }
        if (abcdeResult.evolutionScore != null && abcdeResult.evolutionScore > 2f) {
            combinedScore *= 1.3f
        }
        if (abcdeResult.details.borderDetails.irregularityIndex > 0.3f) {
            combinedScore *= 1.15f
        }
        return minOf(1f, combinedScore)
    }

    private fun generateExplanations(
        aiResult: AIInferenceResult,
        abcdeResult: ABCDEAnalyzerOpenCV.ABCDEResult
    ): List<String> {
        val explanations = mutableListOf<String>()
        explanations.add(
            "IA: Probabilidad de melanoma del ${(aiResult.probability * 100).toInt()}% " +
                    "(confianza: ${(aiResult.confidence * 100).toInt()}%)"
        )
        val asymmetryIcon = when {
            abcdeResult.asymmetryScore == 0f -> "✅"
            abcdeResult.asymmetryScore <= 1f -> "✓"
            else -> "⚠"
        }
        explanations.add(
            "$asymmetryIcon Asimetría: ${abcdeResult.details.asymmetryDetails.description}"
        )
        val borderIcon = when {
            abcdeResult.details.borderDetails.irregularityIndex < 0.1f -> "✅"
            abcdeResult.details.borderDetails.irregularityIndex < 0.25f -> "✓"
            else -> "⚠"
        }
        explanations.add(
            "$borderIcon Bordes: ${abcdeResult.details.borderDetails.description} " +
                    "(Irregularidad: ${(abcdeResult.details.borderDetails.irregularityIndex * 100).toInt()}%)"
        )
        val colorIcon = when {
            abcdeResult.details.colorDetails.hasBlueWhite -> "🔴"
            abcdeResult.colorScore > 3 -> "⚠"
            else -> "✓"
        }
        explanations.add(
            "$colorIcon Color: ${abcdeResult.details.colorDetails.description}"
        )
        val diameterIcon = if (abcdeResult.diameterScore == 0f) "✅" else "⚠"
        explanations.add(
            "$diameterIcon Diámetro: ${abcdeResult.details.diameterDetails.description}"
        )
        abcdeResult.details.evolutionDetails?.let { evolution ->
            val evolutionIcon = if (abcdeResult.evolutionScore!! <= 1) "✓" else "⚠"
            explanations.add(
                "$evolutionIcon Evolución: ${evolution.description}"
            )
        }
        return explanations
    }

    private fun calculateConfidence(probability: Float): Float {
        val distanceFromThreshold = abs(probability - OPTIMAL_THRESHOLD)
        return when {
            probability < 0.1f || probability > 0.9f -> 0.95f
            probability < 0.15f || probability > 0.85f -> 0.90f
            distanceFromThreshold > 0.25f -> 0.85f
            distanceFromThreshold > 0.15f -> 0.75f
            distanceFromThreshold > 0.08f -> 0.65f
            distanceFromThreshold > 0.04f -> 0.55f
            else -> 0.45f
        }
    }

    private fun riskLevelForScore(score: Float): RiskLevel {
        return when {
            score < 0.2f -> RiskLevel.VERY_LOW
            score < 0.4f -> RiskLevel.LOW
            score < 0.6f -> RiskLevel.MEDIUM
            score < 0.8f -> RiskLevel.HIGH
            else -> RiskLevel.VERY_HIGH
        }
    }

    private fun generateRecommendation(
        riskLevel: RiskLevel,
        aiResult: AIInferenceResult,
        abcdeResult: ABCDEAnalyzerOpenCV.ABCDEResult
    ): String {
        return when (riskLevel) {
            RiskLevel.VERY_LOW -> {
                "✅ Aspecto normal. Continúe con autoexámenes regulares cada 3 meses."
            }
            RiskLevel.LOW -> {
                "👀 Lunar de bajo riesgo. Fotografíe mensualmente para detectar cambios."
            }
            RiskLevel.MEDIUM -> {
                buildString {
                    append("⚠️ Riesgo moderado detectado. ")
                    if (abcdeResult.evolutionScore != null && abcdeResult.evolutionScore > 1) {
                        append("Se observan cambios respecto a la imagen anterior. ")
                    }
                    append("Recomendamos evaluación dermatológica en los próximos 1-2 meses.")
                }
            }
            RiskLevel.HIGH -> {
                buildString {
                    append("🔶 Riesgo alto identificado. ")
                    if (aiResult.confidence > 0.8f) {
                        append("El análisis de IA muestra características preocupantes. ")
                    }
                    append("Consulte con un dermatólogo en las próximas 2-4 semanas.")
                }
            }
            RiskLevel.VERY_HIGH -> {
                "🔴 Riesgo muy alto. Múltiples características de alarma detectadas. " +
                        "Solicite cita con dermatólogo lo antes posible (urgente)."
            }
        }
    }

    private fun determineUrgency(
        riskLevel: RiskLevel,
        abcdeResult: ABCDEAnalyzerOpenCV.ABCDEResult,
        highAIProbability: Boolean
    ): UrgencyLevel {
        val criticalFactors = listOf(
            abcdeResult.details.colorDetails.hasBlueWhite,
            abcdeResult.details.colorDetails.hasRedBlueCombination,
            abcdeResult.diameterScore >= 4f,
            abcdeResult.evolutionScore?.let { it >= 2f } ?: false,
            abcdeResult.borderScore >= 6f
        ).count { it }
        return when {
            riskLevel == RiskLevel.VERY_HIGH || criticalFactors >= 3 ->
                UrgencyLevel.URGENT
            riskLevel == RiskLevel.HIGH || (criticalFactors >= 2 && highAIProbability) ->
                UrgencyLevel.CONSULT
            riskLevel == RiskLevel.MEDIUM || criticalFactors >= 1 ->
                UrgencyLevel.MONITOR
            else ->
                UrgencyLevel.ROUTINE
        }
    }

    private fun loadModel() {
        try {
            val model = loadMappedModel()
            val options = Interpreter.Options().apply {
                numThreads = 4
                useNNAPI = false
            }
            interpreter = Interpreter(model, options)
            Log.d(TAG, "Modelo cargado exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando modelo: ${e.message}", e)
        }
    }

    private fun loadMappedModel(): MappedByteBuffer {
        return context.assets.openFd(MODEL_PATH).use { asset ->
            FileInputStream(asset.fileDescriptor).channel.use { channel ->
                channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    asset.startOffset,
                    asset.declaredLength
                )
            }
        }
    }

    fun close() {
        synchronized(inferenceLock) {
            interpreter?.close()
            interpreter = null
        }
    }
}

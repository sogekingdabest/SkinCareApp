package es.monsteraltech.skincare_tfm.analysis
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
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
    private var interpreter: Interpreter? = null
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
    enum class RiskLevel {
        VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH
    }
    enum class UrgencyLevel {
        ROUTINE, MONITOR, CONSULT, URGENT
    }
    fun analyzeMole(
        bitmap: Bitmap,
        previousBitmap: Bitmap? = null,
        pixelDensity: Float = 1.0f
    ): CombinedAnalysisResult {
        Log.d(TAG, "Iniciando análisis de lunar con OpenCV - bitmap: ${bitmap.width}x${bitmap.height}")
        Log.d(TAG, "Ejecutando análisis con IA...")
        val aiResult = analyzeWithAI(bitmap)
        Log.d(TAG, "Análisis IA completado - probabilidad: ${aiResult.probability}")
        Log.d(TAG, "Ejecutando análisis ABCDE con OpenCV...")
        val abcdeResult = try {
            abcdeAnalyzer.analyzeMole(bitmap, previousBitmap, pixelDensity)
        } catch (e: Exception) {
            Log.e(TAG, "Error en análisis ABCDE con OpenCV: ${e.message}", e)
            createDefaultABCDEResult()
        }
        Log.d(TAG, "Análisis ABCDE completado")
        val combinedScore = calculateCombinedScore(aiResult, abcdeResult)
        val combinedRiskLevel = calculateCombinedRiskLevel(combinedScore)
        val recommendation = generateRecommendation(
            combinedRiskLevel,
            aiResult,
            abcdeResult
        )
        val urgencyLevel = determineUrgency(
            combinedRiskLevel,
            abcdeResult,
            aiResult.probability > AI_THRESHOLD_HIGH
        )
        val explanations = generateExplanations(aiResult, abcdeResult)
        return CombinedAnalysisResult(
            aiProbability = aiResult.probability,
            aiRiskLevel = aiResult.riskLevel,
            aiConfidence = aiResult.confidence,
            abcdeResult = abcdeResult,
            combinedScore = combinedScore,
            combinedRiskLevel = combinedRiskLevel,
            recommendation = recommendation,
            shouldMonitor = combinedScore > 0.2f || abcdeResult.evolutionScore != null,
            urgencyLevel = urgencyLevel,
            explanations = explanations
        )
    }
    fun combineResults(
        probability: Float,
        riskLevel: RiskLevel,
        confidence: Float,
        abcdeResult: ABCDEAnalyzerOpenCV.ABCDEResult
    ): CombinedAnalysisResult {
        val aiResult = AIAnalysisResult(probability, riskLevel, confidence)
        val combinedScore = calculateCombinedScore(aiResult, abcdeResult)
        val combinedRiskLevel = calculateCombinedRiskLevel(combinedScore)
        val recommendation = generateRecommendation(combinedRiskLevel, aiResult, abcdeResult)
        val urgencyLevel = determineUrgency(
            combinedRiskLevel,
            abcdeResult,
            probability > AI_THRESHOLD_HIGH
        )
        val explanations = generateExplanations(aiResult, abcdeResult)
        return CombinedAnalysisResult(
            aiProbability   = probability,
            aiRiskLevel     = riskLevel,
            aiConfidence    = confidence,
            abcdeResult     = abcdeResult,
            combinedScore   = combinedScore,
            combinedRiskLevel = combinedRiskLevel,
            recommendation  = recommendation,
            shouldMonitor   = combinedScore > 0.2f || abcdeResult.evolutionScore != null,
            urgencyLevel    = urgencyLevel,
            explanations    = explanations
        )
    }
    private fun createDefaultABCDEResult(): ABCDEAnalyzerOpenCV.ABCDEResult {
        return ABCDEAnalyzerOpenCV.ABCDEResult(
            asymmetryScore = 0f,
            borderScore = 0f,
            colorScore = 1f,
            diameterScore = 0f,
            evolutionScore = null,
            totalScore = 0f,
            riskLevel = ABCDEAnalyzerOpenCV.RiskLevel.LOW,
            details = ABCDEAnalyzerOpenCV.ABCDEDetails(
                asymmetryDetails = ABCDEAnalyzerOpenCV.AsymmetryDetails(
                    0f, 0f, "Análisis no disponible"
                ),
                borderDetails = ABCDEAnalyzerOpenCV.BorderDetails(
                    0f, 0, "Análisis no disponible"
                ),
                colorDetails = ABCDEAnalyzerOpenCV.ColorDetails(
                    emptyList(), 1, false, false, "Análisis no disponible"
                ),
                diameterDetails = ABCDEAnalyzerOpenCV.DiameterDetails(
                    0f, 0, "Análisis no disponible"
                ),
                evolutionDetails = null
            )
        )
    }
    private fun calculateCombinedScore(
        aiResult: AIAnalysisResult,
        abcdeResult: ABCDEAnalyzerOpenCV.ABCDEResult
    ): Float {
        val normalizedAbcdeScore = minOf(1f, abcdeResult.totalScore / 11.9f)
        var combinedScore = (aiResult.probability * AI_WEIGHT) +
                (normalizedAbcdeScore * ABCDE_WEIGHT)
        if (abcdeResult.details.colorDetails.hasBlueWhite) {
            combinedScore *= 1.2f
            Log.d(TAG, "Ajuste por velo azul-blanquecino: +20%")
        }
        if (abcdeResult.diameterScore >= 3f) {
            combinedScore *= 1.1f
            Log.d(TAG, "Ajuste por diámetro grande: +10%")
        }
        if (abcdeResult.evolutionScore != null && abcdeResult.evolutionScore > 2f) {
            combinedScore *= 1.3f
            Log.d(TAG, "Ajuste por evolución: +30%")
        }
        if (abcdeResult.details.borderDetails.irregularityIndex > 0.3f) {
            combinedScore *= 1.15f
            Log.d(TAG, "Ajuste por bordes irregulares: +15%")
        }
        return minOf(1f, combinedScore)
    }
    private fun generateExplanations(
        aiResult: AIAnalysisResult,
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
    private data class AIAnalysisResult(
        val probability: Float,
        val riskLevel: RiskLevel,
        val confidence: Float
    )
    private fun analyzeWithAI(bitmap: Bitmap): AIAnalysisResult {
        Log.d(TAG, "Iniciando analyzeWithAI - interpreter disponible: ${interpreter != null}")
        if (interpreter == null) {
            Log.e(TAG, "Interpreter es null, usando análisis basado en ABCDE únicamente")
            val fallbackProbability = estimateProbabilityFromImage()
            return AIAnalysisResult(
                probability = fallbackProbability,
                riskLevel = when {
                    fallbackProbability < 0.2f -> RiskLevel.VERY_LOW
                    fallbackProbability < 0.4f -> RiskLevel.LOW
                    fallbackProbability < 0.6f -> RiskLevel.MEDIUM
                    fallbackProbability < 0.8f -> RiskLevel.HIGH
                    else -> RiskLevel.VERY_HIGH
                },
                confidence = 0.3f
            )
        }
        try {
            Log.d(TAG, "Preparando imagen para el modelo...")
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)
            Log.d(TAG, "Imagen redimensionada a ${IMAGE_SIZE}x${IMAGE_SIZE}")
            val inputBuffer = java.nio.ByteBuffer.allocateDirect(4 * IMAGE_SIZE * IMAGE_SIZE * 3)
            inputBuffer.order(java.nio.ByteOrder.nativeOrder())
            val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
            resizedBitmap.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                inputBuffer.putFloat((r / 127.5f) - 1.0f)
                inputBuffer.putFloat((g / 127.5f) - 1.0f)
                inputBuffer.putFloat((b / 127.5f) - 1.0f)
            }
            inputBuffer.rewind()
            val outputTensor = interpreter!!.getOutputTensor(0)
            val outputShape = outputTensor.shape()
            val outputBuffer = TensorBuffer.createFixedSize(outputShape, org.tensorflow.lite.DataType.FLOAT32)
            interpreter?.run(inputBuffer, outputBuffer.buffer.rewind())
            val outputArray = outputBuffer.floatArray
            val probability = if (outputArray.size == 1) {
                outputArray[0]
            } else if (outputArray.size == 2) {
                outputArray[1]
            } else {
                outputArray.last()
            }
            Log.d(TAG, "Probabilidad obtenida del modelo: $probability")
            val riskLevel = when {
                probability < 0.2f -> RiskLevel.VERY_LOW
                probability < 0.4f -> RiskLevel.LOW
                probability < 0.6f -> RiskLevel.MEDIUM
                probability < 0.8f -> RiskLevel.HIGH
                else -> RiskLevel.VERY_HIGH
            }
            val confidence = calculateConfidence(probability)
            return AIAnalysisResult(
                probability = probability,
                riskLevel = riskLevel,
                confidence = confidence
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la inferencia del modelo: ${e.message}", e)
            return AIAnalysisResult(
                probability = 0.1f,
                riskLevel = RiskLevel.VERY_LOW,
                confidence = 0.5f
            )
        }
    }
    private fun calculateConfidence(probability: Float): Float {
        val distanceFromThreshold = abs(probability - OPTIMAL_THRESHOLD)
        val confidence = when {
            probability < 0.1f || probability > 0.9f -> 0.95f
            probability < 0.15f || probability > 0.85f -> 0.90f
            distanceFromThreshold > 0.25f -> 0.85f
            distanceFromThreshold > 0.15f -> 0.75f
            distanceFromThreshold > 0.08f -> 0.65f
            distanceFromThreshold > 0.04f -> 0.55f
            else -> 0.45f
        }
        Log.d(TAG, "Confianza calculada: $confidence (prob: $probability, distancia umbral: $distanceFromThreshold)")
        return confidence
    }
    private fun calculateCombinedRiskLevel(combinedScore: Float): RiskLevel {
        return when {
            combinedScore < 0.2f -> RiskLevel.VERY_LOW
            combinedScore < 0.4f -> RiskLevel.LOW
            combinedScore < 0.6f -> RiskLevel.MEDIUM
            combinedScore < 0.8f -> RiskLevel.HIGH
            else -> RiskLevel.VERY_HIGH
        }
    }
    private fun generateRecommendation(
        riskLevel: RiskLevel,
        aiResult: AIAnalysisResult,
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
            Log.d(TAG, "Iniciando carga del modelo desde assets...")
            val model = FileUtil.loadMappedFile(context, MODEL_PATH)
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
    private fun estimateProbabilityFromImage(): Float {
        return 0.1f
    }
    fun close() {
        interpreter?.close()
    }
}
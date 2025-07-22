// MelanomaAIDetector.kt - Integración del modelo IA con análisis ABCDE
package es.monsteraltech.skincare_tfm.analysis

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import kotlin.math.abs

/**
 * Detector de melanomas que combina IA con análisis ABCDE
 * Implementa la metodología descrita en el TFM
 */
class MelanomaAIDetector(private val context: Context) {

    companion object {
        private const val TAG = "MelanomaAIDetector"
        private const val MODEL_PATH = "melanoma_efficientnet_dynamic.tflite"
        private const val IMAGE_SIZE = 224

        // Umbrales del modelo entrenado
        private const val AI_THRESHOLD_LOW = 0.327f      // Para screening
        private const val AI_THRESHOLD_HIGH = 0.5f        // Para confirmación

        // Pesos para combinar IA + ABCDE
        private const val AI_WEIGHT = 0.6f               // 60% peso IA
        private const val ABCDE_WEIGHT = 0.4f            // 40% peso ABCDE
    }

    private var interpreter: Interpreter? = null
    private val abcdeAnalyzer = ABCDEAnalyzer()

    init {
        loadModel()
    }

    /**
     * Resultado combinado del análisis IA + ABCDE
     */
    data class CombinedAnalysisResult(
        // Resultados IA
        val aiProbability: Float,
        val aiRiskLevel: RiskLevel,
        val aiConfidence: Float,

        // Resultados ABCDE
        val abcdeResult: ABCDEAnalyzer.ABCDEResult,

        // Resultado combinado
        val combinedScore: Float,
        val combinedRiskLevel: RiskLevel,
        val recommendation: String,

        // Detalles para UI
        val shouldMonitor: Boolean,
        val urgencyLevel: UrgencyLevel,
        val explanations: List<String>
    )

    enum class RiskLevel {
        VERY_LOW,    // < 20%
        LOW,         // 20-40%
        MODERATE,    // 40-60%
        HIGH,        // 60-80%
        VERY_HIGH    // > 80%
    }

    enum class UrgencyLevel {
        ROUTINE,     // Seguimiento normal
        MONITOR,     // Vigilar mensualmente
        CONSULT,     // Consultar en 1-3 meses
        URGENT       // Consultar inmediatamente
    }

    /**
     * Analiza un lunar combinando IA y criterios ABCDE
     */
    fun analyzeMole(
        bitmap: Bitmap,
        previousBitmap: Bitmap? = null,
        pixelDensity: Float = 1.0f
    ): CombinedAnalysisResult {
        Log.d(TAG, "Iniciando análisis de lunar - bitmap: ${bitmap.width}x${bitmap.height}")

        // 1. Análisis con IA
        Log.d(TAG, "Ejecutando análisis con IA...")
        val aiResult = analyzeWithAI(bitmap)
        Log.d(TAG, "Análisis IA completado - probabilidad: ${aiResult.probability}")

        // 2. Análisis ABCDE
        val abcdeResult = abcdeAnalyzer.analyzeMole(bitmap, previousBitmap, pixelDensity)

        // 3. Combinar resultados
        val combinedScore = calculateCombinedScore(aiResult, abcdeResult)
        val combinedRiskLevel = calculateCombinedRiskLevel(combinedScore)

        // 4. Generar recomendaciones
        val recommendation = generateRecommendation(
            combinedRiskLevel,
            aiResult,
            abcdeResult
        )

        // 5. Determinar urgencia
        val urgencyLevel = determineUrgency(
            combinedRiskLevel,
            abcdeResult,
            aiResult.probability > AI_THRESHOLD_HIGH
        )

        // 6. Generar explicaciones
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

    /**
     * Resultado del análisis con IA
     */
    private data class AIAnalysisResult(
        val probability: Float,
        val riskLevel: RiskLevel,
        val confidence: Float
    )

    /**
     * Analiza la imagen con el modelo de IA
     */
    private fun analyzeWithAI(bitmap: Bitmap): AIAnalysisResult {
        Log.d(TAG, "Iniciando analyzeWithAI - interpreter disponible: ${interpreter != null}")
        
        if (interpreter == null) {
            Log.e(TAG, "Interpreter es null, usando análisis basado en ABCDE únicamente")
            // En lugar de un valor fijo, usar una estimación basada en características básicas
            val fallbackProbability = estimateProbabilityFromImage(bitmap)
            return AIAnalysisResult(
                probability = fallbackProbability,
                riskLevel = when {
                    fallbackProbability < 0.2f -> RiskLevel.VERY_LOW
                    fallbackProbability < 0.4f -> RiskLevel.LOW
                    fallbackProbability < 0.6f -> RiskLevel.MODERATE
                    fallbackProbability < 0.8f -> RiskLevel.HIGH
                    else -> RiskLevel.VERY_HIGH
                },
                confidence = 0.3f // Baja confianza sin IA
            )
        }

        try {
            Log.d(TAG, "Preparando imagen para el modelo...")
            
            // Redimensionar bitmap manualmente
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)
            Log.d(TAG, "Imagen redimensionada de ${bitmap.width}x${bitmap.height} a ${IMAGE_SIZE}x${IMAGE_SIZE}")
            
            // Crear buffer de entrada manualmente con normalización
            val inputBuffer = java.nio.ByteBuffer.allocateDirect(4 * IMAGE_SIZE * IMAGE_SIZE * 3)
            inputBuffer.order(java.nio.ByteOrder.nativeOrder())
            
            Log.d(TAG, "Convirtiendo imagen a buffer normalizado...")
            val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
            resizedBitmap.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)
            
            // Convertir píxeles a float normalizado [0,1]
            for (pixel in pixels) {
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f
                
                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }
            
            inputBuffer.rewind()
            Log.d(TAG, "Buffer de entrada creado correctamente, tamaño: ${inputBuffer.remaining()} bytes")

            // Verificar dimensiones del tensor de entrada
            val inputTensor = interpreter!!.getInputTensor(0)
            val outputTensor = interpreter!!.getOutputTensor(0)
            Log.d(TAG, "Verificando dimensiones - Input: ${inputTensor.shape().contentToString()}, Output: ${outputTensor.shape().contentToString()}")

            // Preparar output buffer con las dimensiones correctas del modelo
            Log.d(TAG, "Creando output buffer...")
            val outputShape = outputTensor.shape()
            val outputBuffer = TensorBuffer.createFixedSize(outputShape, org.tensorflow.lite.DataType.FLOAT32)

            // Ejecutar inferencia
            Log.d(TAG, "Ejecutando inferencia del modelo...")
            Log.d(TAG, "Input buffer size: ${inputBuffer.remaining()}")
            Log.d(TAG, "Expected input size: ${inputTensor.numBytes()}")
            
            interpreter?.run(inputBuffer, outputBuffer.buffer.rewind())
            Log.d(TAG, "Inferencia completada")

            val outputArray = outputBuffer.floatArray
            Log.d(TAG, "Output array length: ${outputArray.size}")
            Log.d(TAG, "Output values: ${outputArray.contentToString()}")
            
            // Para modelos de clasificación binaria, tomar el primer valor o aplicar softmax si es necesario
            val probability = if (outputArray.size == 1) {
                outputArray[0]
            } else if (outputArray.size == 2) {
                // Si el modelo devuelve [prob_benigno, prob_maligno], tomar el segundo
                outputArray[1]
            } else {
                // Si hay más clases, tomar la probabilidad de melanoma (asumiendo que es la última)
                outputArray.last()
            }
            
            Log.d(TAG, "Probabilidad final obtenida del modelo: $probability")

            // Calcular nivel de riesgo basado en IA
            val riskLevel = when {
                probability < 0.2f -> RiskLevel.VERY_LOW
                probability < 0.4f -> RiskLevel.LOW
                probability < 0.6f -> RiskLevel.MODERATE
                probability < 0.8f -> RiskLevel.HIGH
                else -> RiskLevel.VERY_HIGH
            }

            // Calcular confianza basada en distancia al umbral
            val confidence = calculateConfidence(probability)

            Log.d(TAG, "Análisis IA completado exitosamente - riskLevel: $riskLevel, confidence: $confidence")
            return AIAnalysisResult(
                probability = probability,
                riskLevel = riskLevel,
                confidence = confidence
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la inferencia del modelo: ${e.message}", e)
            // Retornar resultado por defecto en caso de error
            return AIAnalysisResult(
                probability = 0.1f,
                riskLevel = RiskLevel.VERY_LOW,
                confidence = 0.5f
            )
        }
    }

    /**
     * Calcula la confianza de la predicción
     */
    private fun calculateConfidence(probability: Float): Float {
        // Mayor confianza cuando está lejos de los umbrales de decisión
        val distanceFromThreshold = minOf(
            abs(probability - AI_THRESHOLD_LOW),
            abs(probability - AI_THRESHOLD_HIGH)
        )

        return when {
            distanceFromThreshold > 0.3f -> 0.9f
            distanceFromThreshold > 0.2f -> 0.8f
            distanceFromThreshold > 0.1f -> 0.7f
            else -> 0.6f
        }
    }

    /**
     * Combina los scores de IA y ABCDE
     */
    private fun calculateCombinedScore(
        aiResult: AIAnalysisResult,
        abcdeResult: ABCDEAnalyzer.ABCDEResult
    ): Float {
        // Normalizar score ABCDE (0-11.9) a (0-1)
        val normalizedAbcdeScore = minOf(1f, abcdeResult.totalScore / 11.9f)

        // Combinar con pesos
        var combinedScore = (aiResult.probability * AI_WEIGHT) +
                (normalizedAbcdeScore * ABCDE_WEIGHT)

        // Ajustes por factores críticos
        if (abcdeResult.details.colorDetails.hasBlueWhite) {
            combinedScore *= 1.2f  // Aumentar 20% si hay velo azul-blanquecino
        }

        if (abcdeResult.diameterScore >= 3f) {
            combinedScore *= 1.1f  // Aumentar 10% si diámetro > 10mm
        }

        if (abcdeResult.evolutionScore != null && abcdeResult.evolutionScore > 2f) {
            combinedScore *= 1.3f  // Aumentar 30% si hay evolución significativa
        }

        return minOf(1f, combinedScore)
    }

    /**
     * Calcula el nivel de riesgo combinado
     */
    private fun calculateCombinedRiskLevel(combinedScore: Float): RiskLevel {
        return when {
            combinedScore < 0.2f -> RiskLevel.VERY_LOW
            combinedScore < 0.4f -> RiskLevel.LOW
            combinedScore < 0.6f -> RiskLevel.MODERATE
            combinedScore < 0.8f -> RiskLevel.HIGH
            else -> RiskLevel.VERY_HIGH
        }
    }

    /**
     * Genera recomendación basada en el análisis combinado
     */
    private fun generateRecommendation(
        riskLevel: RiskLevel,
        aiResult: AIAnalysisResult,
        abcdeResult: ABCDEAnalyzer.ABCDEResult
    ): String {
        return when (riskLevel) {
            RiskLevel.VERY_LOW -> {
                "✅ Aspecto normal. Continúe con autoexámenes regulares cada 3 meses."
            }

            RiskLevel.LOW -> {
                "👀 Lunar de bajo riesgo. Fotografíe mensualmente para detectar cambios."
            }

            RiskLevel.MODERATE -> {
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

    /**
     * Determina el nivel de urgencia
     */
    private fun determineUrgency(
        riskLevel: RiskLevel,
        abcdeResult: ABCDEAnalyzer.ABCDEResult,
        highAIProbability: Boolean
    ): UrgencyLevel {
        // Factores críticos que aumentan urgencia
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

            riskLevel == RiskLevel.MODERATE || criticalFactors >= 1 ->
                UrgencyLevel.MONITOR

            else ->
                UrgencyLevel.ROUTINE
        }
    }

    /**
     * Genera explicaciones detalladas del análisis
     */
    private fun generateExplanations(
        aiResult: AIAnalysisResult,
        abcdeResult: ABCDEAnalyzer.ABCDEResult
    ): List<String> {
        val explanations = mutableListOf<String>()

        // Explicación IA
        explanations.add(
            "🤖 IA: Probabilidad de melanoma del ${(aiResult.probability * 100).toInt()}% " +
                    "(confianza: ${(aiResult.confidence * 100).toInt()}%)"
        )

        // Explicaciones ABCDE

        // A - Asimetría
        val asymmetryIcon = if (abcdeResult.asymmetryScore <= 1) "✓" else "⚠"
        explanations.add(
            "$asymmetryIcon Asimetría: ${abcdeResult.details.asymmetryDetails.description}"
        )

        // B - Bordes
        val borderIcon = if (abcdeResult.borderScore <= 4) "✓" else "⚠"
        explanations.add(
            "$borderIcon Bordes: ${abcdeResult.details.borderDetails.description}"
        )

        // C - Color
        val colorIcon = when {
            abcdeResult.details.colorDetails.hasBlueWhite -> "🔴"
            abcdeResult.colorScore > 3 -> "⚠"
            else -> "✓"
        }
        explanations.add(
            "$colorIcon Color: ${abcdeResult.details.colorDetails.description}"
        )

        // D - Diámetro
        val diameterIcon = if (abcdeResult.diameterScore == 0f) "✓" else "⚠"
        explanations.add(
            "$diameterIcon Diámetro: ${abcdeResult.details.diameterDetails.description}"
        )

        // E - Evolución
        abcdeResult.details.evolutionDetails?.let { evolution ->
            val evolutionIcon = if (abcdeResult.evolutionScore!! <= 1) "✓" else "⚠"
            explanations.add(
                "$evolutionIcon Evolución: ${evolution.description}"
            )
        }

        return explanations
    }

    /**
     * Carga el modelo TensorFlow Lite
     */
    private fun loadModel() {
        try {
            Log.d(TAG, "Iniciando carga del modelo desde assets...")
            Log.d(TAG, "Ruta del modelo: $MODEL_PATH")
            
            // Verificar si el archivo existe en assets
            val assetManager = context.assets
            try {
                val inputStream = assetManager.open(MODEL_PATH)
                val fileSize = inputStream.available()
                inputStream.close()
                Log.d(TAG, "Archivo del modelo encontrado en assets, tamaño: $fileSize bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Error: Archivo del modelo no encontrado en assets: ${e.message}")
                throw e
            }
            
            val model = FileUtil.loadMappedFile(context, MODEL_PATH)
            Log.d(TAG, "Modelo cargado desde $MODEL_PATH")
            
            val options = Interpreter.Options().apply {
                Log.d(TAG, "Configurando opciones del intérprete...")
                setNumThreads(4)
                // Disable NNAPI for dynamic models - use CPU instead
                setUseNNAPI(false)
                Log.d(TAG, "NNAPI deshabilitado, usando CPU")
                // Optionally enable GPU delegate if available
                // setUseGPU(true)  // Uncomment if you want to try GPU
            }
            
            Log.d(TAG, "Creando intérprete...")
            interpreter = Interpreter(model, options)
            
            // Verificar información del modelo
            val inputTensor = interpreter!!.getInputTensor(0)
            val outputTensor = interpreter!!.getOutputTensor(0)
            Log.d(TAG, "Modelo cargado exitosamente")
            Log.d(TAG, "Input shape: ${inputTensor.shape().contentToString()}")
            Log.d(TAG, "Output shape: ${outputTensor.shape().contentToString()}")
            Log.d(TAG, "Input type: ${inputTensor.dataType()}")
            Log.d(TAG, "Output type: ${outputTensor.dataType()}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando modelo: ${e.message}", e)
            Log.e(TAG, "Stack trace completo:", e)
            throw e
        }
    }

    /**
     * Estima la probabilidad basándose en características básicas de la imagen
     * Se usa como fallback cuando el modelo de IA no está disponible
     */
    private fun estimateProbabilityFromImage(bitmap: Bitmap): Float {
        Log.d(TAG, "Estimando probabilidad usando análisis básico de imagen...")
        
        try {
            // Análisis básico de colores
            val colorVariance = calculateColorVariance(bitmap)
            val darkPixelRatio = calculateDarkPixelRatio(bitmap)
            val edgeComplexity = calculateEdgeComplexity(bitmap)
            
            // Combinar factores para estimar riesgo
            var estimatedProbability = 0.1f // Base mínima
            
            // Factor de varianza de color (más colores = mayor riesgo)
            if (colorVariance > 50f) estimatedProbability += 0.2f
            if (colorVariance > 100f) estimatedProbability += 0.1f
            
            // Factor de píxeles oscuros (lunares muy oscuros pueden ser más riesgosos)
            if (darkPixelRatio > 0.3f) estimatedProbability += 0.15f
            if (darkPixelRatio > 0.6f) estimatedProbability += 0.1f
            
            // Factor de complejidad de bordes (bordes irregulares = mayor riesgo)
            if (edgeComplexity > 0.5f) estimatedProbability += 0.2f
            if (edgeComplexity > 0.8f) estimatedProbability += 0.15f
            
            // Limitar entre 0.05 y 0.7 (no queremos dar falsas alarmas muy altas sin IA)
            val finalProbability = minOf(0.7f, maxOf(0.05f, estimatedProbability))
            
            Log.d(TAG, "Estimación completada - colorVariance: $colorVariance, darkPixelRatio: $darkPixelRatio, edgeComplexity: $edgeComplexity, probabilidad: $finalProbability")
            
            return finalProbability
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en estimación básica: ${e.message}")
            return 0.1f // Valor por defecto muy conservador
        }
    }
    
    private fun calculateColorVariance(bitmap: Bitmap): Float {
        val colors = mutableListOf<Int>()
        val sampleSize = minOf(100, bitmap.width * bitmap.height / 100) // Muestrear para eficiencia
        
        for (i in 0 until sampleSize) {
            val x = (Math.random() * bitmap.width).toInt()
            val y = (Math.random() * bitmap.height).toInt()
            colors.add(bitmap.getPixel(x, y))
        }
        
        // Calcular varianza de colores simplificada
        val uniqueColors = colors.toSet().size
        return (uniqueColors.toFloat() / sampleSize) * 255f
    }
    
    private fun calculateDarkPixelRatio(bitmap: Bitmap): Float {
        var darkPixels = 0
        var totalPixels = 0
        val sampleSize = minOf(1000, bitmap.width * bitmap.height / 50)
        
        for (i in 0 until sampleSize) {
            val x = (Math.random() * bitmap.width).toInt()
            val y = (Math.random() * bitmap.height).toInt()
            val pixel = bitmap.getPixel(x, y)
            
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val brightness = (r + g + b) / 3
            
            if (brightness < 100) darkPixels++ // Considerar oscuro si brightness < 100
            totalPixels++
        }
        
        return if (totalPixels > 0) darkPixels.toFloat() / totalPixels else 0f
    }
    
    private fun calculateEdgeComplexity(bitmap: Bitmap): Float {
        // Análisis muy simplificado de complejidad de bordes
        var edgePixels = 0
        var totalChecked = 0
        val step = maxOf(1, bitmap.width / 50) // Muestrear cada 'step' píxeles
        
        for (y in step until bitmap.height - step step step) {
            for (x in step until bitmap.width - step step step) {
                val center = bitmap.getPixel(x, y)
                val right = bitmap.getPixel(x + step, y)
                val bottom = bitmap.getPixel(x, y + step)
                
                // Si hay diferencia significativa con vecinos, es un borde
                if (colorDifference(center, right) > 50 || colorDifference(center, bottom) > 50) {
                    edgePixels++
                }
                totalChecked++
            }
        }
        
        return if (totalChecked > 0) edgePixels.toFloat() / totalChecked else 0f
    }
    
    private fun colorDifference(color1: Int, color2: Int): Int {
        val r1 = (color1 shr 16) and 0xFF
        val g1 = (color1 shr 8) and 0xFF
        val b1 = color1 and 0xFF
        val r2 = (color2 shr 16) and 0xFF
        val g2 = (color2 shr 8) and 0xFF
        val b2 = color2 and 0xFF
        
        return kotlin.math.abs(r1 - r2) + kotlin.math.abs(g1 - g2) + kotlin.math.abs(b1 - b2)
    }

    /**
     * Libera recursos
     */
    fun close() {
        interpreter?.close()
    }

    /**
     * Datos para tracking temporal
     */
    data class TrackingData(
        val moleId: String,
        val timestamp: Long,
        val combinedScore: Float,
        val aiProbability: Float,
        val abcdeTotalScore: Float,
        val imagePath: String
    )

    /**
     * Analiza tendencias en el tiempo
     */
    fun analyzeTrend(trackingHistory: List<TrackingData>): TrendAnalysis {
        if (trackingHistory.size < 2) {
            return TrendAnalysis(
                trend = Trend.INSUFFICIENT_DATA,
                changeRate = 0f,
                recommendation = "Se necesitan al menos 2 mediciones para analizar tendencias"
            )
        }

        // Calcular cambio en scores
        val recentScores = trackingHistory.takeLast(5).map { it.combinedScore }
        val avgRecentScore = recentScores.average().toFloat()
        val firstScore = trackingHistory.first().combinedScore

        val changeRate = (avgRecentScore - firstScore) / firstScore

        val trend = when {
            changeRate > 0.3f -> Trend.WORSENING_FAST
            changeRate > 0.1f -> Trend.WORSENING_SLOW
            changeRate < -0.1f -> Trend.IMPROVING
            else -> Trend.STABLE
        }

        val recommendation = when (trend) {
            Trend.WORSENING_FAST ->
                "⚠️ Deterioro rápido detectado. Consulte dermatólogo urgentemente."
            Trend.WORSENING_SLOW ->
                "📈 Tendencia negativa. Aumente frecuencia de monitoreo."
            Trend.STABLE ->
                "📊 Sin cambios significativos. Continúe monitoreo regular."
            Trend.IMPROVING ->
                "📉 Mejora observada. Mantenga seguimiento."
            Trend.INSUFFICIENT_DATA ->
                "📷 Tome más fotos para establecer tendencia."
        }

        return TrendAnalysis(trend, changeRate, recommendation)
    }

    data class TrendAnalysis(
        val trend: Trend,
        val changeRate: Float,
        val recommendation: String
    )

    enum class Trend {
        WORSENING_FAST,
        WORSENING_SLOW,
        STABLE,
        IMPROVING,
        INSUFFICIENT_DATA
    }
}
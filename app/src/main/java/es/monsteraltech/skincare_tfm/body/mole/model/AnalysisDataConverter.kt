package es.monsteraltech.skincare_tfm.body.mole.model

import com.google.firebase.Timestamp
import es.monsteraltech.skincare_tfm.analysis.ABCDEAnalyzer
import org.json.JSONObject
import java.util.UUID

/**
 * Utilidad para convertir entre formatos existentes y nuevos modelos de datos
 * Facilita la migración y compatibilidad entre versiones
 */
object AnalysisDataConverter {

    /**
     * Convierte el campo aiResult existente en MoleData a AnalysisData estructurado
     */
    fun fromAiResultString(
        aiResult: String,
        moleId: String,
        imageUrl: String,
        createdAt: Timestamp = Timestamp.now()
    ): AnalysisData? {
        if (aiResult.isEmpty()) return null

        return try {
            // Intentar parsear como JSON si es posible
            val jsonResult = JSONObject(aiResult)
            
            AnalysisData(
                id = UUID.randomUUID().toString(),
                moleId = moleId,
                analysisResult = aiResult,
                aiProbability = jsonResult.optDouble("probability", 0.0).toFloat(),
                aiConfidence = jsonResult.optDouble("confidence", 0.0).toFloat(),
                abcdeScores = parseABCDEScoresFromJson(jsonResult),
                combinedScore = jsonResult.optDouble("combinedScore", 0.0).toFloat(),
                riskLevel = jsonResult.optString("riskLevel", "DESCONOCIDO"),
                recommendation = jsonResult.optString("recommendation", ""),
                imageUrl = imageUrl,
                createdAt = createdAt,
                analysisMetadata = parseMetadataFromJson(jsonResult)
            )
        } catch (e: Exception) {
            // Si no es JSON válido, crear AnalysisData básico con el texto completo
            AnalysisData(
                id = UUID.randomUUID().toString(),
                moleId = moleId,
                analysisResult = aiResult,
                aiProbability = 0f,
                aiConfidence = 0f,
                abcdeScores = ABCDEScores(),
                combinedScore = 0f,
                riskLevel = "DESCONOCIDO",
                recommendation = extractRecommendationFromText(aiResult),
                imageUrl = imageUrl,
                createdAt = createdAt,
                analysisMetadata = mapOf("originalFormat" to "text")
            )
        }
    }

    /**
     * Convierte un resultado de ABCDEAnalyzer a AnalysisData
     */
    fun fromABCDEResult(
        abcdeResult: ABCDEAnalyzer.ABCDEResult,
        moleId: String,
        imageUrl: String,
        aiProbability: Float = 0f,
        aiConfidence: Float = 0f,
        analysisResult: String = ""
    ): AnalysisData {
        return AnalysisData(
            id = UUID.randomUUID().toString(),
            moleId = moleId,
            analysisResult = analysisResult.ifEmpty { formatABCDEResultAsText(abcdeResult) },
            aiProbability = aiProbability,
            aiConfidence = aiConfidence,
            abcdeScores = ABCDEScores.fromABCDEResult(abcdeResult),
            combinedScore = abcdeResult.totalScore,
            riskLevel = abcdeResult.riskLevel.name,
            recommendation = generateRecommendationFromABCDE(abcdeResult),
            imageUrl = imageUrl,
            createdAt = Timestamp.now(),
            analysisMetadata = mapOf(
                "abcdeVersion" to "1.0",
                "analysisType" to "ABCDE_FULL"
            )
        )
    }

    /**
     * Convierte AnalysisData de vuelta al formato aiResult para compatibilidad
     */
    fun toAiResultString(analysisData: AnalysisData): String {
        return try {
            val jsonObject = JSONObject().apply {
                put("probability", analysisData.aiProbability)
                put("confidence", analysisData.aiConfidence)
                put("combinedScore", analysisData.combinedScore)
                put("riskLevel", analysisData.riskLevel)
                put("recommendation", analysisData.recommendation)
                put("abcdeScores", JSONObject().apply {
                    put("asymmetryScore", analysisData.abcdeScores.asymmetryScore)
                    put("borderScore", analysisData.abcdeScores.borderScore)
                    put("colorScore", analysisData.abcdeScores.colorScore)
                    put("diameterScore", analysisData.abcdeScores.diameterScore)
                    analysisData.abcdeScores.evolutionScore?.let { 
                        put("evolutionScore", it) 
                    }
                    put("totalScore", analysisData.abcdeScores.totalScore)
                })
                put("analysisDate", analysisData.createdAt.seconds)
                put("metadata", JSONObject(analysisData.analysisMetadata))
            }
            jsonObject.toString()
        } catch (e: Exception) {
            // Fallback al texto original si existe
            analysisData.analysisResult.ifEmpty { 
                "Análisis realizado el ${analysisData.createdAt.toDate()}" 
            }
        }
    }

    /**
     * Actualiza MoleData con información del nuevo análisis
     */
    fun updateMoleDataWithAnalysis(
        moleData: MoleData,
        analysisData: AnalysisData
    ): MoleData {
        val newAnalysisCount = moleData.analysisCount + 1
        val firstAnalysisDate = moleData.firstAnalysisDate ?: analysisData.createdAt
        
        return moleData.copy(
            analysisCount = newAnalysisCount,
            lastAnalysisDate = analysisData.createdAt,
            firstAnalysisDate = firstAnalysisDate,
            updatedAt = Timestamp.now(),
            // Mantener el aiResult más reciente para compatibilidad
            aiResult = toAiResultString(analysisData)
        )
    }

    // Métodos auxiliares privados

    private fun parseABCDEScoresFromJson(jsonObject: JSONObject): ABCDEScores {
        val abcdeJson = jsonObject.optJSONObject("abcdeScores")
        return if (abcdeJson != null) {
            ABCDEScores(
                asymmetryScore = abcdeJson.optDouble("asymmetryScore", 0.0).toFloat(),
                borderScore = abcdeJson.optDouble("borderScore", 0.0).toFloat(),
                colorScore = abcdeJson.optDouble("colorScore", 0.0).toFloat(),
                diameterScore = abcdeJson.optDouble("diameterScore", 0.0).toFloat(),
                evolutionScore = if (abcdeJson.has("evolutionScore")) {
                    abcdeJson.optDouble("evolutionScore", 0.0).toFloat()
                } else null,
                totalScore = abcdeJson.optDouble("totalScore", 0.0).toFloat()
            )
        } else {
            ABCDEScores()
        }
    }

    private fun parseMetadataFromJson(jsonObject: JSONObject): Map<String, Any> {
        val metadata = mutableMapOf<String, Any>()
        
        jsonObject.optJSONObject("metadata")?.let { metaJson ->
            metaJson.keys().forEach { key ->
                metadata[key] = metaJson.get(key)
            }
        }
        
        // Añadir metadatos adicionales si están disponibles
        if (jsonObject.has("analysisDate")) {
            metadata["originalAnalysisDate"] = jsonObject.optLong("analysisDate")
        }
        
        return metadata
    }

    private fun extractRecommendationFromText(text: String): String {
        // Buscar patrones comunes de recomendaciones en el texto
        val recommendationKeywords = listOf(
            "recomendación", "recomendamos", "sugerimos", "aconsejamos",
            "debe", "debería", "consulte", "visite", "contacte"
        )
        
        val lines = text.split("\n")
        for (line in lines) {
            if (recommendationKeywords.any { keyword ->
                line.lowercase().contains(keyword)
            }) {
                return line.trim()
            }
        }
        
        return "Consulte con un dermatólogo para evaluación profesional"
    }

    private fun formatABCDEResultAsText(result: ABCDEAnalyzer.ABCDEResult): String {
        return buildString {
            appendLine("=== ANÁLISIS ABCDE ===")
            appendLine("Asimetría: ${result.asymmetryScore}/2")
            appendLine("Bordes: ${result.borderScore}/8")
            appendLine("Color: ${result.colorScore}/6")
            appendLine("Diámetro: ${result.diameterScore}/5")
            result.evolutionScore?.let { 
                appendLine("Evolución: $it/3")
            }
            appendLine("Score Total: ${result.totalScore}")
            appendLine("Nivel de Riesgo: ${result.riskLevel}")
            appendLine()
            appendLine("=== DETALLES ===")
            appendLine("Asimetría: ${result.details.asymmetryDetails.description}")
            appendLine("Bordes: ${result.details.borderDetails.description}")
            appendLine("Color: ${result.details.colorDetails.description}")
            appendLine("Diámetro: ${result.details.diameterDetails.description}")
            result.details.evolutionDetails?.let {
                appendLine("Evolución: ${it.description}")
            }
        }
    }

    private fun generateRecommendationFromABCDE(result: ABCDEAnalyzer.ABCDEResult): String {
        return when (result.riskLevel) {
            ABCDEAnalyzer.RiskLevel.LOW -> 
                "Lunar de bajo riesgo. Continúe con autoexámenes regulares y consulte si observa cambios."
            ABCDEAnalyzer.RiskLevel.MODERATE -> 
                "Lunar de riesgo moderado. Se recomienda evaluación dermatológica en las próximas semanas."
            ABCDEAnalyzer.RiskLevel.HIGH -> 
                "Lunar de alto riesgo. Consulte con un dermatólogo lo antes posible para evaluación profesional."
        }
    }
}
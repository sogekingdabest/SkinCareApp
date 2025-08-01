package es.monsteraltech.skincare_tfm.body.mole.parser

import android.util.Log
import es.monsteraltech.skincare_tfm.body.mole.model.ABCDEScores
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Parser para convertir el campo aiResult existente a estructura AnalysisData
 * Maneja diferentes formatos de análisis que pueden existir en los datos legacy
 */
class AnalysisResultParser {
    
    private val TAG = "AnalysisResultParser"
    
    /**
     * Resultado parseado del análisis
     */
    data class ParsedAnalysisResult(
        val aiProbability: Float,
        val aiConfidence: Float,
        val abcdeScores: ABCDEScores,
        val combinedScore: Float,
        val riskLevel: String,
        val recommendation: String
    )
    
    /**
     * Parsea el campo aiResult y extrae la información estructurada
     */
    fun parseAnalysisResult(aiResult: String): ParsedAnalysisResult {
        if (aiResult.isBlank()) {
            return createDefaultResult()
        }
        
        // Intentar diferentes estrategias de parseo
        return try {
            // Estrategia 1: JSON estructurado
            parseJsonFormat(aiResult)
        } catch (e: Exception) {
            try {
                // Estrategia 2: Texto con patrones
                parseTextFormat(aiResult)
            } catch (e2: Exception) {
                try {
                    // Estrategia 3: Formato legacy específico
                    parseLegacyFormat(aiResult)
                } catch (e3: Exception) {
                    Log.w(TAG, "No se pudo parsear aiResult, usando valores por defecto: $aiResult")
                    createDefaultResult()
                }
            }
        }
    }
    
    /**
     * Parsea formato JSON estructurado
     */
    private fun parseJsonFormat(aiResult: String): ParsedAnalysisResult {
        val json = JSONObject(aiResult)
        
        return ParsedAnalysisResult(
            aiProbability = json.optDouble("aiProbability", 0.0).toFloat(),
            aiConfidence = json.optDouble("aiConfidence", 0.0).toFloat(),
            abcdeScores = parseAbcdeScoresFromJson(json),
            combinedScore = json.optDouble("combinedScore", 0.0).toFloat(),
            riskLevel = json.optString("riskLevel", "LOW"),
            recommendation = json.optString("recommendation", "Continuar monitoreo regular")
        )
    }
    
    /**
     * Parsea puntuaciones ABCDE desde JSON
     */
    private fun parseAbcdeScoresFromJson(json: JSONObject): ABCDEScores {
        val abcdeJson = json.optJSONObject("abcdeScores")
        
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
            // Intentar extraer scores individuales del JSON principal
            ABCDEScores(
                asymmetryScore = json.optDouble("asymmetry", 0.0).toFloat(),
                borderScore = json.optDouble("border", 0.0).toFloat(),
                colorScore = json.optDouble("color", 0.0).toFloat(),
                diameterScore = json.optDouble("diameter", 0.0).toFloat(),
                evolutionScore = if (json.has("evolution")) {
                    json.optDouble("evolution", 0.0).toFloat()
                } else null,
                totalScore = json.optDouble("abcdeTotal", 0.0).toFloat()
            )
        }
    }
    
    /**
     * Parsea formato de texto con patrones regex
     */
    private fun parseTextFormat(aiResult: String): ParsedAnalysisResult {
        val aiProbability = extractFloatValue(aiResult, listOf(
            "probabilidad.*?([0-9]*\\.?[0-9]+)",
            "probability.*?([0-9]*\\.?[0-9]+)",
            "ai.*?([0-9]*\\.?[0-9]+)"
        ))
        
        val aiConfidence = extractFloatValue(aiResult, listOf(
            "confianza.*?([0-9]*\\.?[0-9]+)",
            "confidence.*?([0-9]*\\.?[0-9]+)",
            "certeza.*?([0-9]*\\.?[0-9]+)"
        ))
        
        val asymmetry = extractFloatValue(aiResult, listOf(
            "asimetría.*?([0-9]*\\.?[0-9]+)",
            "asymmetry.*?([0-9]*\\.?[0-9]+)",
            "a.*?([0-9]*\\.?[0-9]+)"
        ))
        
        val border = extractFloatValue(aiResult, listOf(
            "borde.*?([0-9]*\\.?[0-9]+)",
            "border.*?([0-9]*\\.?[0-9]+)",
            "b.*?([0-9]*\\.?[0-9]+)"
        ))
        
        val color = extractFloatValue(aiResult, listOf(
            "color.*?([0-9]*\\.?[0-9]+)",
            "c.*?([0-9]*\\.?[0-9]+)"
        ))
        
        val diameter = extractFloatValue(aiResult, listOf(
            "diámetro.*?([0-9]*\\.?[0-9]+)",
            "diameter.*?([0-9]*\\.?[0-9]+)",
            "d.*?([0-9]*\\.?[0-9]+)"
        ))
        
        val riskLevel = extractRiskLevel(aiResult)
        val recommendation = extractRecommendation(aiResult)
        
        val abcdeScores = ABCDEScores(
            asymmetryScore = asymmetry,
            borderScore = border,
            colorScore = color,
            diameterScore = diameter,
            evolutionScore = null, // No disponible en formato texto legacy
            totalScore = asymmetry + border + color + diameter
        )
        
        return ParsedAnalysisResult(
            aiProbability = aiProbability,
            aiConfidence = aiConfidence,
            abcdeScores = abcdeScores,
            combinedScore = (aiProbability + abcdeScores.totalScore) / 2,
            riskLevel = riskLevel,
            recommendation = recommendation
        )
    }
    
    /**
     * Parsea formato legacy específico de la aplicación
     */
    private fun parseLegacyFormat(aiResult: String): ParsedAnalysisResult {
        // Formato legacy esperado: "AI: X.XX, ABCDE: A=X.X B=X.X C=X.X D=X.X, Risk: LEVEL"
        val aiMatch = Pattern.compile("AI:\\s*([0-9]*\\.?[0-9]+)").matcher(aiResult)
        val aiProbability = if (aiMatch.find()) aiMatch.group(1)?.toFloat() ?: 0f else 0f
        
        val asymmetryMatch = Pattern.compile("A=([0-9]*\\.?[0-9]+)").matcher(aiResult)
        val asymmetry = if (asymmetryMatch.find()) asymmetryMatch.group(1)?.toFloat() ?: 0f else 0f
        
        val borderMatch = Pattern.compile("B=([0-9]*\\.?[0-9]+)").matcher(aiResult)
        val border = if (borderMatch.find()) borderMatch.group(1)?.toFloat() ?: 0f else 0f
        
        val colorMatch = Pattern.compile("C=([0-9]*\\.?[0-9]+)").matcher(aiResult)
        val color = if (colorMatch.find()) colorMatch.group(1)?.toFloat() ?: 0f else 0f
        
        val diameterMatch = Pattern.compile("D=([0-9]*\\.?[0-9]+)").matcher(aiResult)
        val diameter = if (diameterMatch.find()) diameterMatch.group(1)?.toFloat() ?: 0f else 0f
        
        val riskMatch = Pattern.compile("Risk:\\s*(\\w+)").matcher(aiResult)
        val riskLevel = if (riskMatch.find()) riskMatch.group(1) ?: "LOW" else "LOW"
        
        val abcdeScores = ABCDEScores(
            asymmetryScore = asymmetry,
            borderScore = border,
            colorScore = color,
            diameterScore = diameter,
            evolutionScore = null,
            totalScore = asymmetry + border + color + diameter
        )
        
        return ParsedAnalysisResult(
            aiProbability = aiProbability,
            aiConfidence = aiProbability, // Usar la misma probabilidad como confianza
            abcdeScores = abcdeScores,
            combinedScore = (aiProbability + abcdeScores.totalScore) / 2,
            riskLevel = normalizeRiskLevel(riskLevel),
            recommendation = generateRecommendationFromRisk(riskLevel)
        )
    }
    
    /**
     * Extrae un valor float usando múltiples patrones regex
     */
    private fun extractFloatValue(text: String, patterns: List<String>): Float {
        for (pattern in patterns) {
            val matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text)
            if (matcher.find()) {
                return matcher.group(1)?.toFloat() ?: 0f
            }
        }
        return 0f
    }
    
    /**
     * Extrae el nivel de riesgo del texto
     */
    private fun extractRiskLevel(text: String): String {
        val patterns = listOf(
            "riesgo\\s*(alto|medio|bajo)",
            "risk\\s*(high|medium|low)",
            "nivel\\s*(alto|medio|bajo)"
        )
        
        for (pattern in patterns) {
            val matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text)
            if (matcher.find()) {
                return normalizeRiskLevel(matcher.group(1) ?: "LOW")
            }
        }
        
        return "LOW"
    }
    
    /**
     * Extrae la recomendación del texto
     */
    private fun extractRecommendation(text: String): String {
        val patterns = listOf(
            "recomendación:?\\s*([^.]+)",
            "recommendation:?\\s*([^.]+)",
            "sugerencia:?\\s*([^.]+)"
        )
        
        for (pattern in patterns) {
            val matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text)
            if (matcher.find()) {
                return matcher.group(1)?.trim() ?: "Continuar monitoreo regular"
            }
        }
        
        return "Continuar monitoreo regular"
    }
    
    /**
     * Normaliza el nivel de riesgo a formato estándar
     */
    private fun normalizeRiskLevel(riskLevel: String): String {
        return when (riskLevel.lowercase()) {
            "HIGH", "high" -> "HIGH"
            "medio", "medium" -> "MEDIO"
            "LOW", "low" -> "LOW"
            else -> "LOW"
        }
    }
    
    /**
     * Genera una recomendación basada en el nivel de riesgo
     */
    private fun generateRecommendationFromRisk(riskLevel: String): String {
        return when (normalizeRiskLevel(riskLevel)) {
            "HIGH" -> "Consultar con dermatólogo inmediatamente"
            "MEDIO" -> "Monitorear de cerca y consultar con especialista"
            "LOW" -> "Continuar monitoreo regular"
            else -> "Continuar monitoreo regular"
        }
    }
    
    /**
     * Crea un resultado por defecto cuando no se puede parsear
     */
    private fun createDefaultResult(): ParsedAnalysisResult {
        return ParsedAnalysisResult(
            aiProbability = 0f,
            aiConfidence = 0f,
            abcdeScores = ABCDEScores(),
            combinedScore = 0f,
            riskLevel = "LOW",
            recommendation = "Continuar monitoreo regular"
        )
    }
}
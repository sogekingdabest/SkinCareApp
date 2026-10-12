package com.monsteraltech.skincare.body.mole.data

import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.model.ABCDEScores
import javax.inject.Inject
import java.time.Instant
import org.json.JSONObject
import java.util.UUID
class LegacyAnalysisResultDecoder @Inject constructor() {
    fun decode(
        aiResult: String,
        moleId: String,
        imageUrl: String,
        createdAt: Instant = Instant.ofEpochMilli(System.currentTimeMillis())
    ): AnalysisData? {
        if (aiResult.isEmpty()) return null
        return try {
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
        if (jsonObject.has("analysisDate")) {
            metadata["originalAnalysisDate"] = jsonObject.optLong("analysisDate")
        }
        return metadata
    }
    private fun extractRecommendationFromText(text: String): String {
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
}
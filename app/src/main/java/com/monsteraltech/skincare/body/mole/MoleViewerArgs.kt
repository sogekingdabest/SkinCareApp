package com.monsteraltech.skincare.body.mole

import android.content.Intent
import java.time.Instant
import com.monsteraltech.skincare.body.mole.model.ABCDEScores
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import java.util.Date

/**
 * Argumentos de [MoleViewerActivity], parseados una sola vez desde el Intent.
 * La pantalla admite tres modos: análisis histórico completo en extras,
 * resultado de IA embebido como texto, o carga desde el repositorio por id.
 */
data class MoleViewerArgs(
    val moleId: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val aiResult: String,
    val analysisCount: Int,
    val historicalAnalysis: AnalysisData?,
    val bodyPartColorCode: String? = null
) {
    companion object {
        fun fromIntent(intent: Intent): MoleViewerArgs {
            val isHistorical = intent.getBooleanExtra("IS_HISTORICAL_ANALYSIS", false)
            return MoleViewerArgs(
                moleId = intent.getStringExtra("MOLE_ID") ?: "",
                title = intent.getStringExtra("LUNAR_TITLE") ?: "",
                description = intent.getStringExtra("LUNAR_DESCRIPTION") ?: "",
                imageUrl = intent.getStringExtra("LUNAR_IMAGE_URL") ?: "",
                aiResult = intent.getStringExtra("LUNAR_ANALYSIS_RESULT") ?: "",
                analysisCount = if (isHistorical) 1 else intent.getIntExtra("ANALYSIS_COUNT", 0),
                historicalAnalysis = if (isHistorical) reconstructHistoricalAnalysis(intent) else null,
                bodyPartColorCode = intent.getStringExtra("BODY_PART_COLOR")
            )
        }

        private fun reconstructHistoricalAnalysis(intent: Intent): AnalysisData? {
            return try {
                val analysisId = intent.getStringExtra("HISTORICAL_ANALYSIS_ID") ?: return null
                val abcdeScores = ABCDEScores(
                    asymmetryScore = intent.getFloatExtra("HISTORICAL_ASYMMETRY_SCORE", 0f),
                    borderScore = intent.getFloatExtra("HISTORICAL_BORDER_SCORE", 0f),
                    colorScore = intent.getFloatExtra("HISTORICAL_COLOR_SCORE", 0f),
                    diameterScore = intent.getFloatExtra("HISTORICAL_DIAMETER_SCORE", 0f),
                    evolutionScore = intent.getFloatExtra("HISTORICAL_EVOLUTION_SCORE", -1f)
                        .takeIf { it != -1f },
                    totalScore = intent.getFloatExtra("HISTORICAL_TOTAL_SCORE", 0f)
                )
                val metadata = intent.getBundleExtra("HISTORICAL_ANALYSIS_METADATA")?.let { bundle ->
                    buildMap {
                        for (key in bundle.keySet()) {
                            @Suppress("DEPRECATION")
                            bundle.get(key)?.let { value -> put(key, value) }
                        }
                    }
                } ?: emptyMap()

                AnalysisData(
                    id = analysisId,
                    moleId = intent.getStringExtra("MOLE_ID") ?: "",
                    analysisResult = intent.getStringExtra("HISTORICAL_ANALYSIS_RESULT") ?: "",
                    aiProbability = intent.getFloatExtra("HISTORICAL_AI_PROBABILITY", 0f),
                    aiConfidence = intent.getFloatExtra("HISTORICAL_AI_CONFIDENCE", 0f),
                    abcdeScores = abcdeScores,
                    combinedScore = intent.getFloatExtra("HISTORICAL_COMBINED_SCORE", 0f),
                    riskLevel = intent.getStringExtra("HISTORICAL_RISK_LEVEL") ?: "",
                    recommendation = intent.getStringExtra("HISTORICAL_RECOMMENDATION") ?: "",
                    imageUrl = intent.getStringExtra("LUNAR_IMAGE_URL") ?: "",
                    createdAt = Instant.ofEpochMilli(
                        intent.getLongExtra("HISTORICAL_CREATED_AT", System.currentTimeMillis())
                    ),
                    analysisMetadata = metadata
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

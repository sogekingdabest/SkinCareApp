package es.monsteraltech.skincare_tfm.body.mole.model

import com.google.firebase.Timestamp
import kotlin.math.abs

/**
 * Modelo para representar la comparación entre dos análisis de un mismo lunar
 * Útil para mostrar la evolución y cambios a lo largo del tiempo
 */
data class EvolutionComparison(
    val currentAnalysis: AnalysisData,
    val previousAnalysis: AnalysisData,
    val timeDifferenceInDays: Long,
    val scoreChanges: ScoreChanges,
    val riskLevelChange: RiskLevelChange,
    val significantChanges: List<String>,
    val overallTrend: EvolutionTrend
) {
    /**
     * Cambios en los scores individuales
     */
    data class ScoreChanges(
        val asymmetryChange: Float,
        val borderChange: Float,
        val colorChange: Float,
        val diameterChange: Float,
        val totalScoreChange: Float,
        val combinedScoreChange: Float
    ) {
        val hasSignificantChange: Boolean
            get() = abs(totalScoreChange) > 0.5f || abs(combinedScoreChange) > 0.3f
    }

    /**
     * Cambio en el nivel de riesgo
     */
    data class RiskLevelChange(
        val previousLevel: String,
        val currentLevel: String,
        val hasImproved: Boolean,
        val hasWorsened: Boolean
    ) {
        val hasChanged: Boolean
            get() = previousLevel != currentLevel

        fun getChangeDescription(): String {
            return when {
                hasImproved -> "Mejoría: $previousLevel → $currentLevel"
                hasWorsened -> "Empeoramiento: $previousLevel → $currentLevel"
                else -> "Sin cambios: $currentLevel"
            }
        }
    }

    /**
     * Tendencia general de evolución
     */
    enum class EvolutionTrend {
        IMPROVING,      // Mejorando
        STABLE,         // Estable
        WORSENING,      // Empeorando
        MIXED           // Cambios mixtos
    }

    companion object {
        /**
         * Crea una comparación entre dos análisis
         */
        fun create(current: AnalysisData, previous: AnalysisData): EvolutionComparison {
            val timeDiff = calculateTimeDifference(current.createdAt, previous.createdAt)
            val scoreChanges = calculateScoreChanges(current, previous)
            val riskChange = calculateRiskLevelChange(current, previous)
            val significantChanges = identifySignificantChanges(current, previous, scoreChanges)
            val trend = determineOverallTrend(scoreChanges, riskChange)

            return EvolutionComparison(
                currentAnalysis = current,
                previousAnalysis = previous,
                timeDifferenceInDays = timeDiff,
                scoreChanges = scoreChanges,
                riskLevelChange = riskChange,
                significantChanges = significantChanges,
                overallTrend = trend
            )
        }

        private fun calculateTimeDifference(current: Timestamp, previous: Timestamp): Long {
            val currentTime = current.toDate().time
            val previousTime = previous.toDate().time
            return (currentTime - previousTime) / (24 * 60 * 60 * 1000)
        }

        private fun calculateScoreChanges(current: AnalysisData, previous: AnalysisData): ScoreChanges {
            return ScoreChanges(
                asymmetryChange = current.abcdeScores.asymmetryScore - previous.abcdeScores.asymmetryScore,
                borderChange = current.abcdeScores.borderScore - previous.abcdeScores.borderScore,
                colorChange = current.abcdeScores.colorScore - previous.abcdeScores.colorScore,
                diameterChange = current.abcdeScores.diameterScore - previous.abcdeScores.diameterScore,
                totalScoreChange = current.abcdeScores.totalScore - previous.abcdeScores.totalScore,
                combinedScoreChange = current.combinedScore - previous.combinedScore
            )
        }

        private fun calculateRiskLevelChange(current: AnalysisData, previous: AnalysisData): RiskLevelChange {
            val riskLevels = listOf("LOW", "MODERATE", "HIGH")
            val previousIndex = riskLevels.indexOf(previous.riskLevel)
            val currentIndex = riskLevels.indexOf(current.riskLevel)

            return RiskLevelChange(
                previousLevel = previous.riskLevel,
                currentLevel = current.riskLevel,
                hasImproved = currentIndex < previousIndex && previousIndex != -1 && currentIndex != -1,
                hasWorsened = currentIndex > previousIndex && previousIndex != -1 && currentIndex != -1
            )
        }

        private fun identifySignificantChanges(
            current: AnalysisData,
            previous: AnalysisData,
            scoreChanges: ScoreChanges
        ): List<String> {
            val changes = mutableListOf<String>()

            // Cambios en scores ABCDE
            if (abs(scoreChanges.asymmetryChange) > 0.3f) {
                val direction = if (scoreChanges.asymmetryChange > 0) "aumentó" else "disminuyó"
                changes.add("La asimetría $direction significativamente")
            }

            if (abs(scoreChanges.borderChange) > 0.5f) {
                val direction = if (scoreChanges.borderChange > 0) "aumentó" else "disminuyó"
                changes.add("La irregularidad de bordes $direction")
            }

            if (abs(scoreChanges.colorChange) > 0.5f) {
                val direction = if (scoreChanges.colorChange > 0) "aumentó" else "disminuyó"
                changes.add("La variación de color $direction")
            }

            if (abs(scoreChanges.diameterChange) > 0.3f) {
                val direction = if (scoreChanges.diameterChange > 0) "aumentó" else "disminuyó"
                changes.add("El tamaño del lunar $direction")
            }

            // Cambios en confianza de IA
            val confidenceChange = current.aiConfidence - previous.aiConfidence
            if (abs(confidenceChange) > 0.2f) {
                val direction = if (confidenceChange > 0) "aumentó" else "disminuyó"
                changes.add("La confianza del análisis de IA $direction")
            }

            return changes
        }

        private fun determineOverallTrend(
            scoreChanges: ScoreChanges,
            riskChange: RiskLevelChange
        ): EvolutionTrend {
            val positiveChanges = listOf(
                scoreChanges.asymmetryChange > 0.2f,
                scoreChanges.borderChange > 0.3f,
                scoreChanges.colorChange > 0.3f,
                scoreChanges.diameterChange > 0.2f,
                riskChange.hasWorsened
            ).count { it }

            val negativeChanges = listOf(
                scoreChanges.asymmetryChange < -0.2f,
                scoreChanges.borderChange < -0.3f,
                scoreChanges.colorChange < -0.3f,
                scoreChanges.diameterChange < -0.2f,
                riskChange.hasImproved
            ).count { it }

            return when {
                negativeChanges > positiveChanges && negativeChanges >= 2 -> EvolutionTrend.IMPROVING
                positiveChanges > negativeChanges && positiveChanges >= 2 -> EvolutionTrend.WORSENING
                positiveChanges > 0 && negativeChanges > 0 -> EvolutionTrend.MIXED
                else -> EvolutionTrend.STABLE
            }
        }
    }

    /**
     * Obtiene un resumen textual de la evolución
     */
    fun getEvolutionSummary(): String {
        return buildString {
            append("Evolución en $timeDifferenceInDays días: ")
            
            when (overallTrend) {
                EvolutionTrend.IMPROVING -> append("Tendencia positiva")
                EvolutionTrend.WORSENING -> append("Requiere atención")
                EvolutionTrend.STABLE -> append("Sin cambios significativos")
                EvolutionTrend.MIXED -> append("Cambios mixtos")
            }

            if (riskLevelChange.hasChanged) {
                append(" - ${riskLevelChange.getChangeDescription()}")
            }
        }
    }
}
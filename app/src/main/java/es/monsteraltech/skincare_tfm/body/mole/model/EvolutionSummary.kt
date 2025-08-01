package es.monsteraltech.skincare_tfm.body.mole.model

import com.google.firebase.Timestamp

/**
 * Resumen de la evolución de un lunar a lo largo del tiempo
 * Proporciona una vista general de todos los análisis realizados
 */
data class EvolutionSummary(
    val moleId: String,
    val totalAnalyses: Int,
    val firstAnalysisDate: Timestamp,
    val lastAnalysisDate: Timestamp,
    val currentRiskLevel: String,
    val initialRiskLevel: String,
    val riskTrend: RiskTrend,
    val averageScoreChange: Float,
    val significantEvents: List<SignificantEvent>,
    val recommendedAction: String
) {
    /**
     * Tendencia del riesgo a lo largo del tiempo
     */
    enum class RiskTrend {
        IMPROVING,      // Mejorando consistentemente
        STABLE,         // Manteniéndose estable
        WORSENING,      // Empeorando consistentemente
        FLUCTUATING     // Fluctuando sin patrón claro
    }

    /**
     * Evento significativo en la evolución del lunar
     */
    data class SignificantEvent(
        val date: Timestamp,
        val eventType: EventType,
        val description: String,
        val severity: EventSeverity
    ) {
        enum class EventType {
            RISK_INCREASE,      // Aumento de riesgo
            RISK_DECREASE,      // Disminución de riesgo
            SIZE_CHANGE,        // Cambio significativo de tamaño
            COLOR_CHANGE,       // Cambio significativo de color
            SHAPE_CHANGE,       // Cambio significativo de forma
            NEW_ANALYSIS        // Primer análisis
        }

        enum class EventSeverity {
            LOW,        // Cambio menor
            MODERATE,   // Cambio notable
            HIGH        // Cambio significativo que requiere atención
        }
    }

    companion object {
        /**
         * Crea un resumen de evolución a partir de una lista de análisis
         */
        fun create(moleId: String, analyses: List<AnalysisData>): EvolutionSummary {
            if (analyses.isEmpty()) {
                return createEmptySummary(moleId)
            }

            val sortedAnalyses = analyses.sortedBy { it.createdAt.seconds }
            val firstAnalysis = sortedAnalyses.first()
            val lastAnalysis = sortedAnalyses.last()

            val riskTrend = calculateRiskTrend(sortedAnalyses)
            val averageScoreChange = calculateAverageScoreChange(sortedAnalyses)
            val significantEvents = identifySignificantEvents(sortedAnalyses)
            val recommendedAction = determineRecommendedAction(lastAnalysis, riskTrend, significantEvents)

            return EvolutionSummary(
                moleId = moleId,
                totalAnalyses = analyses.size,
                firstAnalysisDate = firstAnalysis.createdAt,
                lastAnalysisDate = lastAnalysis.createdAt,
                currentRiskLevel = lastAnalysis.riskLevel,
                initialRiskLevel = firstAnalysis.riskLevel,
                riskTrend = riskTrend,
                averageScoreChange = averageScoreChange,
                significantEvents = significantEvents,
                recommendedAction = recommendedAction
            )
        }

        private fun createEmptySummary(moleId: String): EvolutionSummary {
            return EvolutionSummary(
                moleId = moleId,
                totalAnalyses = 0,
                firstAnalysisDate = Timestamp.now(),
                lastAnalysisDate = Timestamp.now(),
                currentRiskLevel = "DESCONOCIDO",
                initialRiskLevel = "DESCONOCIDO",
                riskTrend = RiskTrend.STABLE,
                averageScoreChange = 0f,
                significantEvents = emptyList(),
                recommendedAction = "Realizar primer análisis"
            )
        }

        private fun calculateRiskTrend(analyses: List<AnalysisData>): RiskTrend {
            if (analyses.size < 2) return RiskTrend.STABLE

            val riskLevels = listOf("LOW", "MODERATE", "HIGH")
            val riskIndices = analyses.map { analysis ->
                riskLevels.indexOf(analysis.riskLevel).takeIf { it != -1 } ?: 1
            }

            // Calcular tendencia general
            val firstHalf = riskIndices.take(riskIndices.size / 2).average()
            val secondHalf = riskIndices.drop(riskIndices.size / 2).average()
            val overallTrend = secondHalf - firstHalf

            // Calcular variabilidad
            val variance = riskIndices.map { (it - riskIndices.average()).let { diff -> diff * diff } }.average()

            return when {
                variance > 0.5 -> RiskTrend.FLUCTUATING
                overallTrend > 0.3 -> RiskTrend.WORSENING
                overallTrend < -0.3 -> RiskTrend.IMPROVING
                else -> RiskTrend.STABLE
            }
        }

        private fun calculateAverageScoreChange(analyses: List<AnalysisData>): Float {
            if (analyses.size < 2) return 0f

            val scoreChanges = mutableListOf<Float>()
            for (i in 1 until analyses.size) {
                val change = analyses[i].combinedScore - analyses[i-1].combinedScore
                scoreChanges.add(change)
            }

            return scoreChanges.average().toFloat()
        }

        private fun identifySignificantEvents(analyses: List<AnalysisData>): List<SignificantEvent> {
            val events = mutableListOf<SignificantEvent>()

            // Primer análisis
            if (analyses.isNotEmpty()) {
                events.add(
                    SignificantEvent(
                        date = analyses.first().createdAt,
                        eventType = SignificantEvent.EventType.NEW_ANALYSIS,
                        description = "Primer análisis realizado",
                        severity = SignificantEvent.EventSeverity.LOW
                    )
                )
            }

            // Comparar análisis consecutivos
            for (i in 1 until analyses.size) {
                val current = analyses[i]
                val previous = analyses[i-1]

                // Cambios de riesgo
                val riskLevels = listOf("LOW", "MODERATE", "HIGH")
                val previousRiskIndex = riskLevels.indexOf(previous.riskLevel)
                val currentRiskIndex = riskLevels.indexOf(current.riskLevel)

                if (currentRiskIndex > previousRiskIndex && previousRiskIndex != -1 && currentRiskIndex != -1) {
                    events.add(
                        SignificantEvent(
                            date = current.createdAt,
                            eventType = SignificantEvent.EventType.RISK_INCREASE,
                            description = "Aumento de riesgo: ${previous.riskLevel} → ${current.riskLevel}",
                            severity = when (currentRiskIndex - previousRiskIndex) {
                                1 -> SignificantEvent.EventSeverity.MODERATE
                                else -> SignificantEvent.EventSeverity.HIGH
                            }
                        )
                    )
                } else if (currentRiskIndex < previousRiskIndex && previousRiskIndex != -1 && currentRiskIndex != -1) {
                    events.add(
                        SignificantEvent(
                            date = current.createdAt,
                            eventType = SignificantEvent.EventType.RISK_DECREASE,
                            description = "Disminución de riesgo: ${previous.riskLevel} → ${current.riskLevel}",
                            severity = SignificantEvent.EventSeverity.LOW
                        )
                    )
                }

                // Cambios significativos en scores
                val scoreChange = current.combinedScore - previous.combinedScore
                if (kotlin.math.abs(scoreChange) > 1.0f) {
                    val eventType = if (scoreChange > 0) {
                        SignificantEvent.EventType.RISK_INCREASE
                    } else {
                        SignificantEvent.EventType.RISK_DECREASE
                    }
                    
                    events.add(
                        SignificantEvent(
                            date = current.createdAt,
                            eventType = eventType,
                            description = "Cambio significativo en puntuación: ${String.format("%.1f", scoreChange)}",
                            severity = when {
                                kotlin.math.abs(scoreChange) > 2.0f -> SignificantEvent.EventSeverity.HIGH
                                kotlin.math.abs(scoreChange) > 1.5f -> SignificantEvent.EventSeverity.MODERATE
                                else -> SignificantEvent.EventSeverity.LOW
                            }
                        )
                    )
                }
            }

            return events.sortedByDescending { it.date.seconds }
        }

        private fun determineRecommendedAction(
            lastAnalysis: AnalysisData,
            riskTrend: RiskTrend,
            significantEvents: List<SignificantEvent>
        ): String {
            val hasRecentHighSeverityEvent = significantEvents.any { 
                it.severity == SignificantEvent.EventSeverity.HIGH &&
                (System.currentTimeMillis() - it.date.toDate().time) < (30 * 24 * 60 * 60 * 1000L) // 30 días
            }

            return when {
                hasRecentHighSeverityEvent -> 
                    "Consulta dermatológica urgente recomendada debido a cambios significativos recientes"
                
                lastAnalysis.riskLevel == "HIGH" -> 
                    "Evaluación dermatológica inmediata recomendada"
                
                lastAnalysis.riskLevel == "MODERATE" && riskTrend == RiskTrend.WORSENING -> 
                    "Consulta dermatológica en las próximas 2-4 semanas"
                
                lastAnalysis.riskLevel == "MODERATE" -> 
                    "Seguimiento regular y consulta dermatológica en 2-3 meses"
                
                riskTrend == RiskTrend.WORSENING -> 
                    "Monitoreo más frecuente y consulta si continúan los cambios"
                
                else -> 
                    "Continuar con autoexámenes regulares y seguimiento rutinario"
            }
        }
    }

    /**
     * Obtiene el período de seguimiento en días
     */
    val followUpPeriodDays: Long
        get() = (lastAnalysisDate.seconds - firstAnalysisDate.seconds) / (24 * 60 * 60)

    /**
     * Indica si el lunar requiere atención inmediata
     */
    val requiresImmediateAttention: Boolean
        get() = currentRiskLevel == "HIGH" || 
                riskTrend == RiskTrend.WORSENING ||
                significantEvents.any { it.severity == SignificantEvent.EventSeverity.HIGH }

    /**
     * Obtiene un resumen textual de la evolución
     */
    fun getTextSummary(): String {
        return buildString {
            appendLine("Resumen de evolución:")
            appendLine("• Total de análisis: $totalAnalyses")
            appendLine("• Período de seguimiento: $followUpPeriodDays días")
            appendLine("• Riesgo actual: $currentRiskLevel")
            
            if (initialRiskLevel != currentRiskLevel) {
                appendLine("• Cambio desde inicial: $initialRiskLevel → $currentRiskLevel")
            }
            
            appendLine("• Tendencia: ${getTrendDescription()}")
            
            if (significantEvents.isNotEmpty()) {
                appendLine("• Eventos significativos: ${significantEvents.size}")
            }
            
            appendLine("• Recomendación: $recommendedAction")
        }
    }

    private fun getTrendDescription(): String {
        return when (riskTrend) {
            RiskTrend.IMPROVING -> "Mejorando"
            RiskTrend.STABLE -> "Estable"
            RiskTrend.WORSENING -> "Empeorando"
            RiskTrend.FLUCTUATING -> "Variable"
        }
    }
}
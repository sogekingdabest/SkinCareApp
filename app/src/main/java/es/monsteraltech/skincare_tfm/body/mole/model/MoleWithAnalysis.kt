package es.monsteraltech.skincare_tfm.body.mole.model

/**
 * Modelo que combina un lunar con su análisis más reciente
 * Útil para mostrar información resumida en listas y vistas
 */
data class MoleWithAnalysis(
    val mole: MoleData,
    val latestAnalysis: AnalysisData?
) {
    /**
     * Indica si el lunar tiene análisis históricos
     */
    val hasAnalysisHistory: Boolean
        get() = mole.analysisCount > 0

    /**
     * Obtiene el nivel de riesgo del análisis más reciente
     */
    val currentRiskLevel: String
        get() = latestAnalysis?.riskLevel ?: "DESCONOCIDO"

    /**
     * Obtiene la recomendación del análisis más reciente
     */
    val currentRecommendation: String
        get() = latestAnalysis?.recommendation ?: "Sin recomendación disponible"

    /**
     * Obtiene el score total del análisis más reciente
     */
    val currentTotalScore: Float
        get() = latestAnalysis?.combinedScore ?: 0f

    /**
     * Indica si el lunar necesita seguimiento basado en el riesgo
     */
    val needsFollowUp: Boolean
        get() = when (currentRiskLevel) {
            "HIGH" -> true
            "MODERATE" -> true
            else -> false
        }

    /**
     * Obtiene un resumen textual del estado del lunar
     */
    fun getStatusSummary(): String {
        return when {
            !hasAnalysisHistory -> "Sin análisis previos"
            latestAnalysis == null -> "Análisis no disponible"
            else -> {
                val daysSinceAnalysis = getDaysSinceLastAnalysis()
                when {
                    daysSinceAnalysis == 0L -> "Analizado hoy - Riesgo: $currentRiskLevel"
                    daysSinceAnalysis == 1L -> "Analizado ayer - Riesgo: $currentRiskLevel"
                    daysSinceAnalysis < 7 -> "Analizado hace $daysSinceAnalysis días - Riesgo: $currentRiskLevel"
                    daysSinceAnalysis < 30 -> "Analizado hace ${daysSinceAnalysis / 7} semanas - Riesgo: $currentRiskLevel"
                    else -> "Analizado hace ${daysSinceAnalysis / 30} meses - Riesgo: $currentRiskLevel"
                }
            }
        }
    }

    /**
     * Calcula los días transcurridos desde el último análisis
     */
    private fun getDaysSinceLastAnalysis(): Long {
        val lastAnalysisDate = mole.lastAnalysisDate ?: return Long.MAX_VALUE
        val now = System.currentTimeMillis()
        val analysisTime = lastAnalysisDate.toDate().time
        return (now - analysisTime) / (24 * 60 * 60 * 1000)
    }
}
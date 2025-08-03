package es.monsteraltech.skincare_tfm.body.mole.util

import android.content.Context
import es.monsteraltech.skincare_tfm.R

/**
 * Utilidad para traducir los niveles de riesgo del inglés técnico al español legible
 */
object RiskLevelTranslator {
    
    /**
     * Traduce un nivel de riesgo del formato interno (inglés) al formato de usuario (español)
     */
    fun translateRiskLevel(context: Context, riskLevel: String): String {
        return when (riskLevel.uppercase()) {
            "VERY_LOW" -> context.getString(R.string.risk_level_very_low)
            "LOW" -> context.getString(R.string.risk_level_low)
            "MEDIUM" -> context.getString(R.string.risk_level_medium)
            "HIGH" -> context.getString(R.string.risk_level_high)
            "VERY_HIGH" -> context.getString(R.string.risk_level_very_high)
            // Mantener compatibilidad con valores ya traducidos
            "MUY BAJO", "MUY_BAJO" -> context.getString(R.string.risk_level_very_low)
            "BAJO" -> context.getString(R.string.risk_level_low)
            "MEDIO" -> context.getString(R.string.risk_level_medium)
            "ALTO" -> context.getString(R.string.risk_level_high)
            "MUY ALTO", "MUY_ALTO" -> context.getString(R.string.risk_level_very_high)
            else -> riskLevel // Devolver el valor original si no se encuentra traducción
        }
    }
    
    /**
     * Obtiene el color asociado a un nivel de riesgo
     */
    fun getRiskLevelColor(riskLevel: String): Int {
        return when (riskLevel.uppercase()) {
            "VERY_LOW", "MUY BAJO", "MUY_BAJO" -> android.R.color.holo_green_light
            "LOW", "BAJO" -> android.R.color.holo_green_dark
            "MEDIUM", "MEDIO", "MODERADO" -> android.R.color.holo_orange_dark
            "HIGH", "ALTO" -> android.R.color.holo_red_dark
            "VERY_HIGH", "MUY ALTO", "MUY_ALTO" -> android.R.color.holo_red_light
            else -> android.R.color.darker_gray
        }
    }
}
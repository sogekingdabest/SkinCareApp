package es.monsteraltech.skincare_tfm.body.mole.util
import android.content.Context
import es.monsteraltech.skincare_tfm.R
object RiskLevelTranslator {
    fun translateRiskLevel(context: Context, riskLevel: String): String {
        return when (riskLevel.uppercase()) {
            "VERY_LOW" -> context.getString(R.string.risk_level_very_low)
            "LOW" -> context.getString(R.string.risk_level_low)
            "MEDIUM" -> context.getString(R.string.risk_level_medium)
            "HIGH" -> context.getString(R.string.risk_level_high)
            "VERY_HIGH" -> context.getString(R.string.risk_level_very_high)
            "MUY BAJO", "MUY_BAJO" -> context.getString(R.string.risk_level_very_low)
            "BAJO" -> context.getString(R.string.risk_level_low)
            "MEDIO" -> context.getString(R.string.risk_level_medium)
            "ALTO" -> context.getString(R.string.risk_level_high)
            "MUY ALTO", "MUY_ALTO" -> context.getString(R.string.risk_level_very_high)
            else -> riskLevel
        }
    }
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
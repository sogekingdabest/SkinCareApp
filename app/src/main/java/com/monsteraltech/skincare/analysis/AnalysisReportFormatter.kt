package com.monsteraltech.skincare.analysis

import com.monsteraltech.skincare.body.mole.model.UserAbcdeInput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Formats the saved report without depending on presentation state or Android resources. */
class AnalysisReportFormatter @Inject constructor() {
    fun format(
        result: MelanomaAIDetector.CombinedAnalysisResult,
        userInput: UserAbcdeInput?,
        createdAt: Date,
        locale: Locale = Locale.getDefault()
    ): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", locale)
        return buildString {
            appendLine("=== ANÁLISIS COMBINADO IA + ABCDE ===")
            appendLine("Fecha: ${dateFormat.format(createdAt)}")
            appendLine()
            appendLine("RESULTADO IA:")
            appendLine("- Probabilidad: ${(result.aiProbability * 100).toInt()}%")
            appendLine("- Confianza: ${(result.aiConfidence * 100).toInt()}%")
            appendLine()
            appendLine("ANÁLISIS ABCDE (IA):")
            appendLine("- Asimetría: ${result.abcdeResult.asymmetryScore}/2")
            appendLine("- Bordes: ${result.abcdeResult.borderScore}/8")
            appendLine("- Color: ${result.abcdeResult.colorScore}/6")
            appendLine("- Diámetro: ${result.abcdeResult.diameterScore}/5")
            result.abcdeResult.evolutionScore?.let {
                appendLine("- Evolución: $it/3")
            }
            appendLine("- Score Total ABCDE (IA): ${result.abcdeResult.totalScore}")
            appendLine()
            if (userInput != null) {
                appendLine("ANÁLISIS ABCDE (USUARIO):")
                appendLine("- Asimetría: ${String.format(locale, "%.1f", userInput.asymmetry)}/2")
                appendLine("- Bordes: ${String.format(locale, "%.1f", userInput.border)}/8")
                appendLine("- Color: ${String.format(locale, "%.1f", userInput.color)}/6")
                appendLine("- Diámetro: ${String.format(locale, "%.1f", userInput.diameter)}/5")
                appendLine("- Evolución: ${String.format(locale, "%.1f", userInput.evolution)}/3")
                val userTotal = userInput.total()
                appendLine("- Score Total ABCDE (Usuario): ${String.format(locale, "%.1f", userTotal)}")
                appendLine()
                val difference = UserAbcdeInput.difference(result.abcdeResult.totalScore, userTotal)
                appendLine("COMPARACIÓN:")
                appendLine("- Diferencia entre IA y Usuario: ${String.format(locale, "%.1f", difference)} puntos")
                appendLine()
            }
            appendLine("RESULTADO COMBINADO:")
            appendLine("- Score: ${(result.combinedScore * 100).toInt()}%")
            appendLine("- Nivel de Riesgo: ${result.combinedRiskLevel}")
            appendLine("- Urgencia: ${result.urgencyLevel}")
            appendLine()
            appendLine("RECOMENDACIÓN:")
            appendLine(result.recommendation)
        }
    }

}

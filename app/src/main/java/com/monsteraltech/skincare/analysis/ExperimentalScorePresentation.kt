package com.monsteraltech.skincare.analysis

import java.util.Locale

/** A raw sigmoid score is not an individually calibrated clinical probability. */
object ExperimentalScorePresentation {
    fun ai(score: Float, locale: Locale = Locale.getDefault()): String {
        if (!score.isFinite() || score !in 0f..1f) return "Puntuación experimental IA: no disponible"
        return "Puntuación experimental IA: ${String.format(locale, "%.1f", score * 100)}/100"
    }
}

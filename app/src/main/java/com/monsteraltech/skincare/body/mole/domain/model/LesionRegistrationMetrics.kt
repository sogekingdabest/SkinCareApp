package com.monsteraltech.skincare.body.mole.domain.model

/**
 * Métricas técnicas del registro entre dos segmentaciones.
 *
 * No representan cambio clínico. Únicamente cuantifican si el ajuste
 * geométrico es suficientemente fiable para calcular diferencias después.
 */
data class LesionRegistrationMetrics(
    val algorithmVersion: String,
    val baselineAreaFraction: Double,
    val currentAreaFraction: Double,
    val originalCentroidDistance: Double,
    val appliedScale: Double,
    val appliedRotationDegrees: Double,
    val alignedMaskIou: Double
)

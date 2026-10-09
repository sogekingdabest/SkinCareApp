package com.monsteraltech.skincare.body.mole.domain.model

/**
 * Métricas técnicas de una captura, independientes de Android y OpenCV.
 *
 * No describen riesgo clínico: solo permiten decidir si dos imágenes se pueden
 * comparar de forma suficientemente estable.
 */
data class ObservationImageMetrics(
    val width: Int,
    val height: Int,
    val brightness: Float,
    val isOptimalLighting: Boolean,
    val isOverexposed: Boolean,
    val isUnderexposed: Boolean,
    val sharpness: Double,
    val isBlurry: Boolean
)

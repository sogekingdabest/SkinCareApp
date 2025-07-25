package es.monsteraltech.skincare_tfm.camera.guidance

/**
 * Configuraciones por defecto para el sistema de guías de captura
 */
object CaptureGuidanceDefaults {
    /**
     * Configuración por defecto del sistema de guías
     */
    val DEFAULT_CONFIG = CaptureGuidanceConfig(
        guideCircleRadius = 150f,
        centeringTolerance = 50f,
        minMoleAreaRatio = 0.15f,
        maxMoleAreaRatio = 0.80f,
        qualityThresholds = QualityThresholds(
            minSharpness = 0.3f,
            minBrightness = 80f,
            maxBrightness = 180f,
            minContrast = 0.2f
        ),
        enableHapticFeedback = true,
        enableAutoCapture = false,
        autoCaptureDelay = 3000L
    )
    
    /**
     * Configuración optimizada para dispositivos de gama baja
     */
    val LOW_END_CONFIG = DEFAULT_CONFIG.copy(
        guideCircleRadius = 120f,
        qualityThresholds = QualityThresholds(
            minSharpness = 0.25f,
            minBrightness = 70f,
            maxBrightness = 190f,
            minContrast = 0.15f
        )
    )
    
    /**
     * Configuración para modo de alta precisión
     */
    val HIGH_PRECISION_CONFIG = DEFAULT_CONFIG.copy(
        centeringTolerance = 30f,
        minMoleAreaRatio = 0.20f,
        maxMoleAreaRatio = 0.70f,
        qualityThresholds = QualityThresholds(
            minSharpness = 0.4f,
            minBrightness = 90f,
            maxBrightness = 170f,
            minContrast = 0.25f
        )
    )
}
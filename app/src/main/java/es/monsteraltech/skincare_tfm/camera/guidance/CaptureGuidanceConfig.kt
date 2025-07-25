package es.monsteraltech.skincare_tfm.camera.guidance

/**
 * Configuración para el sistema de guías de captura inteligente
 */
data class CaptureGuidanceConfig(
    val guideCircleRadius: Float = 150f,
    val centeringTolerance: Float = 50f,
    val minMoleAreaRatio: Float = 0.15f,
    val maxMoleAreaRatio: Float = 0.80f,
    val qualityThresholds: QualityThresholds = QualityThresholds(),
    val enableHapticFeedback: Boolean = true,
    val enableAutoCapture: Boolean = false,
    val autoCaptureDelay: Long = 3000L
)

/**
 * Umbrales de calidad para validación de imagen
 */
data class QualityThresholds(
    val minSharpness: Float = 0.3f,
    val minBrightness: Float = 80f,
    val maxBrightness: Float = 180f,
    val minContrast: Float = 0.2f
)
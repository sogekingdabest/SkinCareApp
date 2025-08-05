package es.monsteraltech.skincare_tfm.camera.guidance
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
data class QualityThresholds(
    val minSharpness: Float = 0.25f,
    val minBrightness: Float = 70f,
    val maxBrightness: Float = 200f,
    val minContrast: Float = 0.15f
)
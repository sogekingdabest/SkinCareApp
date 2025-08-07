package es.monsteraltech.skincare_tfm.camera.guidance


data class CaptureGuidanceConfig(
    val guideCircleRadius: Float = 200f,
    val centeringTolerance: Float = 120f,
    val minMoleConfidence: Float = 0.3f,
    val enableAutoCapture: Boolean = false,
    val autoCaptureDelay: Long = 1500L
)
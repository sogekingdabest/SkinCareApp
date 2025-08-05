package es.monsteraltech.skincare_tfm.camera.guidance
data class Point(val x: Float, val y: Float)
sealed class CaptureState {
    object Initializing : CaptureState()
    object Searching : CaptureState()
    data class MoleDetected(
        val position: Point,
        val confidence: Float,
        val area: Float
    ) : CaptureState()
    data class ValidationFailed(
        val reason: ValidationFailureReason,
        val message: String
    ) : CaptureState()
    object ReadyToCapture : CaptureState()
    object Capturing : CaptureState()
}
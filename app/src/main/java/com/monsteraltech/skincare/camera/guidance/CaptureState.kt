package com.monsteraltech.skincare.camera.guidance


sealed class CaptureState {
    object Searching : CaptureState()
    data class MoleDetected(
        val centerX: Float,
        val centerY: Float,
        val confidence: Float,
        val isInCenter: Boolean
    ) : CaptureState()
    object PoorLighting : CaptureState()
    object Blurry : CaptureState()
    object ReadyToCapture : CaptureState()
}

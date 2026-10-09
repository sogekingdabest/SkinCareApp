package com.monsteraltech.skincare.camera.guidance


data class CaptureGuidanceConfig(
    /** Radio del círculo guía como fracción del lado menor de la vista. */
    val guideCircleRadiusFraction: Float = 0.30f,
    /** Distancia máxima al centro (fracción del lado menor del frame) para considerar el lunar centrado. */
    val centeringToleranceFraction: Float = 0.15f,
    /** Confianza mínima de la detección para permitir la captura. */
    val minMoleConfidence: Float = 0.35f,
    /** Frames buenos consecutivos necesarios para pasar a READY. */
    val readyConfirmFrames: Int = 3,
    /** Frames malos consecutivos tolerados antes de perder READY (histéresis anti-parpadeo). */
    val readyLossToleranceFrames: Int = 2
)

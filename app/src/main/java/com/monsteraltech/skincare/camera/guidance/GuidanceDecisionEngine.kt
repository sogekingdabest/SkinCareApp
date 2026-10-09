package com.monsteraltech.skincare.camera.guidance

/**
 * Máquina de decisión pura (sin dependencias de Android ni OpenCV) que convierte
 * las medidas de un frame en el veredicto de guía a mostrar.
 *
 * Aplica estabilidad temporal: READY exige varios frames buenos consecutivos y,
 * una vez alcanzado, tolera pérdidas puntuales antes de degradar el estado
 * (histéresis), de modo que la UI no parpadee entre estados frame a frame.
 */
class GuidanceDecisionEngine(
    private val minConfidence: Float = 0.35f,
    private val readyConfirmFrames: Int = 3,
    private val readyLossTolerance: Int = 2
) {
    enum class Lighting { OK, UNDEREXPOSED, OVEREXPOSED }

    enum class SemanticGate { DISABLED, TARGET, NON_TARGET, UNAVAILABLE }

    enum class Verdict {
        SEARCHING,
        CENTERING,
        POOR_LIGHTING,
        TOO_BRIGHT,
        BLURRY,
        MODEL_UNAVAILABLE,
        READY
    }

    data class FrameObservation(
        val lighting: Lighting,
        val isBlurry: Boolean,
        val moleDetected: Boolean,
        val moleCentered: Boolean = false,
        val moleConfidence: Float = 0f,
        val semanticGate: SemanticGate = SemanticGate.DISABLED
    )

    private var goodStreak = 0
    private var badStreak = 0
    private var isReady = false

    fun evaluate(observation: FrameObservation): Verdict {
        val frameQualifies = observation.lighting == Lighting.OK &&
                !observation.isBlurry &&
                observation.moleDetected &&
                observation.moleCentered &&
                observation.moleConfidence >= minConfidence &&
                (
                    observation.semanticGate == SemanticGate.DISABLED ||
                        observation.semanticGate == SemanticGate.TARGET
                )

        if (frameQualifies) {
            goodStreak++
            badStreak = 0
            if (goodStreak >= readyConfirmFrames) {
                isReady = true
            }
        } else {
            badStreak++
            goodStreak = 0
            if (badStreak > readyLossTolerance) {
                isReady = false
            }
        }

        if (isReady) return Verdict.READY

        return when {
            observation.lighting == Lighting.UNDEREXPOSED -> Verdict.POOR_LIGHTING
            observation.lighting == Lighting.OVEREXPOSED -> Verdict.TOO_BRIGHT
            observation.semanticGate == SemanticGate.UNAVAILABLE ->
                Verdict.MODEL_UNAVAILABLE
            observation.semanticGate == SemanticGate.NON_TARGET -> Verdict.SEARCHING
            !observation.moleDetected -> Verdict.SEARCHING
            observation.isBlurry -> Verdict.BLURRY
            else -> Verdict.CENTERING
        }
    }

    fun reset() {
        goodStreak = 0
        badStreak = 0
        isReady = false
    }
}

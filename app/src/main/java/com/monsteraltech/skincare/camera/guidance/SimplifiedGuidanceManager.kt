package com.monsteraltech.skincare.camera.guidance

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.monsteraltech.skincare.BuildConfig
import com.monsteraltech.skincare.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Mat
import org.opencv.core.Point

/**
 * Orquesta el análisis por frame para el guiado de captura: calidad de imagen
 * (exposición y nitidez) + detección del lunar, y delega la decisión de estado
 * en [GuidanceDecisionEngine] (que aporta estabilidad temporal anti-parpadeo).
 */
class SimplifiedGuidanceManager(
    private val context: Context,
    private val config: CaptureGuidanceConfig = CaptureGuidanceConfig(),
    private val semanticGateEnabled: Boolean =
        BuildConfig.CAPTURE_VALIDITY_MODEL_ENABLED,
    private val semanticClassifier: CaptureValidityClassifier? =
        if (semanticGateEnabled) {
            CaptureValidityClassifier(context.applicationContext)
        } else {
            null
        }
) {
    companion object {
        private const val TAG = "SimplifiedGuidanceManager"
    }

    private val moleDetector = MoleDetectionProcessor(config.centeringToleranceFraction)
    private val qualityAnalyzer = ImageQualityAnalyzer()
    private val decisionEngine = GuidanceDecisionEngine(
        minConfidence = config.minMoleConfidence,
        readyConfirmFrames = config.readyConfirmFrames,
        readyLossTolerance = config.readyLossToleranceFrames
    )

    data class GuidanceResult(
        val state: CaptureState,
        val guideState: GuideState,
        val message: String,
        val molePosition: Point?,
        val moleConfidence: Float,
        val semanticPrediction: CaptureValidityContract.Prediction? = null
    )

    suspend fun processFrame(
        frame: Mat,
        orientedColorFrame: Bitmap? = null
    ): GuidanceResult = withContext(Dispatchers.Default) {
        try {
            val quality = qualityAnalyzer.analyze(frame)
            val detection = moleDetector.detectMoleInCenter(frame)
            val semanticPrediction = runSemanticGate(orientedColorFrame)
            val semanticGate = when {
                !semanticGateEnabled ->
                    GuidanceDecisionEngine.SemanticGate.DISABLED
                semanticPrediction == null ->
                    GuidanceDecisionEngine.SemanticGate.UNAVAILABLE
                semanticPrediction.isReady ->
                    GuidanceDecisionEngine.SemanticGate.TARGET
                else -> GuidanceDecisionEngine.SemanticGate.NON_TARGET
            }

            val lighting = when {
                quality.isOverexposed -> GuidanceDecisionEngine.Lighting.OVEREXPOSED
                quality.isUnderexposed -> GuidanceDecisionEngine.Lighting.UNDEREXPOSED
                quality.isOptimalLighting -> GuidanceDecisionEngine.Lighting.OK
                // Brillo fuera de rango sin saturación clara: orientar según el lado.
                quality.brightness > 128f -> GuidanceDecisionEngine.Lighting.OVEREXPOSED
                else -> GuidanceDecisionEngine.Lighting.UNDEREXPOSED
            }

            val verdict = decisionEngine.evaluate(
                GuidanceDecisionEngine.FrameObservation(
                    lighting = lighting,
                    isBlurry = quality.isBlurry,
                    moleDetected = detection != null,
                    moleCentered = detection?.isInCenter ?: false,
                    moleConfidence = detection?.confidence ?: 0f,
                    semanticGate = semanticGate
                )
            )

            buildResult(verdict, detection, semanticPrediction)
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando frame", e)
            GuidanceResult(
                state = CaptureState.Searching,
                guideState = GuideState.SEARCHING,
                message = context.getString(R.string.guidance_analysis_error),
                molePosition = null,
                moleConfidence = 0f,
                semanticPrediction = null
            )
        }
    }

    private fun runSemanticGate(
        orientedColorFrame: Bitmap?
    ): CaptureValidityContract.Prediction? {
        if (!semanticGateEnabled) return null
        val classifier = semanticClassifier
        if (orientedColorFrame == null || classifier?.isAvailable != true) {
            return null
        }
        return try {
            classifier.classifyGuideRoi(
                orientedFrame = orientedColorFrame,
                roiSideFraction = (
                    config.guideCircleRadiusFraction * 2f
                ).coerceAtMost(1f)
            )
        } catch (error: Exception) {
            Log.e(TAG, "Capture-validity inference failed", error)
            null
        }
    }

    /** Reinicia la estabilidad temporal (p. ej. al reanudar la cámara). */
    fun reset() {
        decisionEngine.reset()
    }

    fun close() {
        semanticClassifier?.close()
    }

    private fun buildResult(
        verdict: GuidanceDecisionEngine.Verdict,
        detection: MoleDetectionProcessor.MoleDetection?,
        semanticPrediction: CaptureValidityContract.Prediction?
    ): GuidanceResult {
        val moleDetectedState = detection?.let {
            CaptureState.MoleDetected(
                centerX = it.centerPoint.x.toFloat(),
                centerY = it.centerPoint.y.toFloat(),
                confidence = it.confidence,
                isInCenter = it.isInCenter
            )
        }

        val (state, guideState, messageRes) = when (verdict) {
            GuidanceDecisionEngine.Verdict.READY ->
                Triple(CaptureState.ReadyToCapture, GuideState.READY, R.string.guidance_ready)
            GuidanceDecisionEngine.Verdict.POOR_LIGHTING ->
                Triple(CaptureState.PoorLighting, GuideState.POOR_LIGHTING, R.string.guidance_poor_lighting)
            GuidanceDecisionEngine.Verdict.TOO_BRIGHT ->
                Triple(CaptureState.PoorLighting, GuideState.POOR_LIGHTING, R.string.guidance_too_bright)
            GuidanceDecisionEngine.Verdict.BLURRY ->
                Triple(CaptureState.Blurry, GuideState.BLURRY, R.string.guidance_blurry)
            GuidanceDecisionEngine.Verdict.MODEL_UNAVAILABLE ->
                Triple(
                    CaptureState.Searching,
                    GuideState.SEARCHING,
                    R.string.guidance_capture_model_unavailable
                )
            GuidanceDecisionEngine.Verdict.SEARCHING ->
                Triple(CaptureState.Searching, GuideState.SEARCHING, R.string.guidance_searching)
            GuidanceDecisionEngine.Verdict.CENTERING ->
                Triple(
                    moleDetectedState ?: CaptureState.Searching,
                    GuideState.CENTERING,
                    R.string.guidance_centering
                )
        }

        return GuidanceResult(
            state = state,
            guideState = guideState,
            message = context.getString(messageRes),
            molePosition = detection?.centerPoint,
            moleConfidence = detection?.confidence ?: 0f,
            semanticPrediction = semanticPrediction
        )
    }
}

package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Mat
import org.opencv.core.Point


class SimplifiedGuidanceManager(
    private val context: Context,
    private val config: CaptureGuidanceConfig = CaptureGuidanceConfig()
) {
    companion object {
        private const val TAG = "SimplifiedGuidanceManager"
    }

    private val moleDetector = MoleDetectionProcessor()
    private val lightingAnalyzer = ImageQualityAnalyzer()
    
    private var currentState: CaptureState = CaptureState.Searching
    private var currentGuideState: GuideState = GuideState.SEARCHING


    suspend fun processFrame(frame: Mat): GuidanceResult = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Procesando frame para guía simplificada")

            val lightingAnalysis = lightingAnalyzer.analyzeLighting(frame)
            
            if (!lightingAnalysis.isOptimalLighting) {
                currentState = CaptureState.PoorLighting
                currentGuideState = GuideState.POOR_LIGHTING
                return@withContext GuidanceResult(
                    state = currentState,
                    guideState = currentGuideState,
                    message = lightingAnalysis.getFeedbackMessage(),
                    molePosition = null
                )
            }

            val moleDetection = moleDetector.detectMoleInCenter(frame)
            
            if (moleDetection == null) {
                currentState = CaptureState.Searching
                currentGuideState = GuideState.SEARCHING
                return@withContext GuidanceResult(
                    state = currentState,
                    guideState = currentGuideState,
                    message = "Busca un lunar en el área circular",
                    molePosition = null
                )
            }

            val isWellCentered = moleDetection.isInCenter
            val hasGoodConfidence = moleDetection.confidence >= config.minMoleConfidence
            
            when {
                !isWellCentered -> {
                    currentState = CaptureState.MoleDetected(
                        centerX = moleDetection.centerPoint.x.toFloat(),
                        centerY = moleDetection.centerPoint.y.toFloat(),
                        confidence = moleDetection.confidence,
                        isInCenter = false
                    )
                    currentGuideState = GuideState.CENTERING
                    GuidanceResult(
                        state = currentState,
                        guideState = currentGuideState,
                        message = "Centra el lunar en el círculo",
                        molePosition = moleDetection.centerPoint
                    )
                }
                
                hasGoodConfidence && isWellCentered -> {
                    currentState = CaptureState.ReadyToCapture
                    currentGuideState = GuideState.READY
                    GuidanceResult(
                        state = currentState,
                        guideState = currentGuideState,
                        message = "¡Listo para capturar!",
                        molePosition = moleDetection.centerPoint
                    )
                }
                
                else -> {
                    currentState = CaptureState.MoleDetected(
                        centerX = moleDetection.centerPoint.x.toFloat(),
                        centerY = moleDetection.centerPoint.y.toFloat(),
                        confidence = moleDetection.confidence,
                        isInCenter = isWellCentered
                    )
                    currentGuideState = GuideState.CENTERING
                    GuidanceResult(
                        state = currentState,
                        guideState = currentGuideState,
                        message = "Ajusta la posición del lunar",
                        molePosition = moleDetection.centerPoint
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando frame", e)
            GuidanceResult(
                state = CaptureState.Searching,
                guideState = GuideState.SEARCHING,
                message = "Error en el análisis",
                molePosition = null
            )
        }
    }


    data class GuidanceResult(
        val state: CaptureState,
        val guideState: GuideState,
        val message: String,
        val molePosition: Point?
    )

    fun getCurrentState(): CaptureState = currentState
    fun getCurrentGuideState(): GuideState = currentGuideState
}
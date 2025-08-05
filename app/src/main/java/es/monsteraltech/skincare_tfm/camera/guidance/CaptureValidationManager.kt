package es.monsteraltech.skincare_tfm.camera.guidance
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt
class CaptureValidationManager {
    companion object {
        private const val TAG = "CaptureValidationManager"
        private const val DEFAULT_CENTERING_TOLERANCE = 100f
        private const val DEFAULT_MIN_MOLE_AREA_RATIO = 0.15f
        private const val DEFAULT_MAX_MOLE_AREA_RATIO = 0.80f
        private const val DEFAULT_MIN_SHARPNESS = 0.3f
        private const val DEFAULT_MIN_BRIGHTNESS = 80f
        private const val DEFAULT_MAX_BRIGHTNESS = 180f
        private const val DEFAULT_MIN_CONFIDENCE = 0.6f
    }
    enum class GuideState {
        SEARCHING,
        CENTERING,
        TOO_FAR,
        TOO_CLOSE,
        POOR_LIGHTING,
        BLURRY,
        READY
    }
    enum class ValidationFailureReason {
        NOT_CENTERED,
        TOO_FAR,
        TOO_CLOSE,
        BLURRY,
        POOR_LIGHTING,
        NO_MOLE_DETECTED,
        LOW_CONFIDENCE
    }
    data class ValidationResult(
        val canCapture: Boolean,
        val guideState: GuideState,
        val message: String,
        val confidence: Float,
        val failureReason: ValidationFailureReason? = null,
        val distanceFromCenter: Float = 0f,
        val moleAreaRatio: Float = 0f
    )
    data class ValidationConfig(
        val centeringTolerance: Float = DEFAULT_CENTERING_TOLERANCE,
        val minMoleAreaRatio: Float = DEFAULT_MIN_MOLE_AREA_RATIO,
        val maxMoleAreaRatio: Float = DEFAULT_MAX_MOLE_AREA_RATIO,
        val minSharpness: Float = DEFAULT_MIN_SHARPNESS,
        val minBrightness: Float = DEFAULT_MIN_BRIGHTNESS,
        val maxBrightness: Float = DEFAULT_MAX_BRIGHTNESS,
        val minConfidence: Float = DEFAULT_MIN_CONFIDENCE
    )
    private val config = ValidationConfig()
    fun validateCapture(
        moleDetection: MoleDetectionProcessor.MoleDetection?,
        qualityMetrics: ImageQualityAnalyzer.QualityMetrics,
        guideArea: RectF
    ): ValidationResult {
        Log.d(TAG, "Iniciando validación de captura")
        Log.d(TAG, "Lunar detectado: ${moleDetection != null}")
        Log.d(TAG, "Calidad - Nitidez: ${qualityMetrics.sharpness}, Brillo: ${qualityMetrics.brightness}")
        val qualityValidation = validateImageQuality(qualityMetrics)
        if (!qualityValidation.canCapture) {
            return qualityValidation
        }
        if (moleDetection == null) {
            return ValidationResult(
                canCapture = false,
                guideState = GuideState.SEARCHING,
                message = "Buscando lunar...",
                confidence = 0f,
                failureReason = ValidationFailureReason.NO_MOLE_DETECTED
            )
        }
        if (moleDetection.confidence < config.minConfidence) {
            return ValidationResult(
                canCapture = false,
                guideState = GuideState.SEARCHING,
                message = "Detección poco confiable - ajusta la posición",
                confidence = moleDetection.confidence,
                failureReason = ValidationFailureReason.LOW_CONFIDENCE
            )
        }
        return validateMolePositioning(moleDetection, guideArea)
    }
    private fun validateImageQuality(qualityMetrics: ImageQualityAnalyzer.QualityMetrics): ValidationResult {
        if (qualityMetrics.isBlurry) {
            return ValidationResult(
                canCapture = false,
                guideState = GuideState.BLURRY,
                message = "Imagen borrosa - mantén firme la cámara",
                confidence = 0f,
                failureReason = ValidationFailureReason.BLURRY
            )
        }
        if (qualityMetrics.isUnderexposed) {
            return ValidationResult(
                canCapture = false,
                guideState = GuideState.POOR_LIGHTING,
                message = "Necesitas más luz",
                confidence = 0f,
                failureReason = ValidationFailureReason.POOR_LIGHTING
            )
        }
        if (qualityMetrics.isOverexposed) {
            return ValidationResult(
                canCapture = false,
                guideState = GuideState.POOR_LIGHTING,
                message = "Demasiada luz - busca sombra",
                confidence = 0f,
                failureReason = ValidationFailureReason.POOR_LIGHTING
            )
        }
        return ValidationResult(
            canCapture = true,
            guideState = GuideState.READY,
            message = "Calidad de imagen buena",
            confidence = 1f
        )
    }
    private fun validateMolePositioning(
        moleDetection: MoleDetectionProcessor.MoleDetection,
        guideArea: RectF
    ): ValidationResult {
        val guideCenterX = guideArea.centerX()
        val guideCenterY = guideArea.centerY()
        val guideCenter = PointF(guideCenterX, guideCenterY)
        val moleCenter = PointF(
            moleDetection.centerPoint.x.toFloat(),
            moleDetection.centerPoint.y.toFloat()
        )
        val distanceFromCenter = calculateDistance(moleCenter, guideCenter)
        Log.d(TAG, "Validación de centrado desactivada: siempre centrado")
        val guideAreaSize = guideArea.width() * guideArea.height()
        val moleAreaRatio = (moleDetection.area / guideAreaSize).toFloat()
        Log.d(TAG, "Ratio de área del lunar: $moleAreaRatio")
        return ValidationResult(
            canCapture = true,
            guideState = GuideState.READY,
            message = "Listo para capturar",
            confidence = moleDetection.confidence,
            distanceFromCenter = distanceFromCenter,
            moleAreaRatio = moleAreaRatio
        )
    }
    private fun calculateDistance(point1: PointF, point2: PointF): Float {
        val dx = point1.x - point2.x
        val dy = point1.y - point2.y
        return sqrt(dx * dx + dy * dy)
    }
    fun calculateCenteringPercentage(
        moleCenter: PointF,
        guideCenter: PointF
    ): Float {
        val distance = calculateDistance(moleCenter, guideCenter)
        val percentage = ((config.centeringTolerance - distance) / config.centeringTolerance * 100f)
            .coerceIn(0f, 100f)
        return percentage
    }
    fun calculateSizePercentage(moleAreaRatio: Float): Float {
        val optimalRatio = (config.minMoleAreaRatio + config.maxMoleAreaRatio) / 2f
        val tolerance = (config.maxMoleAreaRatio - config.minMoleAreaRatio) / 2f
        val distance = abs(moleAreaRatio - optimalRatio)
        val percentage = ((tolerance - distance) / tolerance * 100f).coerceIn(0f, 100f)
        return percentage
    }
    fun getDetailedGuidanceMessage(validationResult: ValidationResult): String {
        return when (validationResult.guideState) {
            GuideState.SEARCHING -> "Busca un lunar y colócalo en el centro de la guía"
            GuideState.CENTERING -> {
                val direction = if (validationResult.distanceFromCenter > config.centeringTolerance / 2) {
                    "Mueve la cámara para centrar el lunar"
                } else {
                    "Casi centrado - ajusta ligeramente"
                }
                direction
            }
            GuideState.TOO_FAR -> "El lunar está muy lejos - acércate más"
            GuideState.TOO_CLOSE -> "El lunar está muy cerca - aléjate un poco"
            GuideState.POOR_LIGHTING -> validationResult.message
            GuideState.BLURRY -> "Mantén la cámara firme y enfoca bien"
            GuideState.READY -> "¡Perfecto! Toca para capturar"
        }
    }
    fun updateConfig(newConfig: ValidationConfig) {
        Log.d(TAG, "Configuración actualizada: $newConfig")
    }
    fun getConfig(): ValidationConfig = config
}
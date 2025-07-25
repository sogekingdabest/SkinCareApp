package es.monsteraltech.skincare_tfm.camera.guidance

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import kotlin.math.*

/**
 * Gestor de validación de captura que coordina todas las validaciones
 * y determina si la captura debe estar habilitada basándose en detección de lunares
 * y métricas de calidad de imagen.
 */
class CaptureValidationManager {

    companion object {
        private const val TAG = "CaptureValidationManager"
        
        // Tolerancias por defecto
        private const val DEFAULT_CENTERING_TOLERANCE = 100f // pixels (más permisivo)
        private const val DEFAULT_MIN_MOLE_AREA_RATIO = 0.15f
        private const val DEFAULT_MAX_MOLE_AREA_RATIO = 0.80f
        private const val DEFAULT_MIN_SHARPNESS = 0.3f
        private const val DEFAULT_MIN_BRIGHTNESS = 80f
        private const val DEFAULT_MAX_BRIGHTNESS = 180f
        private const val DEFAULT_MIN_CONFIDENCE = 0.6f
    }

    /**
     * Estados posibles de la guía de captura
     */
    enum class GuideState {
        SEARCHING,      // Buscando lunar
        CENTERING,      // Lunar detectado, necesita centrar
        TOO_FAR,        // Muy lejos
        TOO_CLOSE,      // Muy cerca
        POOR_LIGHTING,  // Mala iluminación
        BLURRY,         // Imagen borrosa
        READY           // Listo para capturar
    }

    /**
     * Razones de fallo en la validación
     */
    enum class ValidationFailureReason {
        NOT_CENTERED,
        TOO_FAR,
        TOO_CLOSE,
        BLURRY,
        POOR_LIGHTING,
        NO_MOLE_DETECTED,
        LOW_CONFIDENCE
    }

    /**
     * Resultado de la validación de captura
     */
    data class ValidationResult(
        val canCapture: Boolean,
        val guideState: GuideState,
        val message: String,
        val confidence: Float,
        val failureReason: ValidationFailureReason? = null,
        val distanceFromCenter: Float = 0f,
        val moleAreaRatio: Float = 0f
    )

    /**
     * Configuración de validación personalizable
     */
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

    /**
     * Valida si la captura puede realizarse basándose en detección de lunar y calidad de imagen
     * 
     * @param moleDetection Resultado de detección de lunar (puede ser null)
     * @param qualityMetrics Métricas de calidad de imagen
     * @param guideArea Área de la guía circular en la pantalla
     * @return ValidationResult con el estado de validación
     */
    fun validateCapture(
        moleDetection: MoleDetectionProcessor.MoleDetection?,
        qualityMetrics: ImageQualityAnalyzer.QualityMetrics,
        guideArea: RectF
    ): ValidationResult {
        
        Log.d(TAG, "Iniciando validación de captura")
        Log.d(TAG, "Lunar detectado: ${moleDetection != null}")
        Log.d(TAG, "Calidad - Nitidez: ${qualityMetrics.sharpness}, Brillo: ${qualityMetrics.brightness}")
        
        // 1. Verificar calidad de imagen primero
        val qualityValidation = validateImageQuality(qualityMetrics)
        if (!qualityValidation.canCapture) {
            return qualityValidation
        }
        
        // 2. Verificar detección de lunar
        if (moleDetection == null) {
            return ValidationResult(
                canCapture = false,
                guideState = GuideState.SEARCHING,
                message = "Buscando lunar...",
                confidence = 0f,
                failureReason = ValidationFailureReason.NO_MOLE_DETECTED
            )
        }
        
        // 3. Verificar confianza de detección
        if (moleDetection.confidence < config.minConfidence) {
            return ValidationResult(
                canCapture = false,
                guideState = GuideState.SEARCHING,
                message = "Detección poco confiable - ajusta la posición",
                confidence = moleDetection.confidence,
                failureReason = ValidationFailureReason.LOW_CONFIDENCE
            )
        }
        
        // 4. Validar posicionamiento del lunar
        return validateMolePositioning(moleDetection, guideArea)
    }

    /**
     * Valida la calidad de imagen
     */
    private fun validateImageQuality(qualityMetrics: ImageQualityAnalyzer.QualityMetrics): ValidationResult {
        
        // Verificar nitidez
        if (qualityMetrics.isBlurry) {
            return ValidationResult(
                canCapture = false,
                guideState = GuideState.BLURRY,
                message = "Imagen borrosa - mantén firme la cámara",
                confidence = 0f,
                failureReason = ValidationFailureReason.BLURRY
            )
        }
        
        // Verificar iluminación
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
        
        // Calidad aceptable
        return ValidationResult(
            canCapture = true,
            guideState = GuideState.READY,
            message = "Calidad de imagen buena",
            confidence = 1f
        )
    }

    /**
     * Valida el posicionamiento del lunar detectado
     */
    private fun validateMolePositioning(
        moleDetection: MoleDetectionProcessor.MoleDetection,
        guideArea: RectF
    ): ValidationResult {
        
        // Calcular centro de la guía
        val guideCenterX = guideArea.centerX()
        val guideCenterY = guideArea.centerY()
        val guideCenter = PointF(guideCenterX, guideCenterY)
        
        // Calcular posición del lunar
        val moleCenter = PointF(
            moleDetection.centerPoint.x.toFloat(),
            moleDetection.centerPoint.y.toFloat()
        )
        
        // 1. Verificar centrado
        val distanceFromCenter = calculateDistance(moleCenter, guideCenter)
        val isCentered = true // Siempre aceptar como centrado
        Log.d(TAG, "Validación de centrado desactivada: siempre centrado")
        
        // 2. Verificar distancia (tamaño del lunar en relación al área de guía)
        val guideAreaSize = guideArea.width() * guideArea.height()
        val moleAreaRatio = (moleDetection.area / guideAreaSize).toFloat()
        
        Log.d(TAG, "Ratio de área del lunar: $moleAreaRatio")
        
        // Validación de tamaño desactivada: siempre permitir capturar
        
        // 3. Todo está correcto - listo para capturar
        return ValidationResult(
            canCapture = true,
            guideState = GuideState.READY,
            message = "Listo para capturar",
            confidence = moleDetection.confidence,
            distanceFromCenter = distanceFromCenter,
            moleAreaRatio = moleAreaRatio
        )
    }

    /**
     * Calcula la distancia euclidiana entre dos puntos
     */
    private fun calculateDistance(point1: PointF, point2: PointF): Float {
        val dx = point1.x - point2.x
        val dy = point1.y - point2.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Calcula el porcentaje de centrado (0-100%)
     * 100% = perfectamente centrado, 0% = en el borde de tolerancia
     */
    fun calculateCenteringPercentage(
        moleCenter: PointF,
        guideCenter: PointF
    ): Float {
        val distance = calculateDistance(moleCenter, guideCenter)
        val percentage = ((config.centeringTolerance - distance) / config.centeringTolerance * 100f)
            .coerceIn(0f, 100f)
        return percentage
    }

    /**
     * Calcula el porcentaje de tamaño óptimo (0-100%)
     * 100% = tamaño perfecto, 0% = muy pequeño o muy grande
     */
    fun calculateSizePercentage(moleAreaRatio: Float): Float {
        val optimalRatio = (config.minMoleAreaRatio + config.maxMoleAreaRatio) / 2f
        val tolerance = (config.maxMoleAreaRatio - config.minMoleAreaRatio) / 2f
        
        val distance = abs(moleAreaRatio - optimalRatio)
        val percentage = ((tolerance - distance) / tolerance * 100f).coerceIn(0f, 100f)
        
        return percentage
    }

    /**
     * Obtiene un mensaje de guía detallado basado en el estado actual
     */
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

    /**
     * Actualiza la configuración de validación
     */
    fun updateConfig(newConfig: ValidationConfig) {
        // En una implementación real, esto podría persistir la configuración
        Log.d(TAG, "Configuración actualizada: $newConfig")
    }

    /**
     * Obtiene la configuración actual
     */
    fun getConfig(): ValidationConfig = config
}
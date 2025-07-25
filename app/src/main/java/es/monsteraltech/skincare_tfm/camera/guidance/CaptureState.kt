package es.monsteraltech.skincare_tfm.camera.guidance

/**
 * Punto simple para coordenadas de posición
 */
data class Point(val x: Float, val y: Float)

/**
 * Estados del proceso de captura con guías inteligentes
 */
sealed class CaptureState {
    /**
     * Estado inicial, inicializando el sistema
     */
    object Initializing : CaptureState()
    
    /**
     * Buscando lunar en la imagen
     */
    object Searching : CaptureState()
    
    /**
     * Lunar detectado con información de posición
     */
    data class MoleDetected(
        val position: Point,
        val confidence: Float,
        val area: Float
    ) : CaptureState()
    
    /**
     * Validación falló con razón específica
     */
    data class ValidationFailed(
        val reason: ValidationFailureReason,
        val message: String
    ) : CaptureState()
    
    /**
     * Listo para capturar la imagen
     */
    object ReadyToCapture : CaptureState()
    
    /**
     * Proceso de captura en curso
     */
    object Capturing : CaptureState()
}
package es.monsteraltech.skincare_tfm.analysis

/**
 * Interface para comunicar el progreso del procesamiento asíncrono de imágenes
 * con soporte mejorado de accesibilidad y estimación de tiempo.
 */
interface ProgressCallback {
    
    /**
     * Actualización básica de progreso
     * @param progress Progreso actual (0-100)
     * @param message Mensaje descriptivo del estado actual
     */
    fun onProgressUpdate(progress: Int, message: String)
    
    /**
     * Actualización de progreso con estimación de tiempo
     * @param progress Progreso actual (0-100)
     * @param message Mensaje descriptivo del estado actual
     * @param estimatedTotalTimeMs Tiempo total estimado en milisegundos
     */
    fun onProgressUpdateWithTimeEstimate(progress: Int, message: String, estimatedTotalTimeMs: Long)
    
    /**
     * Cambio de etapa del procesamiento
     * @param stage Nueva etapa del procesamiento
     */
    fun onStageChanged(stage: ProcessingStage)
    
    /**
     * Error durante el procesamiento
     * @param error Mensaje de error descriptivo
     */
    fun onError(error: String)
    
    /**
     * Procesamiento completado exitosamente
     * @param result Resultado del análisis combinado
     */
    fun onCompleted(result: MelanomaAIDetector.CombinedAnalysisResult)
    
    /**
     * Procesamiento cancelado por el usuario
     */
    fun onCancelled()
}
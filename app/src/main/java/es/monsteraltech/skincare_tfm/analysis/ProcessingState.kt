package es.monsteraltech.skincare_tfm.analysis

/**
 * Data class que representa el estado actual del procesamiento de imagen
 */
data class ProcessingState(
    /**
     * Etapa actual del procesamiento
     */
    val stage: ProcessingStage,
    
    /**
     * Progreso actual (0-100)
     */
    val progress: Int,
    
    /**
     * Mensaje descriptivo del estado actual
     */
    val message: String,
    
    /**
     * Indica si el procesamiento se ha completado
     */
    val isCompleted: Boolean = false,
    
    /**
     * Mensaje de error si ocurrió algún problema (null si no hay error)
     */
    val error: String? = null,
    
    /**
     * Indica si el procesamiento fue cancelado
     */
    val isCancelled: Boolean = false
) {
    /**
     * Indica si el procesamiento está en un estado de error
     */
    val hasError: Boolean
        get() = error != null
    
    /**
     * Indica si el procesamiento está activo (no completado, cancelado o con error)
     */
    val isActive: Boolean
        get() = !isCompleted && !isCancelled && !hasError
    
    companion object {
        /**
         * Crea un estado inicial para el procesamiento
         */
        fun initial(): ProcessingState {
            return ProcessingState(
                stage = ProcessingStage.INITIALIZING,
                progress = 0,
                message = ProcessingStage.INITIALIZING.message
            )
        }
        
        /**
         * Crea un estado de error
         */
        fun error(errorMessage: String, currentStage: ProcessingStage = ProcessingStage.INITIALIZING): ProcessingState {
            return ProcessingState(
                stage = currentStage,
                progress = currentStage.getProgressUpToStage(),
                message = "Error: $errorMessage",
                error = errorMessage
            )
        }
        
        /**
         * Crea un estado de cancelación
         */
        fun cancelled(currentStage: ProcessingStage = ProcessingStage.INITIALIZING): ProcessingState {
            return ProcessingState(
                stage = currentStage,
                progress = currentStage.getProgressUpToStage(),
                message = "Procesamiento cancelado",
                isCancelled = true
            )
        }
        
        /**
         * Crea un estado de completado
         */
        fun completed(): ProcessingState {
            return ProcessingState(
                stage = ProcessingStage.FINALIZING,
                progress = 100,
                message = "Análisis completado",
                isCompleted = true
            )
        }
    }
}
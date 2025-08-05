package es.monsteraltech.skincare_tfm.analysis
sealed class AnalysisError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    object Timeout : AnalysisError("El análisis está tomando más tiempo del esperado")
    object OutOfMemory : AnalysisError("Memoria insuficiente para procesar la imagen")
    data class AIModelError(val details: String) : AnalysisError("Error en el modelo de IA: $details")
    data class ImageProcessingError(val details: String) : AnalysisError("Error al procesar la imagen: $details")
    data class NetworkError(val details: String) : AnalysisError("Error de conectividad: $details")
    data class ABCDEAnalysisError(val details: String) : AnalysisError("Error en análisis ABCDE: $details")
    data class ConfigurationError(val details: String) : AnalysisError("Configuración inválida: $details")
    data class InvalidImageError(val details: String) : AnalysisError("Imagen inválida: $details")
    object UserCancellation : AnalysisError("Procesamiento cancelado por el usuario")
    data class UnknownError(val details: String, val originalCause: Throwable? = null) :
        AnalysisError("Error desconocido: $details", originalCause)
    fun getUserFriendlyMessage(): String {
        return when (this) {
            is Timeout -> "El análisis está tardando más de lo esperado. Puedes cancelar e intentar de nuevo."
            is OutOfMemory -> "La imagen es muy grande para procesar. Se intentará reducir su tamaño automáticamente."
            is AIModelError -> "El análisis con IA no está disponible temporalmente. Se continuará con análisis ABCDE."
            is ImageProcessingError -> "Hubo un problema al procesar la imagen. Verifica que sea una foto clara del lunar."
            is NetworkError -> "Problema de conectividad. Verifica tu conexión a internet."
            is ABCDEAnalysisError -> "Error en el análisis ABCDE. Se usarán valores por defecto."
            is ConfigurationError -> "Error de configuración. Se usará configuración por defecto."
            is InvalidImageError -> "La imagen no es válida. Toma una nueva foto del lunar."
            is UserCancellation -> "Análisis cancelado."
            is UnknownError -> "Error inesperado. Intenta de nuevo."
        }
    }
    fun isRecoverable(): Boolean {
        return when (this) {
            is Timeout -> true
            is OutOfMemory -> true
            is AIModelError -> true
            is NetworkError -> true
            is ABCDEAnalysisError -> true
            is ConfigurationError -> true
            is ImageProcessingError -> false
            is InvalidImageError -> false
            is UserCancellation -> false
            is UnknownError -> false
        }
    }
    fun getRecoveryStrategy(): RecoveryStrategy {
        return when (this) {
            is Timeout -> RecoveryStrategy.EXTEND_TIMEOUT
            is OutOfMemory -> RecoveryStrategy.REDUCE_IMAGE_RESOLUTION
            is AIModelError -> RecoveryStrategy.FALLBACK_TO_ABCDE
            is NetworkError -> RecoveryStrategy.RETRY_WITH_DELAY
            is ABCDEAnalysisError -> RecoveryStrategy.USE_DEFAULT_VALUES
            is ConfigurationError -> RecoveryStrategy.USE_DEFAULT_CONFIG
            else -> RecoveryStrategy.NONE
        }
    }
}
enum class RecoveryStrategy {
    NONE,
    EXTEND_TIMEOUT,
    REDUCE_IMAGE_RESOLUTION,
    FALLBACK_TO_ABCDE,
    RETRY_WITH_DELAY,
    USE_DEFAULT_VALUES,
    USE_DEFAULT_CONFIG
}
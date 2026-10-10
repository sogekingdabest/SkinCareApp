package com.monsteraltech.skincare.analysis
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
            is Timeout -> "El análisis no terminó a tiempo. Intenta de nuevo."
            is OutOfMemory -> "Memoria insuficiente para completar el análisis. Intenta con una imagen más pequeña."
            is AIModelError -> "El análisis con IA no está disponible."
            is ImageProcessingError -> "Hubo un problema al procesar la imagen. Verifica que sea una foto clara del lunar."
            is NetworkError -> "Problema de conectividad. Verifica tu conexión a internet."
            is ABCDEAnalysisError -> "No se pudo completar el análisis ABCDE."
            is ConfigurationError -> "La configuración del análisis no es válida."
            is InvalidImageError -> "La imagen no es válida. Toma una nueva foto del lunar."
            is UserCancellation -> "Análisis cancelado."
            is UnknownError -> "Error inesperado. Intenta de nuevo."
        }
    }
}

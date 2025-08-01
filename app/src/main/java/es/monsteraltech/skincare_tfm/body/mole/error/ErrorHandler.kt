package es.monsteraltech.skincare_tfm.body.mole.error

import android.content.Context
import android.util.Log
import es.monsteraltech.skincare_tfm.R
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Utilidad centralizada para manejo de errores en el sistema de análisis de lunares
 * Proporciona mensajes de error contextuales y estrategias de recuperación
 */
object ErrorHandler {

    /**
     * Tipos de errores comunes en el sistema
     */
    enum class ErrorType {
        NETWORK_ERROR,
        AUTHENTICATION_ERROR,
        DATA_NOT_FOUND,
        VALIDATION_ERROR,
        PERMISSION_ERROR,
        STORAGE_ERROR,
        PARSING_ERROR,
        UNKNOWN_ERROR
    }

    /**
     * Resultado de manejo de error con información contextual
     */
    data class ErrorResult(
        val type: ErrorType,
        val userMessage: String,
        val technicalMessage: String,
        val isRetryable: Boolean,
        val suggestedAction: String? = null
    )

    /**
     * Maneja una excepción y devuelve información contextual para el usuario
     */
    fun handleError(context: Context, exception: Throwable, operation: String = ""): ErrorResult {
        Log.e("ErrorHandler", "Error en operación: $operation", exception)

        return when (exception) {
            is ConnectException, is UnknownHostException -> {
                ErrorResult(
                    type = ErrorType.NETWORK_ERROR,
                    userMessage = context.getString(R.string.error_network_connection),
                    technicalMessage = "Error de conexión: ${exception.message}",
                    isRetryable = true,
                    suggestedAction = context.getString(R.string.error_action_check_connection)
                )
            }
            
            is SocketTimeoutException -> {
                ErrorResult(
                    type = ErrorType.NETWORK_ERROR,
                    userMessage = context.getString(R.string.error_network_timeout),
                    technicalMessage = "Timeout de conexión: ${exception.message}",
                    isRetryable = true,
                    suggestedAction = context.getString(R.string.error_action_retry_later)
                )
            }
            
            is SSLException -> {
                ErrorResult(
                    type = ErrorType.NETWORK_ERROR,
                    userMessage = context.getString(R.string.error_network_ssl),
                    technicalMessage = "Error SSL: ${exception.message}",
                    isRetryable = true,
                    suggestedAction = context.getString(R.string.error_action_check_connection)
                )
            }
            
            is SecurityException -> {
                ErrorResult(
                    type = ErrorType.PERMISSION_ERROR,
                    userMessage = context.getString(R.string.error_permission_denied),
                    technicalMessage = "Error de permisos: ${exception.message}",
                    isRetryable = false,
                    suggestedAction = context.getString(R.string.error_action_check_permissions)
                )
            }
            
            else -> {
                // Analizar mensaje de error para casos específicos
                val message = exception.message?.lowercase() ?: ""
                
                when {
                    message.contains("usuario no autenticado") || message.contains("authentication") -> {
                        ErrorResult(
                            type = ErrorType.AUTHENTICATION_ERROR,
                            userMessage = context.getString(R.string.error_authentication),
                            technicalMessage = "Error de autenticación: ${exception.message}",
                            isRetryable = false,
                            suggestedAction = context.getString(R.string.error_action_login_again)
                        )
                    }
                    
                    message.contains("no se encontró") || message.contains("not found") -> {
                        ErrorResult(
                            type = ErrorType.DATA_NOT_FOUND,
                            userMessage = context.getString(R.string.error_data_not_found),
                            technicalMessage = "Datos no encontrados: ${exception.message}",
                            isRetryable = true,
                            suggestedAction = context.getString(R.string.error_action_refresh_data)
                        )
                    }
                    
                    message.contains("validación") || message.contains("validation") || message.contains("inválido") -> {
                        ErrorResult(
                            type = ErrorType.VALIDATION_ERROR,
                            userMessage = context.getString(R.string.error_validation),
                            technicalMessage = "Error de validación: ${exception.message}",
                            isRetryable = false,
                            suggestedAction = context.getString(R.string.error_action_check_data)
                        )
                    }
                    
                    message.contains("storage") || message.contains("almacenamiento") -> {
                        ErrorResult(
                            type = ErrorType.STORAGE_ERROR,
                            userMessage = context.getString(R.string.error_storage),
                            technicalMessage = "Error de almacenamiento: ${exception.message}",
                            isRetryable = true,
                            suggestedAction = context.getString(R.string.error_action_free_space)
                        )
                    }
                    
                    message.contains("parsing") || message.contains("parsear") -> {
                        ErrorResult(
                            type = ErrorType.PARSING_ERROR,
                            userMessage = context.getString(R.string.error_parsing),
                            technicalMessage = "Error de parseo: ${exception.message}",
                            isRetryable = true,
                            suggestedAction = context.getString(R.string.error_action_refresh_data)
                        )
                    }
                    
                    else -> {
                        ErrorResult(
                            type = ErrorType.UNKNOWN_ERROR,
                            userMessage = context.getString(R.string.error_unknown),
                            technicalMessage = "Error desconocido: ${exception.message}",
                            isRetryable = true,
                            suggestedAction = context.getString(R.string.error_action_try_again)
                        )
                    }
                }
            }
        }
    }

    /**
     * Determina si un error es recuperable automáticamente
     */
    fun isRecoverableError(exception: Throwable): Boolean {
        return when (exception) {
            is ConnectException, is UnknownHostException, is SocketTimeoutException -> true
            else -> {
                val message = exception.message?.lowercase() ?: ""
                message.contains("timeout") || 
                message.contains("connection") || 
                message.contains("network") ||
                message.contains("temporary")
            }
        }
    }

    /**
     * Obtiene el delay apropiado para reintentos basado en el tipo de error
     */
    fun getRetryDelay(attempt: Int, errorType: ErrorType): Long {
        val baseDelay = when (errorType) {
            ErrorType.NETWORK_ERROR -> 2000L // 2 segundos
            ErrorType.DATA_NOT_FOUND -> 1000L // 1 segundo
            ErrorType.STORAGE_ERROR -> 3000L // 3 segundos
            else -> 1500L // 1.5 segundos por defecto
        }
        
        // Backoff exponencial con jitter
        return (baseDelay * Math.pow(2.0, (attempt - 1).toDouble())).toLong() + 
               (Math.random() * 1000).toLong()
    }

    /**
     * Obtiene el número máximo de reintentos para un tipo de error
     */
    fun getMaxRetries(errorType: ErrorType): Int {
        return when (errorType) {
            ErrorType.NETWORK_ERROR -> 3
            ErrorType.DATA_NOT_FOUND -> 2
            ErrorType.STORAGE_ERROR -> 2
            ErrorType.PARSING_ERROR -> 1
            else -> 1
        }
    }
}
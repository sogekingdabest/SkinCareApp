package es.monsteraltech.skincare_tfm.analysis

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.delay
import androidx.core.graphics.scale

/**
 * Gestor de recuperación de errores que implementa estrategias para manejar errores comunes
 * durante el procesamiento de análisis de imagen
 */
class ErrorRecoveryManager {
    
    companion object {
        private const val TAG = "ErrorRecoveryManager"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val MIN_IMAGE_RESOLUTION = 256 * 256 // 64K pixels mínimo
    }
    
    /**
     * Intenta recuperarse de un error aplicando la estrategia apropiada
     * @param error El error que ocurrió
     * @param bitmap La imagen original (si está disponible)
     * @param config La configuración actual
     * @param attempt Número de intento actual
     * @return Par de bitmap recuperado y configuración modificada, o null si no se puede recuperar
     */
    suspend fun attemptRecovery(
        error: AnalysisError,
        bitmap: Bitmap?,
        config: AnalysisConfiguration,
        attempt: Int = 1
    ): RecoveryResult? {
        
        if (attempt > MAX_RETRY_ATTEMPTS) {
            Log.w(TAG, "Máximo número de intentos de recuperación alcanzado para: ${error.javaClass.simpleName}")
            return null
        }
        
        Log.d(TAG, "Intentando recuperación para ${error.javaClass.simpleName}, intento $attempt")
        
        return when (error.getRecoveryStrategy()) {
            RecoveryStrategy.EXTEND_TIMEOUT -> handleTimeoutRecovery(config, attempt)
            RecoveryStrategy.REDUCE_IMAGE_RESOLUTION -> handleMemoryRecovery(bitmap, config, attempt)
            RecoveryStrategy.FALLBACK_TO_ABCDE -> handleAIFallback(config)
            RecoveryStrategy.RETRY_WITH_DELAY -> handleRetryWithDelay(bitmap, config, attempt)
            RecoveryStrategy.USE_DEFAULT_VALUES -> handleDefaultValues(bitmap, config)
            RecoveryStrategy.USE_DEFAULT_CONFIG -> handleDefaultConfig(bitmap)
            RecoveryStrategy.NONE -> null
        }
    }
    
    /**
     * Maneja la recuperación por timeout extendiendo los tiempos límite
     */
    private fun handleTimeoutRecovery(
        config: AnalysisConfiguration,
        attempt: Int
    ): RecoveryResult {
        val timeoutMultiplier = 1.5f * attempt
        val newConfig = config.copy(
            timeoutMs = (config.timeoutMs * timeoutMultiplier).toLong(),
            aiTimeoutMs = (config.aiTimeoutMs * timeoutMultiplier).toLong(),
            abcdeTimeoutMs = (config.abcdeTimeoutMs * timeoutMultiplier).toLong()
        )
        
        Log.d(TAG, "Extendiendo timeout a ${newConfig.timeoutMs}ms")
        
        return RecoveryResult(
            bitmap = null, // No se modifica la imagen
            config = newConfig,
            message = "Extendiendo tiempo de análisis...",
            strategy = RecoveryStrategy.EXTEND_TIMEOUT
        )
    }
    
    /**
     * Maneja la recuperación por memoria reduciendo la resolución de la imagen
     */
    private fun handleMemoryRecovery(
        bitmap: Bitmap?,
        config: AnalysisConfiguration,
        attempt: Int
    ): RecoveryResult? {
        if (bitmap == null) {
            Log.w(TAG, "No se puede reducir resolución: bitmap es null")
            return null
        }
        
        val currentResolution = bitmap.width * bitmap.height
        val reductionFactor = 0.7f * (1f / attempt) // Reducir más en cada intento
        val newResolution = (currentResolution * reductionFactor).toInt()
        
        if (newResolution < MIN_IMAGE_RESOLUTION) {
            Log.w(TAG, "Resolución mínima alcanzada: $newResolution < $MIN_IMAGE_RESOLUTION")
            return null
        }
        
        val scaleFactor = kotlin.math.sqrt(newResolution.toDouble() / currentResolution)
        val newWidth = (bitmap.width * scaleFactor).toInt()
        val newHeight = (bitmap.height * scaleFactor).toInt()
        
        Log.d(TAG, "Reduciendo imagen de ${bitmap.width}x${bitmap.height} a ${newWidth}x${newHeight}")
        
        val reducedBitmap = try {
            bitmap.scale(newWidth, newHeight)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Error de memoria al reducir imagen", e)
            return null
        }
        
        val newConfig = config.copy(
            maxImageResolution = newResolution,
            enableAutoImageReduction = true,
            compressionQuality = maxOf(50, config.compressionQuality - 10 * attempt)
        )
        
        return RecoveryResult(
            bitmap = reducedBitmap,
            config = newConfig,
            message = "Reduciendo tamaño de imagen para optimizar memoria...",
            strategy = RecoveryStrategy.REDUCE_IMAGE_RESOLUTION
        )
    }
    
    /**
     * Maneja el fallback cuando falla el análisis de IA
     */
    private fun handleAIFallback(config: AnalysisConfiguration): RecoveryResult {
        Log.d(TAG, "Deshabilitando IA, continuando solo con ABCDE")
        
        val newConfig = config.copy(
            enableAI = false,
            enableABCDE = true,
            enableParallelProcessing = false,
            timeoutMs = config.abcdeTimeoutMs + 5000L // Un poco más de tiempo para ABCDE solo
        )
        
        return RecoveryResult(
            bitmap = null,
            config = newConfig,
            message = "IA no disponible, continuando con análisis ABCDE...",
            strategy = RecoveryStrategy.FALLBACK_TO_ABCDE
        )
    }
    
    /**
     * Maneja reintentos con delay para errores temporales
     */
    private suspend fun handleRetryWithDelay(
        bitmap: Bitmap?,
        config: AnalysisConfiguration,
        attempt: Int
    ): RecoveryResult {
        val delayMs = RETRY_DELAY_MS * attempt
        Log.d(TAG, "Reintentando después de ${delayMs}ms")
        
        delay(delayMs)
        
        return RecoveryResult(
            bitmap = bitmap,
            config = config,
            message = "Reintentando análisis...",
            strategy = RecoveryStrategy.RETRY_WITH_DELAY
        )
    }
    
    /**
     * Maneja errores usando valores por defecto
     */
    private fun handleDefaultValues(
        bitmap: Bitmap?,
        config: AnalysisConfiguration
    ): RecoveryResult {
        Log.d(TAG, "Usando valores por defecto para continuar")
        
        return RecoveryResult(
            bitmap = bitmap,
            config = config,
            message = "Usando valores por defecto...",
            strategy = RecoveryStrategy.USE_DEFAULT_VALUES
        )
    }
    
    /**
     * Maneja errores de configuración usando configuración por defecto
     */
    private fun handleDefaultConfig(bitmap: Bitmap?): RecoveryResult {
        Log.d(TAG, "Usando configuración por defecto")
        
        return RecoveryResult(
            bitmap = bitmap,
            config = AnalysisConfiguration.default(),
            message = "Usando configuración por defecto...",
            strategy = RecoveryStrategy.USE_DEFAULT_CONFIG
        )
    }
    
    /**
     * Verifica si un error específico puede ser manejado por el recovery manager
     */
    fun canHandle(error: AnalysisError): Boolean {
        return error.isRecoverable() && error.getRecoveryStrategy() != RecoveryStrategy.NONE
    }
    
    /**
     * Obtiene información sobre la estrategia de recuperación para un error
     */
    fun getRecoveryInfo(error: AnalysisError): RecoveryInfo {
        return RecoveryInfo(
            canRecover = canHandle(error),
            strategy = error.getRecoveryStrategy(),
            userMessage = error.getUserFriendlyMessage(),
            technicalMessage = error.message ?: "Error desconocido"
        )
    }
}

/**
 * Resultado de un intento de recuperación
 */
data class RecoveryResult(
    val bitmap: Bitmap?,
    val config: AnalysisConfiguration,
    val message: String,
    val strategy: RecoveryStrategy
)

/**
 * Información sobre la capacidad de recuperación de un error
 */
data class RecoveryInfo(
    val canRecover: Boolean,
    val strategy: RecoveryStrategy,
    val userMessage: String,
    val technicalMessage: String
)
package es.monsteraltech.skincare_tfm.body.mole.error

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.pow

/**
 * Gestor de reintentos automáticos para operaciones fallidas
 * Implementa backoff exponencial con jitter para optimizar la recuperación
 */
class RetryManager() {

    /**
     * Configuración de reintentos para una operación
     */
    data class RetryConfig(
        val maxAttempts: Int = 3,
        val baseDelayMs: Long = 1000L,
        val maxDelayMs: Long = 30000L,
        val backoffMultiplier: Double = 2.0,
        val jitterMs: Long = 500L
    )

    /**
     * Resultado de una operación con reintentos
     */
    data class RetryResult<T>(
        val result: Result<T>,
        val attemptsMade: Int,
        val totalTimeMs: Long
    )

    /**
     * Ejecuta una operación con reintentos automáticos
     */
    suspend fun <T> executeWithRetry(
        operation: String,
        config: RetryConfig = RetryConfig(),
        onRetryAttempt: ((attempt: Int, exception: Throwable) -> Unit)? = null,
        block: suspend () -> Result<T>
    ): RetryResult<T> {
        val startTime = System.currentTimeMillis()
        var lastException: Throwable? = null
        
        for (attempt in 1..config.maxAttempts) {
            try {
                Log.d("RetryManager", "Ejecutando operación '$operation' - Intento $attempt/${config.maxAttempts}")
                
                val result = block()
                
                if (result.isSuccess) {
                    val totalTime = System.currentTimeMillis() - startTime
                    Log.d("RetryManager", "Operación '$operation' exitosa en intento $attempt (${totalTime}ms)")
                    
                    return RetryResult(
                        result = result,
                        attemptsMade = attempt,
                        totalTimeMs = totalTime
                    )
                } else {
                    val exception = result.exceptionOrNull()
                    lastException = exception
                    
                    if (exception != null && !ErrorHandler.isRecoverableError(exception)) {
                        Log.w("RetryManager", "Error no recuperable en operación '$operation': ${exception.message}")
                        break
                    }
                    
                    if (attempt < config.maxAttempts) {
                        onRetryAttempt?.invoke(attempt, exception ?: Exception("Operación falló"))
                        val delay = calculateDelay(attempt, config)
                        Log.d("RetryManager", "Reintentando operación '$operation' en ${delay}ms")
                        delay(delay)
                    }
                }
                
            } catch (exception: Exception) {
                lastException = exception
                Log.w("RetryManager", "Excepción en operación '$operation' - Intento $attempt", exception)
                
                if (!ErrorHandler.isRecoverableError(exception)) {
                    Log.w("RetryManager", "Excepción no recuperable en operación '$operation'")
                    break
                }
                
                if (attempt < config.maxAttempts) {
                    onRetryAttempt?.invoke(attempt, exception)
                    val delay = calculateDelay(attempt, config)
                    Log.d("RetryManager", "Reintentando operación '$operation' en ${delay}ms")
                    delay(delay)
                }
            }
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        val finalException = lastException ?: Exception("Operación falló después de ${config.maxAttempts} intentos")
        
        Log.e("RetryManager", "Operación '$operation' falló después de ${config.maxAttempts} intentos (${totalTime}ms)")
        
        return RetryResult(
            result = Result.failure(finalException),
            attemptsMade = config.maxAttempts,
            totalTimeMs = totalTime
        )
    }

    /**
     * Calcula el delay para el siguiente intento usando backoff exponencial con jitter
     */
    private fun calculateDelay(attempt: Int, config: RetryConfig): Long {
        val exponentialDelay = (config.baseDelayMs * config.backoffMultiplier.pow(attempt - 1)).toLong()
        val jitter = (Math.random() * config.jitterMs).toLong()
        val totalDelay = exponentialDelay + jitter
        
        return minOf(totalDelay, config.maxDelayMs)
    }

    /**
     * Configuraciones predefinidas para diferentes tipos de operaciones
     */
    companion object {
        fun networkConfig() = RetryConfig(
            maxAttempts = 3,
            baseDelayMs = 2000L,
            maxDelayMs = 15000L,
            backoffMultiplier = 2.0,
            jitterMs = 1000L
        )
        
        fun databaseConfig() = RetryConfig(
            maxAttempts = 2,
            baseDelayMs = 1000L,
            maxDelayMs = 5000L,
            backoffMultiplier = 1.5,
            jitterMs = 500L
        )
        
        fun imageLoadConfig() = RetryConfig(
            maxAttempts = 2,
            baseDelayMs = 1500L,
            maxDelayMs = 8000L,
            backoffMultiplier = 2.0,
            jitterMs = 750L
        )
    }
}
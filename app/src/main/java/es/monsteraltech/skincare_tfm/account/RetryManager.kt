package es.monsteraltech.skincare_tfm.account
import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.pow
class RetryManager {
    companion object {
        private const val TAG = "RetryManager"
        private const val DEFAULT_MAX_RETRIES = 3
        private const val DEFAULT_INITIAL_DELAY = 1000L
        private const val DEFAULT_MAX_DELAY = 10000L
        private const val DEFAULT_BACKOFF_MULTIPLIER = 2.0
    }
    data class RetryConfig(
        val maxRetries: Int = DEFAULT_MAX_RETRIES,
        val initialDelayMs: Long = DEFAULT_INITIAL_DELAY,
        val maxDelayMs: Long = DEFAULT_MAX_DELAY,
        val backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER,
        val retryableErrors: Set<AccountResult.ErrorType> = setOf(
            AccountResult.ErrorType.NETWORK_ERROR,
            AccountResult.ErrorType.FIREBASE_ERROR
        )
    )
    suspend fun <T> executeWithRetry(
        config: RetryConfig = RetryConfig(),
        operation: suspend () -> AccountResult<T>
    ): AccountResult<T> {
        var lastResult: AccountResult<T>
        var attempt = 0
        while (attempt <= config.maxRetries) {
            try {
                Log.d(TAG, "Executing operation, attempt ${attempt + 1}/${config.maxRetries + 1}")
                lastResult = operation()
                if (lastResult.isSuccess() || !shouldRetry(lastResult, config)) {
                    if (lastResult.isSuccess()) {
                        Log.d(TAG, "Operation succeeded on attempt ${attempt + 1}")
                    } else {
                        Log.d(TAG, "Operation failed with non-retryable error on attempt ${attempt + 1}")
                    }
                    return lastResult
                }
                if (attempt >= config.maxRetries) {
                    Log.w(TAG, "Operation failed after ${config.maxRetries + 1} attempts")
                    return lastResult
                }
                val delay = calculateDelay(attempt, config)
                Log.d(TAG, "Operation failed on attempt ${attempt + 1}, retrying in ${delay}ms")
                delay(delay)
                attempt++
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected exception during retry operation: ${e.message}", e)
                lastResult = AccountResult.error<T>(
                    e,
                    "Error inesperado durante la operación"
                )
                if (attempt >= config.maxRetries) {
                    return lastResult
                }
                val delay = calculateDelay(attempt, config)
                delay(delay)
                attempt++
            }
        }
        return AccountResult.error(
            Exception("Operación falló después de todos los intentos"),
            "La operación no pudo completarse después de varios intentos"
        )
    }
    private fun shouldRetry(result: AccountResult<*>, config: RetryConfig): Boolean {
        if (result !is AccountResult.Error) return false
        val shouldRetry = config.retryableErrors.contains(result.errorType)
        Log.d(TAG, "Should retry error type ${result.errorType}: $shouldRetry")
        return shouldRetry
    }
    private fun calculateDelay(attempt: Int, config: RetryConfig): Long {
        val exponentialDelay = (config.initialDelayMs * config.backoffMultiplier.pow(attempt)).toLong()
        val finalDelay = minOf(exponentialDelay, config.maxDelayMs)
        Log.d(TAG, "Calculated delay for attempt $attempt: ${finalDelay}ms")
        return finalDelay
    }
    fun createNetworkRetryConfig(maxRetries: Int = 3): RetryConfig {
        return RetryConfig(
            maxRetries = maxRetries,
            initialDelayMs = 1000L,
            maxDelayMs = 8000L,
            backoffMultiplier = 2.0,
            retryableErrors = setOf(
                AccountResult.ErrorType.NETWORK_ERROR,
                AccountResult.ErrorType.FIREBASE_ERROR
            )
        )
    }
    fun createAuthRetryConfig(maxRetries: Int = 2): RetryConfig {
        return RetryConfig(
            maxRetries = maxRetries,
            initialDelayMs = 2000L,
            maxDelayMs = 5000L,
            backoffMultiplier = 1.5,
            retryableErrors = setOf(
                AccountResult.ErrorType.NETWORK_ERROR,
                AccountResult.ErrorType.FIREBASE_ERROR
            )
        )
    }
    fun createDataRetryConfig(maxRetries: Int = 2): RetryConfig {
        return RetryConfig(
            maxRetries = maxRetries,
            initialDelayMs = 1500L,
            maxDelayMs = 6000L,
            backoffMultiplier = 2.0,
            retryableErrors = setOf(
                AccountResult.ErrorType.NETWORK_ERROR,
                AccountResult.ErrorType.FIREBASE_ERROR
            )
        )
    }
}
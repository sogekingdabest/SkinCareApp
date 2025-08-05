package es.monsteraltech.skincare_tfm.account
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
class ErrorHandlingIntegrationTest {
    @Test
    fun `AccountResult should work with retry manager for successful operations`() = runTest {
        val retryManager = RetryManager()
        var attemptCount = 0
        val result = retryManager.executeWithRetry {
            attemptCount++
            AccountResult.success("success data")
        }
        assertTrue(result.isSuccess())
        assertEquals("success data", result.getDataOrNull())
        assertEquals(1, attemptCount)
    }
    @Test
    fun `AccountResult should work with retry manager for retryable errors`() = runTest {
        val retryManager = RetryManager()
        var attemptCount = 0
        val config = RetryManager.RetryConfig(
            maxRetries = 2,
            initialDelayMs = 10,
            maxDelayMs = 50
        )
        val result = retryManager.executeWithRetry(config) {
            attemptCount++
            if (attemptCount <= 2) {
                AccountResult.error<String>(
                    Exception("Network timeout"),
                    "Network error",
                    AccountResult.ErrorType.NETWORK_ERROR
                )
            } else {
                AccountResult.success("success after retry")
            }
        }
        assertTrue(result.isSuccess())
        assertEquals("success after retry", result.getDataOrNull())
        assertEquals(3, attemptCount)
    }
    @Test
    fun `AccountResult should work with retry manager for non-retryable errors`() = runTest {
        val retryManager = RetryManager()
        var attemptCount = 0
        val result = retryManager.executeWithRetry {
            attemptCount++
            AccountResult.error<String>(
                Exception("Invalid input"),
                "Validation error",
                AccountResult.ErrorType.VALIDATION_ERROR
            )
        }
        assertTrue(result.isError())
        assertEquals(AccountResult.ErrorType.VALIDATION_ERROR, result.getErrorOrNull()?.errorType)
        assertEquals(1, attemptCount)
    }
    @Test
    fun `AccountResult error type detection should work correctly`() {
        val networkError = AccountResult.error<String>(Exception("network connection failed"))
        val authError = AccountResult.error<String>(Exception("authentication required"))
        val firebaseError = AccountResult.error<String>(Exception("firebase service unavailable"))
        val validationError = AccountResult.error<String>(IllegalArgumentException("invalid parameter"))
        val genericError = AccountResult.error<String>(Exception("something went wrong"))
        assertEquals(AccountResult.ErrorType.NETWORK_ERROR, networkError.getErrorOrNull()?.errorType)
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, authError.getErrorOrNull()?.errorType)
        assertEquals(AccountResult.ErrorType.FIREBASE_ERROR, firebaseError.getErrorOrNull()?.errorType)
        assertEquals(AccountResult.ErrorType.VALIDATION_ERROR, validationError.getErrorOrNull()?.errorType)
        assertEquals(AccountResult.ErrorType.GENERIC_ERROR, genericError.getErrorOrNull()?.errorType)
    }
    @Test
    fun `AccountResult chaining should work correctly`() {
        val successResult = AccountResult.success("123")
        val errorResult = AccountResult.error<String>(Exception("Error"))
        val mappedSuccess = successResult.map { it.toInt() }
        assertTrue(mappedSuccess.isSuccess())
        assertEquals(123, mappedSuccess.getDataOrNull())
        val mappedError = errorResult.map { it.toInt() }
        assertTrue(mappedError.isError())
        val flatMappedSuccess = successResult.flatMap {
            AccountResult.success(it.toInt() * 2)
        }
        assertTrue(flatMappedSuccess.isSuccess())
        assertEquals(246, flatMappedSuccess.getDataOrNull())
        val flatMappedError = errorResult.flatMap {
            AccountResult.success(it.toInt())
        }
        assertTrue(flatMappedError.isError())
    }
    @Test
    fun `AccountResult callback methods should work correctly`() {
        var successCalled = false
        var errorCalled = false
        var loadingCalled = false
        val successResult = AccountResult.success("data")
        successResult
            .onSuccess { successCalled = true }
            .onError { errorCalled = true }
            .onLoading { loadingCalled = true }
        assertTrue(successCalled)
        assertFalse(errorCalled)
        assertFalse(loadingCalled)
        successCalled = false
        errorCalled = false
        loadingCalled = false
        val errorResult = AccountResult.error<String>(Exception("Error"))
        errorResult
            .onSuccess { successCalled = true }
            .onError { errorCalled = true }
            .onLoading { loadingCalled = true }
        assertFalse(successCalled)
        assertTrue(errorCalled)
        assertFalse(loadingCalled)
        successCalled = false
        errorCalled = false
        loadingCalled = false
        val loadingResult = AccountResult.loading<String>("Loading...")
        loadingResult
            .onSuccess { successCalled = true }
            .onError { errorCalled = true }
            .onLoading { loadingCalled = true }
        assertFalse(successCalled)
        assertFalse(errorCalled)
        assertTrue(loadingCalled)
    }
    @Test
    fun `RetryManager configurations should have appropriate retry behavior`() = runTest {
        val retryManager = RetryManager()
        val networkConfig = retryManager.createNetworkRetryConfig(3)
        assertTrue(networkConfig.retryableErrors.contains(AccountResult.ErrorType.NETWORK_ERROR))
        assertTrue(networkConfig.retryableErrors.contains(AccountResult.ErrorType.FIREBASE_ERROR))
        assertFalse(networkConfig.retryableErrors.contains(AccountResult.ErrorType.VALIDATION_ERROR))
        assertFalse(networkConfig.retryableErrors.contains(AccountResult.ErrorType.AUTHENTICATION_ERROR))
        val authConfig = retryManager.createAuthRetryConfig(2)
        assertTrue(authConfig.retryableErrors.contains(AccountResult.ErrorType.NETWORK_ERROR))
        assertTrue(authConfig.retryableErrors.contains(AccountResult.ErrorType.FIREBASE_ERROR))
        assertFalse(authConfig.retryableErrors.contains(AccountResult.ErrorType.AUTHENTICATION_ERROR))
        val dataConfig = retryManager.createDataRetryConfig(2)
        assertTrue(dataConfig.retryableErrors.contains(AccountResult.ErrorType.NETWORK_ERROR))
        assertTrue(dataConfig.retryableErrors.contains(AccountResult.ErrorType.FIREBASE_ERROR))
        assertFalse(dataConfig.retryableErrors.contains(AccountResult.ErrorType.VALIDATION_ERROR))
    }
    @Test
    fun `Error messages should be user-friendly in Spanish`() {
        val networkError = AccountResult.error<String>(Exception("network timeout"))
        val authError = AccountResult.error<String>(Exception("auth failed"))
        val firebaseError = AccountResult.error<String>(Exception("firebase error"))
        val validationError = AccountResult.error<String>(IllegalArgumentException("invalid data"))
        val genericError = AccountResult.error<String>(Exception("random error"))
        val networkMessage = networkError.getErrorOrNull()?.message ?: ""
        val authMessage = authError.getErrorOrNull()?.message ?: ""
        val firebaseMessage = firebaseError.getErrorOrNull()?.message ?: ""
        val validationMessage = validationError.getErrorOrNull()?.message ?: ""
        val genericMessage = genericError.getErrorOrNull()?.message ?: ""
        assertTrue(networkMessage.contains("conexión") || networkMessage.contains("internet"))
        assertTrue(authMessage.contains("autenticación") || authMessage.contains("sesión"))
        assertTrue(firebaseMessage.contains("servidor"))
        assertTrue(validationMessage.contains("inválidos") || validationMessage.contains("datos"))
        assertTrue(genericMessage.contains("inesperado") || genericMessage.contains("error"))
        assertFalse(networkMessage.contains("Exception"))
        assertFalse(authMessage.contains("null"))
        assertFalse(firebaseMessage.contains("stack trace"))
    }
}
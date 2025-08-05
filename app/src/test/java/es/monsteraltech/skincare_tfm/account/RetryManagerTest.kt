package es.monsteraltech.skincare_tfm.account
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RetryManagerTest {
    private val retryManager = RetryManager()
    @Test
    fun `executeWithRetry should return success on first attempt`() = runTest {
        val expectedData = "success"
        var attemptCount = 0
        val result = retryManager.executeWithRetry {
            attemptCount++
            AccountResult.success(expectedData)
        }
        assertTrue(result.isSuccess())
        assertEquals(expectedData, result.getDataOrNull())
        assertEquals(1, attemptCount)
    }
    @Test
    fun `executeWithRetry should retry on retryable errors`() = runTest {
        var attemptCount = 0
        val maxRetries = 2
        val config = RetryManager.RetryConfig(
            maxRetries = maxRetries,
            initialDelayMs = 10,
            maxDelayMs = 50
        )
        val result = retryManager.executeWithRetry(config) {
            attemptCount++
            if (attemptCount <= maxRetries) {
                AccountResult.error<String>(
                    Exception("Network error"),
                    "Network error",
                    AccountResult.ErrorType.NETWORK_ERROR
                )
            } else {
                AccountResult.success("success")
            }
        }
        assertTrue(result.isSuccess())
        assertEquals("success", result.getDataOrNull())
        assertEquals(maxRetries + 1, attemptCount)
    }
    @Test
    fun `executeWithRetry should not retry on non-retryable errors`() = runTest {
        var attemptCount = 0
        val result = retryManager.executeWithRetry {
            attemptCount++
            AccountResult.error<String>(
                Exception("Validation error"),
                "Validation error",
                AccountResult.ErrorType.VALIDATION_ERROR
            )
        }
        assertTrue(result.isError())
        assertEquals(1, attemptCount)
        assertEquals(AccountResult.ErrorType.VALIDATION_ERROR, result.getErrorOrNull()?.errorType)
    }
    @Test
    fun `executeWithRetry should stop after max retries`() = runTest {
        var attemptCount = 0
        val maxRetries = 2
        val config = RetryManager.RetryConfig(
            maxRetries = maxRetries,
            initialDelayMs = 10,
            maxDelayMs = 50
        )
        val result = retryManager.executeWithRetry(config) {
            attemptCount++
            AccountResult.error<String>(
                Exception("Network error"),
                "Network error",
                AccountResult.ErrorType.NETWORK_ERROR
            )
        }
        assertTrue(result.isError())
        assertEquals(maxRetries + 1, attemptCount)
        assertEquals(AccountResult.ErrorType.NETWORK_ERROR, result.getErrorOrNull()?.errorType)
    }
    @Test
    fun `executeWithRetry should handle exceptions in operation`() = runTest {
        var attemptCount = 0
        val maxRetries = 1
        val config = RetryManager.RetryConfig(
            maxRetries = maxRetries,
            initialDelayMs = 10,
            maxDelayMs = 50
        )
        val result = retryManager.executeWithRetry(config) {
            attemptCount++
            throw RuntimeException("Unexpected exception")
        }
        assertTrue(result.isError())
        assertEquals(maxRetries + 1, attemptCount)
        assertTrue(result.getErrorOrNull()?.message?.contains("inesperado") == true)
    }
    @Test
    fun `exponential backoff should increase delay`() = runTest {
        var attemptCount = 0
        val config = RetryManager.RetryConfig(
            maxRetries = 3,
            initialDelayMs = 100,
            maxDelayMs = 1000,
            backoffMultiplier = 2.0
        )
        val startTime = System.currentTimeMillis()
        val result = retryManager.executeWithRetry(config) {
            attemptCount++
            if (attemptCount <= 2) {
                AccountResult.error<String>(
                    Exception("Network error"),
                    "Network error",
                    AccountResult.ErrorType.NETWORK_ERROR
                )
            } else {
                AccountResult.success("success")
            }
        }
        val totalTime = System.currentTimeMillis() - startTime
        assertTrue(result.isSuccess())
        assertEquals(3, attemptCount)
        assertTrue("Total time should be at least 300ms, was ${totalTime}ms", totalTime >= 300)
    }
    @Test
    fun `delay should not exceed max delay`() = runTest {
        var attemptCount = 0
        val config = RetryManager.RetryConfig(
            maxRetries = 5,
            initialDelayMs = 100,
            maxDelayMs = 200,
            backoffMultiplier = 10.0
        )
        val startTime = System.currentTimeMillis()
        val result = retryManager.executeWithRetry(config) {
            attemptCount++
            if (attemptCount <= 3) {
                AccountResult.error<String>(
                    Exception("Network error"),
                    "Network error",
                    AccountResult.ErrorType.NETWORK_ERROR
                )
            } else {
                AccountResult.success("success")
            }
        }
        val totalTime = System.currentTimeMillis() - startTime
        assertTrue(result.isSuccess())
        assertEquals(4, attemptCount)
        assertTrue("Total time should be reasonable, was ${totalTime}ms", totalTime < 1000)
    }
    @Test
    fun `network retry config should have appropriate settings`() {
        val config = retryManager.createNetworkRetryConfig(3)
        assertEquals(3, config.maxRetries)
        assertEquals(1000L, config.initialDelayMs)
        assertEquals(8000L, config.maxDelayMs)
        assertEquals(2.0, config.backoffMultiplier, 0.01)
        assertTrue(config.retryableErrors.contains(AccountResult.ErrorType.NETWORK_ERROR))
        assertTrue(config.retryableErrors.contains(AccountResult.ErrorType.FIREBASE_ERROR))
        assertFalse(config.retryableErrors.contains(AccountResult.ErrorType.VALIDATION_ERROR))
    }
    @Test
    fun `auth retry config should have appropriate settings`() {
        val config = retryManager.createAuthRetryConfig(2)
        assertEquals(2, config.maxRetries)
        assertEquals(2000L, config.initialDelayMs)
        assertEquals(5000L, config.maxDelayMs)
        assertEquals(1.5, config.backoffMultiplier, 0.01)
        assertTrue(config.retryableErrors.contains(AccountResult.ErrorType.NETWORK_ERROR))
        assertTrue(config.retryableErrors.contains(AccountResult.ErrorType.FIREBASE_ERROR))
        assertFalse(config.retryableErrors.contains(AccountResult.ErrorType.AUTHENTICATION_ERROR))
    }
    @Test
    fun `data retry config should have appropriate settings`() {
        val config = retryManager.createDataRetryConfig(2)
        assertEquals(2, config.maxRetries)
        assertEquals(1500L, config.initialDelayMs)
        assertEquals(6000L, config.maxDelayMs)
        assertEquals(2.0, config.backoffMultiplier, 0.01)
        assertTrue(config.retryableErrors.contains(AccountResult.ErrorType.NETWORK_ERROR))
        assertTrue(config.retryableErrors.contains(AccountResult.ErrorType.FIREBASE_ERROR))
        assertFalse(config.retryableErrors.contains(AccountResult.ErrorType.VALIDATION_ERROR))
    }
    @Test
    fun `should retry firebase errors`() = runTest {
        var attemptCount = 0
        val config = RetryManager.RetryConfig(
            maxRetries = 2,
            initialDelayMs = 10,
            maxDelayMs = 50
        )
        val result = retryManager.executeWithRetry(config) {
            attemptCount++
            if (attemptCount <= 1) {
                AccountResult.error<String>(
                    Exception("Firebase error"),
                    "Firebase error",
                    AccountResult.ErrorType.FIREBASE_ERROR
                )
            } else {
                AccountResult.success("success")
            }
        }
        assertTrue(result.isSuccess())
        assertEquals(2, attemptCount)
    }
    @Test
    fun `should not retry authentication errors by default`() = runTest {
        var attemptCount = 0
        val result = retryManager.executeWithRetry {
            attemptCount++
            AccountResult.error<String>(
                Exception("Auth error"),
                "Auth error",
                AccountResult.ErrorType.AUTHENTICATION_ERROR
            )
        }
        assertTrue(result.isError())
        assertEquals(1, attemptCount)
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, result.getErrorOrNull()?.errorType)
    }
    @Test
    fun `custom retry config should work`() = runTest {
        var attemptCount = 0
        val customConfig = RetryManager.RetryConfig(
            maxRetries = 1,
            initialDelayMs = 50,
            maxDelayMs = 100,
            backoffMultiplier = 1.5,
            retryableErrors = setOf(AccountResult.ErrorType.VALIDATION_ERROR)
        )
        val result = retryManager.executeWithRetry(customConfig) {
            attemptCount++
            if (attemptCount <= 1) {
                AccountResult.error<String>(
                    Exception("Validation error"),
                    "Validation error",
                    AccountResult.ErrorType.VALIDATION_ERROR
                )
            } else {
                AccountResult.success("success")
            }
        }
        assertTrue(result.isSuccess())
        assertEquals(2, attemptCount)
    }
}
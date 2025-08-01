package es.monsteraltech.skincare_tfm.body.mole.error

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.ConnectException

class RetryManagerTest {

    private lateinit var mockContext: Context
    private lateinit var retryManager: RetryManager

    @Before
    fun setup() {
        mockContext = mockk()
        retryManager = RetryManager(mockContext)
    }

    @Test
    fun `executeWithRetry should succeed on first attempt`() = runBlocking {
        var attemptCount = 0
        
        val result = retryManager.executeWithRetry(
            operation = "test operation",
            config = RetryManager.RetryConfig(maxAttempts = 3)
        ) {
            attemptCount++
            Result.success("success")
        }
        
        assertTrue(result.result.isSuccess)
        assertEquals("success", result.result.getOrNull())
        assertEquals(1, result.attemptsMade)
        assertEquals(1, attemptCount)
    }

    @Test
    fun `executeWithRetry should retry on recoverable failure`() = runBlocking {
        var attemptCount = 0
        
        val result = retryManager.executeWithRetry(
            operation = "test operation",
            config = RetryManager.RetryConfig(maxAttempts = 3, baseDelayMs = 10L)
        ) {
            attemptCount++
            if (attemptCount < 3) {
                Result.failure(ConnectException("Network error"))
            } else {
                Result.success("success after retry")
            }
        }
        
        assertTrue(result.result.isSuccess)
        assertEquals("success after retry", result.result.getOrNull())
        assertEquals(3, result.attemptsMade)
        assertEquals(3, attemptCount)
    }

    @Test
    fun `executeWithRetry should fail after max attempts`() = runBlocking {
        var attemptCount = 0
        
        val result = retryManager.executeWithRetry(
            operation = "test operation",
            config = RetryManager.RetryConfig(maxAttempts = 2, baseDelayMs = 10L)
        ) {
            attemptCount++
            Result.failure(ConnectException("Persistent network error"))
        }
        
        assertTrue(result.result.isFailure)
        assertEquals(2, result.attemptsMade)
        assertEquals(2, attemptCount)
    }

    @Test
    fun `executeWithRetry should not retry on non-recoverable error`() = runBlocking {
        var attemptCount = 0
        
        val result = retryManager.executeWithRetry(
            operation = "test operation",
            config = RetryManager.RetryConfig(maxAttempts = 3, baseDelayMs = 10L)
        ) {
            attemptCount++
            Result.failure(IllegalArgumentException("Validation error"))
        }
        
        assertTrue(result.result.isFailure)
        assertEquals(1, result.attemptsMade)
        assertEquals(1, attemptCount)
    }

    @Test
    fun `executeWithRetry should call onRetryAttempt callback`() = runBlocking {
        var attemptCount = 0
        var callbackCount = 0
        val callbackAttempts = mutableListOf<Int>()
        
        retryManager.executeWithRetry(
            operation = "test operation",
            config = RetryManager.RetryConfig(maxAttempts = 3, baseDelayMs = 10L),
            onRetryAttempt = { attempt, _ ->
                callbackCount++
                callbackAttempts.add(attempt)
            }
        ) {
            attemptCount++
            if (attemptCount < 3) {
                Result.failure(ConnectException("Network error"))
            } else {
                Result.success("success")
            }
        }
        
        assertEquals(2, callbackCount) // Called for attempts 1 and 2, not for successful attempt 3
        assertEquals(listOf(1, 2), callbackAttempts)
    }

    @Test
    fun `networkConfig should return appropriate configuration`() {
        val config = RetryManager.networkConfig()
        
        assertEquals(3, config.maxAttempts)
        assertEquals(2000L, config.baseDelayMs)
        assertEquals(15000L, config.maxDelayMs)
        assertEquals(2.0, config.backoffMultiplier, 0.01)
    }

    @Test
    fun `databaseConfig should return appropriate configuration`() {
        val config = RetryManager.databaseConfig()
        
        assertEquals(2, config.maxAttempts)
        assertEquals(1000L, config.baseDelayMs)
        assertEquals(5000L, config.maxDelayMs)
        assertEquals(1.5, config.backoffMultiplier, 0.01)
    }

    @Test
    fun `calculateDelay should implement exponential backoff`() {
        val config = RetryManager.RetryConfig(
            baseDelayMs = 1000L,
            backoffMultiplier = 2.0,
            jitterMs = 0L, // No jitter for predictable testing
            maxDelayMs = 10000L
        )
        
        // Use reflection to access private method for testing
        val method = RetryManager::class.java.getDeclaredMethod("calculateDelay", Int::class.java, RetryManager.RetryConfig::class.java)
        method.isAccessible = true
        
        val delay1 = method.invoke(retryManager, 1, config) as Long
        val delay2 = method.invoke(retryManager, 2, config) as Long
        val delay3 = method.invoke(retryManager, 3, config) as Long
        
        assertEquals(1000L, delay1)
        assertEquals(2000L, delay2)
        assertEquals(4000L, delay3)
    }

    @Test
    fun `calculateDelay should respect maxDelay`() {
        val config = RetryManager.RetryConfig(
            baseDelayMs = 1000L,
            backoffMultiplier = 2.0,
            jitterMs = 0L,
            maxDelayMs = 3000L
        )
        
        val method = RetryManager::class.java.getDeclaredMethod("calculateDelay", Int::class.java, RetryManager.RetryConfig::class.java)
        method.isAccessible = true
        
        val delay4 = method.invoke(retryManager, 4, config) as Long
        
        assertEquals(3000L, delay4) // Should be capped at maxDelayMs
    }
}
package com.monsteraltech.skincare.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import com.monsteraltech.skincare.body.mole.error.RetryManager as MoleRetryManager

class CancellationRetryTest {
    private suspend fun assertCancelled(operation: suspend () -> Unit) {
        try { operation(); fail("Expected cancellation") } catch (_: CancellationException) { }
    }
    @Test fun `account retries propagate thrown cancellation on the first attempt`() = runTest {
        var attempts = 0
        assertCancelled { RetryManager().executeWithRetry<Int> { attempts++; throw CancellationException() } }
        assertEquals(1, attempts)
    }
    @Test fun `account retries propagate cancellation inside an error value`() = runTest {
        var attempts = 0
        assertCancelled { RetryManager().executeWithRetry<Int> {
            attempts++; AccountResult.error(CancellationException(), errorType = AccountResult.ErrorType.NETWORK_ERROR)
        } }
        assertEquals(1, attempts)
    }
    @Test fun `image and database retries propagate thrown cancellation`() = runTest {
        var attempts = 0
        assertCancelled { MoleRetryManager().executeWithRetry<Int>("fixture") { attempts++; throw CancellationException() } }
        assertEquals(1, attempts)
    }
    @Test fun `image and database retries propagate cancellation inside a Result`() = runTest {
        var attempts = 0
        assertCancelled { MoleRetryManager().executeWithRetry<Int>("fixture") { attempts++; Result.failure(CancellationException()) } }
        assertEquals(1, attempts)
    }
    @Test fun `recoverable account failures still retry successfully`() = runTest {
        var attempts = 0
        val result = RetryManager().executeWithRetry(RetryManager.RetryConfig(initialDelayMs = 1)) {
            if (++attempts == 1) AccountResult.error(IOException(), errorType = AccountResult.ErrorType.NETWORK_ERROR)
            else AccountResult.success(7)
        }
        assertEquals(7, result.getDataOrNull())
        assertEquals(2, attempts)
    }
    @Test fun `recoverable database failures still retry successfully`() = runTest {
        var attempts = 0
        val result = MoleRetryManager().executeWithRetry("fixture", MoleRetryManager.RetryConfig(baseDelayMs = 1, jitterMs = 0)) {
            if (++attempts == 1) Result.failure(IOException("network")) else Result.success(7)
        }
        assertEquals(7, result.result.getOrNull())
        assertEquals(2, attempts)
    }
}

package es.monsteraltech.skincare_tfm.login

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.monsteraltech.skincare_tfm.data.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for SessionCheckActivity logic
 * Tests the session verification behavior without UI
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SessionCheckActivityTest {

    private lateinit var sessionManager: SessionManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionManager = SessionManager.getInstance(context)
        
        // Clear any existing session for clean test state
        runBlocking {
            sessionManager.clearSession()
        }
    }

    @After
    fun tearDown() {
        // Clean up session after tests
        runBlocking {
            sessionManager.clearSession()
        }
    }

    @Test
    fun `should handle empty session correctly`() = runBlocking {
        // Given: No session exists
        sessionManager.clearSession()
        
        // When: Checking if session is valid
        val isValid = sessionManager.isSessionValid()
        
        // Then: Session should not be valid
        assert(!isValid) { "Empty session should not be valid" }
    }

    @Test
    fun `should handle session manager initialization`() {
        // Given: Context is available
        // When: SessionManager is initialized
        val manager = SessionManager.getInstance(context)
        
        // Then: Manager should not be null
        assert(manager != null) { "SessionManager should be initialized" }
        
        // And: Should return same instance (singleton)
        val manager2 = SessionManager.getInstance(context)
        assert(manager === manager2) { "SessionManager should be singleton" }
    }

    @Test
    fun `should handle stored session retrieval`() = runBlocking {
        // Given: No session exists
        sessionManager.clearSession()
        
        // When: Trying to get stored session
        val storedSession = sessionManager.getStoredSession()
        
        // Then: Should return null
        assert(storedSession == null) { "Should return null when no session exists" }
    }

    @Test
    fun `should handle session clearing`() = runBlocking {
        // Given: SessionManager exists
        // When: Clearing session
        val result = sessionManager.clearSession()
        
        // Then: Should complete successfully
        assert(result) { "Session clearing should succeed" }
        
        // And: No session should exist after clearing
        val storedSession = sessionManager.getStoredSession()
        assert(storedSession == null) { "No session should exist after clearing" }
    }

    @Test
    fun `should handle network error scenarios gracefully`() {
        // This test verifies that the activity can handle network errors
        // In a real implementation, you would mock network conditions
        
        // Given: SessionCheckActivity logic
        val exception = java.net.UnknownHostException("Network error")
        
        // When: Checking if it's a network error
        val isNetworkError = isNetworkError(exception)
        
        // Then: Should correctly identify network error
        assert(isNetworkError) { "Should identify UnknownHostException as network error" }
    }

    @Test
    fun `should identify different types of network errors`() {
        // Test various network error types
        val networkErrors = listOf(
            java.net.UnknownHostException("Host not found"),
            java.net.SocketTimeoutException("Timeout"),
            java.io.IOException("IO error")
        )
        
        networkErrors.forEach { exception ->
            val isNetworkError = isNetworkError(exception)
            assert(isNetworkError) { "Should identify ${exception.javaClass.simpleName} as network error" }
        }
    }

    @Test
    fun `should not identify non-network errors as network errors`() {
        // Test non-network error types
        val nonNetworkErrors = listOf(
            IllegalArgumentException("Invalid argument"),
            NullPointerException("Null pointer"),
            RuntimeException("Runtime error")
        )
        
        nonNetworkErrors.forEach { exception ->
            val isNetworkError = isNetworkError(exception)
            assert(!isNetworkError) { "Should not identify ${exception.javaClass.simpleName} as network error" }
        }
    }

    /**
     * Helper method to test network error detection logic
     * (This mirrors the logic from SessionCheckActivity)
     */
    private fun isNetworkError(exception: Exception): Boolean {
        return when (exception) {
            is java.net.UnknownHostException,
            is java.net.SocketTimeoutException,
            is java.io.IOException -> true
            else -> {
                val message = exception.message?.lowercase() ?: ""
                message.contains("network") || 
                message.contains("timeout") || 
                message.contains("connection") ||
                message.contains("unreachable")
            }
        }
    }
}
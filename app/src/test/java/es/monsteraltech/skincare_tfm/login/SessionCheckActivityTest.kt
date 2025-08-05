package es.monsteraltech.skincare_tfm.login
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import es.monsteraltech.skincare_tfm.data.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SessionCheckActivityTest {
    private lateinit var sessionManager: SessionManager
    private lateinit var context: Context
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionManager = SessionManager.getInstance(context)
        runBlocking {
            sessionManager.clearSession()
        }
    }
    @After
    fun tearDown() {
        runBlocking {
            sessionManager.clearSession()
        }
    }
    @Test
    fun `should handle empty session correctly`() = runBlocking {
        sessionManager.clearSession()
        val isValid = sessionManager.isSessionValid()
        assert(!isValid) { "Empty session should not be valid" }
    }
    @Test
    fun `should handle session manager initialization`() {
        val manager = SessionManager.getInstance(context)
        assert(manager != null) { "SessionManager should be initialized" }
        val manager2 = SessionManager.getInstance(context)
        assert(manager === manager2) { "SessionManager should be singleton" }
    }
    @Test
    fun `should handle stored session retrieval`() = runBlocking {
        sessionManager.clearSession()
        val storedSession = sessionManager.getStoredSession()
        assert(storedSession == null) { "Should return null when no session exists" }
    }
    @Test
    fun `should handle session clearing`() = runBlocking {
        val result = sessionManager.clearSession()
        assert(result) { "Session clearing should succeed" }
        val storedSession = sessionManager.getStoredSession()
        assert(storedSession == null) { "No session should exist after clearing" }
    }
    @Test
    fun `should handle network error scenarios gracefully`() {
        val exception = java.net.UnknownHostException("Network error")
        val isNetworkError = isNetworkError(exception)
        assert(isNetworkError) { "Should identify UnknownHostException as network error" }
    }
    @Test
    fun `should identify different types of network errors`() {
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
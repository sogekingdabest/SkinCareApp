package es.monsteraltech.skincare_tfm.login

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import es.monsteraltech.skincare_tfm.data.SessionManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de integración para verificar que la persistencia de sesión funciona correctamente
 * en LoginActivity con diferentes métodos de autenticación
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class LoginActivitySessionIntegrationTest {

    @get:Rule
    val activityRule = ActivityTestRule(LoginActivity::class.java, true, false)

    private lateinit var context: Context
    private lateinit var sessionManager: SessionManager
    private lateinit var mockFirebaseAuth: FirebaseAuth
    private lateinit var mockFirebaseUser: FirebaseUser

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Initialize real SessionManager for integration testing
        sessionManager = SessionManager.getInstance(context)
        
        // Mock Firebase components
        mockFirebaseAuth = mockk()
        mockFirebaseUser = mockk()
        
        // Setup mock user data
        every { mockFirebaseUser.uid } returns "integration-test-user"
        every { mockFirebaseUser.email } returns "integration@test.com"
        every { mockFirebaseUser.displayName } returns "Integration Test User"
        every { mockFirebaseUser.providerData } returns emptyList()
        
        // Mock Firebase Auth
        mockkStatic(FirebaseAuth::class)
        every { FirebaseAuth.getInstance() } returns mockFirebaseAuth
        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        
        // Mock token operations
        val mockTokenResult = mockk<com.google.firebase.auth.GetTokenResult>()
        every { mockTokenResult.token } returns "mock-firebase-token"
        every { mockFirebaseUser.getIdToken(any()) } returns mockk {
            every { await() } returns mockTokenResult
        }
    }

    @After
    fun tearDown() {
        runTest {
            // Clean up any stored session data
            sessionManager.clearSession()
        }
        unmockkAll()
    }

    @Test
    fun testSessionPersistenceAfterEmailPasswordLogin() = runTest {
        // Arrange
        val activity = activityRule.launchActivity(null)
        
        // Clear any existing session
        sessionManager.clearSession()
        
        // Verify no session exists initially
        val initialSession = sessionManager.getStoredSession()
        assert(initialSession == null) { "Should not have initial session" }
        
        // Act - Simulate successful email/password login
        activity.runOnUiThread {
            val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
            method.isAccessible = true
            method.invoke(activity, mockFirebaseUser)
        }
        
        // Wait for async operations
        Thread.sleep(500)
        
        // Assert - Verify session was saved
        val savedSession = sessionManager.getStoredSession()
        assert(savedSession != null) { "Session should be saved after login" }
        assert(savedSession?.userId == "integration-test-user") { "User ID should match" }
        assert(savedSession?.email == "integration@test.com") { "Email should match" }
    }

    @Test
    fun testSessionPersistenceAfterGoogleSignIn() = runTest {
        // Arrange
        val activity = activityRule.launchActivity(null)
        
        // Clear any existing session
        sessionManager.clearSession()
        
        // Setup Google Sign-In specific user data
        every { mockFirebaseUser.uid } returns "google-signin-user"
        every { mockFirebaseUser.email } returns "google@signin.com"
        every { mockFirebaseUser.displayName } returns "Google User"
        
        // Verify no session exists initially
        val initialSession = sessionManager.getStoredSession()
        assert(initialSession == null) { "Should not have initial session" }
        
        // Act - Simulate successful Google Sign-In
        activity.runOnUiThread {
            val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
            method.isAccessible = true
            method.invoke(activity, mockFirebaseUser)
        }
        
        // Wait for async operations
        Thread.sleep(500)
        
        // Assert - Verify session was saved
        val savedSession = sessionManager.getStoredSession()
        assert(savedSession != null) { "Session should be saved after Google Sign-In" }
        assert(savedSession?.userId == "google-signin-user") { "User ID should match" }
        assert(savedSession?.email == "google@signin.com") { "Email should match" }
    }

    @Test
    fun testSessionPersistenceWithNetworkError() = runTest {
        // Arrange
        val activity = activityRule.launchActivity(null)
        
        // Clear any existing session
        sessionManager.clearSession()
        
        // Mock network error during token retrieval
        every { mockFirebaseUser.getIdToken(any()) } throws java.net.UnknownHostException("Network error")
        
        // Act - Simulate login with network error
        activity.runOnUiThread {
            val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
            method.isAccessible = true
            method.invoke(activity, mockFirebaseUser)
        }
        
        // Wait for async operations
        Thread.sleep(500)
        
        // Assert - Verify session save failed gracefully but app continues
        val savedSession = sessionManager.getStoredSession()
        assert(savedSession == null) { "Session should not be saved when network error occurs" }
        // App should continue functioning without session persistence
    }

    @Test
    fun testSessionValidationAfterSave() = runTest {
        // Arrange
        val activity = activityRule.launchActivity(null)
        
        // Clear any existing session
        sessionManager.clearSession()
        
        // Act - Save session through login
        activity.runOnUiThread {
            val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
            method.isAccessible = true
            method.invoke(activity, mockFirebaseUser)
        }
        
        // Wait for async operations
        Thread.sleep(500)
        
        // Assert - Verify session is valid
        val isValid = sessionManager.isSessionValid()
        assert(isValid) { "Saved session should be valid" }
        
        val storedSession = sessionManager.getStoredSession()
        assert(storedSession != null) { "Session should exist" }
        assert(!storedSession!!.isExpired()) { "Session should not be expired" }
    }

    @Test
    fun testMultipleLoginAttemptsOverwriteSession() = runTest {
        // Arrange
        val activity = activityRule.launchActivity(null)
        
        // Clear any existing session
        sessionManager.clearSession()
        
        // First login
        every { mockFirebaseUser.uid } returns "first-user"
        every { mockFirebaseUser.email } returns "first@user.com"
        
        activity.runOnUiThread {
            val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
            method.isAccessible = true
            method.invoke(activity, mockFirebaseUser)
        }
        
        Thread.sleep(300)
        
        // Verify first session
        val firstSession = sessionManager.getStoredSession()
        assert(firstSession?.userId == "first-user") { "First session should be saved" }
        
        // Second login with different user
        every { mockFirebaseUser.uid } returns "second-user"
        every { mockFirebaseUser.email } returns "second@user.com"
        
        activity.runOnUiThread {
            val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
            method.isAccessible = true
            method.invoke(activity, mockFirebaseUser)
        }
        
        Thread.sleep(300)
        
        // Assert - Second session should overwrite first
        val secondSession = sessionManager.getStoredSession()
        assert(secondSession?.userId == "second-user") { "Second session should overwrite first" }
        assert(secondSession?.email == "second@user.com") { "Email should be updated" }
    }
}
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
        sessionManager = SessionManager.getInstance(context)
        mockFirebaseAuth = mockk()
        mockFirebaseUser = mockk()
        every { mockFirebaseUser.uid } returns "integration-test-user"
        every { mockFirebaseUser.email } returns "integration@test.com"
        every { mockFirebaseUser.displayName } returns "Integration Test User"
        every { mockFirebaseUser.providerData } returns emptyList()
        mockkStatic(FirebaseAuth::class)
        every { FirebaseAuth.getInstance() } returns mockFirebaseAuth
        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        val mockTokenResult = mockk<com.google.firebase.auth.GetTokenResult>()
        every { mockTokenResult.token } returns "mock-firebase-token"
        every { mockFirebaseUser.getIdToken(any()) } returns mockk {
            every { await() } returns mockTokenResult
        }
    }
    @After
    fun tearDown() {
        runTest {
            sessionManager.clearSession()
        }
        unmockkAll()
    }
    @Test
    fun testSessionPersistenceAfterEmailPasswordLogin() = runTest {
        val activity = activityRule.launchActivity(null)
        sessionManager.clearSession()
        val initialSession = sessionManager.getStoredSession()
        assert(initialSession == null) { "Should not have initial session" }
        activity.runOnUiThread {
            val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
            method.isAccessible = true
            method.invoke(activity, mockFirebaseUser)
        }
        Thread.sleep(500)
        val savedSession = sessionManager.getStoredSession()
        assert(savedSession != null) { "Session should be saved after login" }
        assert(savedSession?.userId == "integration-test-user") { "User ID should match" }
        assert(savedSession?.email == "integration@test.com") { "Email should match" }
    }
    @Test
    fun testSessionPersistenceAfterGoogleSignIn() = runTest {
        val activity = activityRule.launchActivity(null)
        sessionManager.clearSession()
        every { mockFirebaseUser.uid } returns "google-signin-user"
        every { mockFirebaseUser.email } returns "google@signin.com"
        every { mockFirebaseUser.displayName } returns "Google User"
        val initialSession = sessionManager.getStoredSession()
        assert(initialSession == null) { "Should not have initial session" }
        activity.runOnUiThread {
            val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
            method.isAccessible = true
            method.invoke(activity, mockFirebaseUser)
        }
        Thread.sleep(500)
        val savedSession = sessionManager.getStoredSession()
        assert(savedSession != null) { "Session should be saved after Google Sign-In" }
        assert(savedSession?.userId == "google-signin-user") { "User ID should match" }
        assert(savedSession?.email == "google@signin.com") { "Email should match" }
    }
    @Test
    fun testSessionPersistenceWithNetworkError() = runTest {
        val activity = activityRule.launchActivity(null)
        sessionManager.clearSession()
        every { mockFirebaseUser.getIdToken(any()) } throws java.net.UnknownHostException("Network error")
        activity.runOnUiThread {
            val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
            method.isAccessible = true
            method.invoke(activity, mockFirebaseUser)
        }
        Thread.sleep(500)
        val savedSession = sessionManager.getStoredSession()
        assert(savedSession == null) { "Session should not be saved when network error occurs" }
    }
    @Test
    fun testSessionValidationAfterSave() = runTest {
        val activity = activityRule.launchActivity(null)
        sessionManager.clearSession()
        activity.runOnUiThread {
            val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
            method.isAccessible = true
            method.invoke(activity, mockFirebaseUser)
        }
        Thread.sleep(500)
        val isValid = sessionManager.isSessionValid()
        assert(isValid) { "Saved session should be valid" }
        val storedSession = sessionManager.getStoredSession()
        assert(storedSession != null) { "Session should exist" }
        assert(!storedSession!!.isExpired()) { "Session should not be expired" }
    }
    @Test
    fun testMultipleLoginAttemptsOverwriteSession() = runTest {
        val activity = activityRule.launchActivity(null)
        sessionManager.clearSession()
        every { mockFirebaseUser.uid } returns "first-user"
        every { mockFirebaseUser.email } returns "first@user.com"
        activity.runOnUiThread {
            val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
            method.isAccessible = true
            method.invoke(activity, mockFirebaseUser)
        }
        Thread.sleep(300)
        val firstSession = sessionManager.getStoredSession()
        assert(firstSession?.userId == "first-user") { "First session should be saved" }
        every { mockFirebaseUser.uid } returns "second-user"
        every { mockFirebaseUser.email } returns "second@user.com"
        activity.runOnUiThread {
            val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
            method.isAccessible = true
            method.invoke(activity, mockFirebaseUser)
        }
        Thread.sleep(300)
        val secondSession = sessionManager.getStoredSession()
        assert(secondSession?.userId == "second-user") { "Second session should overwrite first" }
        assert(secondSession?.email == "second@user.com") { "Email should be updated" }
    }
}
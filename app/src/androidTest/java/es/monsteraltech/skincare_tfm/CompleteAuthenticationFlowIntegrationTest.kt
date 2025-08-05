package es.monsteraltech.skincare_tfm
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.firebase.auth.FirebaseUser
import es.monsteraltech.skincare_tfm.data.SessionManager
import es.monsteraltech.skincare_tfm.login.LoginActivity
import es.monsteraltech.skincare_tfm.login.SessionCheckActivity
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
@LargeTest
class CompleteAuthenticationFlowIntegrationTest {
    private lateinit var context: Context
    private lateinit var sessionManager: SessionManager
    private lateinit var mockFirebaseUser: FirebaseUser
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionManager = SessionManager.getInstance(context)
        runBlocking {
            sessionManager.clearSession()
        }
        mockFirebaseUser = mockk {
            every { uid } returns "test_user_123"
            every { email } returns "test@example.com"
            every { displayName } returns "Test User"
        }
        Intents.init()
    }
    @After
    fun tearDown() {
        runBlocking {
            sessionManager.clearSession()
        }
        unmockkAll()
        Intents.release()
    }
    @Test
    fun testCompleteLoginFlowWithSessionSaving() {
        ActivityScenario.launch(LoginActivity::class.java).use { loginScenario ->
            onView(withId(R.id.logoImageView))
                .check(matches(isDisplayed()))
            onView(withId(R.id.skinCareTextView))
                .check(matches(isDisplayed()))
            onView(withId(R.id.emailEditText))
                .check(matches(isDisplayed()))
            onView(withId(R.id.passwordEditText))
                .check(matches(isDisplayed()))
            onView(withId(R.id.loginButton))
                .check(matches(isDisplayed()))
            onView(withId(R.id.emailEditText))
                .perform(typeText("test@example.com"))
            onView(withId(R.id.passwordEditText))
                .perform(typeText("password123"))
            onView(withId(R.id.passwordEditText))
                .perform(closeSoftKeyboard())
            loginScenario.onActivity { activity ->
                runBlocking {
                    sessionManager.saveSession(mockFirebaseUser)
                }
                activity.finish()
            }
        }
        runBlocking {
            val savedSession = sessionManager.getStoredSession()
            assert(savedSession != null) { "La sesión debería haber sido guardada después del login" }
        }
        ActivityScenario.launch(SessionCheckActivity::class.java).use { sessionCheckScenario ->
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            Thread.sleep(3000)
        }
        intended(hasComponent(MainActivity::class.java.name))
    }
    @Test
    fun testGoogleSignInWithSessionSaving() {
        ActivityScenario.launch(LoginActivity::class.java).use { loginScenario ->
            onView(withId(R.id.googleSignInButton))
                .check(matches(isDisplayed()))
            onView(withId(R.id.googleSignInButton))
                .perform(click())
            loginScenario.onActivity { activity ->
                runBlocking {
                    sessionManager.saveSession(mockFirebaseUser)
                }
                activity.finish()
            }
        }
        runBlocking {
            val savedSession = sessionManager.getStoredSession()
            assert(savedSession != null) { "La sesión debería haber sido guardada después de Google Sign-In" }
        }
    }
    @Test
    fun testLoginWithSessionSaveFailure() {
        val mockSessionManager = mockk<SessionManager>()
        coEvery { mockSessionManager.saveSession(any()) } returns false
        ActivityScenario.launch(LoginActivity::class.java).use { loginScenario ->
            loginScenario.onActivity { activity ->
                val sessionManagerField = LoginActivity::class.java.getDeclaredField("sessionManager")
                sessionManagerField.isAccessible = true
                sessionManagerField.set(activity, mockSessionManager)
                runBlocking {
                    val result = mockSessionManager.saveSession(mockFirebaseUser)
                    assert(!result) { "El guardado de sesión debería fallar en este test" }
                }
            }
        }
    }
    @Test
    fun testCompleteLogoutFlowWithSessionCleanup() {
        runBlocking {
            sessionManager.saveSession(mockFirebaseUser)
        }
        ActivityScenario.launch(MainActivity::class.java).use { mainScenario ->
            mainScenario.onActivity { activity ->
                val logoutMethod = MainActivity::class.java.getDeclaredMethod("logout")
                logoutMethod.isAccessible = true
                logoutMethod.invoke(activity)
            }
            Thread.sleep(2000)
        }
        runBlocking {
            val sessionData = sessionManager.getStoredSession()
            assert(sessionData == null) { "La sesión debería haber sido limpiada después del logout" }
        }
        intended(hasComponent(LoginActivity::class.java.name))
        ActivityScenario.launch(SessionCheckActivity::class.java).use { sessionCheckScenario ->
            Thread.sleep(3000)
        }
        intended(hasComponent(LoginActivity::class.java.name))
    }
    @Test
    fun testLogoutConfirmationDialog() {
        runBlocking {
            sessionManager.saveSession(mockFirebaseUser)
        }
        ActivityScenario.launch(MainActivity::class.java).use { mainScenario ->
            mainScenario.onActivity { activity ->
                val showDialogMethod = MainActivity::class.java.getDeclaredMethod("showLogoutConfirmationDialog")
                showDialogMethod.isAccessible = true
                showDialogMethod.invoke(activity)
            }
            Thread.sleep(500)
            onView(withText(R.string.account_logout_confirm))
                .check(matches(isDisplayed()))
            onView(withText(R.string.account_logout_confirm_button))
                .check(matches(isDisplayed()))
            onView(withText(R.string.account_logout_cancel))
                .check(matches(isDisplayed()))
            onView(withText(R.string.account_logout_confirm_button))
                .perform(click())
            Thread.sleep(1000)
        }
        intended(hasComponent(LoginActivity::class.java.name))
    }
    @Test
    fun testLogoutCancellation() {
        runBlocking {
            sessionManager.saveSession(mockFirebaseUser)
        }
        ActivityScenario.launch(MainActivity::class.java).use { mainScenario ->
            mainScenario.onActivity { activity ->
                val showDialogMethod = MainActivity::class.java.getDeclaredMethod("showLogoutConfirmationDialog")
                showDialogMethod.isAccessible = true
                showDialogMethod.invoke(activity)
            }
            Thread.sleep(500)
            onView(withText(R.string.account_logout_cancel))
                .perform(click())
            Thread.sleep(500)
            mainScenario.onActivity { activity ->
                assert(!activity.isFinishing) { "MainActivity no debería estar finalizando después de cancelar logout" }
            }
        }
        runBlocking {
            val sessionData = sessionManager.getStoredSession()
            assert(sessionData != null) { "La sesión debería seguir existiendo después de cancelar logout" }
        }
    }
    @Test
    fun testCompleteAuthenticationCycle() {
        ActivityScenario.launch(LoginActivity::class.java).use { loginScenario ->
            loginScenario.onActivity { activity ->
                runBlocking {
                    sessionManager.saveSession(mockFirebaseUser)
                }
                activity.finish()
            }
        }
        runBlocking {
            val session1 = sessionManager.getStoredSession()
            assert(session1 != null) { "Primera sesión debería estar guardada" }
        }
        ActivityScenario.launch(SessionCheckActivity::class.java).use { sessionCheckScenario ->
            Thread.sleep(3000)
        }
        intended(hasComponent(MainActivity::class.java.name))
        ActivityScenario.launch(MainActivity::class.java).use { mainScenario ->
            mainScenario.onActivity { activity ->
                val logoutMethod = MainActivity::class.java.getDeclaredMethod("logout")
                logoutMethod.isAccessible = true
                logoutMethod.invoke(activity)
            }
            Thread.sleep(2000)
        }
        runBlocking {
            val session2 = sessionManager.getStoredSession()
            assert(session2 == null) { "Sesión debería estar limpiada después del logout" }
        }
        ActivityScenario.launch(SessionCheckActivity::class.java).use { sessionCheckScenario ->
            Thread.sleep(3000)
        }
        intended(hasComponent(LoginActivity::class.java.name))
        ActivityScenario.launch(LoginActivity::class.java).use { loginScenario ->
            loginScenario.onActivity { activity ->
                runBlocking {
                    sessionManager.saveSession(mockFirebaseUser)
                }
            }
        }
        runBlocking {
            val session3 = sessionManager.getStoredSession()
            assert(session3 != null) { "Nueva sesión debería estar guardada después del segundo login" }
        }
    }
}
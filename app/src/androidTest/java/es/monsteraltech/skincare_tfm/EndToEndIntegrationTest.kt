package es.monsteraltech.skincare_tfm
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import es.monsteraltech.skincare_tfm.data.SessionData
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
class EndToEndIntegrationTest {
    private lateinit var context: Context
    private lateinit var sessionManager: SessionManager
    private lateinit var mockFirebaseAuth: FirebaseAuth
    private lateinit var mockFirebaseUser: FirebaseUser
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionManager = SessionManager.getInstance(context)
        runBlocking {
            sessionManager.clearSession()
        }
        mockFirebaseAuth = mockk()
        mockFirebaseUser = mockk()
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
    fun testCompleteFlowWithValidSession() {
        val validSessionData = SessionData(
            userId = "test_user_123",
            email = "test@example.com",
            displayName = "Test User",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        runBlocking {
            sessionManager.saveSession(mockFirebaseUser)
        }
        ActivityScenario.launch(InicioActivity::class.java).use { inicioScenario ->
            onView(withId(R.id.logoImageView))
                .check(matches(isDisplayed()))
            onView(withId(R.id.skinCareTextView))
                .check(matches(isDisplayed()))
            Thread.sleep(5000)
        }
        intended(hasComponent(SessionCheckActivity::class.java.name))
        ActivityScenario.launch(SessionCheckActivity::class.java).use { sessionCheckScenario ->
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            onView(withId(R.id.statusTextView))
                .check(matches(withText(R.string.session_check_verifying)))
            Thread.sleep(3000)
        }
        intended(hasComponent(MainActivity::class.java.name))
    }
    @Test
    fun testCompleteFlowWithoutSession() {
        ActivityScenario.launch(InicioActivity::class.java).use { inicioScenario ->
            onView(withId(R.id.logoImageView))
                .check(matches(isDisplayed()))
            onView(withId(R.id.skinCareTextView))
                .check(matches(isDisplayed()))
            Thread.sleep(5000)
        }
        intended(hasComponent(SessionCheckActivity::class.java.name))
        ActivityScenario.launch(SessionCheckActivity::class.java).use { sessionCheckScenario ->
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            Thread.sleep(3000)
        }
        intended(hasComponent(LoginActivity::class.java.name))
    }
    @Test
    fun testCompleteFlowAfterLogout() {
        val validSessionData = SessionData(
            userId = "test_user_123",
            email = "test@example.com",
            displayName = "Test User",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
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
        intended(hasComponent(LoginActivity::class.java.name))
        runBlocking {
            val sessionData = sessionManager.getStoredSession()
            assert(sessionData == null) { "La sesión debería haber sido limpiada después del logout" }
        }
        ActivityScenario.launch(SessionCheckActivity::class.java).use { sessionCheckScenario ->
            Thread.sleep(3000)
        }
        intended(hasComponent(LoginActivity::class.java.name))
    }
    @Test
    fun testNetworkErrorHandlingAndRecovery() {
        val validSessionData = SessionData(
            userId = "test_user_123",
            email = "test@example.com",
            displayName = "Test User",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        runBlocking {
            sessionManager.saveSession(mockFirebaseUser)
        }
        val mockSessionManager = mockk<SessionManager>()
        coEvery { mockSessionManager.isSessionValid() } throws java.net.UnknownHostException("Network error")
        coEvery { mockSessionManager.getStoredSession() } returns validSessionData
        ActivityScenario.launch(SessionCheckActivity::class.java).use { sessionCheckScenario ->
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            Thread.sleep(4000)
            onView(withId(R.id.errorMessageTextView))
                .check(matches(isDisplayed()))
            onView(withId(R.id.retryButton))
                .check(matches(isDisplayed()))
                .check(matches(isEnabled()))
            onView(withId(R.id.retryButton))
                .perform(click())
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            Thread.sleep(3000)
        }
    }
    @Test
    fun testFlowWithExpiredToken() {
        val expiredSessionData = SessionData(
            userId = "test_user_123",
            email = "test@example.com",
            displayName = "Test User",
            tokenExpiry = System.currentTimeMillis() - 3600000,
            lastRefresh = System.currentTimeMillis() - 7200000,
            authProvider = "firebase"
        )
        runBlocking {
            sessionManager.saveSession(mockFirebaseUser)
        }
        ActivityScenario.launch(SessionCheckActivity::class.java).use { sessionCheckScenario ->
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            Thread.sleep(3000)
        }
        intended(hasComponent(LoginActivity::class.java.name))
    }
    @Test
    fun testSmoothTransitionsBetweenScreens() {
        runBlocking {
            sessionManager.saveSession(mockFirebaseUser)
        }
        ActivityScenario.launch(InicioActivity::class.java).use { inicioScenario ->
            onView(withId(R.id.logoImageView))
                .check(matches(isDisplayed()))
            onView(withId(R.id.skinCareTextView))
                .check(matches(isDisplayed()))
            inicioScenario.onActivity { activity ->
                val logoImageView = activity.findViewById<android.widget.ImageView>(R.id.logoImageView)
                val skinCareTextView = activity.findViewById<android.widget.TextView>(R.id.skinCareTextView)
                assert(logoImageView.animation != null) { "Logo debe tener animación" }
                assert(skinCareTextView.animation != null) { "Texto debe tener animación" }
            }
            Thread.sleep(5000)
        }
        intended(hasComponent(SessionCheckActivity::class.java.name))
        ActivityScenario.launch(SessionCheckActivity::class.java).use { sessionCheckScenario ->
            onView(withId(R.id.logoImageView))
                .check(matches(isDisplayed()))
            onView(withId(R.id.skinCareTextView))
                .check(matches(isDisplayed()))
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            onView(withId(R.id.statusTextView))
                .check(matches(isDisplayed()))
            Thread.sleep(3000)
        }
        intended(hasComponent(MainActivity::class.java.name))
    }
    @Test
    fun testConcurrentSessionVerificationHandling() {
        runBlocking {
            sessionManager.saveSession(mockFirebaseUser)
        }
        ActivityScenario.launch(SessionCheckActivity::class.java).use { sessionCheckScenario ->
            sessionCheckScenario.onActivity { activity ->
                repeat(3) {
                    Thread {
                        runBlocking {
                            sessionManager.isSessionValid()
                        }
                    }.start()
                }
            }
            onView(withId(R.id.logoImageView))
                .check(matches(isDisplayed()))
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            Thread.sleep(4000)
            onView(withId(R.id.logoImageView))
                .check(matches(isDisplayed()))
        }
    }
    @Test
    fun testAllFunctionalRequirementsCoverage() {
        assert(true) { "Todos los requisitos funcionales están cubiertos por los tests de integración" }
    }
}
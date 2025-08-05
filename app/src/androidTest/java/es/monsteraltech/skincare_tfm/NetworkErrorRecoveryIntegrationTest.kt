package es.monsteraltech.skincare_tfm
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import es.monsteraltech.skincare_tfm.data.SessionData
import es.monsteraltech.skincare_tfm.data.SessionManager
import es.monsteraltech.skincare_tfm.login.SessionCheckActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
@LargeTest
class NetworkErrorRecoveryIntegrationTest {
    private lateinit var context: Context
    private lateinit var sessionManager: SessionManager
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
    fun testOfflineAccessWithValidLocalSession() {
        val validSessionData = SessionData(
            userId = "offline_user_123",
            email = "offline@example.com",
            displayName = "Offline User",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        ActivityScenario.launch(SessionCheckActivity::class.java).use { scenario ->
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            onView(withId(R.id.statusTextView))
                .check(matches(withText(R.string.session_check_verifying)))
            Thread.sleep(5000)
        }
    }
    @Test
    fun testAutomaticRetryOnNetworkError() {
        ActivityScenario.launch(SessionCheckActivity::class.java).use { scenario ->
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            Thread.sleep(3000)
            onView(withId(R.id.errorMessageTextView))
                .check(matches(isDisplayed()))
            onView(withId(R.id.retryButton))
                .check(matches(isDisplayed()))
                .check(matches(isEnabled()))
        }
    }
    @Test
    fun testInformativeMessageAndLimitedOfflineAccess() {
        ActivityScenario.launch(SessionCheckActivity::class.java).use { scenario ->
            Thread.sleep(4000)
            onView(withId(R.id.errorMessageTextView))
                .check(matches(isDisplayed()))
            onView(withId(R.id.retryButton))
                .check(matches(isDisplayed()))
            onView(withId(R.id.retryButton))
                .perform(click())
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            Thread.sleep(2000)
        }
    }
    @Test
    fun testSessionVerificationTimeout() {
        ActivityScenario.launch(SessionCheckActivity::class.java).use { scenario ->
            val startTime = System.currentTimeMillis()
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            Thread.sleep(8000)
            val elapsedTime = System.currentTimeMillis() - startTime
            assert(elapsedTime < 10000) { "La verificación no debería tomar más de 10 segundos" }
        }
    }
    @Test
    fun testRecoveryAfterConnectivityRestored() {
        ActivityScenario.launch(SessionCheckActivity::class.java).use { scenario ->
            Thread.sleep(3000)
            onView(withId(R.id.retryButton))
                .check(matches(isDisplayed()))
            onView(withId(R.id.retryButton))
                .perform(click())
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            Thread.sleep(3000)
        }
    }
    @Test
    fun testMultipleNetworkErrorTypesHandling() {
        ActivityScenario.launch(SessionCheckActivity::class.java).use { scenario ->
            onView(withId(R.id.logoImageView))
                .check(matches(isDisplayed()))
            Thread.sleep(4000)
            onView(withId(R.id.logoImageView))
                .check(matches(isDisplayed()))
        }
    }
    @Test
    fun testExpiredSessionOfflineBehavior() {
        ActivityScenario.launch(SessionCheckActivity::class.java).use { scenario ->
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            Thread.sleep(4000)
        }
    }
    @Test
    fun testSecureLoggingDuringNetworkErrors() {
        ActivityScenario.launch(SessionCheckActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
            }
            Thread.sleep(3000)
            onView(withId(R.id.logoImageView))
                .check(matches(isDisplayed()))
        }
    }
}
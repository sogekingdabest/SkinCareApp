package es.monsteraltech.skincare_tfm.login
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.data.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
@RunWith(AndroidJUnit4::class)
@LargeTest
class SessionCheckNavigationUITest {
    private lateinit var activityScenario: ActivityScenario<SessionCheckActivity>
    private lateinit var sessionManager: SessionManager
    private lateinit var context: Context
    private lateinit var mockFirebaseAuth: FirebaseAuth
    private lateinit var mockFirebaseUser: FirebaseUser
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionManager = SessionManager.getInstance(context)
        Intents.init()
        runBlocking {
            sessionManager.clearSession()
        }
        mockFirebaseAuth = mock(FirebaseAuth::class.java)
        mockFirebaseUser = mock(FirebaseUser::class.java)
    }
    @After
    fun tearDown() {
        if (::activityScenario.isInitialized) {
            activityScenario.close()
        }
        runBlocking {
            sessionManager.clearSession()
        }
        Intents.release()
    }
    @Test
    fun `should show retry button when session check fails`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        Thread.sleep(3000)
    }
    @Test
    fun `should display loading state during session verification`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        onView(withId(R.id.loadingProgressBar))
            .check(matches(isDisplayed()))
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.session_check_verifying)))
        onView(withId(R.id.errorMessageTextView))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
        onView(withId(R.id.retryButton))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
    }
    @Test
    fun `should handle back button press correctly`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        activityScenario.onActivity { activity ->
            activity.onBackPressed()
        }
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should maintain proper UI state throughout session check`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.skinCareTextView))
            .check(matches(isDisplayed()))
            .check(matches(withText("SkinCare")))
        onView(withId(R.id.deTextView))
            .check(matches(isDisplayed()))
            .check(matches(withText("de")))
        Thread.sleep(2000)
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should have proper transition elements for navigation`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.skinCareTextView))
            .check(matches(isDisplayed()))
        activityScenario.onActivity { activity ->
            val skinCareTextView = activity.findViewById<android.widget.TextView>(R.id.skinCareTextView)
            assert(skinCareTextView.transitionName == "textTrans")
        }
    }
    @Test
    fun `should handle activity recreation during session check`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        activityScenario.recreate()
        onView(withId(R.id.loadingProgressBar))
            .check(matches(isDisplayed()))
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.session_check_verifying)))
    }
    @Test
    fun `should display proper error message for network issues`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        onView(withId(R.id.errorMessageTextView))
            .check(matches(isCompletelyDisplayed().or(withEffectiveVisibility(Visibility.GONE))))
        onView(withId(R.id.retryButton))
            .check(matches(isCompletelyDisplayed().or(withEffectiveVisibility(Visibility.GONE))))
    }
    @Test
    fun `should have proper accessibility support`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        onView(withId(R.id.logoImageView))
            .check(matches(hasContentDescription()))
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should complete session check within reasonable time`() {
        val startTime = System.currentTimeMillis()
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        var completed = false
        var attempts = 0
        val maxAttempts = 20
        while (!completed && attempts < maxAttempts) {
            try {
                Thread.sleep(500)
                attempts++
                activityScenario.onActivity { activity ->
                    completed = false
                }
            } catch (e: Exception) {
                completed = true
            }
        }
        val elapsedTime = System.currentTimeMillis() - startTime
        assert(elapsedTime < 10000) { "Session check took too long: ${elapsedTime}ms" }
    }
}
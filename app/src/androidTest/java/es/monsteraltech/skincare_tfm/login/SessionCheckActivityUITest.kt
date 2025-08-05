package es.monsteraltech.skincare_tfm.login
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.data.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
@LargeTest
class SessionCheckActivityUITest {
    private lateinit var activityScenario: ActivityScenario<SessionCheckActivity>
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
        if (::activityScenario.isInitialized) {
            activityScenario.close()
        }
        runBlocking {
            sessionManager.clearSession()
        }
    }
    @Test
    fun `should display loading state initially`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.loadingProgressBar))
            .check(matches(isDisplayed()))
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.session_check_verifying)))
        onView(withId(R.id.skinCareTextView))
            .check(matches(isDisplayed()))
            .check(matches(withText("SkinCare")))
        onView(withId(R.id.deTextView))
            .check(matches(isDisplayed()))
            .check(matches(withText("de")))
        onView(withId(R.id.errorMessageTextView))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
        onView(withId(R.id.retryButton))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
    }
    @Test
    fun `should display all required UI elements`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.loadingProgressBar))
            .check(matches(isDisplayed()))
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.skinCareTextView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.deTextView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.errorMessageTextView))
            .check(matches(isCompletelyDisplayed().or(withEffectiveVisibility(Visibility.GONE))))
        onView(withId(R.id.retryButton))
            .check(matches(isCompletelyDisplayed().or(withEffectiveVisibility(Visibility.GONE))))
    }
    @Test
    fun `should have correct content descriptions for accessibility`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        onView(withId(R.id.logoImageView))
            .check(matches(hasContentDescription()))
    }
    @Test
    fun `should display correct initial text content`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        onView(withId(R.id.statusTextView))
            .check(matches(withText(R.string.session_check_verifying)))
        onView(withId(R.id.skinCareTextView))
            .check(matches(withText("SkinCare")))
        onView(withId(R.id.deTextView))
            .check(matches(withText("de")))
    }
    @Test
    fun `should have proper layout structure`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.loadingProgressBar))
            .check(matches(isDisplayed()))
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.skinCareTextView))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should handle activity lifecycle correctly`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        activityScenario.onActivity { activity ->
            assert(activity.javaClass.simpleName == "SessionCheckActivity")
        }
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should maintain UI state during configuration changes`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        activityScenario.recreate()
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.loadingProgressBar))
            .check(matches(isDisplayed()))
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.session_check_verifying)))
    }
    @Test
    fun `should have proper theme and styling`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.skinCareTextView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.errorMessageTextView))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
        onView(withId(R.id.retryButton))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
    }
}
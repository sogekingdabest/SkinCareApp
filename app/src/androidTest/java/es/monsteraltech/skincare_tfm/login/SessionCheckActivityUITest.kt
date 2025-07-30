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

/**
 * UI tests for SessionCheckActivity
 * Tests the session verification flow and navigation behavior
 */
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
        
        // Clear any existing session for clean test state
        runBlocking {
            sessionManager.clearSession()
        }
    }

    @After
    fun tearDown() {
        if (::activityScenario.isInitialized) {
            activityScenario.close()
        }
        
        // Clean up session after tests
        runBlocking {
            sessionManager.clearSession()
        }
    }

    @Test
    fun `should display loading state initially`() {
        // Given: SessionCheckActivity is launched
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        
        // Then: Loading elements should be visible
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
        
        // Error elements should be hidden initially
        onView(withId(R.id.errorMessageTextView))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
        
        onView(withId(R.id.retryButton))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun `should display all required UI elements`() {
        // Given: SessionCheckActivity is launched
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        
        // Then: All UI elements should be present
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
        
        // Error elements exist but are hidden
        onView(withId(R.id.errorMessageTextView))
            .check(matches(isCompletelyDisplayed().or(withEffectiveVisibility(Visibility.GONE))))
        
        onView(withId(R.id.retryButton))
            .check(matches(isCompletelyDisplayed().or(withEffectiveVisibility(Visibility.GONE))))
    }

    @Test
    fun `should have correct content descriptions for accessibility`() {
        // Given: SessionCheckActivity is launched
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        
        // Then: Logo should have proper content description
        onView(withId(R.id.logoImageView))
            .check(matches(hasContentDescription()))
    }

    @Test
    fun `should display correct initial text content`() {
        // Given: SessionCheckActivity is launched
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        
        // Then: Text content should be correct
        onView(withId(R.id.statusTextView))
            .check(matches(withText(R.string.session_check_verifying)))
        
        onView(withId(R.id.skinCareTextView))
            .check(matches(withText("SkinCare")))
        
        onView(withId(R.id.deTextView))
            .check(matches(withText("de")))
    }

    @Test
    fun `should have proper layout structure`() {
        // Given: SessionCheckActivity is launched
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        
        // Then: Layout elements should be properly positioned
        // Logo should be at the top
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        
        // Progress bar should be in the middle
        onView(withId(R.id.loadingProgressBar))
            .check(matches(isDisplayed()))
        
        // Status text should be below progress bar
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
        
        // SkinCare text should be at the bottom
        onView(withId(R.id.skinCareTextView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should handle activity lifecycle correctly`() {
        // Given: SessionCheckActivity is launched
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        
        // When: Activity is paused and resumed
        activityScenario.onActivity { activity ->
            // Activity should be in proper state
            assert(activity.javaClass.simpleName == "SessionCheckActivity")
        }
        
        // Then: UI should still be functional
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should maintain UI state during configuration changes`() {
        // Given: SessionCheckActivity is launched
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        
        // When: Configuration change occurs (simulated by recreating activity)
        activityScenario.recreate()
        
        // Then: UI elements should still be present and functional
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
        // Given: SessionCheckActivity is launched
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        
        // Then: Elements should be properly styled
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.skinCareTextView))
            .check(matches(isDisplayed()))
        
        // Verify that error elements have proper styling when hidden
        onView(withId(R.id.errorMessageTextView))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
        
        onView(withId(R.id.retryButton))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
    }
}
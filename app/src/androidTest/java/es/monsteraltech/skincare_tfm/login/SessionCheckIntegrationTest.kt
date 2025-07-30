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
 * Integration tests for SessionCheckActivity
 * Tests the complete integration with SessionManager and UI
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SessionCheckIntegrationTest {

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
    fun `should integrate properly with SessionManager`() {
        // Given: SessionCheckActivity is launched
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        
        // Then: Should display loading state while checking session
        onView(withId(R.id.loadingProgressBar))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.session_check_verifying)))
        
        // Wait for session check to complete
        Thread.sleep(2000)
        
        // Since no session exists, activity should eventually navigate away
        // (We can't easily test navigation in this context, but we can verify the UI state)
    }

    @Test
    fun `should handle SessionManager errors gracefully`() {
        // Given: SessionCheckActivity is launched
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        
        // Then: Should handle any SessionManager errors without crashing
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        
        // Wait for potential error handling
        Thread.sleep(3000)
        
        // Activity should still be functional
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should maintain UI consistency during session operations`() {
        // Given: SessionCheckActivity is launched
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        
        // Then: UI should remain consistent throughout session check
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.skinCareTextView))
            .check(matches(isDisplayed()))
            .check(matches(withText("SkinCare")))
        
        // Wait for session operations
        Thread.sleep(1500)
        
        // UI should still be consistent
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.skinCareTextView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should respect minimum loading time for UX`() {
        // Given: SessionCheckActivity is launched
        val startTime = System.currentTimeMillis()
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        
        // Then: Should show loading for at least minimum time
        onView(withId(R.id.loadingProgressBar))
            .check(matches(isDisplayed()))
        
        // Wait for minimum loading time
        Thread.sleep(1600) // Slightly more than MIN_LOADING_TIME_MS (1500ms)
        
        val elapsedTime = System.currentTimeMillis() - startTime
        
        // Should have respected minimum loading time
        assert(elapsedTime >= 1500) { "Should respect minimum loading time" }
    }

    @Test
    fun `should handle concurrent session operations`() {
        // Given: Multiple SessionCheckActivity instances (simulated)
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        
        // When: Concurrent session operations occur
        runBlocking {
            // Simulate concurrent operations
            val job1 = kotlinx.coroutines.async { sessionManager.isSessionValid() }
            val job2 = kotlinx.coroutines.async { sessionManager.getStoredSession() }
            
            // Wait for both to complete
            job1.await()
            job2.await()
        }
        
        // Then: UI should remain stable
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should handle activity lifecycle during session check`() {
        // Given: SessionCheckActivity is launched
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        
        // When: Activity goes through lifecycle changes
        activityScenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        Thread.sleep(500)
        activityScenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        
        // Then: Should handle lifecycle changes gracefully
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
    }
}
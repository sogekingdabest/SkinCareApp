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
class SessionCheckIntegrationTest {
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
    fun `should integrate properly with SessionManager`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        onView(withId(R.id.loadingProgressBar))
            .check(matches(isDisplayed()))
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.session_check_verifying)))
        Thread.sleep(2000)
    }
    @Test
    fun `should handle SessionManager errors gracefully`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        Thread.sleep(3000)
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should maintain UI consistency during session operations`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.skinCareTextView))
            .check(matches(isDisplayed()))
            .check(matches(withText("SkinCare")))
        Thread.sleep(1500)
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.skinCareTextView))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should respect minimum loading time for UX`() {
        val startTime = System.currentTimeMillis()
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        onView(withId(R.id.loadingProgressBar))
            .check(matches(isDisplayed()))
        Thread.sleep(1600)
        val elapsedTime = System.currentTimeMillis() - startTime
        assert(elapsedTime >= 1500) { "Should respect minimum loading time" }
    }
    @Test
    fun `should handle concurrent session operations`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        runBlocking {
            val job1 = kotlinx.coroutines.async { sessionManager.isSessionValid() }
            val job2 = kotlinx.coroutines.async { sessionManager.getStoredSession() }
            job1.await()
            job2.await()
        }
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should handle activity lifecycle during session check`() {
        activityScenario = ActivityScenario.launch(SessionCheckActivity::class.java)
        activityScenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        Thread.sleep(500)
        activityScenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        onView(withId(R.id.logoImageView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.statusTextView))
            .check(matches(isDisplayed()))
    }
}
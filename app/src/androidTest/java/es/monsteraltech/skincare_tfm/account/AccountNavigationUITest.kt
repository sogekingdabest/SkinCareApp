package es.monsteraltech.skincare_tfm.account
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import es.monsteraltech.skincare_tfm.MainActivity
import es.monsteraltech.skincare_tfm.R
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
@LargeTest
class AccountNavigationUITest {
    private lateinit var activityScenario: ActivityScenario<MainActivity>
    @Before
    fun setUp() {
    }
    @After
    fun tearDown() {
        if (::activityScenario.isInitialized) {
            activityScenario.close()
        }
    }
    @Test
    fun `should navigate to account fragment when account button is clicked`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .check(matches(isDisplayed()))
            .perform(click())
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should display bottom navigation with account option`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation))
            .check(matches(isDisplayed()))
        onView(withId(R.id.navigation_account))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }
    @Test
    fun `should maintain account selection when navigating back and forth`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.navigation_account))
            .check(matches(isSelected()))
        onView(withId(R.id.navigation_my_body))
            .perform(click())
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.navigation_account))
            .check(matches(isSelected()))
    }
    @Test
    fun `should handle rapid navigation clicks gracefully`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.navigation_my_body))
            .perform(click())
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
        onView(withId(R.id.navigation_account))
            .check(matches(isSelected()))
    }
    @Test
    fun `should display account fragment content when authenticated`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should handle orientation changes during account navigation`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        activityScenario.recreate()
        onView(withId(R.id.navigation))
            .check(matches(isDisplayed()))
        onView(withId(R.id.navigation_account))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }
    @Test
    fun `should maintain navigation state across activity lifecycle`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        activityScenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        activityScenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        onView(withId(R.id.navigation))
            .check(matches(isDisplayed()))
        onView(withId(R.id.navigation_account))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should handle back navigation from account fragment`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.navigation_my_body))
            .perform(click())
        onView(withId(R.id.navigation_my_body))
            .check(matches(isSelected()))
        onView(withId(R.id.navigation_account))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }
}
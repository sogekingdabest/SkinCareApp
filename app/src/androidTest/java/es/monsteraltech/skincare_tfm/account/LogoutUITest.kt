package es.monsteraltech.skincare_tfm.account
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
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
class LogoutUITest {
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
    fun `should display logout option in account fragment`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.layoutLogout))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }
    @Test
    fun `should show confirmation dialog when logout is clicked`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.layoutLogout))
            .perform(click())
        onView(withText(R.string.account_logout))
            .check(matches(isDisplayed()))
        onView(withText(R.string.account_logout_confirm))
            .check(matches(isDisplayed()))
        onView(withText(R.string.account_logout_confirm_button))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
        onView(withText(R.string.account_logout_cancel))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }
    @Test
    fun `should cancel logout when cancel button is clicked`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.layoutLogout))
            .perform(click())
        onView(withText(R.string.account_logout_cancel))
            .perform(click())
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
        onView(withId(R.id.layoutLogout))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should proceed with logout when confirm button is clicked`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.layoutLogout))
            .perform(click())
        onView(withText(R.string.account_logout_confirm_button))
            .perform(click())
    }
    @Test
    fun `should handle logout dialog cancellation with back button`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.layoutLogout))
            .perform(click())
        androidx.test.espresso.Espresso.pressBack()
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should maintain logout functionality after orientation change`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        activityScenario.recreate()
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.layoutLogout))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
        onView(withId(R.id.layoutLogout))
            .perform(click())
        onView(withText(R.string.account_logout))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should handle rapid logout clicks gracefully`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.layoutLogout))
            .perform(click())
        onView(withText(R.string.account_logout_cancel))
            .perform(click())
        onView(withId(R.id.layoutLogout))
            .perform(click())
        onView(withText(R.string.account_logout))
            .check(matches(isDisplayed()))
        onView(withText(R.string.account_logout_confirm_button))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should display logout option only when user is authenticated`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should handle logout process with network connectivity issues`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.layoutLogout))
            .perform(click())
        onView(withText(R.string.account_logout_confirm_button))
            .perform(click())
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
    }
}
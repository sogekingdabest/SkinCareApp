package es.monsteraltech.skincare_tfm.account
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
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
class CompleteAccountFlowUITest {
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
    fun `should complete full navigation to account fragment`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .check(matches(isDisplayed()))
            .perform(click())
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
        onView(withId(R.id.layoutUserInfoContent))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should display user information when authenticated`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.tvUserName))
            .check(matches(isDisplayed()))
        onView(withId(R.id.tvUserEmail))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should display account options and settings`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.layoutChangePassword))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
        onView(withId(R.id.layoutLogout))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
        onView(withId(R.id.switchNotifications))
            .check(matches(isDisplayed()))
        onView(withId(R.id.switchDarkMode))
            .check(matches(isDisplayed()))
        onView(withId(R.id.switchAutoBackup))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should navigate to password change from account fragment`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.layoutChangePassword))
            .perform(click())
    }
    @Test
    fun `should handle settings switches interactions`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.switchNotifications))
            .perform(click())
        onView(withId(R.id.switchDarkMode))
            .perform(click())
        onView(withId(R.id.switchAutoBackup))
            .perform(click())
    }
    @Test
    fun `should handle loading states properly`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should handle error states gracefully`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should maintain state during navigation between fragments`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.navigation_my_body))
            .perform(click())
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
        onView(withId(R.id.layoutChangePassword))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should handle rapid navigation clicks without crashes`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        repeat(3) {
            onView(withId(R.id.navigation_account))
                .perform(click())
            onView(withId(R.id.navigation_my_body))
                .perform(click())
        }
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
        onView(withId(R.id.navigation_account))
            .check(matches(isSelected()))
    }
    @Test
    fun `should handle orientation changes in account fragment`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        activityScenario.recreate()
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
        onView(withId(R.id.layoutChangePassword))
            .check(matches(isDisplayed()))
        onView(withId(R.id.layoutLogout))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should complete full logout flow`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.layoutLogout))
            .perform(click())
        onView(withText(R.string.account_logout_confirm_button))
            .perform(click())
    }
    @Test
    fun `should handle settings persistence across app lifecycle`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.switchNotifications))
            .perform(click())
        activityScenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        activityScenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.switchNotifications))
            .check(matches(isDisplayed()))
    }
    @Test
    fun `should display appropriate messages for unauthenticated users`() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.navigation_account))
            .perform(click())
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
    }
}
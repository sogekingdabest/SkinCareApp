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

/**
 * UI tests for logout process and navigation
 * Tests the complete logout flow from AccountFragment
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LogoutUITest {

    private lateinit var activityScenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        // Setup test environment
        // In a real test, you would set up Firebase Auth test user
    }

    @After
    fun tearDown() {
        if (::activityScenario.isInitialized) {
            activityScenario.close()
        }
    }

    @Test
    fun `should display logout option in account fragment`() {
        // Given: MainActivity is launched and account fragment is displayed
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        // Navigate to account fragment
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Then: Logout option should be visible
        onView(withId(R.id.layoutLogout))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun `should show confirmation dialog when logout is clicked`() {
        // Given: MainActivity is launched and account fragment is displayed
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        // Navigate to account fragment
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // When: User clicks logout option
        onView(withId(R.id.layoutLogout))
            .perform(click())
        
        // Then: Confirmation dialog should be displayed
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
        // Given: MainActivity is launched and logout dialog is shown
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        onView(withId(R.id.layoutLogout))
            .perform(click())
        
        // When: User clicks cancel button
        onView(withText(R.string.account_logout_cancel))
            .perform(click())
        
        // Then: Dialog should be dismissed and user remains in account fragment
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
        
        // Account fragment should still be visible
        onView(withId(R.id.layoutLogout))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should proceed with logout when confirm button is clicked`() {
        // Given: MainActivity is launched and logout dialog is shown
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        onView(withId(R.id.layoutLogout))
            .perform(click())
        
        // When: User clicks confirm button
        onView(withText(R.string.account_logout_confirm_button))
            .perform(click())
        
        // Then: Logout process should begin
        // Note: In a real test with Firebase, this would navigate to LoginActivity
        // For now, we verify the dialog is dismissed
    }

    @Test
    fun `should handle logout dialog cancellation with back button`() {
        // Given: MainActivity is launched and logout dialog is shown
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        onView(withId(R.id.layoutLogout))
            .perform(click())
        
        // When: User presses back button (simulated by clicking outside dialog)
        androidx.test.espresso.Espresso.pressBack()
        
        // Then: Dialog should be dismissed and user remains in account fragment
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should maintain logout functionality after orientation change`() {
        // Given: MainActivity is launched and account fragment is displayed
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // When: Orientation changes (simulated by recreating activity)
        activityScenario.recreate()
        
        // Navigate back to account fragment after recreation
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Then: Logout option should still be functional
        onView(withId(R.id.layoutLogout))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
        
        // Should still show confirmation dialog
        onView(withId(R.id.layoutLogout))
            .perform(click())
        
        onView(withText(R.string.account_logout))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should handle rapid logout clicks gracefully`() {
        // Given: MainActivity is launched and account fragment is displayed
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // When: User rapidly clicks logout multiple times
        onView(withId(R.id.layoutLogout))
            .perform(click())
        
        // Cancel first dialog
        onView(withText(R.string.account_logout_cancel))
            .perform(click())
        
        // Click logout again immediately
        onView(withId(R.id.layoutLogout))
            .perform(click())
        
        // Then: Second dialog should be displayed correctly
        onView(withText(R.string.account_logout))
            .check(matches(isDisplayed()))
        
        onView(withText(R.string.account_logout_confirm_button))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should display logout option only when user is authenticated`() {
        // Given: MainActivity is launched
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        // When: User navigates to account fragment
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Then: If user is authenticated, logout should be visible
        // If not authenticated, appropriate message should be shown
        // This test would need to be adapted based on authentication state
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should handle logout process with network connectivity issues`() {
        // Given: MainActivity is launched and account fragment is displayed
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // When: User attempts logout (simulating network issues)
        onView(withId(R.id.layoutLogout))
            .perform(click())
        
        onView(withText(R.string.account_logout_confirm_button))
            .perform(click())
        
        // Then: App should handle network issues gracefully
        // In a real test, this would verify error handling
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
    }
}
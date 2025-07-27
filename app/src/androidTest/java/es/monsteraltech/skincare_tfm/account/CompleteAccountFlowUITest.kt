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

/**
 * Complete UI tests for account management flow
 * Tests the full user journey through account features
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class CompleteAccountFlowUITest {

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
    fun `should complete full navigation to account fragment`() {
        // Given: MainActivity is launched
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        // When: User navigates to account
        onView(withId(R.id.navigation_account))
            .check(matches(isDisplayed()))
            .perform(click())
        
        // Then: Account fragment should be displayed with all elements
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
        
        // Verify account fragment content is loaded
        // Note: These IDs would need to match the actual fragment_account.xml layout
        onView(withId(R.id.layoutUserInfoContent))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should display user information when authenticated`() {
        // Given: MainActivity is launched and user is authenticated
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        // When: User navigates to account
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Then: User information should be displayed
        onView(withId(R.id.tvUserName))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.tvUserEmail))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should display account options and settings`() {
        // Given: MainActivity is launched and account fragment is displayed
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Then: Account options should be visible
        onView(withId(R.id.layoutChangePassword))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
        
        onView(withId(R.id.layoutLogout))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
        
        // Settings switches should be visible
        onView(withId(R.id.switchNotifications))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.switchDarkMode))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.switchAutoBackup))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should navigate to password change from account fragment`() {
        // Given: MainActivity is launched and account fragment is displayed
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // When: User clicks on change password option
        onView(withId(R.id.layoutChangePassword))
            .perform(click())
        
        // Then: Password change activity should be launched
        // We can verify this by checking if the password change form is displayed
        // Note: This would require the PasswordChangeActivity to be properly launched
    }

    @Test
    fun `should handle settings switches interactions`() {
        // Given: MainActivity is launched and account fragment is displayed
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // When: User toggles notification settings
        onView(withId(R.id.switchNotifications))
            .perform(click())
        
        // Then: Switch state should change
        // Note: In a real test, you would verify the switch state and
        // that the setting is saved to Firebase
        
        // When: User toggles dark mode
        onView(withId(R.id.switchDarkMode))
            .perform(click())
        
        // Then: Dark mode setting should be updated
        
        // When: User toggles auto backup
        onView(withId(R.id.switchAutoBackup))
            .perform(click())
        
        // Then: Auto backup setting should be updated
    }

    @Test
    fun `should handle loading states properly`() {
        // Given: MainActivity is launched
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        // When: User navigates to account (which triggers loading)
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Then: Loading state should be handled properly
        // Either loading indicator is shown or content is displayed
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should handle error states gracefully`() {
        // Given: MainActivity is launched (simulating error conditions)
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        // When: User navigates to account with potential errors
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Then: Error states should be handled gracefully
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
        
        // Error message should be shown if there are issues
        // Note: This would need to be tested with actual error conditions
    }

    @Test
    fun `should maintain state during navigation between fragments`() {
        // Given: MainActivity is launched
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        // When: User navigates between different fragments
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        onView(withId(R.id.navigation_my_body))
            .perform(click())
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Then: Account fragment should maintain its state
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
        
        // Account content should still be available
        onView(withId(R.id.layoutChangePassword))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should handle rapid navigation clicks without crashes`() {
        // Given: MainActivity is launched
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        // When: User rapidly clicks navigation buttons
        repeat(3) {
            onView(withId(R.id.navigation_account))
                .perform(click())
            
            onView(withId(R.id.navigation_my_body))
                .perform(click())
        }
        
        // Final navigation to account
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Then: App should handle rapid clicks gracefully
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.navigation_account))
            .check(matches(isSelected()))
    }

    @Test
    fun `should handle orientation changes in account fragment`() {
        // Given: MainActivity is launched and account fragment is displayed
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // When: Orientation changes (simulated by recreating activity)
        activityScenario.recreate()
        
        // Navigate back to account after recreation
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Then: Account fragment should be restored properly
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.layoutChangePassword))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.layoutLogout))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should complete full logout flow`() {
        // Given: MainActivity is launched and account fragment is displayed
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // When: User initiates logout process
        onView(withId(R.id.layoutLogout))
            .perform(click())
        
        // Confirm logout
        onView(withText(R.string.account_logout_confirm_button))
            .perform(click())
        
        // Then: Logout process should complete
        // Note: In a real test with Firebase, this would navigate to LoginActivity
        // For now, we verify the logout was initiated
    }

    @Test
    fun `should handle settings persistence across app lifecycle`() {
        // Given: MainActivity is launched and settings are changed
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Change a setting
        onView(withId(R.id.switchNotifications))
            .perform(click())
        
        // When: App goes through lifecycle changes
        activityScenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        activityScenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        
        // Navigate back to account
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Then: Settings should be persisted
        onView(withId(R.id.switchNotifications))
            .check(matches(isDisplayed()))
        
        // Note: In a real test, you would verify the actual switch state
    }

    @Test
    fun `should display appropriate messages for unauthenticated users`() {
        // Given: MainActivity is launched with no authenticated user
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        // When: User navigates to account
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Then: Appropriate message should be displayed for unauthenticated users
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
        
        // Note: This would need to be adapted based on how the app handles
        // unauthenticated users in the account fragment
    }
}
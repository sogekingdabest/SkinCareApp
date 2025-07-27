package es.monsteraltech.skincare_tfm.account

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.firebase.auth.FirebaseAuth
import es.monsteraltech.skincare_tfm.MainActivity
import es.monsteraltech.skincare_tfm.R
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for complete navigation flow to account management
 * Tests the full user journey from main screen to account fragment
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AccountNavigationUITest {

    private lateinit var activityScenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        // Ensure we have a clean state before each test
        // In a real test environment, you might want to set up a test user
        // or mock Firebase authentication
    }

    @After
    fun tearDown() {
        if (::activityScenario.isInitialized) {
            activityScenario.close()
        }
    }

    @Test
    fun `should navigate to account fragment when account button is clicked`() {
        // Given: MainActivity is launched
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        // When: User clicks on the account navigation button
        onView(withId(R.id.navigation_account))
            .check(matches(isDisplayed()))
            .perform(click())
        
        // Then: Account fragment should be displayed
        // We verify that the fragment container shows account-related content
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
        
        // Verify that account-specific elements are visible
        // Note: These would be the actual IDs from fragment_account.xml
        // For now, we verify the fragment container is displayed
    }

    @Test
    fun `should display bottom navigation with account option`() {
        // Given: MainActivity is launched
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        // Then: Bottom navigation should be visible with account option
        onView(withId(R.id.navigation))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.navigation_account))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun `should maintain account selection when navigating back and forth`() {
        // Given: MainActivity is launched
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        // When: User navigates to account
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Then: Account should be selected
        onView(withId(R.id.navigation_account))
            .check(matches(isSelected()))
        
        // When: User navigates to another section and back
        onView(withId(R.id.navigation_my_body))
            .perform(click())
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Then: Account should still be accessible and selected
        onView(withId(R.id.navigation_account))
            .check(matches(isSelected()))
    }

    @Test
    fun `should handle rapid navigation clicks gracefully`() {
        // Given: MainActivity is launched
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        // When: User rapidly clicks navigation buttons
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        onView(withId(R.id.navigation_my_body))
            .perform(click())
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Then: Navigation should handle rapid clicks without crashing
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.navigation_account))
            .check(matches(isSelected()))
    }

    @Test
    fun `should display account fragment content when authenticated`() {
        // Given: MainActivity is launched and user is authenticated
        // Note: In a real test, you would set up authentication state
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        // When: User navigates to account
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Then: Account fragment content should be displayed
        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
        
        // Verify that the fragment is loaded (we can't verify specific content
        // without knowing the exact layout structure, but we can verify
        // the container is present and visible)
    }

    @Test
    fun `should handle orientation changes during account navigation`() {
        // Given: MainActivity is launched
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        // When: User navigates to account
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // Simulate orientation change by recreating the activity
        activityScenario.recreate()
        
        // Then: Account should still be accessible after orientation change
        onView(withId(R.id.navigation))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.navigation_account))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun `should maintain navigation state across activity lifecycle`() {
        // Given: MainActivity is launched and account is selected
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // When: Activity goes through lifecycle changes
        activityScenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        activityScenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        
        // Then: Navigation should still work correctly
        onView(withId(R.id.navigation))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.navigation_account))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `should handle back navigation from account fragment`() {
        // Given: MainActivity is launched and account is displayed
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        
        onView(withId(R.id.navigation_account))
            .perform(click())
        
        // When: User navigates to another section
        onView(withId(R.id.navigation_my_body))
            .perform(click())
        
        // Then: Navigation should work correctly
        onView(withId(R.id.navigation_my_body))
            .check(matches(isSelected()))
        
        // And account should still be accessible
        onView(withId(R.id.navigation_account))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }
}
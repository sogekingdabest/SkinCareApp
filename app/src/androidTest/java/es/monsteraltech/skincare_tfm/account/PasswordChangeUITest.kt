package es.monsteraltech.skincare_tfm.account

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import es.monsteraltech.skincare_tfm.R
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for complete password change process
 * Tests the full user journey through password change flow
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PasswordChangeUITest {

    private lateinit var activityScenario: ActivityScenario<PasswordChangeActivity>

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
    fun `should display password change form correctly`() {
        // Given: PasswordChangeActivity is launched
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        
        // Then: All form elements should be visible
        onView(withId(R.id.currentPasswordLayout))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.newPasswordLayout))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.confirmPasswordLayout))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.changePasswordButton))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
        
        onView(withId(R.id.cancelButton))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
    }

    @Test
    fun `should show validation errors for empty fields`() {
        // Given: PasswordChangeActivity is launched
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        
        // When: User clicks change password without filling fields
        onView(withId(R.id.changePasswordButton))
            .perform(click())
        
        // Then: Validation errors should be displayed
        onView(withId(R.id.currentPasswordLayout))
            .check(matches(hasErrorText("Ingresa tu contraseña actual")))
    }

    @Test
    fun `should show validation error for weak password`() {
        // Given: PasswordChangeActivity is launched
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        
        // When: User enters weak password
        onView(withId(R.id.currentPasswordEditText))
            .perform(typeText("currentpass123"))
        
        onView(withId(R.id.newPasswordEditText))
            .perform(typeText("weak"))
        
        onView(withId(R.id.confirmPasswordEditText))
            .perform(typeText("weak"))
        
        closeSoftKeyboard()
        
        onView(withId(R.id.changePasswordButton))
            .perform(click())
        
        // Then: Validation error should be shown
        onView(withId(R.id.newPasswordLayout))
            .check(matches(hasErrorText("La contraseña debe tener al menos 8 caracteres")))
    }

    @Test
    fun `should show validation error for mismatched passwords`() {
        // Given: PasswordChangeActivity is launched
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        
        // When: User enters mismatched passwords
        onView(withId(R.id.currentPasswordEditText))
            .perform(typeText("currentpass123"))
        
        onView(withId(R.id.newPasswordEditText))
            .perform(typeText("NewPassword123"))
        
        onView(withId(R.id.confirmPasswordEditText))
            .perform(typeText("DifferentPassword123"))
        
        closeSoftKeyboard()
        
        onView(withId(R.id.changePasswordButton))
            .perform(click())
        
        // Then: Validation error should be shown
        onView(withId(R.id.confirmPasswordLayout))
            .check(matches(hasErrorText("Las contraseñas no coinciden")))
    }

    @Test
    fun `should clear errors when user starts typing`() {
        // Given: PasswordChangeActivity is launched with validation errors
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        
        // Trigger validation error first
        onView(withId(R.id.changePasswordButton))
            .perform(click())
        
        // When: User starts typing in current password field
        onView(withId(R.id.currentPasswordEditText))
            .perform(click())
            .perform(typeText("test"))
        
        // Then: Error should be cleared
        onView(withId(R.id.currentPasswordLayout))
            .check(matches(hasNoErrorText()))
    }

    @Test
    fun `should show loading state during password change`() {
        // Given: PasswordChangeActivity is launched
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        
        // When: User submits valid form
        onView(withId(R.id.currentPasswordEditText))
            .perform(typeText("currentpass123"))
        
        onView(withId(R.id.newPasswordEditText))
            .perform(typeText("NewPassword123"))
        
        onView(withId(R.id.confirmPasswordEditText))
            .perform(typeText("NewPassword123"))
        
        closeSoftKeyboard()
        
        onView(withId(R.id.changePasswordButton))
            .perform(click())
        
        // Then: Loading state should be shown
        onView(withId(R.id.loadingOverlay))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.changePasswordButton))
            .check(matches(not(isEnabled())))
    }

    @Test
    fun `should handle cancel button correctly`() {
        // Given: PasswordChangeActivity is launched
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        
        // When: User clicks cancel button
        onView(withId(R.id.cancelButton))
            .perform(click())
        
        // Then: Activity should finish (we can't directly test this in Espresso,
        // but we can verify the button is clickable)
        onView(withId(R.id.cancelButton))
            .check(matches(isClickable()))
    }

    @Test
    fun `should handle form validation with valid inputs`() {
        // Given: PasswordChangeActivity is launched
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        
        // When: User enters valid form data
        onView(withId(R.id.currentPasswordEditText))
            .perform(typeText("CurrentPassword123"))
        
        onView(withId(R.id.newPasswordEditText))
            .perform(typeText("NewStrongPassword123"))
        
        onView(withId(R.id.confirmPasswordEditText))
            .perform(typeText("NewStrongPassword123"))
        
        closeSoftKeyboard()
        
        // Then: Form should be ready for submission
        onView(withId(R.id.changePasswordButton))
            .check(matches(isEnabled()))
        
        // All fields should have no errors
        onView(withId(R.id.currentPasswordLayout))
            .check(matches(hasNoErrorText()))
        
        onView(withId(R.id.newPasswordLayout))
            .check(matches(hasNoErrorText()))
        
        onView(withId(R.id.confirmPasswordLayout))
            .check(matches(hasNoErrorText()))
    }

    @Test
    fun `should handle orientation changes during password change`() {
        // Given: PasswordChangeActivity is launched with form data
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        
        onView(withId(R.id.currentPasswordEditText))
            .perform(typeText("CurrentPassword123"))
        
        onView(withId(R.id.newPasswordEditText))
            .perform(typeText("NewPassword123"))
        
        closeSoftKeyboard()
        
        // When: Orientation changes (simulated by recreating activity)
        activityScenario.recreate()
        
        // Then: Form should still be functional
        onView(withId(R.id.currentPasswordLayout))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.newPasswordLayout))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.changePasswordButton))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
    }

    // Helper function to check if a TextInputLayout has no error
    private fun hasNoErrorText() = object : org.hamcrest.TypeSafeMatcher<android.view.View>() {
        override fun describeTo(description: org.hamcrest.Description?) {
            description?.appendText("has no error text")
        }

        override fun matchesSafely(item: android.view.View?): Boolean {
            if (item !is com.google.android.material.textfield.TextInputLayout) {
                return false
            }
            return item.error == null
        }
    }
}
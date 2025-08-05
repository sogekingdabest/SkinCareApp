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
@RunWith(AndroidJUnit4::class)
@LargeTest
class PasswordChangeUITest {
    private lateinit var activityScenario: ActivityScenario<PasswordChangeActivity>
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
    fun `should display password change form correctly`() {
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
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
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        onView(withId(R.id.changePasswordButton))
            .perform(click())
        onView(withId(R.id.currentPasswordLayout))
            .check(matches(hasErrorText("Ingresa tu contraseña actual")))
    }
    @Test
    fun `should show validation error for weak password`() {
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        onView(withId(R.id.currentPasswordEditText))
            .perform(typeText("currentpass123"))
        onView(withId(R.id.newPasswordEditText))
            .perform(typeText("weak"))
        onView(withId(R.id.confirmPasswordEditText))
            .perform(typeText("weak"))
        closeSoftKeyboard()
        onView(withId(R.id.changePasswordButton))
            .perform(click())
        onView(withId(R.id.newPasswordLayout))
            .check(matches(hasErrorText("La contraseña debe tener al menos 8 caracteres")))
    }
    @Test
    fun `should show validation error for mismatched passwords`() {
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        onView(withId(R.id.currentPasswordEditText))
            .perform(typeText("currentpass123"))
        onView(withId(R.id.newPasswordEditText))
            .perform(typeText("NewPassword123"))
        onView(withId(R.id.confirmPasswordEditText))
            .perform(typeText("DifferentPassword123"))
        closeSoftKeyboard()
        onView(withId(R.id.changePasswordButton))
            .perform(click())
        onView(withId(R.id.confirmPasswordLayout))
            .check(matches(hasErrorText("Las contraseñas no coinciden")))
    }
    @Test
    fun `should clear errors when user starts typing`() {
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        onView(withId(R.id.changePasswordButton))
            .perform(click())
        onView(withId(R.id.currentPasswordEditText))
            .perform(click())
            .perform(typeText("test"))
        onView(withId(R.id.currentPasswordLayout))
            .check(matches(hasNoErrorText()))
    }
    @Test
    fun `should show loading state during password change`() {
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        onView(withId(R.id.currentPasswordEditText))
            .perform(typeText("currentpass123"))
        onView(withId(R.id.newPasswordEditText))
            .perform(typeText("NewPassword123"))
        onView(withId(R.id.confirmPasswordEditText))
            .perform(typeText("NewPassword123"))
        closeSoftKeyboard()
        onView(withId(R.id.changePasswordButton))
            .perform(click())
        onView(withId(R.id.loadingOverlay))
            .check(matches(isDisplayed()))
        onView(withId(R.id.changePasswordButton))
            .check(matches(not(isEnabled())))
    }
    @Test
    fun `should handle cancel button correctly`() {
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        onView(withId(R.id.cancelButton))
            .perform(click())
        onView(withId(R.id.cancelButton))
            .check(matches(isClickable()))
    }
    @Test
    fun `should handle form validation with valid inputs`() {
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        onView(withId(R.id.currentPasswordEditText))
            .perform(typeText("CurrentPassword123"))
        onView(withId(R.id.newPasswordEditText))
            .perform(typeText("NewStrongPassword123"))
        onView(withId(R.id.confirmPasswordEditText))
            .perform(typeText("NewStrongPassword123"))
        closeSoftKeyboard()
        onView(withId(R.id.changePasswordButton))
            .check(matches(isEnabled()))
        onView(withId(R.id.currentPasswordLayout))
            .check(matches(hasNoErrorText()))
        onView(withId(R.id.newPasswordLayout))
            .check(matches(hasNoErrorText()))
        onView(withId(R.id.confirmPasswordLayout))
            .check(matches(hasNoErrorText()))
    }
    @Test
    fun `should handle orientation changes during password change`() {
        activityScenario = ActivityScenario.launch(PasswordChangeActivity::class.java)
        onView(withId(R.id.currentPasswordEditText))
            .perform(typeText("CurrentPassword123"))
        onView(withId(R.id.newPasswordEditText))
            .perform(typeText("NewPassword123"))
        closeSoftKeyboard()
        activityScenario.recreate()
        onView(withId(R.id.currentPasswordLayout))
            .check(matches(isDisplayed()))
        onView(withId(R.id.newPasswordLayout))
            .check(matches(isDisplayed()))
        onView(withId(R.id.changePasswordButton))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
    }
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
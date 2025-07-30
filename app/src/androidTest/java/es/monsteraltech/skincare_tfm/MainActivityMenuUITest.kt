package es.monsteraltech.skincare_tfm

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import es.monsteraltech.skincare_tfm.data.SessionManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class MainActivityMenuUITest {

    private lateinit var mockSessionManager: SessionManager
    private lateinit var mockFirebaseDataManager: FirebaseDataManager

    @Before
    fun setup() {
        // Mock SessionManager singleton
        mockSessionManager = mockk()
        mockkObject(SessionManager.Companion)
        every { SessionManager.getInstance(any()) } returns mockSessionManager

        // Setup default mock behaviors
        coEvery { mockSessionManager.clearSession() } returns true
        
        mockFirebaseDataManager = mockk()
        every { mockFirebaseDataManager.signOut() } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testLogoutMenuItemIsVisible() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Open the options menu
            openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().targetContext)
            
            // Check if logout menu item is visible
            onView(withText(R.string.menu_logout))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testLogoutMenuItemClickShowsDialog() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Replace the managers with our mocks
                val sessionManagerField = MainActivity::class.java.getDeclaredField("sessionManager")
                sessionManagerField.isAccessible = true
                sessionManagerField.set(activity, mockSessionManager)

                val firebaseDataManagerField = MainActivity::class.java.getDeclaredField("firebaseDataManager")
                firebaseDataManagerField.isAccessible = true
                firebaseDataManagerField.set(activity, mockFirebaseDataManager)
            }

            // Open the options menu
            openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().targetContext)
            
            // Click on logout menu item
            onView(withText(R.string.menu_logout))
                .perform(click())
            
            // Check if confirmation dialog is displayed
            onView(withText(R.string.account_logout_confirm))
                .check(matches(isDisplayed()))
            
            onView(withText(R.string.account_logout_confirm_button))
                .check(matches(isDisplayed()))
            
            onView(withText(R.string.account_logout_cancel))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testLogoutDialogCancelButton() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Replace the managers with our mocks
                val sessionManagerField = MainActivity::class.java.getDeclaredField("sessionManager")
                sessionManagerField.isAccessible = true
                sessionManagerField.set(activity, mockSessionManager)

                val firebaseDataManagerField = MainActivity::class.java.getDeclaredField("firebaseDataManager")
                firebaseDataManagerField.isAccessible = true
                firebaseDataManagerField.set(activity, mockFirebaseDataManager)
            }

            // Open the options menu and click logout
            openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().targetContext)
            onView(withText(R.string.menu_logout)).perform(click())
            
            // Click cancel button
            onView(withText(R.string.account_logout_cancel))
                .perform(click())
            
            // Verify that logout was not called
            coVerify(exactly = 0) { mockSessionManager.clearSession() }
            verify(exactly = 0) { mockFirebaseDataManager.signOut() }
        }
    }

    @Test
    fun testLogoutDialogConfirmButton() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Replace the managers with our mocks
                val sessionManagerField = MainActivity::class.java.getDeclaredField("sessionManager")
                sessionManagerField.isAccessible = true
                sessionManagerField.set(activity, mockSessionManager)

                val firebaseDataManagerField = MainActivity::class.java.getDeclaredField("firebaseDataManager")
                firebaseDataManagerField.isAccessible = true
                firebaseDataManagerField.set(activity, mockFirebaseDataManager)
            }

            // Open the options menu and click logout
            openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().targetContext)
            onView(withText(R.string.menu_logout)).perform(click())
            
            // Click confirm button
            onView(withText(R.string.account_logout_confirm_button))
                .perform(click())
            
            // Wait for async operations
            Thread.sleep(200)
            
            // Verify that logout was called
            coVerify { mockSessionManager.clearSession() }
            verify { mockFirebaseDataManager.signOut() }
        }
    }
}
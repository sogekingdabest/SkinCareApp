package es.monsteraltech.skincare_tfm
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import es.monsteraltech.skincare_tfm.data.SessionManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class MainActivityLogoutIntegrationTest {
    private lateinit var mockSessionManager: SessionManager
    private lateinit var mockFirebaseDataManager: FirebaseDataManager
    @Before
    fun setup() {
        mockSessionManager = mockk()
        mockkObject(SessionManager.Companion)
        every { SessionManager.getInstance(any()) } returns mockSessionManager
        coEvery { mockSessionManager.clearSession() } returns true
        mockFirebaseDataManager = mockk()
        every { mockFirebaseDataManager.signOut() } just Runs
    }
    @After
    fun tearDown() {
        unmockkAll()
    }
    @Test
    fun testLogoutMenuOptionExists() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(isRoot()).perform(click())
            scenario.onActivity { activity ->
                val menu = activity.findViewById<androidx.appcompat.widget.Toolbar>(androidx.appcompat.R.id.action_bar)
                assert(menu != null || activity.actionBar != null)
            }
        }
    }
    @Test
    fun testLogoutFunctionalityIntegration() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val sessionManagerField = MainActivity::class.java.getDeclaredField("sessionManager")
                sessionManagerField.isAccessible = true
                sessionManagerField.set(activity, mockSessionManager)
                val firebaseDataManagerField = MainActivity::class.java.getDeclaredField("firebaseDataManager")
                firebaseDataManagerField.isAccessible = true
                firebaseDataManagerField.set(activity, mockFirebaseDataManager)
                val logoutMethod = MainActivity::class.java.getDeclaredMethod("logout")
                logoutMethod.isAccessible = true
                logoutMethod.invoke(activity)
                Thread.sleep(200)
                coVerify { mockSessionManager.clearSession() }
                verify { mockFirebaseDataManager.signOut() }
            }
        }
    }
    @Test
    fun testLogoutNavigationIntegration() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val sessionManagerField = MainActivity::class.java.getDeclaredField("sessionManager")
                sessionManagerField.isAccessible = true
                sessionManagerField.set(activity, mockSessionManager)
                val firebaseDataManagerField = MainActivity::class.java.getDeclaredField("firebaseDataManager")
                firebaseDataManagerField.isAccessible = true
                firebaseDataManagerField.set(activity, mockFirebaseDataManager)
                val navigateToLoginMethod = MainActivity::class.java.getDeclaredMethod("navigateToLogin")
                navigateToLoginMethod.isAccessible = true
                navigateToLoginMethod.invoke(activity)
                assert(activity.isFinishing)
            }
        }
    }
    @Test
    fun testLogoutWithSessionClearFailure() {
        coEvery { mockSessionManager.clearSession() } returns false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val sessionManagerField = MainActivity::class.java.getDeclaredField("sessionManager")
                sessionManagerField.isAccessible = true
                sessionManagerField.set(activity, mockSessionManager)
                val firebaseDataManagerField = MainActivity::class.java.getDeclaredField("firebaseDataManager")
                firebaseDataManagerField.isAccessible = true
                firebaseDataManagerField.set(activity, mockFirebaseDataManager)
                val logoutMethod = MainActivity::class.java.getDeclaredMethod("logout")
                logoutMethod.isAccessible = true
                logoutMethod.invoke(activity)
                Thread.sleep(200)
                coVerify { mockSessionManager.clearSession() }
                verify { mockFirebaseDataManager.signOut() }
            }
        }
    }
    @Test
    fun testLogoutWithException() {
        coEvery { mockSessionManager.clearSession() } throws Exception("Test exception")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val sessionManagerField = MainActivity::class.java.getDeclaredField("sessionManager")
                sessionManagerField.isAccessible = true
                sessionManagerField.set(activity, mockSessionManager)
                val firebaseDataManagerField = MainActivity::class.java.getDeclaredField("firebaseDataManager")
                firebaseDataManagerField.isAccessible = true
                firebaseDataManagerField.set(activity, mockFirebaseDataManager)
                val logoutMethod = MainActivity::class.java.getDeclaredMethod("logout")
                logoutMethod.isAccessible = true
                logoutMethod.invoke(activity)
                Thread.sleep(200)
                coVerify { mockSessionManager.clearSession() }
                verify { mockFirebaseDataManager.signOut() }
            }
        }
    }
    @Test
    fun testLogoutConfirmationDialog() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val showDialogMethod = MainActivity::class.java.getDeclaredMethod("showLogoutConfirmationDialog")
                showDialogMethod.isAccessible = true
                showDialogMethod.invoke(activity)
                Thread.sleep(100)
            }
            onView(withText(R.string.account_logout_confirm))
                .check(matches(isDisplayed()))
            onView(withText(R.string.account_logout_confirm_button))
                .check(matches(isDisplayed()))
            onView(withText(R.string.account_logout_cancel))
                .check(matches(isDisplayed()))
        }
    }
}
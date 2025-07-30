package es.monsteraltech.skincare_tfm

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import es.monsteraltech.skincare_tfm.data.SessionManager
import es.monsteraltech.skincare_tfm.login.LoginActivity
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MainActivityLogoutTest {

    private lateinit var activity: MainActivity
    private lateinit var mockSessionManager: SessionManager
    private lateinit var mockFirebaseDataManager: FirebaseDataManager

    @Before
    fun setup() {
        // Mock SessionManager singleton
        mockSessionManager = mockk()
        mockkObject(SessionManager.Companion)
        every { SessionManager.getInstance(any()) } returns mockSessionManager

        // Mock FirebaseDataManager
        mockFirebaseDataManager = mockk()

        // Create activity
        activity = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .get()

        // Replace the firebaseDataManager with our mock
        val field = MainActivity::class.java.getDeclaredField("firebaseDataManager")
        field.isAccessible = true
        field.set(activity, mockFirebaseDataManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `logout should clear session and sign out from Firebase`() = runTest {
        // Arrange
        coEvery { mockSessionManager.clearSession() } returns true
        every { mockFirebaseDataManager.signOut() } just Runs

        // Act
        val logoutMethod = MainActivity::class.java.getDeclaredMethod("logout")
        logoutMethod.isAccessible = true
        logoutMethod.invoke(activity)

        // Wait for coroutine to complete
        Thread.sleep(100)

        // Assert
        coVerify { mockSessionManager.clearSession() }
        verify { mockFirebaseDataManager.signOut() }
    }

    @Test
    fun `logout should navigate to LoginActivity after successful logout`() = runTest {
        // Arrange
        coEvery { mockSessionManager.clearSession() } returns true
        every { mockFirebaseDataManager.signOut() } just Runs

        // Act
        val logoutMethod = MainActivity::class.java.getDeclaredMethod("logout")
        logoutMethod.isAccessible = true
        logoutMethod.invoke(activity)

        // Wait for coroutine and UI thread operations
        Thread.sleep(200)

        // Assert
        val shadowActivity = shadowOf(activity)
        val nextStartedActivity = shadowActivity.nextStartedActivity
        
        assert(nextStartedActivity != null)
        assert(nextStartedActivity.component?.className == LoginActivity::class.java.name)
        assert(nextStartedActivity.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assert(nextStartedActivity.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        assert(activity.isFinishing)
    }

    @Test
    fun `logout should handle session clear failure gracefully`() = runTest {
        // Arrange
        coEvery { mockSessionManager.clearSession() } returns false
        every { mockFirebaseDataManager.signOut() } just Runs

        // Act
        val logoutMethod = MainActivity::class.java.getDeclaredMethod("logout")
        logoutMethod.isAccessible = true
        logoutMethod.invoke(activity)

        // Wait for coroutine to complete
        Thread.sleep(200)

        // Assert - should still sign out from Firebase and navigate
        coVerify { mockSessionManager.clearSession() }
        verify { mockFirebaseDataManager.signOut() }
        
        val shadowActivity = shadowOf(activity)
        val nextStartedActivity = shadowActivity.nextStartedActivity
        assert(nextStartedActivity != null)
        assert(nextStartedActivity.component?.className == LoginActivity::class.java.name)
    }

    @Test
    fun `logout should handle session manager exception gracefully`() = runTest {
        // Arrange
        coEvery { mockSessionManager.clearSession() } throws Exception("Session clear failed")
        every { mockFirebaseDataManager.signOut() } just Runs

        // Act
        val logoutMethod = MainActivity::class.java.getDeclaredMethod("logout")
        logoutMethod.isAccessible = true
        logoutMethod.invoke(activity)

        // Wait for coroutine to complete
        Thread.sleep(200)

        // Assert - should still sign out from Firebase and navigate
        coVerify { mockSessionManager.clearSession() }
        verify { mockFirebaseDataManager.signOut() }
        
        val shadowActivity = shadowOf(activity)
        val nextStartedActivity = shadowActivity.nextStartedActivity
        assert(nextStartedActivity != null)
        assert(nextStartedActivity.component?.className == LoginActivity::class.java.name)
    }

    @Test
    fun `logout should handle Firebase signOut exception gracefully`() = runTest {
        // Arrange
        coEvery { mockSessionManager.clearSession() } returns true
        every { mockFirebaseDataManager.signOut() } throws Exception("Firebase signout failed")

        // Act
        val logoutMethod = MainActivity::class.java.getDeclaredMethod("logout")
        logoutMethod.isAccessible = true
        logoutMethod.invoke(activity)

        // Wait for coroutine to complete
        Thread.sleep(200)

        // Assert - should still navigate to login
        coVerify { mockSessionManager.clearSession() }
        verify { mockFirebaseDataManager.signOut() }
        
        val shadowActivity = shadowOf(activity)
        val nextStartedActivity = shadowActivity.nextStartedActivity
        assert(nextStartedActivity != null)
        assert(nextStartedActivity.component?.className == LoginActivity::class.java.name)
    }

    @Test
    fun `navigateToLogin should create intent with correct flags`() {
        // Act
        val navigateToLoginMethod = MainActivity::class.java.getDeclaredMethod("navigateToLogin")
        navigateToLoginMethod.isAccessible = true
        navigateToLoginMethod.invoke(activity)

        // Assert
        val shadowActivity = shadowOf(activity)
        val nextStartedActivity = shadowActivity.nextStartedActivity
        
        assert(nextStartedActivity != null)
        assert(nextStartedActivity.component?.className == LoginActivity::class.java.name)
        assert(nextStartedActivity.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assert(nextStartedActivity.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        assert(activity.isFinishing)
    }
}
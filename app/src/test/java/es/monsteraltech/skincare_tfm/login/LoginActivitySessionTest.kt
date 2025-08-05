package es.monsteraltech.skincare_tfm.login
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import es.monsteraltech.skincare_tfm.data.SessionManager
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LoginActivitySessionTest {
    @MockK
    private lateinit var mockFirebaseAuth: FirebaseAuth
    @MockK
    private lateinit var mockFirebaseUser: FirebaseUser
    @MockK
    private lateinit var mockSessionManager: SessionManager
    @RelaxedMockK
    private lateinit var mockFirebaseDataManager: FirebaseDataManager
    private lateinit var context: Context
    private lateinit var activity: LoginActivity
    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        context = ApplicationProvider.getApplicationContext()
        mockkObject(SessionManager.Companion)
        every { SessionManager.getInstance(any()) } returns mockSessionManager
        mockkStatic(FirebaseAuth::class)
        every { FirebaseAuth.getInstance() } returns mockFirebaseAuth
        every { mockFirebaseUser.uid } returns "test-user-id"
        every { mockFirebaseUser.email } returns "test@example.com"
        every { mockFirebaseUser.displayName } returns "Test User"
        activity = Robolectric.buildActivity(LoginActivity::class.java).create().get()
    }
    @After
    fun tearDown() {
        unmockkAll()
    }
    @Test
    fun `initializeUserSettingsAndNavigate should save session successfully after email login`() = runTest {
        coEvery { mockSessionManager.saveSession(mockFirebaseUser) } returns true
        coEvery { mockFirebaseDataManager.initializeUserSettings("test-user-id") } returns Unit
        val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
        method.isAccessible = true
        method.invoke(activity, mockFirebaseUser)
        Thread.sleep(100)
        coVerify(exactly = 1) { mockSessionManager.saveSession(mockFirebaseUser) }
        coVerify(exactly = 1) { mockFirebaseDataManager.initializeUserSettings("test-user-id") }
    }
    @Test
    fun `initializeUserSettingsAndNavigate should continue without persistence when session save fails`() = runTest {
        coEvery { mockSessionManager.saveSession(mockFirebaseUser) } returns false
        coEvery { mockFirebaseDataManager.initializeUserSettings("test-user-id") } returns Unit
        val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
        method.isAccessible = true
        method.invoke(activity, mockFirebaseUser)
        Thread.sleep(100)
        coVerify(exactly = 1) { mockSessionManager.saveSession(mockFirebaseUser) }
        coVerify(exactly = 1) { mockFirebaseDataManager.initializeUserSettings("test-user-id") }
    }
    @Test
    fun `initializeUserSettingsAndNavigate should handle session save exception gracefully`() = runTest {
        coEvery { mockSessionManager.saveSession(mockFirebaseUser) } throws RuntimeException("Session save error")
        coEvery { mockFirebaseDataManager.initializeUserSettings("test-user-id") } returns Unit
        val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
        method.isAccessible = true
        method.invoke(activity, mockFirebaseUser)
        Thread.sleep(100)
        coVerify(exactly = 1) { mockSessionManager.saveSession(mockFirebaseUser) }
        coVerify(exactly = 1) { mockFirebaseDataManager.initializeUserSettings("test-user-id") }
    }
    @Test
    fun `initializeUserSettingsAndNavigate should save session even when user settings initialization fails`() = runTest {
        coEvery { mockSessionManager.saveSession(mockFirebaseUser) } returns true
        coEvery { mockFirebaseDataManager.initializeUserSettings("test-user-id") } throws RuntimeException("Settings error")
        val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
        method.isAccessible = true
        method.invoke(activity, mockFirebaseUser)
        Thread.sleep(100)
        coVerify(exactly = 1) { mockSessionManager.saveSession(mockFirebaseUser) }
        coVerify(exactly = 1) { mockFirebaseDataManager.initializeUserSettings("test-user-id") }
    }
    @Test
    fun `initializeUserSettingsAndNavigate should not save session when user is null`() = runTest {
        val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
        method.isAccessible = true
        method.invoke(activity, null)
        Thread.sleep(100)
        coVerify(exactly = 0) { mockSessionManager.saveSession(any()) }
        coVerify(exactly = 0) { mockFirebaseDataManager.initializeUserSettings(any()) }
    }
    @Test
    fun `SessionManager should be initialized correctly in onCreate`() {
        verify(exactly = 1) { SessionManager.getInstance(any()) }
    }
    @Test
    fun `session save should be called with correct user data`() = runTest {
        val specificUser = mockk<FirebaseUser>()
        every { specificUser.uid } returns "specific-user-id"
        every { specificUser.email } returns "specific@example.com"
        every { specificUser.displayName } returns "Specific User"
        coEvery { mockSessionManager.saveSession(specificUser) } returns true
        coEvery { mockFirebaseDataManager.initializeUserSettings("specific-user-id") } returns Unit
        val method = activity.javaClass.getDeclaredMethod("initializeUserSettingsAndNavigate", FirebaseUser::class.java)
        method.isAccessible = true
        method.invoke(activity, specificUser)
        Thread.sleep(100)
        coVerify(exactly = 1) { mockSessionManager.saveSession(specificUser) }
        coVerify(exactly = 1) { mockFirebaseDataManager.initializeUserSettings("specific-user-id") }
    }
}
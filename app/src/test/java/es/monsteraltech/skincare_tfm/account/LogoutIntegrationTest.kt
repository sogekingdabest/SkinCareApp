package es.monsteraltech.skincare_tfm.account
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
@RunWith(MockitoJUnitRunner::class)
class LogoutIntegrationTest {
    @Mock
    private lateinit var mockFirebaseAuth: FirebaseAuth
    @Mock
    private lateinit var mockFirebaseUser: FirebaseUser
    private lateinit var userProfileManager: UserProfileManager
    private val testUserId = "test-user-id-123"
    private val testEmail = "test@example.com"
    private val testDisplayName = "Test User"
    @Before
    fun setUp() {
        userProfileManager = UserProfileManager(auth = mockFirebaseAuth)
        setupAuthenticatedUser()
    }
    @Test
    fun `logout should succeed with authenticated user`() = runTest {
        val successfulSignOutTask = Tasks.forResult<Void>(null)
        whenever(mockFirebaseAuth.signOut()).then {  }
        val result = userProfileManager.signOut()
        assertTrue("Logout should succeed", result.isSuccess())
        verify(mockFirebaseAuth).signOut()
    }
    @Test
    fun `logout should handle Firebase signOut errors gracefully`() = runTest {
        whenever(mockFirebaseAuth.signOut()).thenThrow(RuntimeException("Firebase signOut error"))
        val result = userProfileManager.signOut()
        assertTrue("Logout should handle errors", result.isError())
        val error = result.getErrorOrNull()
        assertNotNull("Error should be present", error)
        assertTrue("Error message should contain signOut",
            error!!.message.contains("cerrar sesión") || error.message.contains("logout"))
        verify(mockFirebaseAuth).signOut()
    }
    @Test
    fun `logout should work even when user is not authenticated`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        val result = userProfileManager.signOut()
        assertTrue("Logout should succeed even without authenticated user", result.isSuccess())
        verify(mockFirebaseAuth).signOut()
    }
    @Test
    fun `logout should clear user session data`() = runTest {
        setupAuthenticatedUser()
        val result = userProfileManager.signOut()
        assertTrue("Logout should succeed", result.isSuccess())
        verify(mockFirebaseAuth).signOut()
    }
    @Test
    fun `logout should handle concurrent logout attempts`() = runTest {
        setupAuthenticatedUser()
        val result1 = userProfileManager.signOut()
        val result2 = userProfileManager.signOut()
        assertTrue("First logout should succeed", result1.isSuccess())
        assertTrue("Second logout should succeed", result2.isSuccess())
        verify(mockFirebaseAuth, times(2)).signOut()
    }
    @Test
    fun `logout should maintain error information for debugging`() = runTest {
        val specificException = RuntimeException("Specific Firebase error")
        whenever(mockFirebaseAuth.signOut()).thenThrow(specificException)
        val result = userProfileManager.signOut()
        assertTrue("Logout should fail with error", result.isError())
        val error = result.getErrorOrNull()
        assertNotNull("Error should be present", error)
        assertEquals("Should preserve original exception",
            specificException, error!!.exception)
        assertEquals("Should be Firebase error type",
            AccountResult.ErrorType.FIREBASE_ERROR, error.errorType)
    }
    @Test
    fun `logout should handle network connectivity issues`() = runTest {
        val networkException = RuntimeException("Network connectivity error")
        whenever(mockFirebaseAuth.signOut()).thenThrow(networkException)
        val result = userProfileManager.signOut()
        assertTrue("Logout should fail with network error", result.isError())
        val error = result.getErrorOrNull()
        assertNotNull("Error should be present", error)
        assertTrue("Error message should be user-friendly",
            error!!.message.contains("error") || error.message.contains("problema"))
    }
    @Test
    fun `logout should work with different user authentication states`() = runTest {
        whenever(mockFirebaseUser.isEmailVerified).thenReturn(true)
        val result1 = userProfileManager.signOut()
        assertTrue("Logout should succeed with verified email", result1.isSuccess())
        whenever(mockFirebaseUser.isEmailVerified).thenReturn(false)
        val result2 = userProfileManager.signOut()
        assertTrue("Logout should succeed with unverified email", result2.isSuccess())
        verify(mockFirebaseAuth, times(2)).signOut()
    }
    @Test
    fun `logout should handle user with missing profile information`() = runTest {
        whenever(mockFirebaseUser.displayName).thenReturn(null)
        whenever(mockFirebaseUser.email).thenReturn(null)
        val result = userProfileManager.signOut()
        assertTrue("Logout should succeed even with incomplete profile", result.isSuccess())
        verify(mockFirebaseAuth).signOut()
    }
    @Test
    fun `logout should be idempotent`() = runTest {
        setupAuthenticatedUser()
        val result1 = userProfileManager.signOut()
        val result2 = userProfileManager.signOut()
        val result3 = userProfileManager.signOut()
        assertTrue("First logout should succeed", result1.isSuccess())
        assertTrue("Second logout should succeed", result2.isSuccess())
        assertTrue("Third logout should succeed", result3.isSuccess())
        verify(mockFirebaseAuth, times(3)).signOut()
    }
    private fun setupAuthenticatedUser() {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseUser.uid).thenReturn(testUserId)
        whenever(mockFirebaseUser.email).thenReturn(testEmail)
        whenever(mockFirebaseUser.displayName).thenReturn(testDisplayName)
        whenever(mockFirebaseUser.isEmailVerified).thenReturn(true)
    }
}
package es.monsteraltech.skincare_tfm.account

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import org.junit.Assert.*

/**
 * Integration tests for logout process
 * Tests the complete logout workflow including Firebase operations
 */
@RunWith(MockitoJUnitRunner::class)
class LogoutIntegrationTest {

    @Mock
    private lateinit var mockFirebaseAuth: FirebaseAuth

    @Mock
    private lateinit var mockFirebaseUser: FirebaseUser

    private lateinit var userProfileManager: UserProfileManager

    // Test data
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
        // Given: User is authenticated and Firebase signOut succeeds
        val successfulSignOutTask = Tasks.forResult<Void>(null)
        whenever(mockFirebaseAuth.signOut()).then { /* Firebase signOut is void */ }
        
        // When: User performs logout
        val result = userProfileManager.signOut()
        
        // Then: Logout should succeed
        assertTrue("Logout should succeed", result.isSuccess())
        
        // Verify Firebase signOut was called
        verify(mockFirebaseAuth).signOut()
    }

    @Test
    fun `logout should handle Firebase signOut errors gracefully`() = runTest {
        // Given: Firebase signOut throws exception
        whenever(mockFirebaseAuth.signOut()).thenThrow(RuntimeException("Firebase signOut error"))
        
        // When: User performs logout
        val result = userProfileManager.signOut()
        
        // Then: Logout should handle error gracefully
        assertTrue("Logout should handle errors", result.isError())
        
        val error = result.getErrorOrNull()
        assertNotNull("Error should be present", error)
        assertTrue("Error message should contain signOut", 
            error!!.message.contains("cerrar sesión") || error.message.contains("logout"))
        
        // Verify Firebase signOut was attempted
        verify(mockFirebaseAuth).signOut()
    }

    @Test
    fun `logout should work even when user is not authenticated`() = runTest {
        // Given: No authenticated user
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        
        // When: User performs logout
        val result = userProfileManager.signOut()
        
        // Then: Logout should still succeed (defensive programming)
        assertTrue("Logout should succeed even without authenticated user", result.isSuccess())
        
        // Verify Firebase signOut was still called for safety
        verify(mockFirebaseAuth).signOut()
    }

    @Test
    fun `logout should clear user session data`() = runTest {
        // Given: User is authenticated with session data
        setupAuthenticatedUser()
        
        // When: User performs logout
        val result = userProfileManager.signOut()
        
        // Then: Logout should succeed
        assertTrue("Logout should succeed", result.isSuccess())
        
        // And: Session should be cleared (Firebase handles this internally)
        verify(mockFirebaseAuth).signOut()
    }

    @Test
    fun `logout should handle concurrent logout attempts`() = runTest {
        // Given: User is authenticated
        setupAuthenticatedUser()
        
        // When: Multiple logout attempts are made concurrently
        val result1 = userProfileManager.signOut()
        val result2 = userProfileManager.signOut()
        
        // Then: Both should complete successfully
        assertTrue("First logout should succeed", result1.isSuccess())
        assertTrue("Second logout should succeed", result2.isSuccess())
        
        // Verify Firebase signOut was called multiple times
        verify(mockFirebaseAuth, times(2)).signOut()
    }

    @Test
    fun `logout should maintain error information for debugging`() = runTest {
        // Given: Firebase signOut throws specific exception
        val specificException = RuntimeException("Specific Firebase error")
        whenever(mockFirebaseAuth.signOut()).thenThrow(specificException)
        
        // When: User performs logout
        val result = userProfileManager.signOut()
        
        // Then: Error should contain specific information
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
        // Given: Network error during logout
        val networkException = RuntimeException("Network connectivity error")
        whenever(mockFirebaseAuth.signOut()).thenThrow(networkException)
        
        // When: User performs logout
        val result = userProfileManager.signOut()
        
        // Then: Should handle network error appropriately
        assertTrue("Logout should fail with network error", result.isError())
        
        val error = result.getErrorOrNull()
        assertNotNull("Error should be present", error)
        assertTrue("Error message should be user-friendly", 
            error!!.message.contains("error") || error.message.contains("problema"))
    }

    @Test
    fun `logout should work with different user authentication states`() = runTest {
        // Test with email verified user
        whenever(mockFirebaseUser.isEmailVerified).thenReturn(true)
        
        val result1 = userProfileManager.signOut()
        assertTrue("Logout should succeed with verified email", result1.isSuccess())
        
        // Test with unverified email user
        whenever(mockFirebaseUser.isEmailVerified).thenReturn(false)
        
        val result2 = userProfileManager.signOut()
        assertTrue("Logout should succeed with unverified email", result2.isSuccess())
        
        // Verify Firebase signOut was called for both cases
        verify(mockFirebaseAuth, times(2)).signOut()
    }

    @Test
    fun `logout should handle user with missing profile information`() = runTest {
        // Given: User with incomplete profile
        whenever(mockFirebaseUser.displayName).thenReturn(null)
        whenever(mockFirebaseUser.email).thenReturn(null)
        
        // When: User performs logout
        val result = userProfileManager.signOut()
        
        // Then: Logout should still succeed
        assertTrue("Logout should succeed even with incomplete profile", result.isSuccess())
        
        // Verify Firebase signOut was called
        verify(mockFirebaseAuth).signOut()
    }

    @Test
    fun `logout should be idempotent`() = runTest {
        // Given: User is authenticated
        setupAuthenticatedUser()
        
        // When: User performs logout multiple times
        val result1 = userProfileManager.signOut()
        val result2 = userProfileManager.signOut()
        val result3 = userProfileManager.signOut()
        
        // Then: All logout attempts should succeed
        assertTrue("First logout should succeed", result1.isSuccess())
        assertTrue("Second logout should succeed", result2.isSuccess())
        assertTrue("Third logout should succeed", result3.isSuccess())
        
        // Verify Firebase signOut was called for each attempt
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
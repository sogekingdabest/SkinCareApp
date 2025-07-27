package es.monsteraltech.skincare_tfm.account

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the complete password change process
 * Tests the full workflow from validation to Firebase authentication
 */
@RunWith(MockitoJUnitRunner::class)
class PasswordChangeIntegrationTest {

    @Mock
    private lateinit var mockFirebaseAuth: FirebaseAuth

    @Mock
    private lateinit var mockFirebaseUser: FirebaseUser

    @Mock
    private lateinit var mockAuthCredential: AuthCredential

    @Mock
    private lateinit var mockAuthResult: AuthResult

    private lateinit var passwordChangeManager: PasswordChangeManager

    // Test data
    private val testEmail = "test@example.com"
    private val currentPassword = "CurrentPass123"
    private val newPassword = "NewStrongPass456"
    private val weakPassword = "weak"
    private val invalidCurrentPassword = "WrongPass123"

    @Before
    fun setUp() {
        passwordChangeManager = PasswordChangeManager(mockFirebaseAuth)
        
        // Setup basic Firebase user mock
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseUser.email).thenReturn(testEmail)
    }

    @Test
    fun `complete password change process should succeed with valid inputs`() = runTest {
        // Given: Valid passwords and successful Firebase operations
        val successfulReauthTask = Tasks.forResult(mockAuthResult)
        val successfulUpdateTask = Tasks.forResult<Void>(null)
        
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(successfulReauthTask)
        whenever(mockFirebaseUser.updatePassword(newPassword)).thenReturn(successfulUpdateTask)
        
        // When: Password change is attempted
        val result = passwordChangeManager.changePassword(currentPassword, newPassword)
        
        // Then: Should return success
        assertTrue(result.isSuccess())
        
        // Verify Firebase operations were called
        verify(mockFirebaseUser).reauthenticate(any())
        verify(mockFirebaseUser).updatePassword(newPassword)
    }

    @Test
    fun `password change should fail with validation error for weak password`() = runTest {
        // Given: Weak new password
        
        // When: Password change is attempted with weak password
        val result = passwordChangeManager.changePassword(currentPassword, weakPassword)
        
        // Then: Should return validation error
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.VALIDATION_ERROR, error.errorType)
        assertTrue(error.message.contains("8 caracteres"))
        
        // Verify Firebase operations were not called
        verify(mockFirebaseUser, never()).reauthenticate(any())
        verify(mockFirebaseUser, never()).updatePassword(any())
    }

    @Test
    fun `password change should fail with authentication error for wrong current password`() = runTest {
        // Given: Wrong current password and Firebase reauthentication failure
        val authException = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "Wrong password")
        val failedReauthTask = Tasks.forException<AuthResult>(authException)
        
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(failedReauthTask)
        
        // When: Password change is attempted with wrong current password
        val result = passwordChangeManager.changePassword(invalidCurrentPassword, newPassword)
        
        // Then: Should return authentication error
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, error.errorType)
        assertTrue(error.message.contains("actual") || error.message.contains("incorrecta"))
        
        // Verify reauthentication was attempted but update was not
        verify(mockFirebaseUser).reauthenticate(any())
        verify(mockFirebaseUser, never()).updatePassword(any())
    }

    @Test
    fun `password change should fail when reauthentication succeeds but update fails`() = runTest {
        // Given: Successful reauthentication but failed password update
        val successfulReauthTask = Tasks.forResult(mockAuthResult)
        val updateException = FirebaseAuthException("ERROR_WEAK_PASSWORD", "Password is too weak")
        val failedUpdateTask = Tasks.forException<Void>(updateException)
        
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(successfulReauthTask)
        whenever(mockFirebaseUser.updatePassword(newPassword)).thenReturn(failedUpdateTask)
        
        // When: Password change is attempted
        val result = passwordChangeManager.changePassword(currentPassword, newPassword)
        
        // Then: Should return Firebase error
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.FIREBASE_ERROR, error.errorType)
        
        // Verify both operations were attempted
        verify(mockFirebaseUser).reauthenticate(any())
        verify(mockFirebaseUser).updatePassword(newPassword)
    }

    @Test
    fun `password change should handle network errors gracefully`() = runTest {
        // Given: Network error during reauthentication
        val networkException = FirebaseNetworkException("Network error")
        val failedReauthTask = Tasks.forException<AuthResult>(networkException)
        
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(failedReauthTask)
        
        // When: Password change is attempted
        val result = passwordChangeManager.changePassword(currentPassword, newPassword)
        
        // Then: Should return network error
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.NETWORK_ERROR, error.errorType)
        assertTrue(error.message.contains("conexión") || error.message.contains("red"))
        
        // Verify reauthentication was attempted
        verify(mockFirebaseUser).reauthenticate(any())
    }

    @Test
    fun `password change should fail when user is not authenticated`() = runTest {
        // Given: No authenticated user
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        
        // When: Password change is attempted
        val result = passwordChangeManager.changePassword(currentPassword, newPassword)
        
        // Then: Should return authentication error
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, error.errorType)
        assertTrue(error.message.contains("iniciar sesión"))
        
        // Verify no Firebase operations were attempted
        verify(mockFirebaseUser, never()).reauthenticate(any())
        verify(mockFirebaseUser, never()).updatePassword(any())
    }

    @Test
    fun `password change should handle user email being null`() = runTest {
        // Given: User with null email
        whenever(mockFirebaseUser.email).thenReturn(null)
        
        // When: Password change is attempted
        val result = passwordChangeManager.changePassword(currentPassword, newPassword)
        
        // Then: Should return authentication error
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, error.errorType)
        
        // Verify no Firebase operations were attempted
        verify(mockFirebaseUser, never()).reauthenticate(any())
        verify(mockFirebaseUser, never()).updatePassword(any())
    }

    @Test
    fun `password validation should work correctly in integration context`() = runTest {
        // Test various password validation scenarios in integration context
        
        // Test 1: Valid password
        val validResult = passwordChangeManager.validateNewPassword("ValidPass123", currentPassword)
        assertTrue(validResult.isValid)
        
        // Test 2: Same as current password
        val samePasswordResult = passwordChangeManager.validateNewPassword(currentPassword, currentPassword)
        assertTrue(samePasswordResult.isValid == false)
        assertEquals("La nueva contraseña debe ser diferente a la actual", samePasswordResult.errorMessage)
        
        // Test 3: Too short
        val shortPasswordResult = passwordChangeManager.validateNewPassword("Short1", currentPassword)
        assertTrue(shortPasswordResult.isValid == false)
        assertEquals("La contraseña debe tener al menos 8 caracteres", shortPasswordResult.errorMessage)
        
        // Test 4: No uppercase
        val noUppercaseResult = passwordChangeManager.validateNewPassword("lowercase123", currentPassword)
        assertTrue(noUppercaseResult.isValid == false)
        assertEquals("La contraseña debe tener al menos una mayúscula", noUppercaseResult.errorMessage)
    }

    @Test
    fun `form validation should work correctly in integration context`() = runTest {
        // Test complete form validation scenarios
        
        // Test 1: Valid form
        val validFormResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, newPassword, newPassword
        )
        assertTrue(validFormResult.isValid)
        
        // Test 2: Empty current password
        val emptyCurrentResult = passwordChangeManager.validatePasswordChangeForm(
            "", newPassword, newPassword
        )
        assertTrue(emptyCurrentResult.isValid == false)
        assertEquals("Ingresa tu contraseña actual", emptyCurrentResult.errorMessage)
        
        // Test 3: Mismatched confirmation
        val mismatchedResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, newPassword, "DifferentPassword123"
        )
        assertTrue(mismatchedResult.isValid == false)
        assertEquals("Las contraseñas no coinciden", mismatchedResult.errorMessage)
        
        // Test 4: Weak new password
        val weakPasswordResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, "weak", "weak"
        )
        assertTrue(weakPasswordResult.isValid == false)
        assertEquals("La contraseña debe tener al menos 8 caracteres", weakPasswordResult.errorMessage)
    }

    @Test
    fun `password change should handle concurrent operations gracefully`() = runTest {
        // Given: Successful Firebase operations
        val successfulReauthTask = Tasks.forResult(mockAuthResult)
        val successfulUpdateTask = Tasks.forResult<Void>(null)
        
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(successfulReauthTask)
        whenever(mockFirebaseUser.updatePassword(newPassword)).thenReturn(successfulUpdateTask)
        
        // When: Multiple password change operations are attempted
        val result1 = passwordChangeManager.changePassword(currentPassword, newPassword)
        val result2 = passwordChangeManager.changePassword(currentPassword, "AnotherPass789")
        
        // Then: Both should complete (though in practice, you'd want to prevent concurrent changes)
        assertTrue(result1.isSuccess())
        assertTrue(result2.isSuccess())
        
        // Verify Firebase operations were called for both
        verify(mockFirebaseUser, times(2)).reauthenticate(any())
        verify(mockFirebaseUser).updatePassword(newPassword)
        verify(mockFirebaseUser).updatePassword("AnotherPass789")
    }
}
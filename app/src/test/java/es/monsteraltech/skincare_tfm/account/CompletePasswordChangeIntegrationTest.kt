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
import org.junit.Assert.*

/**
 * Complete integration tests for password change process
 * Tests the full workflow from UI validation to Firebase operations
 */
@RunWith(MockitoJUnitRunner::class)
class CompletePasswordChangeIntegrationTest {

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
        passwordChangeManager = PasswordChangeManager()
        
        // Setup basic Firebase user mock
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseUser.email).thenReturn(testEmail)
    }

    @Test
    fun `complete password change flow should succeed with valid inputs`() = runTest {
        // Given: Valid passwords and successful Firebase operations
        val successfulReauthTask = Tasks.forResult(mockAuthResult)
        val successfulUpdateTask = Tasks.forResult<Void>(null)
        
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(successfulReauthTask)
        whenever(mockFirebaseUser.updatePassword(newPassword)).thenReturn(successfulUpdateTask)
        
        // When: Complete password change process is executed
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, newPassword, newPassword
        )
        
        // Then: Validation should pass
        assertTrue("Form validation should pass", validationResult.isValid)
        
        // And: Password change should succeed
        val changeResult = passwordChangeManager.changePassword(currentPassword, newPassword)
        assertTrue("Password change should succeed", changeResult.isSuccess())
        
        // Verify all Firebase operations were called
        verify(mockFirebaseUser).reauthenticate(any())
        verify(mockFirebaseUser).updatePassword(newPassword)
    }

    @Test
    fun `complete flow should fail at validation stage for weak password`() = runTest {
        // Given: Weak new password
        
        // When: Complete password change process is executed
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, weakPassword, weakPassword
        )
        
        // Then: Validation should fail
        assertFalse("Form validation should fail for weak password", validationResult.isValid)
        assertEquals("Should show weak password error", 
            "La contraseña debe tener al menos 8 caracteres", validationResult.errorMessage)
        
        // And: Password change should not be attempted
        val changeResult = passwordChangeManager.changePassword(currentPassword, weakPassword)
        assertTrue("Password change should fail at validation", changeResult.isError())
        
        // Verify Firebase operations were not called
        verify(mockFirebaseUser, never()).reauthenticate(any())
        verify(mockFirebaseUser, never()).updatePassword(any())
    }

    @Test
    fun `complete flow should fail at Firebase authentication stage`() = runTest {
        // Given: Valid form but wrong current password
        val authException = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "Wrong password")
        val failedReauthTask = Tasks.forException<AuthResult>(authException)
        
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(failedReauthTask)
        
        // When: Complete password change process is executed
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            invalidCurrentPassword, newPassword, newPassword
        )
        
        // Then: Validation should pass
        assertTrue("Form validation should pass", validationResult.isValid)
        
        // But: Password change should fail at Firebase stage
        val changeResult = passwordChangeManager.changePassword(invalidCurrentPassword, newPassword)
        assertTrue("Password change should fail at Firebase auth", changeResult.isError())
        
        val error = changeResult.getErrorOrNull()
        assertNotNull("Error should be present", error)
        assertEquals("Should be authentication error", 
            AccountResult.ErrorType.AUTHENTICATION_ERROR, error!!.errorType)
        
        // Verify reauthentication was attempted but update was not
        verify(mockFirebaseUser).reauthenticate(any())
        verify(mockFirebaseUser, never()).updatePassword(any())
    }

    @Test
    fun `complete flow should handle Firebase update failure after successful reauthentication`() = runTest {
        // Given: Successful reauthentication but failed password update
        val successfulReauthTask = Tasks.forResult(mockAuthResult)
        val updateException = FirebaseAuthException("ERROR_WEAK_PASSWORD", "Password is too weak")
        val failedUpdateTask = Tasks.forException<Void>(updateException)
        
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(successfulReauthTask)
        whenever(mockFirebaseUser.updatePassword(newPassword)).thenReturn(failedUpdateTask)
        
        // When: Complete password change process is executed
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, newPassword, newPassword
        )
        
        // Then: Validation should pass
        assertTrue("Form validation should pass", validationResult.isValid)
        
        // But: Password change should fail at Firebase update stage
        val changeResult = passwordChangeManager.changePassword(currentPassword, newPassword)
        assertTrue("Password change should fail at Firebase update", changeResult.isError())
        
        val error = changeResult.getErrorOrNull()
        assertNotNull("Error should be present", error)
        assertEquals("Should be Firebase error", 
            AccountResult.ErrorType.FIREBASE_ERROR, error!!.errorType)
        
        // Verify both operations were attempted
        verify(mockFirebaseUser).reauthenticate(any())
        verify(mockFirebaseUser).updatePassword(newPassword)
    }

    @Test
    fun `complete flow should handle network errors gracefully`() = runTest {
        // Given: Network error during reauthentication
        val networkException = FirebaseNetworkException("Network error")
        val failedReauthTask = Tasks.forException<AuthResult>(networkException)
        
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(failedReauthTask)
        
        // When: Complete password change process is executed
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, newPassword, newPassword
        )
        
        // Then: Validation should pass
        assertTrue("Form validation should pass", validationResult.isValid)
        
        // But: Password change should fail with network error
        val changeResult = passwordChangeManager.changePassword(currentPassword, newPassword)
        assertTrue("Password change should fail with network error", changeResult.isError())
        
        val error = changeResult.getErrorOrNull()
        assertNotNull("Error should be present", error)
        assertEquals("Should be network error", 
            AccountResult.ErrorType.NETWORK_ERROR, error!!.errorType)
        assertTrue("Should contain network error message", 
            error.message.contains("conexión") || error.message.contains("red"))
        
        // Verify reauthentication was attempted
        verify(mockFirebaseUser).reauthenticate(any())
    }

    @Test
    fun `complete flow should fail when user is not authenticated`() = runTest {
        // Given: No authenticated user
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        
        // When: Complete password change process is executed
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, newPassword, newPassword
        )
        
        // Then: Validation should pass (it doesn't check authentication)
        assertTrue("Form validation should pass", validationResult.isValid)
        
        // But: Password change should fail due to no authentication
        val changeResult = passwordChangeManager.changePassword(currentPassword, newPassword)
        assertTrue("Password change should fail without authentication", changeResult.isError())
        
        val error = changeResult.getErrorOrNull()
        assertNotNull("Error should be present", error)
        assertEquals("Should be authentication error", 
            AccountResult.ErrorType.AUTHENTICATION_ERROR, error!!.errorType)
        assertTrue("Should contain login message", 
            error.message.contains("iniciar sesión"))
        
        // Verify no Firebase operations were attempted
        verify(mockFirebaseUser, never()).reauthenticate(any())
        verify(mockFirebaseUser, never()).updatePassword(any())
    }

    @Test
    fun `complete flow should validate password confirmation mismatch`() = runTest {
        // Given: Mismatched password confirmation
        val mismatchedConfirmation = "DifferentPassword123"
        
        // When: Complete password change process is executed
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, newPassword, mismatchedConfirmation
        )
        
        // Then: Validation should fail
        assertFalse("Form validation should fail for mismatched passwords", validationResult.isValid)
        assertEquals("Should show mismatch error", 
            "Las contraseñas no coinciden", validationResult.errorMessage)
        
        // And: Password change should not be attempted
        val changeResult = passwordChangeManager.changePassword(currentPassword, newPassword)
        assertTrue("Password change should succeed if called directly", changeResult.isSuccess())
        
        // But in a real UI flow, the validation failure would prevent the change attempt
    }

    @Test
    fun `complete flow should handle empty form fields`() = runTest {
        // Given: Empty form fields
        val emptyPassword = ""
        
        // When: Form validation is performed
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            emptyPassword, newPassword, newPassword
        )
        
        // Then: Validation should fail
        assertFalse("Form validation should fail for empty current password", validationResult.isValid)
        assertEquals("Should show empty field error", 
            "Ingresa tu contraseña actual", validationResult.errorMessage)
        
        // Test empty new password
        val newPasswordValidation = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, emptyPassword, emptyPassword
        )
        
        assertFalse("Form validation should fail for empty new password", newPasswordValidation.isValid)
        assertEquals("Should show empty new password error", 
            "La nueva contraseña no puede estar vacía", newPasswordValidation.errorMessage)
    }

    @Test
    fun `complete flow should handle same current and new password`() = runTest {
        // Given: Same current and new password
        
        // When: Form validation is performed
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, currentPassword, currentPassword
        )
        
        // Then: Validation should fail
        assertFalse("Form validation should fail for same passwords", validationResult.isValid)
        assertEquals("Should show same password error", 
            "La nueva contraseña debe ser diferente a la actual", validationResult.errorMessage)
    }

    @Test
    fun `complete flow should handle complex password requirements`() = runTest {
        // Test various password complexity scenarios
        
        // Test password without uppercase
        val noUppercasePassword = "newpassword123"
        val noUppercaseValidation = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, noUppercasePassword, noUppercasePassword
        )
        
        assertFalse("Should fail without uppercase", noUppercaseValidation.isValid)
        assertEquals("Should show uppercase error", 
            "La contraseña debe tener al menos una mayúscula", noUppercaseValidation.errorMessage)
        
        // Test password without lowercase
        val noLowercasePassword = "NEWPASSWORD123"
        val noLowercaseValidation = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, noLowercasePassword, noLowercasePassword
        )
        
        assertFalse("Should fail without lowercase", noLowercaseValidation.isValid)
        assertEquals("Should show lowercase error", 
            "La contraseña debe tener al menos una minúscula", noLowercaseValidation.errorMessage)
        
        // Test password without numbers
        val noNumberPassword = "NewPassword"
        val noNumberValidation = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, noNumberPassword, noNumberPassword
        )
        
        assertFalse("Should fail without numbers", noNumberValidation.isValid)
        assertEquals("Should show number error", 
            "La contraseña debe tener al menos un número", noNumberValidation.errorMessage)
    }
}
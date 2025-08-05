package es.monsteraltech.skincare_tfm.account
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
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
    private val testEmail = "test@example.com"
    private val currentPassword = "CurrentPass123"
    private val newPassword = "NewStrongPass456"
    private val weakPassword = "weak"
    private val invalidCurrentPassword = "WrongPass123"
    @Before
    fun setUp() {
        passwordChangeManager = PasswordChangeManager()
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseUser.email).thenReturn(testEmail)
    }
    @Test
    fun `complete password change flow should succeed with valid inputs`() = runTest {
        val successfulReauthTask = Tasks.forResult(mockAuthResult)
        val successfulUpdateTask = Tasks.forResult<Void>(null)
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(successfulReauthTask)
        whenever(mockFirebaseUser.updatePassword(newPassword)).thenReturn(successfulUpdateTask)
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, newPassword, newPassword
        )
        assertTrue("Form validation should pass", validationResult.isValid)
        val changeResult = passwordChangeManager.changePassword(currentPassword, newPassword)
        assertTrue("Password change should succeed", changeResult.isSuccess())
        verify(mockFirebaseUser).reauthenticate(any())
        verify(mockFirebaseUser).updatePassword(newPassword)
    }
    @Test
    fun `complete flow should fail at validation stage for weak password`() = runTest {
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, weakPassword, weakPassword
        )
        assertFalse("Form validation should fail for weak password", validationResult.isValid)
        assertEquals("Should show weak password error",
            "La contraseña debe tener al menos 8 caracteres", validationResult.errorMessage)
        val changeResult = passwordChangeManager.changePassword(currentPassword, weakPassword)
        assertTrue("Password change should fail at validation", changeResult.isError())
        verify(mockFirebaseUser, never()).reauthenticate(any())
        verify(mockFirebaseUser, never()).updatePassword(any())
    }
    @Test
    fun `complete flow should fail at Firebase authentication stage`() = runTest {
        val authException = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "Wrong password")
        val failedReauthTask = Tasks.forException<AuthResult>(authException)
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(failedReauthTask)
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            invalidCurrentPassword, newPassword, newPassword
        )
        assertTrue("Form validation should pass", validationResult.isValid)
        val changeResult = passwordChangeManager.changePassword(invalidCurrentPassword, newPassword)
        assertTrue("Password change should fail at Firebase auth", changeResult.isError())
        val error = changeResult.getErrorOrNull()
        assertNotNull("Error should be present", error)
        assertEquals("Should be authentication error",
            AccountResult.ErrorType.AUTHENTICATION_ERROR, error!!.errorType)
        verify(mockFirebaseUser).reauthenticate(any())
        verify(mockFirebaseUser, never()).updatePassword(any())
    }
    @Test
    fun `complete flow should handle Firebase update failure after successful reauthentication`() = runTest {
        val successfulReauthTask = Tasks.forResult(mockAuthResult)
        val updateException = FirebaseAuthException("ERROR_WEAK_PASSWORD", "Password is too weak")
        val failedUpdateTask = Tasks.forException<Void>(updateException)
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(successfulReauthTask)
        whenever(mockFirebaseUser.updatePassword(newPassword)).thenReturn(failedUpdateTask)
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, newPassword, newPassword
        )
        assertTrue("Form validation should pass", validationResult.isValid)
        val changeResult = passwordChangeManager.changePassword(currentPassword, newPassword)
        assertTrue("Password change should fail at Firebase update", changeResult.isError())
        val error = changeResult.getErrorOrNull()
        assertNotNull("Error should be present", error)
        assertEquals("Should be Firebase error",
            AccountResult.ErrorType.FIREBASE_ERROR, error!!.errorType)
        verify(mockFirebaseUser).reauthenticate(any())
        verify(mockFirebaseUser).updatePassword(newPassword)
    }
    @Test
    fun `complete flow should handle network errors gracefully`() = runTest {
        val networkException = FirebaseNetworkException("Network error")
        val failedReauthTask = Tasks.forException<AuthResult>(networkException)
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(failedReauthTask)
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, newPassword, newPassword
        )
        assertTrue("Form validation should pass", validationResult.isValid)
        val changeResult = passwordChangeManager.changePassword(currentPassword, newPassword)
        assertTrue("Password change should fail with network error", changeResult.isError())
        val error = changeResult.getErrorOrNull()
        assertNotNull("Error should be present", error)
        assertEquals("Should be network error",
            AccountResult.ErrorType.NETWORK_ERROR, error!!.errorType)
        assertTrue("Should contain network error message",
            error.message.contains("conexión") || error.message.contains("red"))
        verify(mockFirebaseUser).reauthenticate(any())
    }
    @Test
    fun `complete flow should fail when user is not authenticated`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, newPassword, newPassword
        )
        assertTrue("Form validation should pass", validationResult.isValid)
        val changeResult = passwordChangeManager.changePassword(currentPassword, newPassword)
        assertTrue("Password change should fail without authentication", changeResult.isError())
        val error = changeResult.getErrorOrNull()
        assertNotNull("Error should be present", error)
        assertEquals("Should be authentication error",
            AccountResult.ErrorType.AUTHENTICATION_ERROR, error!!.errorType)
        assertTrue("Should contain login message",
            error.message.contains("iniciar sesión"))
        verify(mockFirebaseUser, never()).reauthenticate(any())
        verify(mockFirebaseUser, never()).updatePassword(any())
    }
    @Test
    fun `complete flow should validate password confirmation mismatch`() = runTest {
        val mismatchedConfirmation = "DifferentPassword123"
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, newPassword, mismatchedConfirmation
        )
        assertFalse("Form validation should fail for mismatched passwords", validationResult.isValid)
        assertEquals("Should show mismatch error",
            "Las contraseñas no coinciden", validationResult.errorMessage)
        val changeResult = passwordChangeManager.changePassword(currentPassword, newPassword)
        assertTrue("Password change should succeed if called directly", changeResult.isSuccess())
    }
    @Test
    fun `complete flow should handle empty form fields`() = runTest {
        val emptyPassword = ""
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            emptyPassword, newPassword, newPassword
        )
        assertFalse("Form validation should fail for empty current password", validationResult.isValid)
        assertEquals("Should show empty field error",
            "Ingresa tu contraseña actual", validationResult.errorMessage)
        val newPasswordValidation = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, emptyPassword, emptyPassword
        )
        assertFalse("Form validation should fail for empty new password", newPasswordValidation.isValid)
        assertEquals("Should show empty new password error",
            "La nueva contraseña no puede estar vacía", newPasswordValidation.errorMessage)
    }
    @Test
    fun `complete flow should handle same current and new password`() = runTest {
        val validationResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, currentPassword, currentPassword
        )
        assertFalse("Form validation should fail for same passwords", validationResult.isValid)
        assertEquals("Should show same password error",
            "La nueva contraseña debe ser diferente a la actual", validationResult.errorMessage)
    }
    @Test
    fun `complete flow should handle complex password requirements`() = runTest {
        val noUppercasePassword = "newpassword123"
        val noUppercaseValidation = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, noUppercasePassword, noUppercasePassword
        )
        assertFalse("Should fail without uppercase", noUppercaseValidation.isValid)
        assertEquals("Should show uppercase error",
            "La contraseña debe tener al menos una mayúscula", noUppercaseValidation.errorMessage)
        val noLowercasePassword = "NEWPASSWORD123"
        val noLowercaseValidation = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, noLowercasePassword, noLowercasePassword
        )
        assertFalse("Should fail without lowercase", noLowercaseValidation.isValid)
        assertEquals("Should show lowercase error",
            "La contraseña debe tener al menos una minúscula", noLowercaseValidation.errorMessage)
        val noNumberPassword = "NewPassword"
        val noNumberValidation = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, noNumberPassword, noNumberPassword
        )
        assertFalse("Should fail without numbers", noNumberValidation.isValid)
        assertEquals("Should show number error",
            "La contraseña debe tener al menos un número", noNumberValidation.errorMessage)
    }
}
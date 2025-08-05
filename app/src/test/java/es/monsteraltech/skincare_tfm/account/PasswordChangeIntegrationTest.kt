package es.monsteraltech.skincare_tfm.account
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
    private val testEmail = "test@example.com"
    private val currentPassword = "CurrentPass123"
    private val newPassword = "NewStrongPass456"
    private val weakPassword = "weak"
    private val invalidCurrentPassword = "WrongPass123"
    @Before
    fun setUp() {
        passwordChangeManager = PasswordChangeManager(mockFirebaseAuth)
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseUser.email).thenReturn(testEmail)
    }
    @Test
    fun `complete password change process should succeed with valid inputs`() = runTest {
        val successfulReauthTask = Tasks.forResult(mockAuthResult)
        val successfulUpdateTask = Tasks.forResult<Void>(null)
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(successfulReauthTask)
        whenever(mockFirebaseUser.updatePassword(newPassword)).thenReturn(successfulUpdateTask)
        val result = passwordChangeManager.changePassword(currentPassword, newPassword)
        assertTrue(result.isSuccess())
        verify(mockFirebaseUser).reauthenticate(any())
        verify(mockFirebaseUser).updatePassword(newPassword)
    }
    @Test
    fun `password change should fail with validation error for weak password`() = runTest {
        val result = passwordChangeManager.changePassword(currentPassword, weakPassword)
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.VALIDATION_ERROR, error.errorType)
        assertTrue(error.message.contains("8 caracteres"))
        verify(mockFirebaseUser, never()).reauthenticate(any())
        verify(mockFirebaseUser, never()).updatePassword(any())
    }
    @Test
    fun `password change should fail with authentication error for wrong current password`() = runTest {
        val authException = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "Wrong password")
        val failedReauthTask = Tasks.forException<AuthResult>(authException)
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(failedReauthTask)
        val result = passwordChangeManager.changePassword(invalidCurrentPassword, newPassword)
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, error.errorType)
        assertTrue(error.message.contains("actual") || error.message.contains("incorrecta"))
        verify(mockFirebaseUser).reauthenticate(any())
        verify(mockFirebaseUser, never()).updatePassword(any())
    }
    @Test
    fun `password change should fail when reauthentication succeeds but update fails`() = runTest {
        val successfulReauthTask = Tasks.forResult(mockAuthResult)
        val updateException = FirebaseAuthException("ERROR_WEAK_PASSWORD", "Password is too weak")
        val failedUpdateTask = Tasks.forException<Void>(updateException)
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(successfulReauthTask)
        whenever(mockFirebaseUser.updatePassword(newPassword)).thenReturn(failedUpdateTask)
        val result = passwordChangeManager.changePassword(currentPassword, newPassword)
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.FIREBASE_ERROR, error.errorType)
        verify(mockFirebaseUser).reauthenticate(any())
        verify(mockFirebaseUser).updatePassword(newPassword)
    }
    @Test
    fun `password change should handle network errors gracefully`() = runTest {
        val networkException = FirebaseNetworkException("Network error")
        val failedReauthTask = Tasks.forException<AuthResult>(networkException)
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(failedReauthTask)
        val result = passwordChangeManager.changePassword(currentPassword, newPassword)
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.NETWORK_ERROR, error.errorType)
        assertTrue(error.message.contains("conexión") || error.message.contains("red"))
        verify(mockFirebaseUser).reauthenticate(any())
    }
    @Test
    fun `password change should fail when user is not authenticated`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        val result = passwordChangeManager.changePassword(currentPassword, newPassword)
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, error.errorType)
        assertTrue(error.message.contains("iniciar sesión"))
        verify(mockFirebaseUser, never()).reauthenticate(any())
        verify(mockFirebaseUser, never()).updatePassword(any())
    }
    @Test
    fun `password change should handle user email being null`() = runTest {
        whenever(mockFirebaseUser.email).thenReturn(null)
        val result = passwordChangeManager.changePassword(currentPassword, newPassword)
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, error.errorType)
        verify(mockFirebaseUser, never()).reauthenticate(any())
        verify(mockFirebaseUser, never()).updatePassword(any())
    }
    @Test
    fun `password validation should work correctly in integration context`() = runTest {
        val validResult = passwordChangeManager.validateNewPassword("ValidPass123", currentPassword)
        assertTrue(validResult.isValid)
        val samePasswordResult = passwordChangeManager.validateNewPassword(currentPassword, currentPassword)
        assertTrue(samePasswordResult.isValid == false)
        assertEquals("La nueva contraseña debe ser diferente a la actual", samePasswordResult.errorMessage)
        val shortPasswordResult = passwordChangeManager.validateNewPassword("Short1", currentPassword)
        assertTrue(shortPasswordResult.isValid == false)
        assertEquals("La contraseña debe tener al menos 8 caracteres", shortPasswordResult.errorMessage)
        val noUppercaseResult = passwordChangeManager.validateNewPassword("lowercase123", currentPassword)
        assertTrue(noUppercaseResult.isValid == false)
        assertEquals("La contraseña debe tener al menos una mayúscula", noUppercaseResult.errorMessage)
    }
    @Test
    fun `form validation should work correctly in integration context`() = runTest {
        val validFormResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, newPassword, newPassword
        )
        assertTrue(validFormResult.isValid)
        val emptyCurrentResult = passwordChangeManager.validatePasswordChangeForm(
            "", newPassword, newPassword
        )
        assertTrue(emptyCurrentResult.isValid == false)
        assertEquals("Ingresa tu contraseña actual", emptyCurrentResult.errorMessage)
        val mismatchedResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, newPassword, "DifferentPassword123"
        )
        assertTrue(mismatchedResult.isValid == false)
        assertEquals("Las contraseñas no coinciden", mismatchedResult.errorMessage)
        val weakPasswordResult = passwordChangeManager.validatePasswordChangeForm(
            currentPassword, "weak", "weak"
        )
        assertTrue(weakPasswordResult.isValid == false)
        assertEquals("La contraseña debe tener al menos 8 caracteres", weakPasswordResult.errorMessage)
    }
    @Test
    fun `password change should handle concurrent operations gracefully`() = runTest {
        val successfulReauthTask = Tasks.forResult(mockAuthResult)
        val successfulUpdateTask = Tasks.forResult<Void>(null)
        whenever(mockFirebaseUser.reauthenticate(any())).thenReturn(successfulReauthTask)
        whenever(mockFirebaseUser.updatePassword(newPassword)).thenReturn(successfulUpdateTask)
        val result1 = passwordChangeManager.changePassword(currentPassword, newPassword)
        val result2 = passwordChangeManager.changePassword(currentPassword, "AnotherPass789")
        assertTrue(result1.isSuccess())
        assertTrue(result2.isSuccess())
        verify(mockFirebaseUser, times(2)).reauthenticate(any())
        verify(mockFirebaseUser).updatePassword(newPassword)
        verify(mockFirebaseUser).updatePassword("AnotherPass789")
    }
}
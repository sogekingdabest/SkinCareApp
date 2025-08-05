package es.monsteraltech.skincare_tfm.account
import android.util.Log
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
class PasswordChangeManager(
    private val retryManager: RetryManager = RetryManager()
) {
    private val TAG = "PasswordChangeManager"
    private val auth = FirebaseAuth.getInstance()
    data class PasswordValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )
    private suspend fun validateCurrentPassword(currentPassword: String): PasswordValidationResult {
        return withContext(Dispatchers.IO) {
            try {
                val user = auth.currentUser
                    ?: return@withContext PasswordValidationResult(false, "Usuario no autenticado")
                val email = user.email
                    ?: return@withContext PasswordValidationResult(false, "Email no disponible")
                if (currentPassword.isBlank()) {
                    return@withContext PasswordValidationResult(false, "La contraseña actual no puede estar vacía")
                }
                val credential = EmailAuthProvider.getCredential(email, currentPassword)
                user.reauthenticate(credential).await()
                Log.d(TAG, "Contraseña actual validada exitosamente")
                PasswordValidationResult(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error al validar contraseña actual: ${e.message}")
                val errorMessage = when {
                    e.message?.contains("password is invalid") == true ||
                    e.message?.contains("wrong-password") == true -> "La contraseña actual es incorrecta"
                    e.message?.contains("network") == true ||
                    e.message?.contains("timeout") == true -> "Error de conexión. Inténtalo de nuevo"
                    else -> "Error al validar contraseña"
                }
                PasswordValidationResult(false, errorMessage)
            }
        }
    }
    fun validateNewPassword(newPassword: String, currentPassword: String? = null): PasswordValidationResult {
        if (currentPassword != null && newPassword == currentPassword) {
            return PasswordValidationResult(false, "La nueva contraseña debe ser diferente a la actual")
        }
        return validatePassword(newPassword)
    }
    fun validatePassword(password: String? = null): PasswordValidationResult {
        if (password == null) {
            return PasswordValidationResult(false, "La contraseña no puede ser nula")
        }
        if (password.isBlank()) {
            return PasswordValidationResult(false, "La nueva contraseña no puede estar vacía")
        }
        if (password.length < 8) {
            return PasswordValidationResult(false, "La contraseña debe tener al menos 8 caracteres")
        }
        if (!password.any { it.isUpperCase() }) {
            return PasswordValidationResult(false, "La contraseña debe tener al menos una mayúscula")
        }
        if (!password.any { it.isLowerCase() }) {
            return PasswordValidationResult(false, "La contraseña debe tener al menos una minúscula")
        }
        if (!password.any { it.isDigit() }) {
            return PasswordValidationResult(false, "La contraseña debe tener al menos un número")
        }
        return PasswordValidationResult(true)
    }
    suspend fun changePassword(currentPassword: String, newPassword: String): AccountResult<Unit> {
        return retryManager.executeWithRetry(
            config = retryManager.createAuthRetryConfig()
        ) {
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Iniciando cambio de contraseña")
                    val user = auth.currentUser
                        ?: return@withContext AccountResult.error<Unit>(
                            IllegalStateException("Usuario no autenticado"),
                            "Debes iniciar sesión para cambiar la contraseña",
                            AccountResult.ErrorType.AUTHENTICATION_ERROR
                        )
                    val currentPasswordValidation = validateCurrentPassword(currentPassword)
                    if (!currentPasswordValidation.isValid) {
                        return@withContext AccountResult.error<Unit>(
                            Exception(currentPasswordValidation.errorMessage ?: "Contraseña actual inválida"),
                            currentPasswordValidation.errorMessage ?: "La contraseña actual es incorrecta",
                            AccountResult.ErrorType.VALIDATION_ERROR
                        )
                    }
                    val newPasswordValidation = validateNewPassword(newPassword, currentPassword)
                    if (!newPasswordValidation.isValid) {
                        return@withContext AccountResult.error<Unit>(
                            Exception(newPasswordValidation.errorMessage ?: "Nueva contraseña inválida"),
                            newPasswordValidation.errorMessage ?: "La nueva contraseña no cumple los requisitos",
                            AccountResult.ErrorType.VALIDATION_ERROR
                        )
                    }
                    user.updatePassword(newPassword).await()
                    Log.d(TAG, "Contraseña cambiada exitosamente")
                    AccountResult.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al cambiar contraseña: ${e.message}", e)
                    val (message, errorType) = when {
                        e.message?.contains("password is invalid") == true ||
                        e.message?.contains("wrong-password") == true ->
                            "La contraseña actual es incorrecta" to AccountResult.ErrorType.VALIDATION_ERROR
                        e.message?.contains("network") == true ||
                        e.message?.contains("timeout") == true ->
                            "Error de conexión. Inténtalo de nuevo" to AccountResult.ErrorType.NETWORK_ERROR
                        e.message?.contains("auth") == true ->
                            "Error de autenticación. Inicia sesión nuevamente" to AccountResult.ErrorType.AUTHENTICATION_ERROR
                        else -> "Error al cambiar contraseña" to AccountResult.ErrorType.GENERIC_ERROR
                    }
                    AccountResult.error<Unit>(e, message, errorType)
                }
            }
        }
    }
    fun validatePasswordConfirmation(newPassword: String, confirmPassword: String): PasswordValidationResult {
        if (confirmPassword.isBlank()) {
            return PasswordValidationResult(false, "Confirma la nueva contraseña")
        }
        if (newPassword != confirmPassword) {
            return PasswordValidationResult(false, "Las contraseñas no coinciden")
        }
        return PasswordValidationResult(true)
    }
    fun validatePasswordChangeForm(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ): PasswordValidationResult {
        if (currentPassword.isBlank()) {
            return PasswordValidationResult(false, "Ingresa tu contraseña actual")
        }
        val newPasswordValidation = validateNewPassword(newPassword, currentPassword)
        if (!newPasswordValidation.isValid) {
            return newPasswordValidation
        }
        val confirmationValidation = validatePasswordConfirmation(newPassword, confirmPassword)
        if (!confirmationValidation.isValid) {
            return confirmationValidation
        }
        return PasswordValidationResult(true)
    }
}
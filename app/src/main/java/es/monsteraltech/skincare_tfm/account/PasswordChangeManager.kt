package es.monsteraltech.skincare_tfm.account

import android.util.Log
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manager class for handling password change operations
 * Provides validation and Firebase Auth integration for password changes
 * Implements error handling and retry logic with AccountResult
 */
class PasswordChangeManager(
    private val retryManager: RetryManager = RetryManager()
) {

    private val TAG = "PasswordChangeManager"
    private val auth = FirebaseAuth.getInstance()

    /**
     * Data class for password validation result
     */
    data class PasswordValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Validates the current password by attempting re-authentication
     * @param currentPassword The current password to validate
     * @return PasswordValidationResult indicating if password is valid
     */
    suspend fun validateCurrentPassword(currentPassword: String): PasswordValidationResult {
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

    /**
     * Validates new password according to security criteria
     * @param newPassword The new password to validate
     * @param currentPassword The current password to ensure it's different
     * @return PasswordValidationResult indicating if new password meets criteria
     */
    fun validateNewPassword(newPassword: String, currentPassword: String? = null): PasswordValidationResult {
        if (newPassword.isBlank()) {
            return PasswordValidationResult(false, "La nueva contraseña no puede estar vacía")
        }

        // Check minimum length
        if (newPassword.length < 8) {
            return PasswordValidationResult(false, "La contraseña debe tener al menos 8 caracteres")
        }

        // Check for uppercase letter
        if (!newPassword.any { it.isUpperCase() }) {
            return PasswordValidationResult(false, "La contraseña debe tener al menos una mayúscula")
        }

        // Check for lowercase letter
        if (!newPassword.any { it.isLowerCase() }) {
            return PasswordValidationResult(false, "La contraseña debe tener al menos una minúscula")
        }

        // Check for digit
        if (!newPassword.any { it.isDigit() }) {
            return PasswordValidationResult(false, "La contraseña debe tener al menos un número")
        }

        // Check if new password is different from current
        if (currentPassword != null && newPassword == currentPassword) {
            return PasswordValidationResult(false, "La nueva contraseña debe ser diferente a la actual")
        }

        // Check for common weak passwords
        val commonPasswords = listOf(
            "12345678", "password", "password123", "123456789", "qwerty123",
            "abc123456", "password1", "12345678a", "Password1", "contraseña"
        )
        
        if (commonPasswords.any { it.equals(newPassword, ignoreCase = true) }) {
            return PasswordValidationResult(false, "Esta contraseña es muy común. Elige una más segura")
        }

        return PasswordValidationResult(true)
    }

    /**
     * Changes the user's password after validation with retry logic
     * @param currentPassword The current password for re-authentication
     * @param newPassword The new password to set
     * @return AccountResult<Unit> indicating success or failure with details
     */
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

                    // Validate current password first
                    val currentPasswordValidation = validateCurrentPassword(currentPassword)
                    if (!currentPasswordValidation.isValid) {
                        return@withContext AccountResult.error<Unit>(
                            Exception(currentPasswordValidation.errorMessage ?: "Contraseña actual inválida"),
                            currentPasswordValidation.errorMessage ?: "La contraseña actual es incorrecta",
                            AccountResult.ErrorType.VALIDATION_ERROR
                        )
                    }

                    // Validate new password
                    val newPasswordValidation = validateNewPassword(newPassword, currentPassword)
                    if (!newPasswordValidation.isValid) {
                        return@withContext AccountResult.error<Unit>(
                            Exception(newPasswordValidation.errorMessage ?: "Nueva contraseña inválida"),
                            newPasswordValidation.errorMessage ?: "La nueva contraseña no cumple los requisitos",
                            AccountResult.ErrorType.VALIDATION_ERROR
                        )
                    }

                    // Update password in Firebase Auth
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

    /**
     * Validates password confirmation matches new password
     * @param newPassword The new password
     * @param confirmPassword The confirmation password
     * @return PasswordValidationResult indicating if passwords match
     */
    fun validatePasswordConfirmation(newPassword: String, confirmPassword: String): PasswordValidationResult {
        if (confirmPassword.isBlank()) {
            return PasswordValidationResult(false, "Confirma la nueva contraseña")
        }
        
        if (newPassword != confirmPassword) {
            return PasswordValidationResult(false, "Las contraseñas no coinciden")
        }
        
        return PasswordValidationResult(true)
    }

    /**
     * Comprehensive validation for complete password change form
     * @param currentPassword Current password
     * @param newPassword New password
     * @param confirmPassword Confirmation password
     * @return PasswordValidationResult with overall validation result
     */
    fun validatePasswordChangeForm(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ): PasswordValidationResult {
        
        // Validate current password is not empty
        if (currentPassword.isBlank()) {
            return PasswordValidationResult(false, "Ingresa tu contraseña actual")
        }
        
        // Validate new password
        val newPasswordValidation = validateNewPassword(newPassword, currentPassword)
        if (!newPasswordValidation.isValid) {
            return newPasswordValidation
        }
        
        // Validate password confirmation
        val confirmationValidation = validatePasswordConfirmation(newPassword, confirmPassword)
        if (!confirmationValidation.isValid) {
            return confirmationValidation
        }
        
        return PasswordValidationResult(true)
    }
}
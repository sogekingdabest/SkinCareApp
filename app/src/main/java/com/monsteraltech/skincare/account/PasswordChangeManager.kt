package com.monsteraltech.skincare.account

import javax.inject.Inject

class PasswordChangeManager @Inject constructor(
    private val repository: PasswordChangeRepository,
    private val validator: PasswordValidator,
    private val retryManager: RetryManager = RetryManager()
) {
    data class PasswordValidationResult(val isValid: Boolean, val errorMessage: String? = null)
    fun isAuthenticated(): Boolean = repository.isAuthenticated()
    fun validatePassword(password: String? = null) = validator.validatePassword(password)
    fun validateNewPassword(newPassword: String, currentPassword: String? = null) = validator.validateNewPassword(newPassword, currentPassword)
    fun validatePasswordConfirmation(newPassword: String, confirmPassword: String) = validator.validatePasswordConfirmation(newPassword, confirmPassword)
    fun validatePasswordChangeForm(currentPassword: String, newPassword: String, confirmPassword: String) =
        validator.validatePasswordChangeForm(currentPassword, newPassword, confirmPassword)

    suspend fun changePassword(currentPassword: String, newPassword: String): AccountResult<Unit> =
        retryManager.executeWithRetry(retryManager.createAuthRetryConfig()) {
            repository.changePassword(currentPassword, newPassword)
        }
}

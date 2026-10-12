package com.monsteraltech.skincare.account

import com.monsteraltech.skincare.account.PasswordChangeManager.PasswordValidationResult
import javax.inject.Inject

class PasswordValidator @Inject constructor() {
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

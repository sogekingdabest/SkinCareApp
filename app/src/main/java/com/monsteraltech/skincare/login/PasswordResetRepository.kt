package com.monsteraltech.skincare.login

interface PasswordResetRepository {
    suspend fun sendPasswordReset(email: String): Result<Unit>
}

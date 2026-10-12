package com.monsteraltech.skincare.account

interface PasswordChangeRepository {
    fun isAuthenticated(): Boolean
    suspend fun changePassword(currentPassword: String, newPassword: String): AccountResult<Unit>
}

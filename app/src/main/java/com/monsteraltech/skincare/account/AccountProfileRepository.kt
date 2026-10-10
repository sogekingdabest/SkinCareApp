package com.monsteraltech.skincare.account

/** Profile operations used by account presentation, with cancellation propagated to the caller. */
interface AccountProfileRepository {
    suspend fun getCurrentUserInfo(): AccountResult<UserInfo>
    suspend fun getUserSettings(): AccountResult<AccountSettings>
    suspend fun updateUserSettings(settings: AccountSettings): AccountResult<Boolean>
    fun isUserAuthenticated(): Boolean
    fun getCurrentUserId(): String?
}

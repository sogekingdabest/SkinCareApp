package com.monsteraltech.skincare.account

/** Settings belong to a local owner, independently of the authentication provider. */
interface AccountSettingsRepository {
    suspend fun getUserSettings(userId: String): AccountSettings
    suspend fun updateUserSettings(settings: AccountSettings, userId: String): Boolean
    suspend fun initializeUserSettings(userId: String): Boolean
    /** Reads and applies this patch under the same storage lock; null means no commit. */
    suspend fun applyPatch(patch: AccountSettingsPatch, userId: String): AccountSettings?
}

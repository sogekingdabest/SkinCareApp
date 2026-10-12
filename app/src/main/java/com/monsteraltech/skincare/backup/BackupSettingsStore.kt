package com.monsteraltech.skincare.backup

import com.monsteraltech.skincare.account.AccountSettings
import com.monsteraltech.skincare.account.AccountSettingsRepository
import javax.inject.Inject

interface BackupSettingsStore {
    suspend fun read(ownerId: String): AccountSettings
    suspend fun write(settings: AccountSettings, ownerId: String): Boolean
}

class LocalBackupSettingsStore @Inject constructor(private val manager: AccountSettingsRepository) : BackupSettingsStore {
    override suspend fun read(ownerId: String) = manager.getUserSettings(ownerId)
    override suspend fun write(settings: AccountSettings, ownerId: String) = manager.updateUserSettings(settings, ownerId)
}

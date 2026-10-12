package com.monsteraltech.skincare.data.account

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.monsteraltech.skincare.account.AccountSettings
import com.monsteraltech.skincare.account.AccountSettingsPatch
import com.monsteraltech.skincare.account.AccountSettingsRepository
import com.monsteraltech.skincare.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class LocalAccountSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AccountSettingsRepository {
    private val TAG = "LocalAccountSettings"
    private val gson = Gson()
    private val mutex = Mutex()
    private val settingsPreferences by lazy {
        context.getSharedPreferences("private_account_settings", Context.MODE_PRIVATE)
    }
    override suspend fun updateUserSettings(settings: AccountSettings, userId: String): Boolean = withContext(ioDispatcher) {
        mutex.withLock {
            if (userId.isBlank()) return@withLock false
            val localSettings = settings.copy(userId = userId, autoBackup = false)
            settingsPreferences.edit()
                .putString(settingsKey(userId), gson.toJson(localSettings))
                .commit()
        }
    }

    override suspend fun getUserSettings(userId: String): AccountSettings = withContext(ioDispatcher) {
        mutex.withLock {
            if (userId.isBlank()) return@withLock AccountSettings(autoBackup = false)
            readSettings(userId)
        }
    }

    override suspend fun initializeUserSettings(userId: String): Boolean = withContext(ioDispatcher) {
        mutex.withLock {
            if (userId.isBlank()) return@withLock false
            if (settingsPreferences.contains(settingsKey(userId))) return@withLock true
            settingsPreferences.edit()
                .putString(
                    settingsKey(userId),
                    gson.toJson(AccountSettings(userId = userId, autoBackup = false))
                )
                .commit()
        }
    }

    override suspend fun applyPatch(patch: AccountSettingsPatch, userId: String): AccountSettings? = withContext(ioDispatcher) {
        mutex.withLock {
            if (userId.isBlank()) return@withLock null
            val settings = patch.applyTo(readSettings(userId)).copy(userId = userId, autoBackup = false)
            val committed = settingsPreferences.edit().putString(settingsKey(userId), gson.toJson(settings)).commit()
            if (committed) settings else null
        }
    }

    private fun readSettings(userId: String): AccountSettings {
        val json = settingsPreferences.getString(settingsKey(userId), null)
            ?: return AccountSettings(userId = userId, autoBackup = false)
        return runCatching {
            gson.fromJson(json, AccountSettings::class.java)
                .copy(userId = userId, autoBackup = false)
        }.getOrElse {
            Log.w(TAG, "Configuración local inválida; se usarán valores por defecto", it)
            AccountSettings(userId = userId, autoBackup = false)
        }
    }

    private fun settingsKey(userId: String) = "account_settings_$userId"
}

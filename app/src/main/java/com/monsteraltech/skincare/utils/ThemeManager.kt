package com.monsteraltech.skincare.utils

import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatDelegate
import com.monsteraltech.skincare.account.AccountProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Applies global theme changes on Main; the caller owns the settings load. */
class ThemeManager internal constructor() {
    companion object {
        private val sharedManager by lazy { ThemeManager() }
        fun getInstance(): ThemeManager = sharedManager
    }
    private var repository: AccountProfileRepository? = null
    private var revision = 0L

    @MainThread
    fun initialize(repository: AccountProfileRepository) { this.repository = repository }

    @MainThread
    suspend fun applyThemeFromUserSettings() {
        val request = ++revision
        val darkMode = try {
            repository?.getUserSettings()?.getDataOrNull()?.darkModeEnabled ?: false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { false }
        currentCoroutineContext().ensureActive()
        // An explicit user change or a newer load wins over a delayed response.
        if (request == revision) applyTheme(darkMode)
    }

    @MainThread
    fun updateThemeFromSettings(darkModeEnabled: Boolean) {
        revision++
        applyTheme(darkModeEnabled)
    }

    private fun applyTheme(darkModeEnabled: Boolean) {
        val mode = if (darkModeEnabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            // AppCompat owns recreation of its active activities.
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}

package com.monsteraltech.skincare.account

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.skincare.R
import dagger.hilt.android.lifecycle.HiltViewModel
import com.monsteraltech.skincare.utils.ThemeManager
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: AccountProfileRepository,
    private val themeManager: ThemeManager = ThemeManager.getInstance()
) : ViewModel() {
    data class UiState(
        val userInfo: AccountResult<UserInfo> = AccountResult.Loading(),
        val settings: AccountSettings? = null,
        val isSaving: Boolean = false,
        val committedDarkMode: Boolean? = null
    )
    enum class Setting { NOTIFICATIONS, DARK_MODE }
    sealed interface Event {
        object SettingsSaved : Event
        data class Error(@StringRes val message: Int) : Event
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private val messages = Channel<Event>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private val settingsMutex = Mutex()
    private var infoJob: Job? = null
    private var settingsJob: Job? = null

    fun refresh() {
        if (infoJob?.isActive != true) infoJob = viewModelScope.launch {
            val result = accountResult { repository.getCurrentUserInfo() }
            currentCoroutineContext().ensureActive()
            _uiState.update { it.copy(userInfo = result) }
        }
        if (settingsJob?.isActive != true) settingsJob = viewModelScope.launch {
            settingsMutex.withLock {
                val result = accountResult { repository.getUserSettings() }
                currentCoroutineContext().ensureActive()
                val settings = result.getDataOrNull() ?: AccountSettings()
                _uiState.update { it.copy(settings = settings,
                    committedDarkMode = result.getDataOrNull()?.darkModeEnabled) }
            }
        }
    }

    fun changeSetting(setting: Setting, value: Boolean) {
        viewModelScope.launch {
            settingsMutex.withLock {
                if (!repository.isUserAuthenticated()) {
                    messages.trySend(Event.Error(R.string.error_authentication))
                    return@withLock
                }
                _uiState.update { it.copy(isSaving = true) }
                var previous = _uiState.value.settings
                try {
                    // Read under the same lock so rapid edits and changes from other screens are preserved.
                    val read = accountResult { repository.getUserSettings() }
                    val current = read.getDataOrNull()
                    if (current == null) {
                        messages.trySend(Event.Error(R.string.settings_error))
                        return@withLock
                    }
                    previous = current
                    val next = when (setting) {
                        Setting.NOTIFICATIONS -> current.copy(notificationsEnabled = value)
                        Setting.DARK_MODE -> current.copy(darkModeEnabled = value)
                    }
                    _uiState.update { it.copy(settings = next) }
                    val result = accountResult { repository.updateUserSettings(next) }
                    currentCoroutineContext().ensureActive()
                    if (result.getDataOrNull() == true) {
                        if (setting == Setting.DARK_MODE) themeManager.updateThemeFromSettings(next.darkModeEnabled)
                        _uiState.update { it.copy(committedDarkMode = next.darkModeEnabled) }
                        messages.trySend(Event.SettingsSaved)
                    } else {
                        _uiState.update { it.copy(settings = current) }
                        messages.trySend(Event.Error(R.string.settings_error))
                    }
                } catch (cancelled: CancellationException) {
                    _uiState.update { it.copy(settings = previous) }
                    throw cancelled
                } finally {
                    _uiState.update { it.copy(isSaving = false) }
                }
            }
        }
    }

    private suspend fun <T> accountResult(operation: suspend () -> AccountResult<T>): AccountResult<T> {
        return try {
            operation().also { result ->
                (result.getErrorOrNull()?.exception as? CancellationException)?.let { throw it }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AccountResult.error(error)
        }
    }
}

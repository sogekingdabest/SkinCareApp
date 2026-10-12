package com.monsteraltech.skincare.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.skincare.account.AccountProfileRepository
import com.monsteraltech.skincare.account.AccountResult
import com.monsteraltech.skincare.account.AccountSettings
import com.monsteraltech.skincare.account.AccountSettingsPatch
import com.monsteraltech.skincare.data.identity.ActiveProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val repository: AccountProfileRepository,
    private val profile: ActiveProfile,
    private val updateSettings: UpdateNotificationSettings
) : ViewModel() {
    sealed interface State {
        object Loading : State
        data class Ready(val settings: AccountSettings, val isSaving: Boolean = false) : State
        object Failed : State
    }
    enum class Event { Saved, Failed }
    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()
    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private val mutex = Mutex()

    fun refresh() {
        val owner = profile.ownerId
        viewModelScope.launch {
            mutex.withLock {
                if (owner == null || profile.ownerId != owner) {
                    _state.value = State.Failed
                    return@withLock
                }
                try {
                    val result = repository.getUserSettings()
                    currentCoroutineContext().ensureActive()
                    (result.getErrorOrNull()?.exception as? CancellationException)?.let { throw it }
                    val settings = result.getDataOrNull()
                    _state.value = if (settings?.userId == owner && profile.ownerId == owner) State.Ready(settings) else State.Failed
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { _state.value = State.Failed }
            }
        }
    }

    fun setCheckups(value: Boolean) = change(AccountSettingsPatch(moleCheckupsEnabled = value))
    fun setVibration(value: Boolean) = change(AccountSettingsPatch(notificationVibration = value))
    fun setSound(value: Boolean) = change(AccountSettingsPatch(notificationSound = value))
    fun setTime(value: String) = change(AccountSettingsPatch(moleCheckupTime = value))
    fun setFrequency(value: Int) = change(AccountSettingsPatch(moleCheckupFrequency = value))

    private fun change(patch: AccountSettingsPatch) {
        val owner = profile.ownerId ?: return
        if (_state.value !is State.Ready) return
        viewModelScope.launch {
            mutex.withLock {
                val previous = _state.value as? State.Ready ?: return@withLock
                if (previous.settings.userId != owner || profile.ownerId != owner) return@withLock
                _state.value = previous.copy(isSaving = true)
                try {
                    val result = updateSettings(owner, patch)
                    currentCoroutineContext().ensureActive()
                    (result.getErrorOrNull()?.exception as? CancellationException)?.let { throw it }
                    if (profile.ownerId != owner) {
                        _state.value = State.Failed
                        return@withLock
                    }
                    val saved = result.getDataOrNull()
                    _state.value = saved?.let { State.Ready(it) } ?: previous.copy(isSaving = false)
                    _events.send(if (saved != null) Event.Saved else Event.Failed)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _state.value = previous.copy(isSaving = false)
                    _events.send(Event.Failed)
                } finally {
                    val state = _state.value
                    if (state is State.Ready && state.isSaving) _state.value = state.copy(isSaving = false)
                }
            }
        }
    }
}

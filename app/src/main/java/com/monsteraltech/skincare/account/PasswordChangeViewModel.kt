package com.monsteraltech.skincare.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

@HiltViewModel
class PasswordChangeViewModel @Inject constructor(private val manager: PasswordChangeManager, private val savedState: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    sealed interface Event {
        data class Failed(val error: AccountResult.Error<Unit>) : Event
    }
    val changed = savedState.getStateFlow("passwordChanged", false)
    val isAuthenticated: Boolean get() = manager.isAuthenticated()
    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()
    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun change(current: String, new: String) {
        if (_isSaving.value || changed.value) return
        _isSaving.value = true
        viewModelScope.launch {
            try {
                val result = manager.changePassword(current, new)
                currentCoroutineContext().ensureActive()
                when (result) {
                    is AccountResult.Success -> savedState["passwordChanged"] = true
                    is AccountResult.Error -> {
                        (result.exception as? CancellationException)?.let { throw it }
                        _events.send(Event.Failed(result))
                    }
                    is AccountResult.Loading -> Unit
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _events.send(Event.Failed(AccountResult.Error(error, "Error al cambiar contraseña")))
            } finally { _isSaving.value = false }
        }
    }
}

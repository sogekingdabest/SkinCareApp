package com.monsteraltech.skincare.login

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
class ForgotPasswordViewModel @Inject constructor(private val repository: PasswordResetRepository) : ViewModel() {
    enum class Event { Sent, Failed }
    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()
    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun send(email: String) {
        if (_isSending.value) return
        _isSending.value = true
        viewModelScope.launch {
            try {
                val result = repository.sendPasswordReset(email.trim())
                currentCoroutineContext().ensureActive()
                (result.exceptionOrNull() as? CancellationException)?.let { throw it }
                _events.send(if (result.isSuccess) Event.Sent else Event.Failed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _events.send(Event.Failed)
            } finally { _isSending.value = false }
        }
    }
}

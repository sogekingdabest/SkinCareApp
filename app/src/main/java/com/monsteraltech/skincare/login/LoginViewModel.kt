package com.monsteraltech.skincare.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.data.auth.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    sealed interface Event {
        object NavigateToMain : Event
        data class ShowMessage(@StringRes val messageRes: Int) : Event
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    fun signInWithEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _events.trySend(Event.ShowMessage(R.string.error_empty_fields))
            return
        }
        launchAuth { authRepository.signInWithEmail(email.trim(), password) }
    }

    fun signInWithGoogleToken(idToken: String) {
        launchAuth { authRepository.signInWithGoogleIdToken(idToken) }
    }

    fun continueWithoutAccount() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.continueWithoutAccount()
                currentCoroutineContext().ensureActive()
                _events.send(Event.NavigateToMain)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _events.send(Event.ShowMessage(R.string.error_authentication))
            } finally { _isLoading.value = false }
        }
    }

    private fun launchAuth(authCall: suspend () -> Result<FirebaseUser>) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            val result = try { authCall() } finally { _isLoading.value = false }
            currentCoroutineContext().ensureActive()
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            result
                .onSuccess { _events.send(Event.NavigateToMain) }
                .onFailure { _events.send(Event.ShowMessage(R.string.error_authentication)) }
        }
    }
}

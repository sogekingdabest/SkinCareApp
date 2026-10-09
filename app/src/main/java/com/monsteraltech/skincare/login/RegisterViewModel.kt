package com.monsteraltech.skincare.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.account.PasswordChangeManager
import com.monsteraltech.skincare.data.auth.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val passwordChangeManager: PasswordChangeManager
) : ViewModel() {

    companion object {
        // Equivalente a android.util.Patterns.EMAIL_ADDRESS, pero sin depender
        // de clases de Android (mantiene el ViewModel testeable en JVM).
        private val EMAIL_REGEX = Regex(
            "[a-zA-Z0-9+._%\\-]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+"
        )
    }

    sealed interface Event {
        object NavigateToMain : Event
        data class ShowMessage(@StringRes val messageRes: Int) : Event
        /** Mensajes dinámicos del validador de contraseñas. */
        data class ShowMessageText(val message: String) : Event
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    fun register(email: String, confirmEmail: String, password: String, confirmPassword: String) {
        val trimmedEmail = email.trim()
        val trimmedConfirmEmail = confirmEmail.trim()

        if (trimmedEmail.isEmpty() || trimmedConfirmEmail.isEmpty() ||
            password.isEmpty() || confirmPassword.isEmpty()
        ) {
            _events.trySend(Event.ShowMessage(R.string.error_empty_fields))
            return
        }
        if (!EMAIL_REGEX.matches(trimmedEmail)) {
            _events.trySend(Event.ShowMessage(R.string.error_invalid_email))
            return
        }
        if (trimmedEmail != trimmedConfirmEmail) {
            _events.trySend(Event.ShowMessage(R.string.error_email_mismatch))
            return
        }
        val passwordValidation = passwordChangeManager.validatePassword(password)
        if (!passwordValidation.isValid) {
            val message = passwordValidation.errorMessage
            if (message != null) {
                _events.trySend(Event.ShowMessageText(message))
            } else {
                _events.trySend(Event.ShowMessage(R.string.error_authentication))
            }
            return
        }
        if (password != confirmPassword) {
            _events.trySend(Event.ShowMessage(R.string.error_password_mismatch))
            return
        }

        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            val result = authRepository.registerWithEmail(trimmedEmail, password)
            _isLoading.value = false
            result
                .onSuccess { _events.send(Event.NavigateToMain) }
                .onFailure { error ->
                    val messageRes = when (error) {
                        is AuthRepository.RegisterError.EmailAlreadyInUse -> R.string.error_email_in_use
                        else -> R.string.error_authentication
                    }
                    _events.send(Event.ShowMessage(messageRes))
                }
        }
    }
}

package com.monsteraltech.skincare.login

import kotlinx.coroutines.CancellationException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.data.SessionManager
import com.monsteraltech.skincare.data.identity.ActiveProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

@HiltViewModel
class SessionCheckViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val activeProfile: ActiveProfile
) : ViewModel() {

    companion object {
        private const val MIN_LOADING_TIME_MS = 1500L
        private const val SUCCESS_TRANSITION_DELAY_MS = 300L
        private const val OFFLINE_FALLBACK_DELAY_MS = 2000L
    }

    sealed interface UiState {
        object Splash : UiState
        object Loading : UiState
        object Success : UiState
        data class Error(@StringRes val messageRes: Int) : UiState
    }

    enum class Destination { MAIN, LOGIN }
    private val _uiState = MutableStateFlow<UiState>(UiState.Splash)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private val _destination = MutableStateFlow<Destination?>(null)
    val destination = _destination.asStateFlow()

    private var isChecking = false

    fun checkSession() {
        if (isChecking || _destination.value != null) return
        isChecking = true
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading
                if (activeProfile.isLocal) {
                    _uiState.value = UiState.Success
                    _destination.value = Destination.MAIN
                    return@launch
                }
                val startTime = System.currentTimeMillis()
                val isSessionValid = sessionManager.isSessionValid(fastMode = true)
                val elapsed = System.currentTimeMillis() - startTime
                // Mantener el estado de carga un mínimo para evitar parpadeos.
                if (elapsed < MIN_LOADING_TIME_MS) {
                    delay(MIN_LOADING_TIME_MS - elapsed)
                }
                if (isSessionValid) {
                    _uiState.value = UiState.Success
                    delay(SUCCESS_TRANSITION_DELAY_MS)
                    _destination.value = Destination.MAIN
                } else {
                    _destination.value = Destination.LOGIN
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                handleSessionCheckError(e)
            } finally {
                isChecking = false
            }
        }
    }

    /**
     * Sin red no se puede verificar contra Firebase: si hay una sesión local
     * no caducada se permite el acceso offline; si no, se vuelve al login.
     */
    private suspend fun handleSessionCheckError(exception: Exception) {
        if (!isNetworkError(exception)) {
            _uiState.value = UiState.Error(R.string.session_check_error_generic)
            return
        }
        _uiState.value = UiState.Error(R.string.session_check_error_network)
        delay(OFFLINE_FALLBACK_DELAY_MS)
        val storedSession = try {
            sessionManager.getStoredSession()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            null
        }
        if (storedSession != null && !storedSession.isExpired()) {
            _uiState.value = UiState.Success
            delay(SUCCESS_TRANSITION_DELAY_MS)
            _destination.value = Destination.MAIN
        } else {
            _destination.value = Destination.LOGIN
        }
    }

    private fun isNetworkError(exception: Exception): Boolean {
        return when (exception) {
            is UnknownHostException,
            is SocketTimeoutException,
            is IOException -> true
            else -> {
                val message = exception.message?.lowercase() ?: ""
                message.contains("network") ||
                        message.contains("timeout") ||
                        message.contains("connection") ||
                        message.contains("unreachable")
            }
        }
    }
}

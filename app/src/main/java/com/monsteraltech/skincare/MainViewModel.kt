package com.monsteraltech.skincare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.skincare.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Logout survives Activity recreation; repeating local cleanup after process restore is safe. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _isLoggingOut = MutableStateFlow(false)
    val isLoggingOut = _isLoggingOut.asStateFlow()
    val requiresLogin = savedState.getStateFlow("logoutCompleted", false)

    init { if (savedState.get<Boolean>("logoutRequested") == true && !requiresLogin.value) logout() }

    fun logout() {
        if (_isLoggingOut.value || requiresLogin.value) return
        savedState["logoutRequested"] = true
        _isLoggingOut.value = true
        viewModelScope.launch {
            try {
                auth.signOut()
                savedState["logoutCompleted"] = true
            } finally { _isLoggingOut.value = false }
        }
    }
}

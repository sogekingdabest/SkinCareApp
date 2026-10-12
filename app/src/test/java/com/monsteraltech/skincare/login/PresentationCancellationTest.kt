package com.monsteraltech.skincare.login

import androidx.lifecycle.ViewModelStore
import com.monsteraltech.skincare.account.PasswordChangeManager
import com.monsteraltech.skincare.data.SessionManager
import com.monsteraltech.skincare.data.auth.AuthRepository
import com.monsteraltech.skincare.data.identity.ActiveProfile
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresentationCancellationTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun close() { store.clear(); Dispatchers.resetMain() }
    @Test fun `clearing login cancels email authentication without navigation or an error toast`() = runTest(dispatcher) {
        val auth = mockk<AuthRepository>()
        coEvery { auth.signInWithEmail(any(), any()) } coAnswers { awaitCancellation() }
        val model = LoginViewModel(auth); store.put("login", model)
        model.signInWithEmail("fixture@example.test", "fixture"); runCurrent()
        store.clear(); runCurrent()
        assertFalse(model.isLoading.value)
        assertFalse(model.requiresMain.value)
        assertNull(withTimeoutOrNull(1) { model.events.first() })
    }
    @Test fun `clearing local login does not convert cancellation to an authentication error`() = runTest(dispatcher) {
        val auth = mockk<AuthRepository>()
        coEvery { auth.continueWithoutAccount() } coAnswers { awaitCancellation() }
        val model = LoginViewModel(auth); store.put("login", model)
        model.continueWithoutAccount(); runCurrent(); store.clear(); runCurrent()
        assertFalse(model.isLoading.value)
        assertFalse(model.requiresMain.value)
        assertNull(withTimeoutOrNull(1) { model.events.first() })
    }
    @Test fun `clearing registration resets loading and emits no stale event`() = runTest(dispatcher) {
        val auth = mockk<AuthRepository>()
        val passwords = mockk<PasswordChangeManager>()
        every { passwords.validatePassword(any()) } returns PasswordChangeManager.PasswordValidationResult(true)
        coEvery { auth.registerWithEmail(any(), any()) } coAnswers { awaitCancellation() }
        val model = RegisterViewModel(auth, passwords); store.put("register", model)
        model.register("fixture@example.test", "fixture@example.test", "Fixture123!", "Fixture123!")
        runCurrent(); store.clear(); runCurrent()
        assertFalse(model.isLoading.value)
        assertFalse(model.requiresMain.value)
        assertNull(withTimeoutOrNull(1) { model.events.first() })
    }
    @Test fun `clearing session checking keeps cancellation out of its error state`() = runTest(dispatcher) {
        val session = mockk<SessionManager>()
        val profile = mockk<ActiveProfile> { every { isLocal } returns false }
        coEvery { session.isSessionValid(any()) } coAnswers { awaitCancellation() }
        val model = SessionCheckViewModel(session, profile); store.put("session", model)
        model.checkSession(); runCurrent(); store.clear(); runCurrent()
        assertEquals(SessionCheckViewModel.UiState.Loading, model.uiState.value)
        assertNull(withTimeoutOrNull(1) { model.destination.filterNotNull().first() })
    }
}

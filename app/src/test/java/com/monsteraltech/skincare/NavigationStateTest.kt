package com.monsteraltech.skincare

import androidx.lifecycle.SavedStateHandle
import com.monsteraltech.skincare.account.PasswordChangeManager
import com.monsteraltech.skincare.account.PasswordChangeViewModel
import com.monsteraltech.skincare.data.auth.AuthRepository
import com.monsteraltech.skincare.login.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationStateTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun close() { Dispatchers.resetMain() }

    @Test fun `restored completion does not repeat credential or password operations`() = runTest {
        val auth = mockk<AuthRepository>()
        val passwords = mockk<PasswordChangeManager>()
        val login = LoginViewModel(auth, SavedStateHandle(mapOf("authCompleted" to true)))
        val register = RegisterViewModel(auth, passwords, SavedStateHandle(mapOf("authCompleted" to true)))
        val reset = ForgotPasswordViewModel(auth, SavedStateHandle(mapOf("resetSent" to true)))
        val change = PasswordChangeViewModel(passwords, SavedStateHandle(mapOf("passwordChanged" to true)))
        assertTrue(login.requiresMain.value)
        assertTrue(register.requiresMain.value)
        assertTrue(reset.sent.value)
        assertTrue(change.changed.value)
        login.signInWithEmail("fixture@example.test", "fixture")
        reset.send("fixture@example.test")
        change.change("fixture", "fixture")
        coVerify(exactly = 0) { auth.signInWithEmail(any(), any()) }
        coVerify(exactly = 0) { auth.sendPasswordReset(any()) }
        coVerify(exactly = 0) { passwords.changePassword(any(), any()) }
    }

    @Test fun `logout completion remains observable when the screen has no collector`() = runTest {
        val auth = mockk<AuthRepository>()
        val response = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { auth.signOut() } coAnswers { response.await() }
        val saved = SavedStateHandle()
        val model = MainViewModel(auth, saved)
        repeat(2) { model.logout() }
        assertTrue(model.isLoggingOut.value)
        response.complete(Unit)
        assertTrue(model.requiresLogin.value)
        assertFalse(model.isLoggingOut.value)
        coVerify(exactly = 1) { auth.signOut() }
        val restored = MainViewModel(auth, SavedStateHandle(mapOf("logoutRequested" to true, "logoutCompleted" to true)))
        assertTrue(restored.requiresLogin.value)
        coVerify(exactly = 1) { auth.signOut() }
    }

    @Test fun `an interrupted logout resumes cleanup from a fresh state holder`() = runTest {
        val auth = mockk<AuthRepository>()
        coEvery { auth.signOut() } returns Unit
        val model = MainViewModel(auth, SavedStateHandle(mapOf("logoutRequested" to true)))
        assertTrue(model.requiresLogin.value)
        coVerify(exactly = 1) { auth.signOut() }
    }
}

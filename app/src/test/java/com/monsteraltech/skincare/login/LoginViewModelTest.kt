package com.monsteraltech.skincare.login

import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.data.auth.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        authRepository = mockk()
        viewModel = LoginViewModel(authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `campos vacios muestran error sin llamar al repositorio`() = runTest {
        viewModel.signInWithEmail("", "")

        val event = viewModel.events.first()
        assertEquals(LoginViewModel.Event.ShowMessage(R.string.error_empty_fields), event)
        coVerify(exactly = 0) { authRepository.signInWithEmail(any(), any()) }
    }

    @Test
    fun `login con email correcto navega a main`() = runTest {
        coEvery { authRepository.signInWithEmail("a@b.com", "1234") } returns Result.success(Unit)

        viewModel.signInWithEmail("a@b.com", "1234")

        assertEquals(LoginViewModel.Event.NavigateToMain, viewModel.events.first())
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `login fallido muestra error de autenticacion`() = runTest {
        coEvery { authRepository.signInWithEmail(any(), any()) } returns
                Result.failure(Exception("credenciales inválidas"))

        viewModel.signInWithEmail("a@b.com", "wrong")

        assertEquals(
            LoginViewModel.Event.ShowMessage(R.string.error_authentication),
            viewModel.events.first()
        )
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `el email se normaliza con trim antes de autenticar`() = runTest {
        coEvery { authRepository.signInWithEmail("a@b.com", "1234") } returns Result.success(Unit)

        viewModel.signInWithEmail("  a@b.com  ", "1234")

        coVerify { authRepository.signInWithEmail("a@b.com", "1234") }
    }

    @Test
    fun `login con google exitoso navega a main`() = runTest {
        coEvery { authRepository.signInWithGoogleIdToken("token-123") } returns Result.success(Unit)

        viewModel.signInWithGoogleToken("token-123")

        assertEquals(LoginViewModel.Event.NavigateToMain, viewModel.events.first())
    }

    @Test
    fun `login con google fallido muestra error`() = runTest {
        coEvery { authRepository.signInWithGoogleIdToken(any()) } returns
                Result.failure(Exception("token inválido"))

        viewModel.signInWithGoogleToken("token-caducado")

        assertEquals(
            LoginViewModel.Event.ShowMessage(R.string.error_authentication),
            viewModel.events.first()
        )
    }

    @Test
    fun `continuar sin cuenta activa perfil local y navega a main`() = runTest {
        coEvery { authRepository.continueWithoutAccount() } returns Unit

        viewModel.continueWithoutAccount()

        coVerify(exactly = 1) { authRepository.continueWithoutAccount() }
        assertEquals(LoginViewModel.Event.NavigateToMain, viewModel.events.first())
        assertFalse(viewModel.isLoading.value)
    }
}

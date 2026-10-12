package com.monsteraltech.skincare.login

import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.account.PasswordChangeManager
import com.monsteraltech.skincare.data.auth.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var passwordChangeManager: PasswordChangeManager
    private lateinit var viewModel: RegisterViewModel

    private val validPassword = "Abcdef1!"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        authRepository = mockk()
        passwordChangeManager = mockk()
        every { passwordChangeManager.validatePassword(any()) } returns
                PasswordChangeManager.PasswordValidationResult(true)
        viewModel = RegisterViewModel(authRepository, passwordChangeManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `campos vacios muestran error`() = runTest {
        viewModel.register("", "", "", "")

        assertEquals(
            RegisterViewModel.Event.ShowMessage(R.string.error_empty_fields),
            viewModel.events.first()
        )
        coVerify(exactly = 0) { authRepository.registerWithEmail(any(), any()) }
    }

    @Test
    fun `email invalido muestra error`() = runTest {
        viewModel.register("no-es-un-email", "no-es-un-email", validPassword, validPassword)

        assertEquals(
            RegisterViewModel.Event.ShowMessage(R.string.error_invalid_email),
            viewModel.events.first()
        )
    }

    @Test
    fun `emails que no coinciden muestran error`() = runTest {
        viewModel.register("a@b.com", "otro@b.com", validPassword, validPassword)

        assertEquals(
            RegisterViewModel.Event.ShowMessage(R.string.error_email_mismatch),
            viewModel.events.first()
        )
    }

    @Test
    fun `contrasena debil muestra el mensaje del validador`() = runTest {
        every { passwordChangeManager.validatePassword("corta") } returns
                PasswordChangeManager.PasswordValidationResult(false, "Demasiado corta")

        viewModel.register("a@b.com", "a@b.com", "corta", "corta")

        assertEquals(
            RegisterViewModel.Event.ShowMessageText("Demasiado corta"),
            viewModel.events.first()
        )
        coVerify(exactly = 0) { authRepository.registerWithEmail(any(), any()) }
    }

    @Test
    fun `contrasenas que no coinciden muestran error`() = runTest {
        viewModel.register("a@b.com", "a@b.com", validPassword, "OtraDistinta1!")

        assertEquals(
            RegisterViewModel.Event.ShowMessage(R.string.error_password_mismatch),
            viewModel.events.first()
        )
    }

    @Test
    fun `email ya registrado muestra error especifico`() = runTest {
        coEvery { authRepository.registerWithEmail(any(), any()) } returns
                Result.failure(AuthRepository.RegisterError.EmailAlreadyInUse())

        viewModel.register("a@b.com", "a@b.com", validPassword, validPassword)

        assertEquals(
            RegisterViewModel.Event.ShowMessage(R.string.error_email_in_use),
            viewModel.events.first()
        )
    }

    @Test
    fun `registro correcto navega a main`() = runTest {
        coEvery { authRepository.registerWithEmail("a@b.com", validPassword) } returns
                Result.success(Unit)

        viewModel.register("a@b.com", "a@b.com", validPassword, validPassword)

        assertEquals(true, viewModel.requiresMain.value)
    }

    @Test
    fun `el email se normaliza con trim antes de registrar`() = runTest {
        coEvery { authRepository.registerWithEmail("a@b.com", validPassword) } returns
                Result.success(Unit)

        viewModel.register(" a@b.com ", "a@b.com", validPassword, validPassword)

        coVerify { authRepository.registerWithEmail("a@b.com", validPassword) }
    }
}

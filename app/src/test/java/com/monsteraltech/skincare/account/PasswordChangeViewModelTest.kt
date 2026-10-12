package com.monsteraltech.skincare.account

import androidx.lifecycle.ViewModelStore
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordChangeViewModelTest {
    private val manager = mockk<PasswordChangeManager>()
    private val model = PasswordChangeViewModel(manager)
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `a pending password change is guarded against double clicks`() = runTest {
        val finish = CompletableDeferred<AccountResult<Unit>>()
        coEvery { manager.changePassword(any(), any()) } coAnswers { finish.await() }
        model.change("old", "new")
        model.change("old", "new")
        coVerify(exactly = 1) { manager.changePassword("old", "new") }
        finish.complete(AccountResult.success(Unit))
        assertEquals(true, model.changed.value)
        assertFalse(model.isSaving.value)
    }

    @Test fun `validation errors retain their field specific presentation message`() = runTest {
        val error = AccountResult.Error<Unit>(IllegalArgumentException(), "La contraseña actual es incorrecta", AccountResult.ErrorType.VALIDATION_ERROR)
        coEvery { manager.changePassword(any(), any()) } returns error
        model.change("old", "new")
        assertEquals(PasswordChangeViewModel.Event.Failed(error), model.events.first())
        assertFalse(model.isSaving.value)
    }

    @Test fun `destruction cancels password work before it can emit a success`() = runTest {
        var cancelled = false
        coEvery { manager.changePassword(any(), any()) } coAnswers { try { awaitCancellation() } finally { cancelled = true } }
        model.change("old", "new")
        ViewModelStore().apply { put("password", model); clear() }
        assertTrue(cancelled)
        assertFalse(model.isSaving.value)
        assertNull(withTimeoutOrNull(1) { model.events.first() })
    }
}

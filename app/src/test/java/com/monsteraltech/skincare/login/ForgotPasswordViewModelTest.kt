package com.monsteraltech.skincare.login

import androidx.lifecycle.ViewModelStore
import com.monsteraltech.skincare.data.auth.AuthRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {
    private val repository = mockk<AuthRepository>()
    private val model = ForgotPasswordViewModel(repository)
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `successful reset trims the email and emits navigation once`() = runTest {
        coEvery { repository.sendPasswordReset("fixture@example.test") } returns Result.success(Unit)
        model.send(" fixture@example.test ")
        assertEquals(ForgotPasswordViewModel.Event.Sent, model.events.first())
        assertFalse(model.isSending.value)
        coVerify(exactly = 1) { repository.sendPasswordReset("fixture@example.test") }
    }

    @Test fun `a double click cannot start two simultaneous SDK requests`() = runTest {
        val response = CompletableDeferred<Result<Unit>>()
        coEvery { repository.sendPasswordReset(any()) } coAnswers { response.await() }
        model.send("fixture@example.test")
        model.send("fixture@example.test")
        assertTrue(model.isSending.value)
        coVerify(exactly = 1) { repository.sendPasswordReset(any()) }
        response.complete(Result.failure(IllegalStateException("failure")))
        assertEquals(ForgotPasswordViewModel.Event.Failed, model.events.first())
        assertFalse(model.isSending.value)
    }

    @Test fun `clearing the ViewModel cancels the request without late navigation`() = runTest {
        var cancelled = false
        coEvery { repository.sendPasswordReset(any()) } coAnswers {
            try { awaitCancellation() } finally { cancelled = true }
        }
        model.send("fixture@example.test")
        ViewModelStore().apply { put("reset", model); clear() }
        assertTrue(cancelled)
        assertFalse(model.isSending.value)
    }

    @Test fun `encoded cancellation never becomes a failure notification`() = runTest {
        coEvery { repository.sendPasswordReset(any()) } returns Result.failure(CancellationException())
        model.send("fixture@example.test")
        assertFalse(model.isSending.value)
        assertNull(withTimeoutOrNull(1) { model.events.first() })
    }
}

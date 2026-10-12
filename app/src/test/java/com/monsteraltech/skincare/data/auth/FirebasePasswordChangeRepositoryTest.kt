package com.monsteraltech.skincare.data.auth

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.monsteraltech.skincare.account.AccountResult
import com.monsteraltech.skincare.account.PasswordValidator
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirebasePasswordChangeRepositoryTest {
    private val auth = mockk<FirebaseAuth>()
    private val user = mockk<FirebaseUser> {
        every { uid } returns "fixture"
        every { email } returns "fixture@example.test"
    }

    @Test fun `no session cannot change a password`() = runTest {
        every { auth.currentUser } returns null
        val repository = FirebasePasswordChangeRepository(auth, PasswordValidator(), UnconfinedTestDispatcher(testScheduler))
        val result = repository.changePassword("Oldpass1", "Newpass2")
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, result.getErrorOrNull()?.errorType)
    }

    @Test fun `cancelling reauthentication prevents the password write`() = runTest {
        every { auth.currentUser } returns user
        val task = TaskCompletionSource<Void>()
        every { user.reauthenticate(any()) } returns task.task
        val repository = FirebasePasswordChangeRepository(auth, PasswordValidator(), UnconfinedTestDispatcher(testScheduler))
        val job = launch { repository.changePassword("Oldpass1", "Newpass2") }
        runCurrent()
        job.cancelAndJoin()
        task.setResult(null)
        runCurrent()
        assertTrue(job.isCancelled)
        verify(exactly = 0) { user.updatePassword(any()) }
    }

    @Test fun `a changed SDK session prevents writing to the previous user`() = runTest {
        var current = user
        every { auth.currentUser } answers { current }
        val task = TaskCompletionSource<Void>()
        every { user.reauthenticate(any()) } returns task.task
        val repository = FirebasePasswordChangeRepository(auth, PasswordValidator(), UnconfinedTestDispatcher(testScheduler))
        var result: AccountResult<Unit>? = null
        val job = launch { result = repository.changePassword("Oldpass1", "Newpass2") }
        runCurrent()
        current = mockk { every { uid } returns "another" }
        task.setResult(null)
        job.join()
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, result?.getErrorOrNull()?.errorType)
        verify(exactly = 0) { user.updatePassword(any()) }
    }

    @Test fun `validated reauthentication updates only the captured user`() = runTest {
        every { auth.currentUser } returns user
        val reauth = TaskCompletionSource<Void>().apply { setResult(null) }
        val write = TaskCompletionSource<Void>().apply { setResult(null) }
        every { user.reauthenticate(any()) } returns reauth.task
        every { user.updatePassword("Newpass2") } returns write.task
        val repository = FirebasePasswordChangeRepository(auth, PasswordValidator(), UnconfinedTestDispatcher(testScheduler))
        assertTrue(repository.changePassword("Oldpass1", "Newpass2") is AccountResult.Success)
        verify(exactly = 1) { user.updatePassword("Newpass2") }
    }
}

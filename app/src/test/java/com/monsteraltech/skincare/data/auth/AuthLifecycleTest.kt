package com.monsteraltech.skincare.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.skincare.account.AccountSettingsRepository
import com.monsteraltech.skincare.data.SessionManager
import com.monsteraltech.skincare.data.identity.ActiveProfile
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthLifecycleTest {
    private val context = mockk<Context>()
    private val auth = mockk<FirebaseAuth>(relaxed = true)
    private val session = mockk<SessionManager>()
    private val data = mockk<AccountSettingsRepository>()
    private val profile = mockk<ActiveProfile>(relaxed = true)
    private val credentials = mockk<CredentialManager>()
    private val repository = AuthRepository(context, auth, session, data, profile)
    @Before fun setup() {
        mockkObject(CredentialManager.Companion)
        every { CredentialManager.create(context) } returns credentials
        coEvery { credentials.clearCredentialState(any()) } just Runs
        coEvery { session.clearSession() } returns true
    }
    @After fun close() { unmockkObject(CredentialManager.Companion) }

    @Test fun `cancelled authentication never finalizes a late SDK result`() = runTest {
        val sdk = TaskCompletionSource<AuthResult>()
        every { auth.signInWithEmailAndPassword(any(), any()) } returns sdk.task
        var result: Result<Unit>? = null
        val job = launch { result = repository.signInWithEmail("fixture@example.test", "fixture") }
        runCurrent(); job.cancelAndJoin()
        sdk.setResult(mockk(relaxed = true)); runCurrent()
        assertNull(result)
        verify(exactly = 0) { profile.activateAccountProfile() }
        coVerify(exactly = 0) { session.saveSession(any()) }
    }
    @Test fun `logout finishes local cleanup after its caller is cancelled`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Boolean>()
        coEvery { session.clearSession() } coAnswers { entered.complete(Unit); finish.await() }
        val job = launch { repository.signOut() }
        entered.await(); job.cancel(); finish.complete(true); job.join()
        assertTrue(job.isCancelled)
        verify(exactly = 1) { profile.clear() }
        verify(exactly = 1) { auth.signOut() }
        coVerify(exactly = 1) { credentials.clearCredentialState(any()) }
    }
    @Test fun `local-profile transition completes when its view disappears`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Boolean>()
        coEvery { session.clearSession() } coAnswers { entered.complete(Unit); finish.await() }
        val job = launch { repository.continueWithoutAccount() }
        entered.await(); job.cancel(); finish.complete(true); job.join()
        assertTrue(job.isCancelled)
        verify(exactly = 1) { profile.activateLocalProfile() }
        verify(exactly = 1) { auth.signOut() }
        coVerify(exactly = 1) { credentials.clearCredentialState(any()) }
    }
    @Test fun `credential cleanup has a bounded wait and preserves the local transition`() = runTest {
        coEvery { credentials.clearCredentialState(any()) } coAnswers { awaitCancellation() }
        repository.continueWithoutAccount()
        assertEquals(5000L, testScheduler.currentTime)
        verify(exactly = 1) { profile.activateLocalProfile() }
    }
}

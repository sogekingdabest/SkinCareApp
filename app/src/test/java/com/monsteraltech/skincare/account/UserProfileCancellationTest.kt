package com.monsteraltech.skincare.account

import com.monsteraltech.skincare.data.FirebaseDataManager
import com.monsteraltech.skincare.data.identity.ActiveProfile
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UserProfileCancellationTest {
    private val data = mockk<FirebaseDataManager>()
    private val profile = mockk<ActiveProfile> {
        every { ownerId } returns "fixture"
        every { isLocal } returns false
        every { requireOwnerId() } returns "fixture"
    }
    private val manager = UserProfileManager(data, mockk(), profile)
    private suspend fun assertCancelled(operation: suspend () -> Unit) {
        try { operation(); fail("Expected cancellation") } catch (_: CancellationException) { }
    }
    @Test fun `profile cancellation is never converted to a retry error`() = runTest {
        coEvery { data.getCurrentUserProfile() } throws CancellationException()
        assertCancelled { manager.getCurrentUserInfo() }
        coVerify(exactly = 1) { data.getCurrentUserProfile() }
    }
    @Test fun `settings cancellation never becomes default preferences`() = runTest {
        coEvery { data.getUserSettings("fixture") } throws CancellationException()
        assertCancelled { manager.getUserSettings() }
        coVerify(exactly = 1) { data.getUserSettings("fixture") }
    }
    @Test fun `write cancellation propagates without retrying the write`() = runTest {
        coEvery { data.updateUserSettings(any(), "fixture") } throws CancellationException()
        assertCancelled { manager.updateUserSettings(AccountSettings(userId = "fixture")) }
        coVerify(exactly = 1) { data.updateUserSettings(any(), "fixture") }
    }
    @Test fun `preferences from a different profile are never written`() = runTest {
        val result = manager.updateUserSettings(AccountSettings(userId = "another"))
        assertTrue(result.isError())
        coVerify(exactly = 0) { data.updateUserSettings(any(), any()) }
    }
}

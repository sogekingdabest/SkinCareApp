package com.monsteraltech.skincare.account

import com.monsteraltech.skincare.account.AccountSettingsRepository
import com.monsteraltech.skincare.data.identity.ActiveProfile
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UserProfileCancellationTest {
    private val data = mockk<AccountSettingsRepository>()
    private val profile = mockk<ActiveProfile> {
        every { ownerId } returns "fixture"
        every { isLocal } returns false
        every { requireOwnerId() } returns "fixture"
    }
    private val source = mockk<CurrentAccountProfileSource>()
    private val manager = UserProfileManager(data, source, profile)
    private suspend fun assertCancelled(operation: suspend () -> Unit) {
        try { operation(); fail("Expected cancellation") } catch (_: CancellationException) { }
    }
    @Test fun `profile cancellation is never converted to a retry error`() = runTest {
        coEvery { source.getCurrentUserProfile() } throws CancellationException()
        assertCancelled { manager.getCurrentUserInfo() }
        coVerify(exactly = 1) { source.getCurrentUserProfile() }
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

    @Test fun `a different SDK profile is rejected instead of displayed`() = runTest {
        coEvery { source.getCurrentUserProfile() } returns UserInfo(uid = "another")
        assertTrue(manager.getCurrentUserInfo().isError())
    }

    @Test fun `an atomic patch propagates cancellation without replaying it`() = runTest {
        coEvery { data.applyPatch(any(), "fixture") } throws CancellationException()
        assertCancelled { manager.applySettingsPatch(AccountSettingsPatch(darkModeEnabled = true), "fixture") }
        coVerify(exactly = 1) { data.applyPatch(any(), "fixture") }
    }

    @Test fun `patches for another owner are rejected before touching preferences`() = runTest {
        assertTrue(manager.applySettingsPatch(AccountSettingsPatch(darkModeEnabled = true), "another").isError())
        coVerify(exactly = 0) { data.applyPatch(any(), any()) }
    }
}

package com.monsteraltech.skincare.notifications

import androidx.lifecycle.ViewModelStore
import com.monsteraltech.skincare.account.AccountProfileRepository
import com.monsteraltech.skincare.account.AccountResult
import com.monsteraltech.skincare.account.AccountSettings
import com.monsteraltech.skincare.account.AccountSettingsPatch
import com.monsteraltech.skincare.data.identity.ActiveProfile
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {
    private val repository = mockk<AccountProfileRepository>()
    private var owner: String? = "fixture"
    private val profile = mockk<ActiveProfile> { every { ownerId } answers { owner } }
    private var stored = AccountSettings(userId = "fixture", notificationsEnabled = true)
    private val applied = mutableListOf<AccountSettings>()
    private val scheduler = object : NotificationScheduleService {
        override fun apply(settings: AccountSettings) { applied += settings }
    }
    private val update = UpdateNotificationSettings(repository, profile, scheduler)
    private val model = NotificationSettingsViewModel(repository, profile, update)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.getUserSettings() } answers { AccountResult.success(stored) }
        coEvery { repository.applySettingsPatch(any(), any()) } answers {
            stored = firstArg<AccountSettingsPatch>().applyTo(stored)
            AccountResult.success(stored)
        }
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `notification edits merge into fresh account settings`() = runTest {
        model.refresh()
        stored = stored.copy(darkModeEnabled = true, language = "en")
        model.setSound(false)
        assertEquals(NotificationSettingsViewModel.Event.Saved, model.events.first())
        assertTrue(stored.darkModeEnabled)
        assertEquals("en", stored.language)
        assertFalse(stored.notificationSound)
        assertEquals(listOf(stored), applied)
    }

    @Test fun `rapid edits are serialized and retain all chosen notification fields`() = runTest {
        model.refresh()
        val firstWrite = CompletableDeferred<Boolean>()
        var writes = 0
        coEvery { repository.applySettingsPatch(any(), any()) } coAnswers {
            if (++writes == 1) firstWrite.await()
            stored = firstArg<AccountSettingsPatch>().applyTo(stored)
            AccountResult.success(stored)
        }
        model.setVibration(false)
        model.setSound(false)
        firstWrite.complete(true)
        assertEquals(2, writes)
        assertFalse(stored.notificationSound)
        assertFalse(stored.notificationVibration)
        assertEquals(2, applied.size)
        assertFalse((model.state.value as NotificationSettingsViewModel.State.Ready).isSaving)
    }

    @Test fun `failed settings writes never schedule notifications or publish success`() = runTest {
        model.refresh()
        coEvery { repository.applySettingsPatch(any(), any()) } returns AccountResult.error(IllegalStateException("write failed"))
        model.setCheckups(true)
        assertEquals(NotificationSettingsViewModel.Event.Failed, model.events.first())
        assertTrue(applied.isEmpty())
        assertFalse(stored.moleCheckupsEnabled)
        assertFalse((model.state.value as NotificationSettingsViewModel.State.Ready).settings.moleCheckupsEnabled)
    }

    @Test fun `a profile change while patching prevents scheduling the old profile`() = runTest {
        model.refresh()
        coEvery { repository.applySettingsPatch(any(), any()) } answers { owner = "another"; AccountResult.success(stored) }
        model.setTime("18:30")
        assertTrue(applied.isEmpty())
        coVerify(exactly = 1) { repository.applySettingsPatch(any(), any()) }
        assertEquals(NotificationSettingsViewModel.State.Failed, model.state.value)
    }

    @Test fun `destroying the owner during a committed edit finishes scheduling`() = runTest {
        model.refresh()
        val write = CompletableDeferred<Boolean>()
        coEvery { repository.applySettingsPatch(any(), any()) } coAnswers {
            write.await()
            stored = firstArg<AccountSettingsPatch>().applyTo(stored)
            AccountResult.success(stored)
        }
        model.setFrequency(14)
        ViewModelStore().apply { put("notifications", model); clear() }
        assertTrue(applied.isEmpty())
        write.complete(true)
        assertEquals(14, applied.single().moleCheckupFrequency)
        assertEquals(14, stored.moleCheckupFrequency)
    }

    @Test fun `an old profile snapshot is never rendered`() = runTest {
        coEvery { repository.getUserSettings() } answers { owner = "another"; AccountResult.success(stored) }
        model.refresh()
        assertEquals(NotificationSettingsViewModel.State.Failed, model.state.value)
    }

    @Test fun `encoded cancellation in a read never becomes an editable default`() = runTest {
        coEvery { repository.getUserSettings() } returns AccountResult.Error(CancellationException(), "cancelled")
        model.refresh()
        assertEquals(NotificationSettingsViewModel.State.Loading, model.state.value)
        coVerify(exactly = 0) { repository.applySettingsPatch(any(), any()) }
    }
}

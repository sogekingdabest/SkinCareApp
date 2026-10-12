package com.monsteraltech.skincare.account

import androidx.lifecycle.ViewModelStore
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.utils.ThemeManager
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<AccountProfileRepository>()
    private val themeManager = mockk<ThemeManager>(relaxed = true)
    private lateinit var model: AccountViewModel
    private val store = ViewModelStore()
    private var saved = AccountSettings(userId = "fixture", language = "en", moleCheckupFrequency = 21)

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { repository.isUserAuthenticated() } returns true
        coEvery { repository.getCurrentUserInfo() } returns AccountResult.success(UserInfo(uid = "fixture"))
        coEvery { repository.getUserSettings() } coAnswers { AccountResult.success(saved) }
        coEvery { repository.applySettingsPatch(any(), any()) } coAnswers { saved = firstArg<AccountSettingsPatch>().applyTo(saved); AccountResult.success(saved) }
        model = AccountViewModel(repository, themeManager)
        store.put("account", model)
    }
    @After fun close() { store.clear(); Dispatchers.resetMain() }

    @Test fun `repeated refresh keeps in-flight loads and retains state without a collector`() = runTest(dispatcher) {
        val info = CompletableDeferred<AccountResult<UserInfo>>()
        val settings = CompletableDeferred<AccountResult<AccountSettings>>()
        coEvery { repository.getCurrentUserInfo() } coAnswers { info.await() }
        coEvery { repository.getUserSettings() } coAnswers { settings.await() }
        model.refresh(); runCurrent(); model.refresh(); runCurrent()
        info.complete(AccountResult.success(UserInfo(uid = "fixture", displayName = "Loaded")))
        settings.complete(AccountResult.success(saved)); runCurrent()
        assertEquals("Loaded", model.uiState.value.userInfo.getDataOrNull()!!.displayName)
        assertEquals(saved, model.uiState.value.settings)
        coVerify(exactly = 1) { repository.getCurrentUserInfo() }
        coVerify(exactly = 1) { repository.getUserSettings() }
    }

    @Test fun `rapid setting edits merge serially with latest persisted values`() = runTest(dispatcher) {
        coEvery { repository.applySettingsPatch(any(), any()) } coAnswers {
            delay(50); saved = firstArg<AccountSettingsPatch>().applyTo(saved); AccountResult.success(saved)
        }
        model.refresh(); runCurrent()
        model.changeSetting(AccountViewModel.Setting.NOTIFICATIONS, false)
        model.changeSetting(AccountViewModel.Setting.DARK_MODE, true)
        advanceUntilIdle()
        assertFalse(saved.notificationsEnabled)
        assertTrue(saved.darkModeEnabled)
        assertEquals("en", saved.language)
        assertEquals(21, saved.moleCheckupFrequency)
        assertEquals(saved, model.uiState.value.settings)
        assertEquals(true, model.uiState.value.committedDarkMode)
        assertFalse(model.uiState.value.isSaving)
        verify(exactly = 1) { themeManager.updateThemeFromSettings(true) }
    }

    @Test fun `an external settings change is preserved by the next patch`() = runTest(dispatcher) {
        model.refresh(); runCurrent()
        saved = saved.copy(moleCheckupFrequency = 35, notificationSound = false)
        model.changeSetting(AccountViewModel.Setting.DARK_MODE, true); advanceUntilIdle()
        assertEquals(35, saved.moleCheckupFrequency)
        assertFalse(saved.notificationSound)
    }

    @Test fun `false write result restores confirmed settings and does not apply the requested theme`() = runTest(dispatcher) {
        model.refresh(); runCurrent()
        coEvery { repository.applySettingsPatch(any(), any()) } returns AccountResult.error(IllegalStateException("write failed"))
        model.changeSetting(AccountViewModel.Setting.DARK_MODE, true); advanceUntilIdle()
        assertEquals(saved, model.uiState.value.settings)
        assertEquals(false, model.uiState.value.committedDarkMode)
        verify(exactly = 0) { themeManager.updateThemeFromSettings(any()) }
        assertEquals(AccountViewModel.Event.Error(R.string.settings_error), model.events.first())
    }

    @Test fun `read failure never overwrites preferences`() = runTest(dispatcher) {
        model.refresh(); runCurrent()
        coEvery { repository.getUserSettings() } returns AccountResult.error(Exception("read failed"))
        model.changeSetting(AccountViewModel.Setting.NOTIFICATIONS, false); advanceUntilIdle()
        coVerify(exactly = 0) { repository.applySettingsPatch(any(), any()) }
        assertEquals(saved, model.uiState.value.settings)
        assertEquals(AccountViewModel.Event.Error(R.string.settings_error), model.events.first())
    }

    @Test fun `clearing the ViewModel cancels pending writes without an error event`() = runTest(dispatcher) {
        model.refresh(); runCurrent()
        var cancelled = false
        coEvery { repository.applySettingsPatch(any(), any()) } coAnswers {
            try { awaitCancellation() } finally { cancelled = true }
        }
        model.changeSetting(AccountViewModel.Setting.DARK_MODE, true); runCurrent()
        assertTrue(model.uiState.value.isSaving)
        store.clear(); runCurrent()
        assertTrue(cancelled)
        assertEquals(saved, model.uiState.value.settings)
        assertFalse(model.uiState.value.isSaving)
        assertNull(withTimeoutOrNull(1) { model.events.first() })
    }

    @Test fun `cancellation carried as an error remains cancellation`() = runTest(dispatcher) {
        coEvery { repository.getCurrentUserInfo() } returns AccountResult.error(CancellationException("cancelled"))
        model.refresh(); advanceUntilIdle()
        assertTrue(model.uiState.value.userInfo is AccountResult.Loading)
        assertEquals(saved, model.uiState.value.settings)
    }

    @Test fun `unauthenticated edits never read or write settings`() = runTest(dispatcher) {
        every { repository.isUserAuthenticated() } returns false
        model.changeSetting(AccountViewModel.Setting.DARK_MODE, true); runCurrent()
        coVerify(exactly = 0) { repository.getUserSettings() }
        coVerify(exactly = 0) { repository.applySettingsPatch(any(), any()) }
        assertEquals(AccountViewModel.Event.Error(R.string.error_authentication), model.events.first())
    }
}

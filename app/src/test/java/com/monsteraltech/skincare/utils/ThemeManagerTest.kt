package com.monsteraltech.skincare.utils

import androidx.appcompat.app.AppCompatDelegate
import com.monsteraltech.skincare.account.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeManagerTest {
    private val repository = mockk<AccountProfileRepository>()
    private val manager = ThemeManager()
    private var mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    @Before fun setup() {
        mockkStatic(AppCompatDelegate::class)
        every { AppCompatDelegate.getDefaultNightMode() } answers { mode }
        every { AppCompatDelegate.setDefaultNightMode(any()) } answers { mode = firstArg() }
        manager.initialize(repository)
    }
    @After fun close() { unmockkStatic(AppCompatDelegate::class) }
    @Test fun `unchanged mode does not ask AppCompat to recreate again`() = runTest {
        coEvery { repository.getUserSettings() } returns AccountResult.success(AccountSettings())
        manager.applyThemeFromUserSettings()
        manager.updateThemeFromSettings(false)
        verify(exactly = 0) { AppCompatDelegate.setDefaultNightMode(any()) }
    }
    @Test fun `explicit user selection wins over a late initial load`() = runTest {
        val pending = CompletableDeferred<AccountResult<AccountSettings>>()
        coEvery { repository.getUserSettings() } coAnswers { pending.await() }
        val job = launch { manager.applyThemeFromUserSettings() }; runCurrent()
        manager.updateThemeFromSettings(true)
        pending.complete(AccountResult.success(AccountSettings(darkModeEnabled = false))); job.join()
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, mode)
        verify(exactly = 1) { AppCompatDelegate.setDefaultNightMode(any()) }
    }
    @Test fun `newer request wins when loads finish in the opposite order`() = runTest {
        val first = CompletableDeferred<AccountResult<AccountSettings>>()
        val second = CompletableDeferred<AccountResult<AccountSettings>>()
        var calls = 0
        coEvery { repository.getUserSettings() } coAnswers { if (++calls == 1) first.await() else second.await() }
        val old = launch { manager.applyThemeFromUserSettings() }; runCurrent()
        val recent = launch { manager.applyThemeFromUserSettings() }; runCurrent()
        second.complete(AccountResult.success(AccountSettings(darkModeEnabled = true))); recent.join()
        first.complete(AccountResult.success(AccountSettings())); old.join()
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, mode)
    }
    @Test fun `caller cancellation does not apply a fallback theme`() = runTest {
        coEvery { repository.getUserSettings() } coAnswers { awaitCancellation() }
        val job = launch { manager.applyThemeFromUserSettings() }; runCurrent()
        job.cancelAndJoin()
        verify(exactly = 0) { AppCompatDelegate.setDefaultNightMode(any()) }
    }
    @Test fun `ordinary settings failure applies system theme`() = runTest {
        mode = AppCompatDelegate.MODE_NIGHT_YES
        coEvery { repository.getUserSettings() } throws IllegalStateException("settings unavailable")
        manager.applyThemeFromUserSettings()
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, mode)
    }
}

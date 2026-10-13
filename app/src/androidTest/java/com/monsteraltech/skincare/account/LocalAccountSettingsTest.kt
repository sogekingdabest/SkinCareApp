package com.monsteraltech.skincare.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsteraltech.skincare.data.account.LocalAccountSettingsRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAccountSettingsTest {
    @Test fun optionalAnalysisProfilesAreLocalOwnerScopedAndSurviveJsonRoundTrips() = runBlocking {
        val owner = "profile-${UUID.randomUUID()}"
        val other = "profile-other-${UUID.randomUUID()}"
        val repository = LocalAccountSettingsRepository(context, Dispatchers.IO)
        try {
            val profile = AnalysisProfile(45, 2026, com.monsteraltech.skincare.analysis.model.AnalysisSex.FEMALE)
            assertNotNull(repository.applyPatch(AccountSettingsPatch(analysisProfile = profile), owner))
            assertEquals(profile, repository.getUserSettings(owner).analysisProfile)
            assertNull(repository.getUserSettings(other).analysisProfile)
            repository.applyPatch(AccountSettingsPatch(darkModeEnabled = true), owner)
            assertEquals(profile, repository.getUserSettings(owner).analysisProfile)
            repository.applyPatch(AccountSettingsPatch(analysisProfile = AnalysisProfile()), owner)
            assertEquals(AnalysisProfile(), repository.getUserSettings(owner).analysisProfile)
        } finally { remove(owner, other) }
    }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val preferences get() = context.getSharedPreferences("private_account_settings", 0)
    private suspend fun remove(vararg owners: String) = withContext(Dispatchers.IO) {
        preferences.edit().apply { owners.forEach { remove("account_settings_$it") } }.commit()
    }

    @Test fun existingSettingsSurviveInitializationAndRemainIsolatedBetweenOwners() = runBlocking {
        val first = "settings-a-${UUID.randomUUID()}"
        val second = "settings-b-${UUID.randomUUID()}"
        val repository = LocalAccountSettingsRepository(context, Dispatchers.IO)
        try {
            assertTrue(repository.updateUserSettings(AccountSettings(darkModeEnabled = true, language = "en", autoBackup = true), first))
            assertTrue(repository.initializeUserSettings(first))
            assertTrue(repository.initializeUserSettings(second))
            val a = repository.getUserSettings(first)
            val b = repository.getUserSettings(second)
            assertEquals(first, a.userId)
            assertTrue(a.darkModeEnabled)
            assertEquals("en", a.language)
            assertFalse(a.autoBackup)
            assertEquals(second, b.userId)
            assertFalse(b.darkModeEnabled)
            assertEquals("es", b.language)
        } finally { remove(first, second) }
    }

    @Test fun legacyJsonAndCorruptPreferencesKeepTheirOwnerAndSafeDefaults() = runBlocking {
        val legacy = "settings-legacy-${UUID.randomUUID()}"
        val corrupt = "settings-corrupt-${UUID.randomUUID()}"
        val repository = LocalAccountSettingsRepository(context, Dispatchers.IO)
        try {
            withContext(Dispatchers.IO) {
                assertTrue(preferences.edit()
                    .putString("account_settings_$legacy", """{"userId":"old-owner","darkModeEnabled":true,"language":"es","autoBackup":true,"moleCheckupFrequency":14}""")
                    .putString("account_settings_$corrupt", "invalid-json").commit())
            }
            val settings = repository.getUserSettings(legacy)
            assertEquals(legacy, settings.userId)
            assertTrue(settings.darkModeEnabled)
            assertEquals(14, settings.moleCheckupFrequency)
            assertFalse(settings.autoBackup)
            assertEquals(AccountSettings(userId = corrupt, autoBackup = false), repository.getUserSettings(corrupt))
        } finally { remove(legacy, corrupt) }
    }

    @Test fun concurrentFieldPatchesNeverOverwriteChangesFromTheOtherScreen() = runBlocking {
        val owner = "settings-race-${UUID.randomUUID()}"
        val repository = LocalAccountSettingsRepository(context, Dispatchers.IO)
        try {
            assertTrue(repository.updateUserSettings(AccountSettings(language = "en", moleCheckupFrequency = 21), owner))
            listOf(
                async(Dispatchers.Default) { repository.applyPatch(AccountSettingsPatch(darkModeEnabled = true), owner) },
                async(Dispatchers.Default) { repository.applyPatch(AccountSettingsPatch(notificationSound = false), owner) },
                async(Dispatchers.Default) { repository.initializeUserSettings(owner) }
            ).awaitAll()
            val stored = repository.getUserSettings(owner)
            assertTrue(stored.darkModeEnabled)
            assertFalse(stored.notificationSound)
            assertEquals("en", stored.language)
            assertEquals(21, stored.moleCheckupFrequency)
        } finally { remove(owner) }
    }
}

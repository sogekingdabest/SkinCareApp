package com.monsteraltech.skincare.account

import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsteraltech.skincare.data.account.LocalAccountSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class AccountSettingsDispatcherTest {
    @Test fun settingsCalledFromMainPerformTheirDiskWorkOffMain() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val owner = "dispatcher-fixture-${UUID.randomUUID()}"
        val data = LocalAccountSettingsRepository(context, Dispatchers.IO)
        val violations = CopyOnWriteArrayList<String>()
        try {
            withContext(Dispatchers.Main) {
                val previous = StrictMode.getThreadPolicy()
                StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                    .penaltyListener(Executor { it.run() }) { violation ->
                        if (violation.stackTrace.any { it.className.contains("LocalAccountSettingsRepository") }) violations += violation.toString()
                    }.build())
                try {
                    assertTrue(data.initializeUserSettings(owner))
                    assertTrue(data.updateUserSettings(AccountSettings(userId = owner, darkModeEnabled = true), owner))
                    assertTrue(data.getUserSettings(owner).darkModeEnabled)
                } finally { StrictMode.setThreadPolicy(previous) }
            }
            assertTrue("Main-thread preferences IO: $violations", violations.isEmpty())
        } finally {
            withContext(Dispatchers.IO) {
                context.getSharedPreferences("private_account_settings", 0).edit().remove("account_settings_$owner").commit()
            }
        }
    }
}

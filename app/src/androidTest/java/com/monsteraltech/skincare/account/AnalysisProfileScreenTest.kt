package com.monsteraltech.skincare.account

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.skincare.analysis.model.AnalysisSex
import com.monsteraltech.skincare.data.account.LocalAccountSettingsRepository
import com.monsteraltech.skincare.data.identity.DeviceActiveProfile
import java.time.Year
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisProfileScreenTest {
    @Test fun optionalProfileEditsSurviveRecreationAndRemainLocal() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val active = DeviceActiveProfile(context, FirebaseAuth.getInstance())
        val originallyLocal = active.isLocal
        val owner = active.activateLocalProfile()
        val repository = LocalAccountSettingsRepository(context, Dispatchers.IO)
        val original = repository.getUserSettings(owner)
        val device = UiDevice.getInstance(instrumentation)
        var scenario: ActivityScenario<AnalysisProfileActivity>? = null
        try {
            repository.applyPatch(AccountSettingsPatch(analysisProfile = AnalysisProfile(45, Year.now().value, AnalysisSex.MALE)), owner)
            scenario = ActivityScenario.launch(AnalysisProfileActivity::class.java)
            assertNotNull(device.wait(Until.findObject(By.res(context.packageName, "age_input").text("45")), 5000))
            device.findObject(By.res(context.packageName, "age_input")).text = "46"
            device.findObject(By.res(context.packageName, "sex_input")).click()
            val female = device.wait(Until.findObject(By.text("Femenino")), 5000)
            assertNotNull(female); female.click()
            assertTrue(device.wait(Until.gone(By.clazz("android.widget.ListView")), 5000))
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals(AnalysisSex.FEMALE, ViewModelProvider(it)[AnalysisProfileViewModel::class.java].state.value.sex) }
            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertNotNull(device.wait(Until.findObject(By.res(context.packageName, "age_input").text("46")), 5000))
            assertNotNull(device.wait(Until.findObject(By.res(context.packageName, "sex_input").text("Femenino")), 5000))
            assertTrue(device.wait(Until.gone(By.clazz("android.widget.ListView")), 5000))
            val save = device.wait(Until.findObject(By.res(context.packageName, "save_button").enabled(true)), 5000)
            assertNotNull(save)
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "profile-verified.png"))
            save.click()
            assertTrue(device.wait(Until.gone(By.res(context.packageName, "age_input")), 5000))
            assertEquals(AnalysisProfile(46, Year.now().value, AnalysisSex.FEMALE), repository.getUserSettings(owner).analysisProfile)
        } catch (failure: AssertionError) {
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "profile-failure.png"))
            device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null), "profile-failure.xml"))
            throw failure
        } finally {
            scenario?.close()
            repository.updateUserSettings(original, owner)
            if (!originallyLocal) active.clear()
        }
    }
}

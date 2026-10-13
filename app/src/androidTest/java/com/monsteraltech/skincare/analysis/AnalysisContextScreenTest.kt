package com.monsteraltech.skincare.analysis

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.skincare.account.*
import com.monsteraltech.skincare.analysis.model.AnalysisSex
import com.monsteraltech.skincare.analysis.model.AnatomicalSite
import com.monsteraltech.skincare.body.domain.BodyRegionId
import com.monsteraltech.skincare.body.presentation.BodyRegionContract
import com.monsteraltech.skincare.camera.CaptureDraftStore
import com.monsteraltech.skincare.camera.MoleCaptureContract
import com.monsteraltech.skincare.data.account.LocalAccountSettingsRepository
import com.monsteraltech.skincare.data.identity.DeviceActiveProfile
import java.time.Year
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisContextScreenTest {
    @Test fun captureOverridesSurviveRecreationWithoutEditingTheLocalProfile() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val active = DeviceActiveProfile(context, FirebaseAuth.getInstance())
        val originallyLocal = active.isLocal
        val owner = active.activateLocalProfile()
        val settings = LocalAccountSettingsRepository(context, Dispatchers.IO)
        val original = settings.getUserSettings(owner)
        val profile = AnalysisProfile(45, Year.now().value, AnalysisSex.MALE)
        val drafts = CaptureDraftStore(context, active)
        val draft = drafts.create()
        val device = UiDevice.getInstance(instrumentation)
        var scenario: ActivityScenario<AnalysisResultActivity>? = null
        try {
            settings.applyPatch(AccountSettingsPatch(analysisProfile = profile), owner)
            val bitmap = instrumentation.context.assets.open("model-parity/legacy-shape.png").use { BitmapFactory.decodeStream(it) }
            draft.file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
            bitmap.recycle(); drafts.markReady(draft.id)
            val intent = Intent(context, AnalysisResultActivity::class.java)
                .putExtra(MoleCaptureContract.EXTRA_CAPTURE_ID, draft.id)
                .putExtra(BodyRegionContract.EXTRA_REGION_ID, BodyRegionId.RIGHT_ARM.value)
            scenario = ActivityScenario.launch(intent)
            fun reveal(id: String) {
                UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().resourceId("${context.packageName}:id/$id"))
            }
            reveal("context_age_input")
            assertNotNull(device.wait(Until.findObject(By.res(context.packageName, "context_age_input").text("45")), 5000))
            device.findObject(By.res(context.packageName, "context_age_input")).text = "47"
            reveal("context_sex_input")
            device.findObject(By.res(context.packageName, "context_sex_input")).click()
            val unknown = device.wait(Until.findObject(By.clazz("android.widget.TextView").text("Sin especificar")), 5000)
            assertNotNull(unknown); unknown.click()
            assertTrue(device.wait(Until.gone(By.clazz("android.widget.ListView")), 5000))
            reveal("context_site_input")
            device.findObject(By.res(context.packageName, "context_site_input")).click()
            val palms = device.wait(Until.findObject(By.text("Palma o planta del pie")), 5000)
            assertNotNull(palms); palms.click()
            assertTrue(device.wait(Until.gone(By.clazz("android.widget.ListView")), 5000))
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val recorded = ViewModelProvider(it)[AnalysisContextViewModel::class.java].context()!!
                assertEquals(47, recorded.ageYears)
                assertNull(recorded.sex)
                assertEquals(AnatomicalSite.PALMS_SOLES, recorded.site)
            }
            scenario.recreate()
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(AnatomicalSite.PALMS_SOLES, ViewModelProvider(it)[AnalysisContextViewModel::class.java].context()!!.site)
            }
            reveal("context_age_input")
            assertNotNull(device.wait(Until.findObject(By.res(context.packageName, "context_age_input").text("47")), 5000))
            reveal("context_site_input")
            assertNotNull(device.wait(Until.findObject(By.res(context.packageName, "context_site_input").text("Palma o planta del pie")), 5000))
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "context-verified.png"))
            assertEquals(profile, settings.getUserSettings(owner).analysisProfile)
        } catch (failure: AssertionError) {
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "context-failure.png"))
            device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null), "context-failure.xml"))
            throw failure
        } finally {
            scenario?.close(); drafts.discard(draft.id)
            settings.updateUserSettings(original, owner)
            if (!originallyLocal) active.clear()
        }
    }
}

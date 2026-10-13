package com.monsteraltech.skincare.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.monsteraltech.skincare.account.*
import com.monsteraltech.skincare.analysis.model.*
import com.monsteraltech.skincare.data.identity.ActiveProfile
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import java.time.Year
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AnalysisContextViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = mockk<AccountSettingsRepository>()
    private val active = mockk<ActiveProfile>()
    private var owner: String? = "owner"
    private val store = ViewModelStore()
    @Before fun start() {
        Dispatchers.setMain(dispatcher)
        every { active.ownerId } answers { owner }
        coEvery { settings.getUserSettings("owner") } returns AccountSettings(analysisProfile = AnalysisProfile(45, Year.now().value, AnalysisSex.MALE))
    }
    @After fun stop() { store.clear(); Dispatchers.resetMain() }
    private fun model(saved: SavedStateHandle = SavedStateHandle()) = AnalysisContextViewModel(settings, active, saved).also { store.put("context", it) }

    @Test fun `defaults are optional and user corrections survive recreation without writing the profile`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = model(saved); runCurrent()
        assertEquals(45, first.context()!!.ageYears)
        assertNull(first.context()!!.site)
        first.setAge(""); first.setSex(null); first.setSite(AnatomicalSite.PALMS_SOLES)
        val second = model(saved); runCurrent()
        assertNull(second.context()!!.ageYears)
        assertNull(second.context()!!.sex)
        assertEquals(AnatomicalSite.PALMS_SOLES, second.context()!!.site)
        coVerify(exactly = 0) { settings.applyPatch(any(), any()) }
    }
    @Test fun `invalid ages and changed owners cannot produce capture annotations`() = runTest(dispatcher) {
        val model = model(); runCurrent(); model.setAge("999")
        assertNull(model.context())
        model.setAge("45"); owner = "other"
        assertNull(model.context())
    }
}

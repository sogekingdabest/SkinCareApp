package com.monsteraltech.skincare.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.monsteraltech.skincare.analysis.model.AnalysisSex
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AnalysisProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<AccountProfileRepository>()
    private var owner: String? = "owner"
    private var settings = AccountSettings(userId = "owner", darkModeEnabled = true)
    private val store = ViewModelStore()
    @Before fun start() {
        Dispatchers.setMain(dispatcher)
        every { repository.getCurrentUserId() } answers { owner }
        coEvery { repository.getUserSettings() } answers { AccountResult.success(settings) }
        coEvery { repository.applySettingsPatch(any(), any()) } answers {
            settings = firstArg<AccountSettingsPatch>().applyTo(settings)
            AccountResult.success(settings)
        }
    }
    @After fun stop() { store.clear(); Dispatchers.resetMain() }
    private fun model(saved: SavedStateHandle = SavedStateHandle()) = AnalysisProfileViewModel(repository, saved).also { store.put("profile", it) }

    @Test fun `profile stays optional and patches only its own data`() = runTest(dispatcher) {
        val model = model(); runCurrent()
        model.setAge("45"); model.setSex(AnalysisSex.FEMALE); model.save(2026); runCurrent()
        assertEquals(AnalysisProfile(45, 2026, AnalysisSex.FEMALE), settings.analysisProfile)
        assertTrue(settings.darkModeEnabled)
        assertTrue(model.state.value.saved)
    }
    @Test fun `invalid age and a changed owner cannot be committed`() = runTest(dispatcher) {
        val model = model(); runCurrent(); model.setAge("999"); model.save(); runCurrent()
        assertNotNull(model.state.value.error)
        model.setAge("45"); owner = "other"; model.save(); runCurrent()
        coVerify(exactly = 0) { repository.applySettingsPatch(any(), any()) }
    }
    @Test fun `restored explicit unknown sex is not replaced by profile defaults`() = runTest(dispatcher) {
        settings = settings.copy(analysisProfile = AnalysisProfile(sex = AnalysisSex.MALE))
        val saved = SavedStateHandle(mapOf("owner" to "owner", "age" to "", "sex" to null))
        val model = model(saved); runCurrent()
        assertNull(model.state.value.sex)
        model.clear(); model.save(2026); runCurrent()
        assertEquals(AnalysisProfile(), settings.analysisProfile)
    }
}

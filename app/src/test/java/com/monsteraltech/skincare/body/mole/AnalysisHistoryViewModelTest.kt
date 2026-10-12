package com.monsteraltech.skincare.body.mole

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.monsteraltech.skincare.body.mole.domain.model.MoleListEntry
import com.monsteraltech.skincare.body.mole.domain.repository.*
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.data.identity.ActiveProfile
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisHistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<MoleReadRepository>()
    private val profile = mockk<ActiveProfile> { every { ownerId } returns "fixture" }
    private val store = ViewModelStore()
    private val mole = MoleListEntry("m", "Title", "", "", "", "", "", 70, 1000, 1000)
    private val data = MutableStateFlow((1..70).map { AnalysisData(id = "a$it", moleId = "m") })
    private lateinit var model: AnalysisHistoryViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { repository.observeHistory(any(), any(), any()) } answers {
            val limit = thirdArg<Int>(); data.map { AnalysisHistoryPage(mole, it.take(limit), it.size) }
        }
        model = AnalysisHistoryViewModel(repository, profile, SavedStateHandle()); store.put("history", model)
    }
    @After fun close() { store.clear(); Dispatchers.resetMain() }
    private fun TestScope.observe() = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
    @Test fun `history paging uses the total count rather than the loaded rows`() = runTest(dispatcher) {
        observe(); model.start("m"); runCurrent()
        val first = (model.uiState.value as AnalysisHistoryViewModel.UiState.Ready).page
        assertEquals(50, first.analyses.size); assertEquals(70, first.totalCount)
        model.loadMore(); runCurrent()
        assertEquals(70, (model.uiState.value as AnalysisHistoryViewModel.UiState.Ready).page.analyses.size)
    }
    @Test fun `new observations reach presentation without separate summary and history loads`() = runTest(dispatcher) {
        observe(); model.start("m"); runCurrent()
        data.value = listOf(AnalysisData(id = "new", moleId = "m")); runCurrent()
        assertEquals("new", (model.uiState.value as AnalysisHistoryViewModel.UiState.Ready).page.analyses.single().id)
        verify(exactly = 1) { repository.observeHistory(any(), any(), any()) }
    }
    @Test fun `missing mole is an explicit state and can be retried`() = runTest(dispatcher) {
        every { repository.observeHistory(any(), any(), any()) } returns flow { throw NoSuchElementException() }
        observe(); model.start("m"); runCurrent()
        assertEquals(AnalysisHistoryViewModel.UiState.Failed(false, true), model.uiState.value)
    }
    @Test fun `clearing history cancels observation without turning it into an error`() = runTest(dispatcher) {
        var cancelled = false
        every { repository.observeHistory(any(), any(), any()) } returns flow {
            try { emit(AnalysisHistoryPage(mole, emptyList(), 0)); awaitCancellation() } finally { cancelled = true }
        }
        observe(); model.start("m"); runCurrent(); store.clear(); runCurrent()
        assertTrue(cancelled); assertTrue(model.uiState.value is AnalysisHistoryViewModel.UiState.Ready)
    }
}

package com.monsteraltech.skincare.body.mole

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.monsteraltech.skincare.body.mole.domain.model.*
import com.monsteraltech.skincare.body.mole.domain.repository.MoleReadRepository
import com.monsteraltech.skincare.data.identity.ActiveProfile
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MoleListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<MoleReadRepository>()
    private val profile = TestProfile()
    private val saved = SavedStateHandle()
    private val store = ViewModelStore()
    private lateinit var model: MoleListViewModel
    private val entries = MutableStateFlow((1..70).map { entry("m$it") })
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { repository.observeMoles(any(), any(), any()) } answers {
            val limit = thirdArg<Int>()
            entries.map { MoleListPage(it.take(limit), it.size, it.size) }
        }
        model = MoleListViewModel(repository, profile, saved)
        store.put("list", model)
    }
    @After fun close() { store.clear(); Dispatchers.resetMain() }
    private fun TestScope.observe() = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
    private fun ready() = model.uiState.value as MoleListViewModel.UiState.Ready

    @Test fun `page controls expose all records after the first fifty`() = runTest(dispatcher) {
        observe(); model.start(selector = true); runCurrent()
        assertEquals(50, ready().page.items.size)
        assertEquals(70, ready().page.matchingCount)
        assertTrue(ready().page.hasMore)
        model.loadMore(); runCurrent()
        assertEquals(70, ready().page.items.size)
        assertFalse(ready().page.hasMore)
    }
    @Test fun `live changes update the list without another start or manual refresh`() = runTest(dispatcher) {
        observe(); model.start("zone"); runCurrent()
        entries.value = listOf(entry("new", "New title")); runCurrent()
        assertEquals("New title", ready().page.items.single().title)
        verify(exactly = 1) { repository.observeMoles(any(), any(), any()) }
    }
    @Test fun `new query resets the visible window and preserves its search mode`() = runTest(dispatcher) {
        observe(); model.start(selector = true); runCurrent(); model.loadMore(); runCurrent()
        model.search("needle"); runCurrent()
        verify { repository.observeMoles("fixture", MoleListQuery("needle", selector = true), 50) }
        assertEquals("needle", saved.get<String>("list_query"))
    }
    @Test fun `switching profiles resets paging and binds the new owner`() = runTest(dispatcher) {
        observe(); model.start(selector = true); runCurrent(); model.loadMore(); runCurrent()
        profile.owner = "other"; model.start(selector = true); runCurrent()
        verify { repository.observeMoles("other", MoleListQuery(selector = true), 50) }
    }
    @Test fun `restored query and page window survive recreation`() = runTest(dispatcher) {
        saved["list_query"] = "restored"; saved["list_limit"] = 100
        observe(); model.start(selector = true); runCurrent()
        verify { repository.observeMoles("fixture", MoleListQuery("restored", selector = true), 100) }
    }
    @Test fun `retry restarts a failed stream`() = runTest(dispatcher) {
        var attempt = 0
        every { repository.observeMoles(any(), any(), any()) } answers {
            if (++attempt == 1) flow { throw IOException("fixture") } else flowOf(MoleListPage(emptyList(), 0, 0))
        }
        observe(); model.start(selector = true); runCurrent()
        assertTrue(model.uiState.value is MoleListViewModel.UiState.Failed)
        model.retry(); runCurrent()
        assertTrue(model.uiState.value is MoleListViewModel.UiState.Ready)
        assertEquals(2, attempt)
    }
    @Test fun `missing profile never queries health records`() = runTest(dispatcher) {
        profile.owner = null; observe(); model.start(selector = true); runCurrent()
        assertEquals(MoleListViewModel.UiState.Failed(true), model.uiState.value)
        verify(exactly = 0) { repository.observeMoles(any(), any(), any()) }
    }
    @Test fun `clearing presentation cancels its stream without an error state`() = runTest(dispatcher) {
        var cancelled = false
        every { repository.observeMoles(any(), any(), any()) } returns flow {
            try { emit(MoleListPage(emptyList(), 0, 0)); awaitCancellation() } finally { cancelled = true }
        }
        observe(); model.start(selector = true); runCurrent(); store.clear(); runCurrent()
        assertTrue(cancelled)
        assertTrue(model.uiState.value is MoleListViewModel.UiState.Ready)
    }
    private fun entry(id: String, title: String = id) = MoleListEntry(id, title, "", "zone", "zone", "", "", 1, 1000, 1000)
    private class TestProfile : ActiveProfile {
        var owner: String? = "fixture"
        override val ownerId get() = owner
        override val isLocal = true
        override fun activateLocalProfile() = "fixture"
        override fun activateAccountProfile() = Unit
        override fun clear() = Unit
    }
}

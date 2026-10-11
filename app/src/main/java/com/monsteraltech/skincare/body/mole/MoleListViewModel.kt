package com.monsteraltech.skincare.body.mole

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.skincare.body.mole.domain.model.*
import com.monsteraltech.skincare.body.mole.domain.repository.MoleReadRepository
import com.monsteraltech.skincare.data.identity.ActiveProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MoleListViewModel @Inject constructor(
    private val repository: MoleReadRepository,
    private val profile: ActiveProfile,
    private val saved: SavedStateHandle
) : ViewModel() {
    sealed interface UiState {
        object Loading : UiState
        data class Ready(val page: MoleListPage, val query: String) : UiState
        data class Failed(val authentication: Boolean) : UiState
    }
    private data class Request(val owner: String, val query: MoleListQuery, val limit: Int, val revision: Int = 0)
    private val request = MutableStateFlow<Request?>(null)
    val query: String get() = saved["list_query"] ?: ""
    val uiState = request.flatMapLatest { current ->
        flow<UiState> {
            emit(UiState.Loading)
            if (current != null) {
                if (current.owner.isBlank()) throw SecurityException("Perfil no disponible")
                emitAll(repository.observeMoles(current.owner, current.query, current.limit)
                    .map { UiState.Ready(it, current.query.text) })
            }
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(UiState.Failed(error is SecurityException))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    fun start(bodyPartColor: String? = null, selector: Boolean = false) {
        val owner = profile.ownerId.orEmpty()
        val previous = request.value
        if (previous != null && previous.owner == owner && previous.query.bodyPartColor == bodyPartColor && previous.query.selector == selector) return
        val limit = if (previous == null) (saved.get<Int>("list_limit") ?: PAGE_SIZE).coerceAtLeast(PAGE_SIZE) else PAGE_SIZE
        request.value = Request(owner, MoleListQuery(query, bodyPartColor, selector), limit)
    }
    fun search(text: String) {
        if (text == query) return
        saved["list_query"] = text
        saved["list_limit"] = PAGE_SIZE
        request.update { it?.copy(query = it.query.copy(text = text), limit = PAGE_SIZE) }
    }
    fun loadMore() {
        val page = (uiState.value as? UiState.Ready)?.page ?: return
        if (!page.hasMore) return
        request.update { current -> current?.copy(limit = minOf(current.limit.toLong() + PAGE_SIZE, Int.MAX_VALUE.toLong()).toInt())
            ?.also { saved["list_limit"] = it.limit } }
    }
    fun retry() { request.update { it?.copy(revision = it.revision + 1) } }
    companion object { const val PAGE_SIZE = 50 }
}

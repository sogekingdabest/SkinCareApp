package com.monsteraltech.skincare.body.mole

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.skincare.body.mole.domain.repository.AnalysisHistoryPage
import com.monsteraltech.skincare.body.mole.domain.repository.MoleReadRepository
import com.monsteraltech.skincare.data.identity.ActiveProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalysisHistoryViewModel @Inject constructor(
    private val repository: MoleReadRepository,
    private val profile: ActiveProfile,
    private val saved: SavedStateHandle
) : ViewModel() {
    sealed interface UiState {
        object Loading : UiState
        data class Ready(val page: AnalysisHistoryPage) : UiState
        data class Failed(val authentication: Boolean, val missing: Boolean) : UiState
    }
    private data class Request(val owner: String, val id: String, val limit: Int, val revision: Int = 0)
    private val request = MutableStateFlow<Request?>(null)
    val uiState = request.flatMapLatest { current ->
        flow<UiState> {
            emit(UiState.Loading)
            if (current != null) {
                if (current.owner.isBlank()) throw SecurityException("Perfil no disponible")
                if (current.id.isBlank()) throw NoSuchElementException("Lunar no disponible")
                emitAll(repository.observeHistory(current.owner, current.id, current.limit).map { UiState.Ready(it) })
            }
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(UiState.Failed(error is SecurityException, error is NoSuchElementException))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)
    fun start(moleId: String) {
        val owner = profile.ownerId.orEmpty()
        if (request.value?.let { it.owner == owner && it.id == moleId } == true) return
        request.value = Request(owner, moleId, (saved.get<Int>("history_limit") ?: PAGE_SIZE).coerceAtLeast(PAGE_SIZE))
    }
    fun loadMore() {
        if ((uiState.value as? UiState.Ready)?.page?.hasMore != true) return
        request.update { it?.copy(limit = minOf(it.limit.toLong() + PAGE_SIZE, Int.MAX_VALUE.toLong()).toInt())
            ?.also { next -> saved["history_limit"] = next.limit } }
    }
    fun retry() { request.update { it?.copy(revision = it.revision + 1) } }
    companion object { const val PAGE_SIZE = 50 }
}

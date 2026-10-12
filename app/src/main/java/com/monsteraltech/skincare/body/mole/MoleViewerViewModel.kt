package com.monsteraltech.skincare.body.mole

import com.monsteraltech.skincare.body.domain.BodyRegionId
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.skincare.data.identity.ActiveProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import com.monsteraltech.skincare.body.mole.error.ErrorHandler
import com.monsteraltech.skincare.body.mole.error.RetryManager
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.data.LegacyAnalysisResultDecoder
import com.monsteraltech.skincare.body.mole.model.UserAbcdeInput
import com.monsteraltech.skincare.body.mole.repository.MoleRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

@HiltViewModel
class MoleViewerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val moleRepository: MoleRepository,
    private val activeProfile: ActiveProfile,
    private val legacyDecoder: LegacyAnalysisResultDecoder
) : ViewModel() {

    private val retryManager = RetryManager()

    sealed interface UiState {
        object Loading : UiState
        /** Análisis estructurado listo para pintar. */
        data class Analysis(val data: AnalysisData, val analysisCount: Int) : UiState
        /** El resultado no se pudo parsear: se muestra el texto tal cual. */
        data class RawResult(val text: String, val analysisCount: Int) : UiState
        object NoAnalysis : UiState
        data class Error(val result: ErrorHandler.ErrorResult) : UiState
    }

    sealed interface Event {
        data class RetryAttempt(val attempt: Int, val maxAttempts: Int) : Event
        data class RetrySucceeded(val attemptsMade: Int) : Event
        data class RetriesFailed(val attemptsMade: Int) : Event
        object UserValuesSaved : Event
        object UserValuesSaveFailed : Event
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isSavingUserValues = MutableStateFlow(false)
    val isSavingUserValues: StateFlow<Boolean> = _isSavingUserValues.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private val _bodyRegionId = MutableStateFlow<BodyRegionId?>(null)
    val bodyRegionId = _bodyRegionId.asStateFlow()
    private var args: MoleViewerArgs? = null
    private var isLoading = false

    /** Carga inicial. Idempotente: las rotaciones de pantalla no recargan. */
    fun initialize(args: MoleViewerArgs) {
        if (this.args != null) return
        this.args = args
        _bodyRegionId.value = args.bodyRegionId
        load(args)
    }

    fun retry() {
        args?.let { load(it) }
    }

    private fun load(args: MoleViewerArgs) {
        when {
            args.historicalAnalysis != null ->
                _uiState.value = UiState.Analysis(args.historicalAnalysis, args.analysisCount)

            args.moleId.isNotEmpty() -> loadFromRepository(args.moleId)

            args.aiResult.isNotEmpty() -> displayFromAiResult(args)

            else -> _uiState.value = UiState.NoAnalysis
        }
    }

    private fun displayFromAiResult(args: MoleViewerArgs) {
        val analysisData = try {
            legacyDecoder.decode(
                aiResult = args.aiResult,
                moleId = args.moleId,
                imageUrl = args.imageUrl
            )
        } catch (e: Exception) {
            null
        }
        _uiState.value = if (analysisData != null) {
            UiState.Analysis(analysisData, args.analysisCount)
        } else {
            UiState.RawResult(args.aiResult, args.analysisCount)
        }
    }

    private fun loadFromRepository(moleId: String) {
        if (isLoading) return
        isLoading = true
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            val userId = activeProfile.ownerId
            if (userId == null) {
                isLoading = false
                _uiState.value = UiState.Error(
                    ErrorHandler.ErrorResult(
                        type = ErrorHandler.ErrorType.AUTHENTICATION_ERROR,
                        userMessage = "Usuario no autenticado",
                        technicalMessage = "No active local profile",
                        isRetryable = false,
                        suggestedAction = "Inicia sesión para ver los datos"
                    )
                )
                return@launch
            }

            val retryConfig = RetryManager.databaseConfig()
            val retryResult = retryManager.executeWithRetry(
                operation = "Cargar lunar $moleId",
                config = retryConfig,
                onRetryAttempt = { attempt, _ ->
                    _events.trySend(Event.RetryAttempt(attempt, retryConfig.maxAttempts))
                }
            ) {
                moleRepository.getMoleWithCurrentAnalysis(userId, moleId)
            }

            isLoading = false
            retryResult.result
                .onSuccess { snapshot ->
                    _bodyRegionId.value = snapshot.mole.bodyRegionId ?: BodyRegionId.UNKNOWN
                    if (retryResult.attemptsMade > 1) {
                        _events.trySend(Event.RetrySucceeded(retryResult.attemptsMade))
                    }
                    _uiState.value = snapshot.analysis?.let {
                        UiState.Analysis(it, snapshot.mole.analysisCount)
                    } ?: UiState.NoAnalysis
                }
                .onFailure { exception ->
                    if (retryResult.attemptsMade > 1) {
                        _events.trySend(Event.RetriesFailed(retryResult.attemptsMade))
                    }
                    _uiState.value = UiState.Error(
                        ErrorHandler.handleError(appContext, exception, "Cargar lunar")
                    )
                }
        }
    }

    /** Persiste la valoración ABCDE manual del usuario en los metadatos del lunar. */
    fun saveUserValues(input: UserAbcdeInput) {
        val moleId = args?.moleId
        val userId = activeProfile.ownerId
        val displayed = _uiState.value as? UiState.Analysis
        if (userId == null || moleId.isNullOrEmpty() || displayed == null) {
            _events.trySend(Event.UserValuesSaveFailed)
            return
        }
        if (_isSavingUserValues.value) return

        viewModelScope.launch {
            _isSavingUserValues.value = true
            try {
                val result = moleRepository.updateMoleAnalysisMetadata(
                    userId, moleId, input.toMetadataMap(), displayed.data.id
                )
                if (result.isSuccess) {
                    _events.trySend(Event.UserValuesSaved)
                    // Recargar para mostrar la tarjeta de valores guardados.
                    if (args?.historicalAnalysis != null) {
                        _uiState.value = displayed.copy(data = displayed.data.copy(
                            analysisMetadata = displayed.data.analysisMetadata + input.toMetadataMap()
                        ))
                    } else {
                        loadFromRepository(moleId)
                    }
                } else {
                    _events.trySend(Event.UserValuesSaveFailed)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                _events.trySend(Event.UserValuesSaveFailed)
            } finally {
                _isSavingUserValues.value = false
            }
        }
    }

}

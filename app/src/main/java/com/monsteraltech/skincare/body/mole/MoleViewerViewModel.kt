package com.monsteraltech.skincare.body.mole

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.skincare.data.identity.ActiveProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import com.monsteraltech.skincare.body.mole.error.ErrorHandler
import com.monsteraltech.skincare.body.mole.error.RetryManager
import com.monsteraltech.skincare.body.mole.model.ABCDEScores
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.model.AnalysisDataConverter
import com.monsteraltech.skincare.body.mole.model.MoleData
import com.monsteraltech.skincare.body.mole.model.UserAbcdeInput
import com.monsteraltech.skincare.body.mole.repository.MoleRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoleViewerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val moleRepository: MoleRepository,
    private val activeProfile: ActiveProfile
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

    private var args: MoleViewerArgs? = null
    private var isLoading = false

    /** Carga inicial. Idempotente: las rotaciones de pantalla no recargan. */
    fun initialize(args: MoleViewerArgs) {
        if (this.args != null) return
        this.args = args
        load(args)
    }

    fun retry() {
        args?.let { load(it) }
    }

    private fun load(args: MoleViewerArgs) {
        when {
            args.historicalAnalysis != null ->
                _uiState.value = UiState.Analysis(args.historicalAnalysis, args.analysisCount)

            args.aiResult.isNotEmpty() -> displayFromAiResult(args)

            args.moleId.isNotEmpty() -> loadFromRepository(args.moleId)

            else -> _uiState.value = UiState.NoAnalysis
        }
    }

    private fun displayFromAiResult(args: MoleViewerArgs) {
        val analysisData = try {
            AnalysisDataConverter.fromAiResultString(
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
                moleRepository.getMoleById(userId, moleId)
            }

            isLoading = false
            retryResult.result
                .onSuccess { moleData ->
                    if (retryResult.attemptsMade > 1) {
                        _events.trySend(Event.RetrySucceeded(retryResult.attemptsMade))
                    }
                    _uiState.value = UiState.Analysis(
                        data = analysisDataFrom(moleData),
                        analysisCount = moleData.analysisCount
                    )
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
        if (userId == null || moleId.isNullOrEmpty()) {
            _events.trySend(Event.UserValuesSaveFailed)
            return
        }
        if (_isSavingUserValues.value) return

        viewModelScope.launch {
            _isSavingUserValues.value = true
            try {
                val result = moleRepository.updateMoleAnalysisMetadata(
                    userId, moleId, input.toMetadataMap()
                )
                if (result.isSuccess) {
                    _events.trySend(Event.UserValuesSaved)
                    // Recargar para mostrar la tarjeta de valores guardados.
                    loadFromRepository(moleId)
                } else {
                    _events.trySend(Event.UserValuesSaveFailed)
                }
            } catch (e: Exception) {
                _events.trySend(Event.UserValuesSaveFailed)
            } finally {
                _isSavingUserValues.value = false
            }
        }
    }

    private fun analysisDataFrom(moleData: MoleData): AnalysisData {
        return AnalysisData(
            id = "${moleData.id}_current",
            moleId = moleData.id,
            analysisResult = moleData.aiResult,
            aiProbability = moleData.aiProbability?.toFloat() ?: 0f,
            aiConfidence = moleData.aiConfidence?.toFloat() ?: 0f,
            abcdeScores = ABCDEScores(
                asymmetryScore = moleData.abcdeAsymmetry?.toFloat() ?: 0f,
                borderScore = moleData.abcdeBorder?.toFloat() ?: 0f,
                colorScore = moleData.abcdeColor?.toFloat() ?: 0f,
                diameterScore = moleData.abcdeDiameter?.toFloat() ?: 0f,
                evolutionScore = null,
                totalScore = moleData.abcdeTotalScore?.toFloat() ?: 0f
            ),
            combinedScore = moleData.combinedScore?.toFloat() ?: 0f,
            riskLevel = moleData.riskLevel,
            recommendation = moleData.recommendation,
            imageUrl = moleData.imageUrl,
            createdAt = moleData.lastAnalysisDate ?: moleData.createdAt,
            analysisMetadata = moleData.analysisMetadata
        )
    }
}

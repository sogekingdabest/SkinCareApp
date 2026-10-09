package com.monsteraltech.skincare.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.skincare.data.identity.ActiveProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.analysis.comparison.ObservationComparisonPreparer
import com.monsteraltech.skincare.body.mole.model.ABCDEScores
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.model.MoleData
import com.monsteraltech.skincare.body.mole.model.UserAbcdeInput
import com.monsteraltech.skincare.body.mole.domain.model.MoleObservation
import com.monsteraltech.skincare.body.mole.domain.model.LesionRegistrationMetrics
import com.monsteraltech.skincare.body.mole.domain.model.ObservationComparability
import com.monsteraltech.skincare.body.mole.domain.usecase.GetBaselineObservationUseCase
import com.monsteraltech.skincare.body.mole.repository.MoleRepository
import com.monsteraltech.skincare.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AnalysisResultViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val moleRepository: MoleRepository,
    private val activeProfile: ActiveProfile,
    private val getBaselineObservation: GetBaselineObservationUseCase,
    private val comparisonPreparer: ObservationComparisonPreparer,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    sealed interface AnalysisState {
        object Idle : AnalysisState
        data class Processing(
            val progress: Int,
            val message: String,
            val stage: ProcessingStage?
        ) : AnalysisState
        data class Ready(val result: MelanomaAIDetector.CombinedAnalysisResult) : AnalysisState
        data class Failed(val message: String) : AnalysisState
    }

    sealed interface Event {
        data class ValidationFailed(@StringRes val messageRes: Int) : Event
        /** Guardado correcto: la Activity notifica y navega atrás. */
        data class SaveCompleted(val existingMoleTitle: String? = null) : Event
        data class SaveFailed(val detail: String?) : Event
        object AnalysisCancelled : Event
    }

    sealed interface BaselineObservationState {
        object NewMole : BaselineObservationState
        data class Loading(val moleId: String) : BaselineObservationState
        data class Available(
            val observation: MoleObservation
        ) : BaselineObservationState
        data class Missing(val moleId: String) : BaselineObservationState
        data class Failed(
            val moleId: String,
            val message: String?
        ) : BaselineObservationState
    }

    sealed interface ComparisonPreparationState {
        object NotRequested : ComparisonPreparationState
        data class Loading(
            val baselineObservationId: String
        ) : ComparisonPreparationState
        data class Ready(
            val assessment: ObservationComparability.Comparable,
            val registrationMetrics: LesionRegistrationMetrics
        ) : ComparisonPreparationState
        data class Abstained(
            val assessment: ObservationComparability.NotComparable
        ) : ComparisonPreparationState
    }

    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    private val _baselineObservationState =
        MutableStateFlow<BaselineObservationState>(
            BaselineObservationState.NewMole
        )
    val baselineObservationState: StateFlow<BaselineObservationState> =
        _baselineObservationState.asStateFlow()

    private val _comparisonPreparationState =
        MutableStateFlow<ComparisonPreparationState>(
            ComparisonPreparationState.NotRequested
        )
    val comparisonPreparationState:
        StateFlow<ComparisonPreparationState> =
        _comparisonPreparationState.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private var asyncImageProcessor: AsyncImageProcessor? = null
    private var photoFile: File? = null
    private var comparisonBaselineBitmap: Bitmap? = null
    private var analysisStarted = false

    val isProcessing: Boolean
        get() = _analysisState.value is AnalysisState.Processing

    /**
     * Puente entre los callbacks del procesador asíncrono y los StateFlows.
     * El resultado sobrevive a rotaciones: el análisis no se relanza.
     */
    private val progressBridge = object : ProgressCallback {
        override fun onProgressUpdate(progress: Int, message: String) {
            val stage = (_analysisState.value as? AnalysisState.Processing)?.stage
            _analysisState.value = AnalysisState.Processing(progress, message, stage)
        }

        override fun onProgressUpdateWithTimeEstimate(progress: Int, message: String, estimatedTotalTimeMs: Long) {
            onProgressUpdate(progress, message)
        }

        override fun onStageChanged(stage: ProcessingStage) {
            val current = _analysisState.value as? AnalysisState.Processing
            _analysisState.value = AnalysisState.Processing(
                progress = current?.progress ?: 0,
                message = current?.message ?: stage.message,
                stage = stage
            )
        }

        override fun onError(error: String) {
            _analysisState.value = AnalysisState.Failed(error)
        }

        override fun onCompleted(result: MelanomaAIDetector.CombinedAnalysisResult) {
            handleAnalysisCompleted(result)
        }

        override fun onCancelled() {
            _events.trySend(Event.AnalysisCancelled)
        }
    }

    @VisibleForTesting
    internal fun handleAnalysisCompleted(result: MelanomaAIDetector.CombinedAnalysisResult) {
        _analysisState.value = AnalysisState.Ready(result)
    }

    /** Lanza el análisis una única vez (idempotente frente a recreaciones de la Activity). */
    fun startAnalysis(
        photoPath: String,
        isFrontCamera: Boolean,
        pixelDensity: Float,
        targetMoleId: String? = null
    ) {
        if (analysisStarted) return
        analysisStarted = true
        photoFile = File(photoPath)

        viewModelScope.launch(ioDispatcher) {
            resolveBaselineObservation(targetMoleId)
            val bitmap = decodeBitmap(photoPath, isFrontCamera)
            if (bitmap == null) {
                _analysisState.value = AnalysisState.Failed(
                    appContext.getString(R.string.analysis_image_load_error)
                )
                return@launch
            }
            _capturedBitmap.value = bitmap
            prepareComparison(bitmap, targetMoleId)
            _analysisState.value = AnalysisState.Processing(0, "", null)
            try {
                obtainProcessor().processImage(
                    bitmap = bitmap,
                    previousBitmap = null,
                    pixelDensity = pixelDensity,
                    config = AnalysisConfiguration.default()
                )
                // El resultado (o el error) llega por el ProgressCallback.
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                // Cancelaciones y fallos totales ya se notificaron vía onError/onCancelled.
                if (_analysisState.value is AnalysisState.Processing) {
                    _analysisState.value = AnalysisState.Failed(e.message ?: "")
                }
            }
        }
    }

    /**
     * Prepara la pareja sin activar aún el cálculo de evolución.
     *
     * Una abstención no bloquea el análisis de imagen individual.
     */
    @VisibleForTesting
    internal suspend fun prepareComparison(
        currentBitmap: Bitmap,
        targetMoleId: String?
    ) {
        if (targetMoleId.isNullOrBlank()) {
            _comparisonPreparationState.value =
                ComparisonPreparationState.NotRequested
            return
        }

        when (val baselineState = _baselineObservationState.value) {
            is BaselineObservationState.Available -> {
                val baseline = baselineState.observation
                _comparisonPreparationState.value =
                    ComparisonPreparationState.Loading(baseline.id)
                try {
                    val prepared = comparisonPreparer.prepare(
                        currentBitmap = currentBitmap,
                        baseline = baseline
                    )
                    when (val assessment = prepared.assessment) {
                        is ObservationComparability.Comparable -> {
                            val decodedBaseline = prepared.baselineBitmap
                            val registrationMetrics =
                                prepared.registrationMetrics
                            if (decodedBaseline == null) {
                                _comparisonPreparationState.value =
                                    ComparisonPreparationState.Abstained(
                                        ObservationComparability.NotComparable(
                                            reason = ObservationComparability.Reason
                                                .BASELINE_IMAGE_UNREADABLE,
                                            baselineObservationId = baseline.id,
                                            detail = "No se conservó el bitmap base"
                                        )
                                    )
                            } else if (registrationMetrics == null) {
                                decodedBaseline.recycle()
                                _comparisonPreparationState.value =
                                    ComparisonPreparationState.Abstained(
                                        ObservationComparability.NotComparable(
                                            reason = ObservationComparability.Reason
                                                .REGISTRATION_UNRELIABLE,
                                            baselineObservationId = baseline.id,
                                            detail = "No se obtuvieron métricas de registro"
                                        )
                                    )
                            } else {
                                comparisonBaselineBitmap
                                    ?.takeIf { it !== decodedBaseline }
                                    ?.recycle()
                                comparisonBaselineBitmap = decodedBaseline
                                _comparisonPreparationState.value =
                                    ComparisonPreparationState.Ready(
                                        assessment = assessment,
                                        registrationMetrics =
                                            registrationMetrics
                                    )
                            }
                        }
                        is ObservationComparability.NotComparable -> {
                            _comparisonPreparationState.value =
                                ComparisonPreparationState.Abstained(assessment)
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    _comparisonPreparationState.value =
                        ComparisonPreparationState.Abstained(
                            ObservationComparability.NotComparable(
                                reason = ObservationComparability.Reason
                                    .BASELINE_IMAGE_UNREADABLE,
                                baselineObservationId = baseline.id,
                                detail = error.message
                            )
                        )
                }
            }
            is BaselineObservationState.Missing -> {
                _comparisonPreparationState.value =
                    ComparisonPreparationState.Abstained(
                        ObservationComparability.NotComparable(
                            reason = ObservationComparability.Reason.NO_BASELINE,
                            baselineObservationId = null,
                            detail = baselineState.moleId
                        )
                    )
            }
            is BaselineObservationState.Failed -> {
                _comparisonPreparationState.value =
                    ComparisonPreparationState.Abstained(
                        ObservationComparability.NotComparable(
                            reason = ObservationComparability.Reason
                                .BASELINE_LOOKUP_FAILED,
                            baselineObservationId = null,
                            detail = baselineState.message
                        )
                    )
            }
            BaselineObservationState.NewMole,
            is BaselineObservationState.Loading -> {
                _comparisonPreparationState.value =
                    ComparisonPreparationState.NotRequested
            }
        }
    }

    private suspend fun resolveBaselineObservation(targetMoleId: String?) {
        if (targetMoleId.isNullOrBlank()) {
            _baselineObservationState.value =
                BaselineObservationState.NewMole
            return
        }

        _baselineObservationState.value =
            BaselineObservationState.Loading(targetMoleId)
        try {
            getBaselineObservation(targetMoleId).fold(
                onSuccess = { observation ->
                    _baselineObservationState.value =
                        observation?.let(
                            BaselineObservationState::Available
                        ) ?: BaselineObservationState.Missing(targetMoleId)
                },
                onFailure = { error ->
                    _baselineObservationState.value =
                        BaselineObservationState.Failed(
                            moleId = targetMoleId,
                            message = error.message
                        )
                }
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            _baselineObservationState.value =
                BaselineObservationState.Failed(
                    moleId = targetMoleId,
                    message = error.message
                )
        }
    }

    fun cancelAnalysis() {
        asyncImageProcessor?.cancelProcessing()
    }

    fun saveAsNewMole(
        title: String,
        description: String,
        bodyPart: String,
        bodyPartColorCode: String?,
        userInput: UserAbcdeInput?
    ) {
        val result = validateForSave(requireTitle = true, title = title, bodyPart = bodyPart) ?: return
        val imageFile = photoFile ?: run {
            _events.trySend(Event.SaveFailed(null))
            return
        }
        if (_isSaving.value) return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val moleId = UUID.randomUUID().toString()
                val analysisData = buildAnalysisData(result, moleId, title, description, bodyPart, bodyPartColorCode, userInput)
                val saveResult = moleRepository.saveMoleWithAnalysis(
                    context = appContext,
                    imageFile = imageFile,
                    title = title,
                    description = description,
                    bodyPart = bodyPart,
                    bodyPartColorCode = bodyPartColorCode ?: "",
                    analysisData = analysisData
                )
                if (saveResult.isSuccess) {
                    _events.send(Event.SaveCompleted())
                } else {
                    _events.send(Event.SaveFailed(saveResult.exceptionOrNull()?.message))
                }
            } catch (e: Exception) {
                _events.send(Event.SaveFailed(e.message))
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveToExistingMole(
        mole: MoleData,
        title: String,
        description: String,
        bodyPart: String,
        bodyPartColorCode: String?,
        userInput: UserAbcdeInput?
    ) = saveToExistingMole(
        moleId = mole.id,
        existingMoleTitle = mole.title,
        title = title,
        description = description,
        bodyPart = bodyPart,
        bodyPartColorCode = bodyPartColorCode,
        userInput = userInput
    )

    fun saveToExistingMole(
        moleId: String,
        existingMoleTitle: String?,
        title: String,
        description: String,
        bodyPart: String,
        bodyPartColorCode: String?,
        userInput: UserAbcdeInput?
    ) {
        val result = validateForSave(requireTitle = false, title = title, bodyPart = bodyPart) ?: return
        val imageFile = photoFile ?: run {
            _events.trySend(Event.SaveFailed(null))
            return
        }
        if (_isSaving.value) return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val analysisData = buildAnalysisData(result, moleId, title, description, bodyPart, bodyPartColorCode, userInput)
                val saveResult = moleRepository.saveAnalysisToMole(appContext, moleId, analysisData, imageFile)
                if (saveResult.isSuccess) {
                    _events.send(
                        Event.SaveCompleted(
                            existingMoleTitle = existingMoleTitle
                        )
                    )
                } else {
                    _events.send(Event.SaveFailed(saveResult.exceptionOrNull()?.message))
                }
            } catch (e: Exception) {
                _events.send(Event.SaveFailed(e.message))
            } finally {
                _isSaving.value = false
            }
        }
    }

    /** Devuelve el resultado listo para guardar, o null si la validación falla (evento emitido). */
    private fun validateForSave(
        requireTitle: Boolean,
        title: String,
        bodyPart: String
    ): MelanomaAIDetector.CombinedAnalysisResult? {
        if (activeProfile.ownerId == null) {
            _events.trySend(Event.ValidationFailed(R.string.save_validation_login))
            return null
        }
        if (requireTitle && title.isEmpty()) {
            _events.trySend(Event.ValidationFailed(R.string.save_validation_title))
            return null
        }
        if (bodyPart.isEmpty()) {
            _events.trySend(Event.ValidationFailed(R.string.save_validation_body_part))
            return null
        }
        val state = _analysisState.value
        if (state !is AnalysisState.Ready) {
            _events.trySend(Event.SaveFailed(null))
            return null
        }
        return state.result
    }

    private fun decodeBitmap(photoPath: String, isFrontCamera: Boolean): Bitmap? {
        val decoded = BitmapFactory.decodeFile(photoPath) ?: return null
        if (!isFrontCamera) return decoded
        val matrix = Matrix().apply { preScale(-1.0f, 1.0f) }
        val flipped = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (flipped !== decoded) decoded.recycle()
        return flipped
    }

    private fun obtainProcessor(): AsyncImageProcessor {
        return asyncImageProcessor ?: AsyncImageProcessor(appContext, progressBridge)
            .also { asyncImageProcessor = it }
    }

    private fun buildAnalysisData(
        result: MelanomaAIDetector.CombinedAnalysisResult,
        moleId: String,
        title: String,
        description: String,
        bodyPart: String,
        bodyPartColorCode: String?,
        userInput: UserAbcdeInput?
    ): AnalysisData {
        val abcdeScores = ABCDEScores(
            asymmetryScore = result.abcdeResult.asymmetryScore,
            borderScore = result.abcdeResult.borderScore,
            colorScore = result.abcdeResult.colorScore,
            diameterScore = result.abcdeResult.diameterScore,
            evolutionScore = result.abcdeResult.evolutionScore,
            totalScore = result.abcdeResult.totalScore
        )
        val metadata = mapOf(
            "title" to title,
            "description" to description,
            "bodyPart" to bodyPart,
            "bodyPartColorCode" to (bodyPartColorCode ?: ""),
            "urgencyLevel" to result.urgencyLevel.name,
            "explanations" to result.explanations
        ) + (userInput?.toMetadataMap() ?: emptyMap())

        return AnalysisData(
            moleId = moleId,
            description = description,
            analysisResult = buildAnalysisSummary(result, userInput),
            aiProbability = result.aiProbability,
            aiConfidence = result.aiConfidence,
            abcdeScores = abcdeScores,
            combinedScore = result.combinedScore,
            riskLevel = result.combinedRiskLevel.name,
            recommendation = result.recommendation,
            imageData = photoFile,
            analysisMetadata = metadata
        )
    }

    private fun buildAnalysisSummary(
        result: MelanomaAIDetector.CombinedAnalysisResult,
        userInput: UserAbcdeInput?
    ): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return buildString {
            appendLine("=== ANÁLISIS COMBINADO IA + ABCDE ===")
            appendLine("Fecha: ${dateFormat.format(Date())}")
            appendLine()
            appendLine("RESULTADO IA:")
            appendLine("- Probabilidad: ${(result.aiProbability * 100).toInt()}%")
            appendLine("- Confianza: ${(result.aiConfidence * 100).toInt()}%")
            appendLine()
            appendLine("ANÁLISIS ABCDE (IA):")
            appendLine("- Asimetría: ${result.abcdeResult.asymmetryScore}/2")
            appendLine("- Bordes: ${result.abcdeResult.borderScore}/8")
            appendLine("- Color: ${result.abcdeResult.colorScore}/6")
            appendLine("- Diámetro: ${result.abcdeResult.diameterScore}/5")
            result.abcdeResult.evolutionScore?.let {
                appendLine("- Evolución: $it/3")
            }
            appendLine("- Score Total ABCDE (IA): ${result.abcdeResult.totalScore}")
            appendLine()
            if (userInput != null) {
                appendLine("ANÁLISIS ABCDE (USUARIO):")
                appendLine("- Asimetría: ${String.format("%.1f", userInput.asymmetry)}/2")
                appendLine("- Bordes: ${String.format("%.1f", userInput.border)}/8")
                appendLine("- Color: ${String.format("%.1f", userInput.color)}/6")
                appendLine("- Diámetro: ${String.format("%.1f", userInput.diameter)}/5")
                appendLine("- Evolución: ${String.format("%.1f", userInput.evolution)}/3")
                val userTotal = userInput.total()
                appendLine("- Score Total ABCDE (Usuario): ${String.format("%.1f", userTotal)}")
                appendLine()
                val difference = UserAbcdeInput.difference(result.abcdeResult.totalScore, userTotal)
                appendLine("COMPARACIÓN:")
                appendLine("- Diferencia entre IA y Usuario: ${String.format("%.1f", difference)} puntos")
                appendLine()
            }
            appendLine("RESULTADO COMBINADO:")
            appendLine("- Score: ${(result.combinedScore * 100).toInt()}%")
            appendLine("- Nivel de Riesgo: ${result.combinedRiskLevel}")
            appendLine("- Urgencia: ${result.urgencyLevel}")
            appendLine()
            appendLine("RECOMENDACIÓN:")
            appendLine(result.recommendation)
        }
    }

    override fun onCleared() {
        super.onCleared()
        comparisonBaselineBitmap?.recycle()
        comparisonBaselineBitmap = null
        asyncImageProcessor?.cleanup()
    }
}

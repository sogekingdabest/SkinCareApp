package com.monsteraltech.skincare.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModelStore
import com.monsteraltech.skincare.data.identity.ActiveProfile
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.analysis.comparison.ObservationComparisonPreparer
import com.monsteraltech.skincare.analysis.comparison.PreparedObservationComparison
import com.monsteraltech.skincare.body.mole.domain.model.MoleObservation
import com.monsteraltech.skincare.body.mole.domain.model.LesionRegistrationMetrics
import com.monsteraltech.skincare.body.mole.domain.model.ObservationComparability
import com.monsteraltech.skincare.body.mole.domain.model.ObservationImageMetrics
import com.monsteraltech.skincare.body.mole.domain.usecase.GetBaselineObservationUseCase
import com.monsteraltech.skincare.body.mole.model.MoleData
import com.monsteraltech.skincare.body.mole.model.UserAbcdeInput
import com.monsteraltech.skincare.body.mole.repository.MoleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisResultViewModelTest {

    private lateinit var context: Context
    private lateinit var moleRepository: MoleRepository
    private lateinit var activeProfile: ActiveProfile
    private lateinit var getBaselineObservation:
        GetBaselineObservationUseCase
    private lateinit var comparisonPreparer:
        ObservationComparisonPreparer
    private lateinit var viewModel: AnalysisResultViewModel
    private lateinit var runner: ImageAnalysisRunner

    @Before
    fun setUp() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        context = mockk(relaxed = true)
        moleRepository = mockk()
        activeProfile = mockk()
        getBaselineObservation = mockk()
        comparisonPreparer = mockk()
        runner = mockk(relaxed = true)
        every { activeProfile.ownerId } returns "user-1"
        viewModel = AnalysisResultViewModel(
            context,
            moleRepository,
            activeProfile,
            getBaselineObservation,
            comparisonPreparer,
            dispatcher,
            runner
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(BitmapFactory::class)
        Dispatchers.resetMain()
    }

    private fun abcdeResult(totalScore: Float = 4f) = ABCDEAnalyzerOpenCV.ABCDEResult(
        asymmetryScore = 1f,
        borderScore = 2f,
        colorScore = 1f,
        diameterScore = 1f,
        evolutionScore = null,
        totalScore = totalScore,
        riskLevel = ABCDEAnalyzerOpenCV.RiskLevel.LOW,
        details = ABCDEAnalyzerOpenCV.ABCDEDetails(
            asymmetryDetails = ABCDEAnalyzerOpenCV.AsymmetryDetails(0f, 0f, "test"),
            borderDetails = ABCDEAnalyzerOpenCV.BorderDetails(0f, 0, "test"),
            colorDetails = ABCDEAnalyzerOpenCV.ColorDetails(emptyList(), 1, false, false, "test"),
            diameterDetails = ABCDEAnalyzerOpenCV.DiameterDetails(0f, 0, "test"),
            evolutionDetails = null
        )
    )

    private fun combinedResult() = MelanomaAIDetector.CombinedAnalysisResult(
        aiProbability = 0.3f,
        aiRiskLevel = MelanomaAIDetector.RiskLevel.LOW,
        aiConfidence = 0.8f,
        abcdeResult = abcdeResult(),
        combinedScore = 0.35f,
        combinedRiskLevel = MelanomaAIDetector.RiskLevel.LOW,
        recommendation = "vigilar",
        shouldMonitor = true,
        urgencyLevel = MelanomaAIDetector.UrgencyLevel.MONITOR,
        explanations = listOf("explicación")
    )

    /** Fija photoFile (el decode falla en JVM y no llega a crear el procesador) y siembra el resultado. */
    private fun seedReadyState() {
        viewModel.startAnalysis("foto-inexistente.jpg", isFrontCamera = false, pixelDensity = 1f)
        viewModel.handleAnalysisCompleted(AnalysisOutcome.Complete(combinedResult()))
    }

    @Test
    fun `guardar sin usuario emite validacion de login`() = runTest {
        every { activeProfile.ownerId } returns null
        seedReadyState()

        viewModel.saveAsNewMole("Título", "", "Torso", "#FFFFC90E", null)

        assertEquals(
            AnalysisResultViewModel.Event.ValidationFailed(R.string.save_validation_login),
            viewModel.events.first()
        )
        coVerify(exactly = 0) { moleRepository.saveMoleWithAnalysis(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `guardar sin titulo emite validacion de titulo`() = runTest {
        seedReadyState()

        viewModel.saveAsNewMole("", "", "Torso", "#FFFFC90E", null)

        assertEquals(
            AnalysisResultViewModel.Event.ValidationFailed(R.string.save_validation_title),
            viewModel.events.first()
        )
    }

    @Test
    fun `guardar sin parte del cuerpo emite su validacion`() = runTest {
        seedReadyState()

        viewModel.saveAsNewMole("Título", "", "", null, null)

        assertEquals(
            AnalysisResultViewModel.Event.ValidationFailed(R.string.save_validation_body_part),
            viewModel.events.first()
        )
    }

    @Test
    fun `guardar sin analisis listo emite fallo`() = runTest {
        // Sin seedReadyState: el estado no es Ready.
        viewModel.saveAsNewMole("Título", "", "Torso", "#FFFFC90E", null)

        assertEquals(AnalysisResultViewModel.Event.ValidationFailed(R.string.analysis_complete_required), viewModel.events.first())
    }

    @Test
    fun `un parcial conserva el componente real y bloquea ambos tipos de guardado`() = runTest {
        seedReadyState()
        val partial = AnalysisOutcome.Partial(
            AnalysisSignal.Unavailable(AnalysisSignal.Reason.ERROR, "IA no disponible"),
            AnalysisSignal.Available(abcdeResult())
        )
        viewModel.handleAnalysisCompleted(partial)
        assertEquals(AnalysisResultViewModel.AnalysisState.Partial(partial), viewModel.analysisState.value)

        viewModel.saveAsNewMole("Título", "", "Torso", null, null)
        assertEquals(AnalysisResultViewModel.Event.ValidationFailed(R.string.analysis_complete_required), viewModel.events.first())
        viewModel.saveToExistingMole("m-1", null, "", "", "Torso", null, null)
        assertEquals(AnalysisResultViewModel.Event.ValidationFailed(R.string.analysis_complete_required), viewModel.events.first())
        coVerify(exactly = 0) { moleRepository.saveMoleWithAnalysis(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { moleRepository.saveAnalysisToMole(any(), any(), any(), any()) }
    }

    @Test
    fun `un fallo total sustituye el resultado y no permite guardar`() = runTest {
        seedReadyState()
        viewModel.handleAnalysisCompleted(AnalysisOutcome.Failed(AnalysisError.Timeout))
        assertTrue(viewModel.analysisState.value is AnalysisResultViewModel.AnalysisState.Failed)
        viewModel.saveAsNewMole("Título", "", "Torso", null, null)
        assertEquals(AnalysisResultViewModel.Event.ValidationFailed(R.string.analysis_complete_required), viewModel.events.first())
        coVerify(exactly = 0) { moleRepository.saveMoleWithAnalysis(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `el resultado del runner inyectado llega a Ready una sola vez`() = runTest {
        val bitmap = mockk<Bitmap>()
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeFile("foto.jpg") } returns bitmap
        val outcome = AnalysisOutcome.Complete(combinedResult())
        coEvery { runner.processImage(any(), any(), any(), any(), any()) } returns outcome
        viewModel.startAnalysis("foto.jpg", false, 1f)
        viewModel.startAnalysis("foto.jpg", false, 1f)
        assertEquals(AnalysisResultViewModel.AnalysisState.Ready(outcome.result), viewModel.analysisState.value)
        coVerify(exactly = 1) { runner.processImage(bitmap, null, 1f, any(), any()) }
    }

    @Test
    fun `cancelar detiene el trabajo y descarta progreso y resultados tardios`() = runTest {
        val bitmap = mockk<Bitmap>()
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeFile("foto.jpg") } returns bitmap
        var stopped = false
        lateinit var progress: (AnalysisProgress) -> Unit
        coEvery { runner.processImage(any(), any(), any(), any(), any()) } coAnswers {
            progress = arg(4)
            try { awaitCancellation() } finally { stopped = true }
        }
        viewModel.startAnalysis("foto.jpg", false, 1f)
        assertTrue(viewModel.isProcessing)
        viewModel.cancelAnalysis()
        assertTrue(stopped)
        assertEquals(AnalysisResultViewModel.Event.AnalysisCancelled, viewModel.events.first())
        progress(AnalysisProgress(ProcessingStage.FINALIZING, 100, "finalizado"))
        viewModel.handleAnalysisCompleted(AnalysisOutcome.Complete(combinedResult()))
        assertEquals(AnalysisResultViewModel.AnalysisState.Cancelled, viewModel.analysisState.value)
        viewModel.saveAsNewMole("Título", "", "Torso", null, null)
        assertEquals(AnalysisResultViewModel.Event.ValidationFailed(R.string.analysis_complete_required), viewModel.events.first())
    }

    @Test
    fun `cancelar durante la carga de observacion tambien cancela el trabajo`() = runTest {
        var stopped = false
        coEvery { getBaselineObservation("m-1") } coAnswers {
            try { awaitCancellation() } finally { stopped = true }
        }
        viewModel.startAnalysis("foto.jpg", false, 1f, "m-1")
        viewModel.cancelAnalysis()
        assertTrue(stopped)
        assertEquals(AnalysisResultViewModel.AnalysisState.Cancelled, viewModel.analysisState.value)
        coVerify(exactly = 0) { runner.processImage(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `un motor que transforma cancelacion en error no cambia el estado cancelado`() = runTest {
        val bitmap = mockk<Bitmap>()
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeFile("foto.jpg") } returns bitmap
        coEvery { runner.processImage(any(), any(), any(), any(), any()) } coAnswers {
            try { awaitCancellation() } catch (_: CancellationException) { throw IllegalStateException("late failure") }
        }
        viewModel.startAnalysis("foto.jpg", false, 1f)
        viewModel.cancelAnalysis()
        assertEquals(AnalysisResultViewModel.AnalysisState.Cancelled, viewModel.analysisState.value)
    }

    @Test
    fun `al destruir el ViewModel se cancela antes de cerrar el motor`() = runTest {
        val bitmap = mockk<Bitmap>()
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeFile("foto.jpg") } returns bitmap
        var stopped = false
        coEvery { runner.processImage(any(), any(), any(), any(), any()) } coAnswers {
            try { awaitCancellation() } finally { stopped = true }
        }
        every { runner.close() } answers { assertTrue(stopped) }
        val store = ViewModelStore()
        store.put("analysis", viewModel)
        viewModel.startAnalysis("foto.jpg", false, 1f)
        store.clear()
        verify(exactly = 1) { runner.close() }
    }

    @Test
    fun `guardado correcto como lunar nuevo emite SaveCompleted`() = runTest {
        seedReadyState()
        coEvery {
            moleRepository.saveMoleWithAnalysis(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(MoleData(id = "nuevo"))

        viewModel.saveAsNewMole("Título", "desc", "Torso", "#FFFFC90E", null)

        assertEquals(AnalysisResultViewModel.Event.SaveCompleted(), viewModel.events.first())
        assertEquals(false, viewModel.isSaving.value)
    }

    @Test
    fun `el guardado incluye los valores ABCDE del usuario en los metadatos`() = runTest {
        seedReadyState()
        coEvery {
            moleRepository.saveMoleWithAnalysis(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(MoleData(id = "nuevo"))

        viewModel.saveAsNewMole(
            "Título", "", "Torso", "#FFFFC90E",
            userInput = UserAbcdeInput(asymmetry = 1.5f, border = 3f)
        )
        viewModel.events.first()

        coVerify {
            moleRepository.saveMoleWithAnalysis(
                any(), any(), any(), any(), any(), any(),
                match { analysisData ->
                    analysisData.analysisMetadata["userAsymmetry"] == 1.5f &&
                            analysisData.analysisMetadata.containsKey("userTotal") &&
                            analysisData.analysisMetadata["analysisStatus"] == "COMPLETE" &&
                            analysisData.analysisMetadata["aiAvailable"] == true &&
                            analysisData.analysisMetadata["abcdeAvailable"] == true &&
                            analysisData.riskLevel == "LOW"
                }
            )
        }
    }

    @Test
    fun `asociar a un lunar existente emite SaveCompleted con su titulo`() = runTest {
        seedReadyState()
        coEvery {
            moleRepository.saveAnalysisToMole(any(), any(), any(), any())
        } returns Result.success(Unit)

        viewModel.saveToExistingMole(
            mole = MoleData(id = "m-1", title = "Lunar hombro"),
            title = "", description = "", bodyPart = "Torso", bodyPartColorCode = null, userInput = null
        )

        assertEquals(
            AnalysisResultViewModel.Event.SaveCompleted(existingMoleTitle = "Lunar hombro"),
            viewModel.events.first()
        )
    }

    @Test
    fun `un fallo del repositorio emite SaveFailed con el detalle`() = runTest {
        seedReadyState()
        coEvery {
            moleRepository.saveMoleWithAnalysis(any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(IllegalStateException("sin conexión"))

        viewModel.saveAsNewMole("Título", "", "Torso", "#FFFFC90E", null)

        assertEquals(
            AnalysisResultViewModel.Event.SaveFailed("sin conexión"),
            viewModel.events.first()
        )
        assertEquals(false, viewModel.isSaving.value)
    }

    @Test
    fun `startAnalysis es idempotente frente a recreaciones`() = runTest {
        viewModel.startAnalysis("foto.jpg", false, 1f)
        val firstState = viewModel.analysisState.value
        viewModel.startAnalysis("foto.jpg", false, 1f)

        // El segundo intento no relanza nada: el estado no cambia.
        assertEquals(firstState, viewModel.analysisState.value)
    }

    @Test
    fun `si la imagen no se puede decodificar el estado es Failed`() = runTest {
        viewModel.startAnalysis("no-existe.jpg", false, 1f)

        assertTrue(viewModel.analysisState.value is AnalysisResultViewModel.AnalysisState.Failed)
    }

    @Test
    fun `un lunar nuevo no intenta cargar observacion base`() =
        runTest {
            viewModel.startAnalysis(
                "no-existe.jpg",
                false,
                1f,
                targetMoleId = null
            )

            assertEquals(
                AnalysisResultViewModel.BaselineObservationState.NewMole,
                viewModel.baselineObservationState.value
            )
            coVerify(exactly = 0) {
                getBaselineObservation(any())
            }
        }

    @Test
    fun `un lunar existente expone la observacion base correcta`() =
        runTest {
            val expected =
                MoleObservation(
                    id = "obs-1",
                    moleId = "m-1",
                    imageReference = "imagenes/m-1.jpg",
                    capturedAtEpochMillis = 1234L
                )
            coEvery {
                getBaselineObservation("m-1")
            } returns Result.success(expected)

            viewModel.startAnalysis(
                "no-existe.jpg",
                false,
                1f,
                targetMoleId = "m-1"
            )

            assertEquals(
                AnalysisResultViewModel.BaselineObservationState.Available(
                    expected
                ),
                viewModel.baselineObservationState.value
            )
            coVerify(exactly = 1) {
                getBaselineObservation("m-1")
            }
        }

    @Test
    fun `una pareja comparable queda preparada sin activar evolucion`() =
        runTest {
            val observation = MoleObservation(
                id = "obs-1",
                moleId = "m-1",
                imageReference = "imagenes/m-1.jpg",
                capturedAtEpochMillis = 1234L
            )
            val currentBitmap = mockk<Bitmap>()
            val baselineBitmap = mockk<Bitmap>(relaxed = true)
            val metrics = ObservationImageMetrics(
                width = 1024,
                height = 768,
                brightness = 120f,
                isOptimalLighting = true,
                isOverexposed = false,
                isUnderexposed = false,
                sharpness = 100.0,
                isBlurry = false
            )
            val assessment = ObservationComparability.Comparable(
                baselineObservationId = observation.id,
                baselineMetrics = metrics,
                currentMetrics = metrics
            )
            val registrationMetrics = LesionRegistrationMetrics(
                algorithmVersion = "test-v1",
                baselineAreaFraction = 0.12,
                currentAreaFraction = 0.13,
                originalCentroidDistance = 0.04,
                appliedScale = 1.03,
                appliedRotationDegrees = 2.0,
                alignedMaskIou = 0.82
            )
            coEvery {
                getBaselineObservation("m-1")
            } returns Result.success(observation)
            coEvery {
                comparisonPreparer.prepare(currentBitmap, observation)
            } returns PreparedObservationComparison(
                assessment = assessment,
                baselineBitmap = baselineBitmap,
                registrationMetrics = registrationMetrics
            )

            viewModel.startAnalysis(
                "no-existe.jpg",
                false,
                1f,
                targetMoleId = "m-1"
            )
            viewModel.prepareComparison(currentBitmap, "m-1")

            assertEquals(
                AnalysisResultViewModel.ComparisonPreparationState.Ready(
                    assessment = assessment,
                    registrationMetrics = registrationMetrics
                ),
                viewModel.comparisonPreparationState.value
            )
            coVerify(exactly = 1) {
                comparisonPreparer.prepare(currentBitmap, observation)
            }
        }

    @Test
    fun `sin imagen base la comparacion se abstiene y no invoca el preparador`() =
        runTest {
            val currentBitmap = mockk<Bitmap>()
            coEvery {
                getBaselineObservation("m-1")
            } returns Result.success(null)

            viewModel.startAnalysis(
                "no-existe.jpg",
                false,
                1f,
                targetMoleId = "m-1"
            )
            viewModel.prepareComparison(currentBitmap, "m-1")

            val state = viewModel.comparisonPreparationState.value
            assertTrue(
                state is
                    AnalysisResultViewModel.ComparisonPreparationState.Abstained
            )
            assertEquals(
                ObservationComparability.Reason.NO_BASELINE,
                (
                    state as
                        AnalysisResultViewModel.ComparisonPreparationState.Abstained
                    ).assessment.reason
            )
            coVerify(exactly = 0) {
                comparisonPreparer.prepare(any(), any())
            }
        }

    @Test
    fun `sin metricas de registro nunca expone la pareja como preparada`() =
        runTest {
            val observation = MoleObservation(
                id = "obs-1",
                moleId = "m-1",
                imageReference = "imagenes/m-1.jpg",
                capturedAtEpochMillis = 1234L
            )
            val currentBitmap = mockk<Bitmap>()
            val baselineBitmap = mockk<Bitmap>(relaxed = true)
            val metrics = ObservationImageMetrics(
                width = 1024,
                height = 768,
                brightness = 120f,
                isOptimalLighting = true,
                isOverexposed = false,
                isUnderexposed = false,
                sharpness = 100.0,
                isBlurry = false
            )
            val assessment = ObservationComparability.Comparable(
                baselineObservationId = observation.id,
                baselineMetrics = metrics,
                currentMetrics = metrics
            )
            coEvery {
                getBaselineObservation("m-1")
            } returns Result.success(observation)
            coEvery {
                comparisonPreparer.prepare(currentBitmap, observation)
            } returns PreparedObservationComparison(
                assessment = assessment,
                baselineBitmap = baselineBitmap,
                registrationMetrics = null
            )

            viewModel.startAnalysis(
                "no-existe.jpg",
                false,
                1f,
                targetMoleId = "m-1"
            )
            viewModel.prepareComparison(currentBitmap, "m-1")

            val state = viewModel.comparisonPreparationState.value
            assertTrue(
                state is
                    AnalysisResultViewModel.ComparisonPreparationState.Abstained
            )
            assertEquals(
                ObservationComparability.Reason.REGISTRATION_UNRELIABLE,
                (
                    state as
                        AnalysisResultViewModel.ComparisonPreparationState.Abstained
                    ).assessment.reason
            )
        }
}

package com.monsteraltech.skincare.body.mole

import android.content.Context
import com.google.firebase.Timestamp
import com.monsteraltech.skincare.data.identity.ActiveProfile
import com.monsteraltech.skincare.body.mole.error.ErrorHandler
import com.monsteraltech.skincare.body.mole.model.ABCDEScores
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.model.MoleData
import com.monsteraltech.skincare.body.mole.model.UserAbcdeInput
import com.monsteraltech.skincare.body.mole.repository.MoleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
class MoleViewerViewModelTest {

    private lateinit var context: Context
    private lateinit var moleRepository: MoleRepository
    private lateinit var activeProfile: ActiveProfile
    private lateinit var viewModel: MoleViewerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = mockk(relaxed = true)
        moleRepository = mockk()
        activeProfile = mockk()
        every { activeProfile.ownerId } returns "user-1"
        viewModel = MoleViewerViewModel(context, moleRepository, activeProfile)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun args(
        moleId: String = "",
        aiResult: String = "",
        historical: AnalysisData? = null,
        analysisCount: Int = 0
    ) = MoleViewerArgs(
        moleId = moleId,
        title = "Lunar test",
        description = "",
        imageUrl = "",
        aiResult = aiResult,
        analysisCount = analysisCount,
        historicalAnalysis = historical
    )

    private fun historicalAnalysis() = AnalysisData(
        id = "hist-1",
        moleId = "mole-1",
        combinedScore = 0.4f,
        riskLevel = "MEDIUM",
        recommendation = "test",
        abcdeScores = ABCDEScores(totalScore = 5f),
        createdAt = Timestamp.now()
    )

    private fun moleData() = MoleData(
        id = "mole-1",
        title = "Lunar",
        aiResult = "resultado",
        analysisCount = 3,
        aiProbability = 0.35,
        aiConfidence = 0.8,
        riskLevel = "LOW",
        combinedScore = 0.3,
        abcdeTotalScore = 4.2,
        recommendation = "vigilar"
    )

    @Test
    fun `un analisis historico se muestra directamente`() = runTest {
        viewModel.initialize(args(moleId = "mole-1", historical = historicalAnalysis(), analysisCount = 1))

        val state = viewModel.uiState.value
        assertTrue(state is MoleViewerViewModel.UiState.Analysis)
        assertEquals("hist-1", (state as MoleViewerViewModel.UiState.Analysis).data.id)
        coVerify(exactly = 0) { moleRepository.getMoleById(any(), any()) }
    }

    @Test
    fun `con moleId se carga del repositorio y se mapean los campos`() = runTest {
        coEvery { moleRepository.getMoleById("user-1", "mole-1") } returns Result.success(moleData())

        viewModel.initialize(args(moleId = "mole-1"))

        val state = viewModel.uiState.value
        assertTrue(state is MoleViewerViewModel.UiState.Analysis)
        val analysis = (state as MoleViewerViewModel.UiState.Analysis)
        assertEquals(3, analysis.analysisCount)
        assertEquals(0.35f, analysis.data.aiProbability, 0.001f)
        assertEquals(4.2f, analysis.data.abcdeScores.totalScore, 0.001f)
        assertEquals("LOW", analysis.data.riskLevel)
    }

    @Test
    fun `sin usuario autenticado el estado es error de autenticacion`() = runTest {
        every { activeProfile.ownerId } returns null

        viewModel.initialize(args(moleId = "mole-1"))

        val state = viewModel.uiState.value
        assertTrue(state is MoleViewerViewModel.UiState.Error)
        assertEquals(
            ErrorHandler.ErrorType.AUTHENTICATION_ERROR,
            (state as MoleViewerViewModel.UiState.Error).result.type
        )
    }

    @Test
    fun `un fallo del repositorio produce estado de error`() = runTest {
        coEvery { moleRepository.getMoleById(any(), any()) } returns
                Result.failure(IllegalStateException("boom"))

        viewModel.initialize(args(moleId = "mole-1"))

        assertTrue(viewModel.uiState.value is MoleViewerViewModel.UiState.Error)
    }

    @Test
    fun `sin datos en el intent el estado es NoAnalysis`() = runTest {
        viewModel.initialize(args())

        assertEquals(MoleViewerViewModel.UiState.NoAnalysis, viewModel.uiState.value)
    }

    @Test
    fun `initialize es idempotente`() = runTest {
        coEvery { moleRepository.getMoleById(any(), any()) } returns Result.success(moleData())

        viewModel.initialize(args(moleId = "mole-1"))
        viewModel.initialize(args(moleId = "mole-1"))

        coVerify(exactly = 1) { moleRepository.getMoleById("user-1", "mole-1") }
    }

    @Test
    fun `guardar valores de usuario emite evento y recarga`() = runTest {
        coEvery { moleRepository.getMoleById(any(), any()) } returns Result.success(moleData())
        coEvery { moleRepository.updateMoleAnalysisMetadata(any(), any(), any()) } returns
                Result.success(Unit)
        viewModel.initialize(args(moleId = "mole-1"))

        viewModel.saveUserValues(UserAbcdeInput(asymmetry = 1f))

        assertEquals(MoleViewerViewModel.Event.UserValuesSaved, viewModel.events.first())
        coVerify {
            moleRepository.updateMoleAnalysisMetadata(
                "user-1",
                "mole-1",
                match { it["userAsymmetry"] == 1f && it.containsKey("userTotal") }
            )
        }
        // Recarga tras guardar: initialize + save = 2 lecturas
        coVerify(exactly = 2) { moleRepository.getMoleById("user-1", "mole-1") }
    }

    @Test
    fun `guardar sin moleId emite fallo sin tocar el repositorio`() = runTest {
        viewModel.initialize(args())

        viewModel.saveUserValues(UserAbcdeInput(asymmetry = 1f))

        assertEquals(MoleViewerViewModel.Event.UserValuesSaveFailed, viewModel.events.first())
        coVerify(exactly = 0) { moleRepository.updateMoleAnalysisMetadata(any(), any(), any()) }
    }

    @Test
    fun `un fallo al guardar emite el evento de error`() = runTest {
        coEvery { moleRepository.getMoleById(any(), any()) } returns Result.success(moleData())
        coEvery { moleRepository.updateMoleAnalysisMetadata(any(), any(), any()) } returns
                Result.failure(IllegalStateException("sin conexión"))
        viewModel.initialize(args(moleId = "mole-1"))

        viewModel.saveUserValues(UserAbcdeInput(border = 2f))

        assertEquals(MoleViewerViewModel.Event.UserValuesSaveFailed, viewModel.events.first())
    }
}

package es.monsteraltech.skincare_tfm.body.mole

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import es.monsteraltech.skincare_tfm.body.mole.model.ABCDEScores
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.EvolutionComparison
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class AnalysisComparisonIntegrationTest {

    @Test
    fun testEvolutionComparisonCreation() {
        // Crear análisis de prueba
        val currentAnalysis = createTestAnalysis(
            id = "current",
            riskLevel = "MODERATE",
            abcdeScores = ABCDEScores(2.1f, 1.8f, 2.5f, 1.2f, 0.8f, 8.4f),
            combinedScore = 0.65f,
            aiConfidence = 0.85f,
            createdAt = Timestamp.now()
        )

        val previousAnalysis = createTestAnalysis(
            id = "previous",
            riskLevel = "HIGH",
            abcdeScores = ABCDEScores(1.8f, 2.0f, 2.4f, 1.3f, 0.9f, 8.4f),
            combinedScore = 0.70f,
            aiConfidence = 0.80f,
            createdAt = Timestamp(Date(System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000))
        )

        // Crear comparación
        val comparison = EvolutionComparison.create(currentAnalysis, previousAnalysis)

        // Verificar cálculos básicos
        assertEquals(30L, comparison.timeDifferenceInDays)
        assertEquals("MODERATE", comparison.currentAnalysis.riskLevel)
        assertEquals("HIGH", comparison.previousAnalysis.riskLevel)

        // Verificar cambios en scores
        assertEquals(0.3f, comparison.scoreChanges.asymmetryChange, 0.01f)
        assertEquals(-0.2f, comparison.scoreChanges.borderChange, 0.01f)
        assertEquals(0.1f, comparison.scoreChanges.colorChange, 0.01f)
        assertEquals(-0.1f, comparison.scoreChanges.diameterChange, 0.01f)
        assertEquals(-0.05f, comparison.scoreChanges.combinedScoreChange, 0.01f)

        // Verificar cambio de nivel de riesgo
        assertTrue(comparison.riskLevelChange.hasImproved)
        assertFalse(comparison.riskLevelChange.hasWorsened)
        assertEquals("HIGH", comparison.riskLevelChange.previousLevel)
        assertEquals("MODERATE", comparison.riskLevelChange.currentLevel)

        // Verificar tendencia general
        assertEquals(EvolutionComparison.EvolutionTrend.IMPROVING, comparison.overallTrend)

        // Verificar que hay cambios significativos
        assertTrue(comparison.significantChanges.isNotEmpty())
        assertTrue(comparison.scoreChanges.hasSignificantChange)
    }

    @Test
    fun testWorseningEvolution() {
        // Crear análisis que empeora
        val currentAnalysis = createTestAnalysis(
            id = "current",
            riskLevel = "HIGH",
            abcdeScores = ABCDEScores(3.0f, 3.5f, 3.2f, 2.8f, 1.5f, 14.0f),
            combinedScore = 0.85f,
            aiConfidence = 0.70f
        )

        val previousAnalysis = createTestAnalysis(
            id = "previous",
            riskLevel = "MODERATE",
            abcdeScores = ABCDEScores(2.0f, 2.0f, 2.0f, 2.0f, 1.0f, 9.0f),
            combinedScore = 0.60f,
            aiConfidence = 0.80f,
            createdAt = Timestamp(Date(System.currentTimeMillis() - 15 * 24 * 60 * 60 * 1000))
        )

        val comparison = EvolutionComparison.create(currentAnalysis, previousAnalysis)

        // Verificar empeoramiento
        assertTrue(comparison.riskLevelChange.hasWorsened)
        assertFalse(comparison.riskLevelChange.hasImproved)
        assertEquals(EvolutionComparison.EvolutionTrend.WORSENING, comparison.overallTrend)

        // Verificar cambios significativos negativos
        assertTrue(comparison.scoreChanges.asymmetryChange > 0.5f)
        assertTrue(comparison.scoreChanges.borderChange > 0.5f)
        assertTrue(comparison.scoreChanges.totalScoreChange > 2.0f)
    }

    @Test
    fun testStableEvolution() {
        // Crear análisis estables
        val currentAnalysis = createTestAnalysis(
            id = "current",
            riskLevel = "LOW",
            abcdeScores = ABCDEScores(1.0f, 1.1f, 1.0f, 0.9f, 0.5f, 4.5f),
            combinedScore = 0.30f,
            aiConfidence = 0.90f
        )

        val previousAnalysis = createTestAnalysis(
            id = "previous",
            riskLevel = "LOW",
            abcdeScores = ABCDEScores(1.1f, 1.0f, 1.1f, 1.0f, 0.4f, 4.6f),
            combinedScore = 0.32f,
            aiConfidence = 0.88f,
            createdAt = Timestamp(Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000))
        )

        val comparison = EvolutionComparison.create(currentAnalysis, previousAnalysis)

        // Verificar estabilidad
        assertFalse(comparison.riskLevelChange.hasChanged)
        assertEquals(EvolutionComparison.EvolutionTrend.STABLE, comparison.overallTrend)
        assertFalse(comparison.scoreChanges.hasSignificantChange)
    }

    @Test
    fun testEvolutionSummaryGeneration() {
        val currentAnalysis = createTestAnalysis(
            id = "current",
            riskLevel = "MODERATE",
            abcdeScores = ABCDEScores(2.0f, 1.5f, 2.0f, 1.0f, 0.5f, 7.0f),
            combinedScore = 0.55f
        )

        val previousAnalysis = createTestAnalysis(
            id = "previous",
            riskLevel = "HIGH",
            abcdeScores = ABCDEScores(2.5f, 2.0f, 2.5f, 1.5f, 1.0f, 9.5f),
            combinedScore = 0.75f,
            createdAt = Timestamp(Date(System.currentTimeMillis() - 45 * 24 * 60 * 60 * 1000))
        )

        val comparison = EvolutionComparison.create(currentAnalysis, previousAnalysis)
        val summary = comparison.getEvolutionSummary()

        // Verificar que el resumen contiene información clave
        assertTrue(summary.contains("45 días"))
        assertTrue(summary.contains("Tendencia positiva") || summary.contains("HIGH") || summary.contains("MODERATE"))
    }

    private fun createTestAnalysis(
        id: String,
        riskLevel: String,
        abcdeScores: ABCDEScores,
        combinedScore: Float,
        aiConfidence: Float = 0.80f,
        createdAt: Timestamp = Timestamp.now()
    ): AnalysisData {
        return AnalysisData(
            id = id,
            moleId = "test_mole",
            analysisResult = "Test analysis result",
            aiProbability = 0.75f,
            aiConfidence = aiConfidence,
            abcdeScores = abcdeScores,
            combinedScore = combinedScore,
            riskLevel = riskLevel,
            recommendation = "Test recommendation",
            imageUrl = "",
            createdAt = createdAt,
            analysisMetadata = emptyMap()
        )
    }
}
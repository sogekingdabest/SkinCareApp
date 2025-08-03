package es.monsteraltech.skincare_tfm.analysis

import es.monsteraltech.skincare_tfm.body.mole.model.ABCDEScores
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import org.junit.Test
import org.junit.Assert.*

/**
 * Test básico para verificar la integración del selector de lunares
 */
class AnalysisResultActivityIntegrationTest {

    @Test
    fun testABCDEScoresCreation() {
        val scores = ABCDEScores(
            asymmetryScore = 1.5f,
            borderScore = 3.2f,
            colorScore = 2.1f,
            diameterScore = 1.8f,
            evolutionScore = 0.5f,
            totalScore = 9.1f
        )

        assertEquals(1.5f, scores.asymmetryScore, 0.01f)
        assertEquals(3.2f, scores.borderScore, 0.01f)
        assertEquals(2.1f, scores.colorScore, 0.01f)
        assertEquals(1.8f, scores.diameterScore, 0.01f)
        assertEquals(0.5f, scores.evolutionScore!!, 0.01f)
        assertEquals(9.1f, scores.totalScore, 0.01f)
    }

    @Test
    fun testABCDEScoresToMap() {
        val scores = ABCDEScores(
            asymmetryScore = 1.5f,
            borderScore = 3.2f,
            colorScore = 2.1f,
            diameterScore = 1.8f,
            evolutionScore = 0.5f,
            totalScore = 9.1f
        )

        val map = scores.toMap()
        
        assertEquals(1.5f, map["asymmetryScore"] as Float, 0.01f)
        assertEquals(3.2f, map["borderScore"] as Float, 0.01f)
        assertEquals(2.1f, map["colorScore"] as Float, 0.01f)
        assertEquals(1.8f, map["diameterScore"] as Float, 0.01f)
        assertEquals(0.5f, map["evolutionScore"] as Float, 0.01f)
        assertEquals(9.1f, map["totalScore"] as Float, 0.01f)
    }

    @Test
    fun testABCDEScoresFromMap() {
        val map = mapOf(
            "asymmetryScore" to 1.5f,
            "borderScore" to 3.2f,
            "colorScore" to 2.1f,
            "diameterScore" to 1.8f,
            "evolutionScore" to 0.5f,
            "totalScore" to 9.1f
        )

        val scores = ABCDEScores.fromMap(map)
        
        assertEquals(1.5f, scores.asymmetryScore, 0.01f)
        assertEquals(3.2f, scores.borderScore, 0.01f)
        assertEquals(2.1f, scores.colorScore, 0.01f)
        assertEquals(1.8f, scores.diameterScore, 0.01f)
        assertEquals(0.5f, scores.evolutionScore!!, 0.01f)
        assertEquals(9.1f, scores.totalScore, 0.01f)
    }

    @Test
    fun testAnalysisDataCreation() {
        val abcdeScores = ABCDEScores(
            asymmetryScore = 1.5f,
            borderScore = 3.2f,
            colorScore = 2.1f,
            diameterScore = 1.8f,
            evolutionScore = 0.5f,
            totalScore = 9.1f
        )

        val analysisData = AnalysisData(
            moleId = "test-mole-id",
            analysisResult = "Test analysis result",
            aiProbability = 0.75f,
            aiConfidence = 0.85f,
            abcdeScores = abcdeScores,
            combinedScore = 0.80f,
            riskLevel = "MEDIUM",
            recommendation = "Test recommendation",
            imageUrl = "test-image-url",
            analysisMetadata = mapOf("test" to "value")
        )

        assertEquals("test-mole-id", analysisData.moleId)
        assertEquals("Test analysis result", analysisData.analysisResult)
        assertEquals(0.75f, analysisData.aiProbability, 0.01f)
        assertEquals(0.85f, analysisData.aiConfidence, 0.01f)
        assertEquals(abcdeScores, analysisData.abcdeScores)
        assertEquals(0.80f, analysisData.combinedScore, 0.01f)
        assertEquals("MEDIUM", analysisData.riskLevel)
        assertEquals("Test recommendation", analysisData.recommendation)
        assertEquals("test-image-url", analysisData.imageUrl)
        assertEquals(mapOf("test" to "value"), analysisData.analysisMetadata)
    }
}
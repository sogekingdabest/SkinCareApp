package es.monsteraltech.skincare_tfm.body.mole
import com.google.firebase.Timestamp
import es.monsteraltech.skincare_tfm.body.mole.model.ABCDEScores
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisDataConverter
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
class MoleViewerIntegrationTest {
    @Test
    fun testCompleteAnalysisDataFlow() {
        val moleData = MoleData(
            id = "test-mole-123",
            title = "Lunar en brazo izquierdo",
            description = "Lunar pequeño observado desde hace 2 años",
            bodyPart = "brazo_izquierdo",
            bodyPartColorCode = "#FF5722",
            imageUrl = "test-image-url.jpg",
            aiResult = """
                {
                    "probability": 0.25,
                    "confidence": 0.85,
                    "combinedScore": 3.2,
                    "riskLevel": "LOW",
                    "recommendation": "Continúe con autoexámenes regulares",
                    "abcdeScores": {
                        "asymmetryScore": 0.5,
                        "borderScore": 1.0,
                        "colorScore": 2.0,
                        "diameterScore": 0.5,
                        "totalScore": 4.0
                    }
                }
            """.trimIndent(),
            analysisCount = 3,
            lastAnalysisDate = Timestamp.now(),
            firstAnalysisDate = Timestamp(Timestamp.now().seconds - 86400 * 30, 0)
        )
        val analysisData = AnalysisDataConverter.fromAiResultString(
            aiResult = moleData.aiResult,
            moleId = moleData.id,
            imageUrl = moleData.imageUrl,
            createdAt = moleData.lastAnalysisDate ?: Timestamp.now()
        )
        assertNotNull("AnalysisData no debe ser null", analysisData)
        analysisData?.let { data ->
            assertEquals("MoleId debe coincidir", moleData.id, data.moleId)
            assertEquals("ImageUrl debe coincidir", moleData.imageUrl, data.imageUrl)
            assertEquals("AI Probability debe ser 0.25", 0.25f, data.aiProbability, 0.01f)
            assertEquals("AI Confidence debe ser 0.85", 0.85f, data.aiConfidence, 0.01f)
            assertEquals("Combined Score debe ser 3.2", 3.2f, data.combinedScore, 0.01f)
            assertEquals("Risk Level debe ser LOW", "LOW", data.riskLevel)
            assertEquals("Recommendation debe coincidir", "Continúe con autoexámenes regulares", data.recommendation)
            assertEquals("Asymmetry Score debe ser 0.5", 0.5f, data.abcdeScores.asymmetryScore, 0.01f)
            assertEquals("Border Score debe ser 1.0", 1.0f, data.abcdeScores.borderScore, 0.01f)
            assertEquals("Color Score debe ser 2.0", 2.0f, data.abcdeScores.colorScore, 0.01f)
            assertEquals("Diameter Score debe ser 0.5", 0.5f, data.abcdeScores.diameterScore, 0.01f)
            assertEquals("Total Score debe ser 4.0", 4.0f, data.abcdeScores.totalScore, 0.01f)
        }
        analysisData?.let { data ->
            val convertedAiResult = AnalysisDataConverter.toAiResultString(data)
            assertFalse("Converted AI Result no debe estar vacío", convertedAiResult.isEmpty())
            assertTrue("Debe contener probability", convertedAiResult.contains("probability"))
            assertTrue("Debe contener riskLevel", convertedAiResult.contains("riskLevel"))
        }
    }
    @Test
    fun testAnalysisDataWithPlainTextResult() {
        val plainTextResult = """
            Análisis ABCDE realizado:
            - Asimetría: Lunar simétrico (Score: 0)
            - Bordes: Bordes regulares (Score: 1)
            - Color: Dos colores detectados (Score: 2)
            - Diámetro: Menor a 6mm (Score: 0)
            - Score Total: 3
            Recomendación: Lunar de bajo riesgo. Continuar con autoexámenes regulares.
        """.trimIndent()
        val analysisData = AnalysisDataConverter.fromAiResultString(
            aiResult = plainTextResult,
            moleId = "test-mole-456",
            imageUrl = "test-image-2.jpg"
        )
        assertNotNull("AnalysisData no debe ser null para texto plano", analysisData)
        analysisData?.let { data ->
            assertEquals("MoleId debe coincidir", "test-mole-456", data.moleId)
            assertEquals("Analysis Result debe contener el texto original", plainTextResult, data.analysisResult)
            assertEquals("Risk Level debe ser DESCONOCIDO", "DESCONOCIDO", data.riskLevel)
            assertTrue("Recommendation debe contener texto", data.recommendation.isNotEmpty())
        }
    }
    @Test
    fun testMoleDataUpdateWithAnalysis() {
        val originalMole = MoleData(
            id = "test-mole-789",
            title = "Lunar en espalda",
            description = "Lunar observado recientemente",
            analysisCount = 1,
            firstAnalysisDate = Timestamp.now()
        )
        val newAnalysis = AnalysisData(
            id = "analysis-123",
            moleId = originalMole.id,
            analysisResult = "Nuevo análisis realizado",
            aiProbability = 0.3f,
            aiConfidence = 0.9f,
            abcdeScores = ABCDEScores(
                asymmetryScore = 1.0f,
                borderScore = 2.0f,
                colorScore = 1.5f,
                diameterScore = 1.0f,
                totalScore = 5.5f
            ),
            combinedScore = 4.0f,
            riskLevel = "MODERATE",
            recommendation = "Evaluación dermatológica recomendada",
            imageUrl = "new-analysis-image.jpg",
            createdAt = Timestamp.now()
        )
        val updatedMole = AnalysisDataConverter.updateMoleDataWithAnalysis(originalMole, newAnalysis)
        assertEquals("Analysis count debe incrementarse", 2, updatedMole.analysisCount)
        assertEquals("Last analysis date debe actualizarse", newAnalysis.createdAt, updatedMole.lastAnalysisDate)
        assertEquals("First analysis date debe mantenerse", originalMole.firstAnalysisDate, updatedMole.firstAnalysisDate)
        assertFalse("AI Result debe actualizarse", updatedMole.aiResult.isEmpty())
    }
    @Test
    fun testAnalysisDataValidation() {
        val validAnalysis = AnalysisData(
            id = "valid-analysis",
            moleId = "valid-mole",
            analysisResult = "Análisis válido",
            aiProbability = 0.5f,
            aiConfidence = 0.8f,
            combinedScore = 3.5f,
            riskLevel = "MODERATE",
            recommendation = "Recomendación válida",
            imageUrl = "valid-image.jpg"
        )
        assertTrue("MoleId no debe estar vacío", validAnalysis.moleId.isNotEmpty())
        assertTrue("Analysis Result no debe estar vacío", validAnalysis.analysisResult.isNotEmpty())
        assertTrue("AI Probability debe estar en rango válido", validAnalysis.aiProbability >= 0f && validAnalysis.aiProbability <= 1f)
        assertTrue("AI Confidence debe estar en rango válido", validAnalysis.aiConfidence >= 0f && validAnalysis.aiConfidence <= 1f)
        assertTrue("Risk Level no debe estar vacío", validAnalysis.riskLevel.isNotEmpty())
        assertTrue("Image URL no debe estar vacía", validAnalysis.imageUrl.isNotEmpty())
    }
    @Test
    fun testABCDEScoresFormatting() {
        val scores = ABCDEScores(
            asymmetryScore = 1.5f,
            borderScore = 3.2f,
            colorScore = 2.8f,
            diameterScore = 2.0f,
            evolutionScore = 1.2f,
            totalScore = 10.7f
        )
        assertTrue("Asymmetry score debe estar en rango", scores.asymmetryScore >= 0f && scores.asymmetryScore <= 2f)
        assertTrue("Border score debe estar en rango", scores.borderScore >= 0f && scores.borderScore <= 8f)
        assertTrue("Color score debe estar en rango", scores.colorScore >= 0f && scores.colorScore <= 6f)
        assertTrue("Diameter score debe estar en rango", scores.diameterScore >= 0f && scores.diameterScore <= 5f)
        scores.evolutionScore?.let { evolution ->
            assertTrue("Evolution score debe estar en rango", evolution >= 0f && evolution <= 3f)
        }
        assertTrue("Total score debe ser positivo", scores.totalScore > 0f)
        val scoresMap = scores.toMap()
        assertTrue("Map debe contener asymmetryScore", scoresMap.containsKey("asymmetryScore"))
        assertTrue("Map debe contener borderScore", scoresMap.containsKey("borderScore"))
        assertTrue("Map debe contener colorScore", scoresMap.containsKey("colorScore"))
        assertTrue("Map debe contener diameterScore", scoresMap.containsKey("diameterScore"))
        assertTrue("Map debe contener evolutionScore", scoresMap.containsKey("evolutionScore"))
        assertTrue("Map debe contener totalScore", scoresMap.containsKey("totalScore"))
    }
}
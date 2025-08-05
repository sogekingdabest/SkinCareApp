package es.monsteraltech.skincare_tfm.body.mole
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import es.monsteraltech.skincare_tfm.body.mole.model.ABCDEScores
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisDataConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoleViewerActivityTest {
    @Test
    fun testAnalysisDataConverterFromAiResult() {
        val aiResult = """
            {
                "probability": 0.75,
                "confidence": 0.85,
                "combinedScore": 4.2,
                "riskLevel": "MODERATE",
                "recommendation": "Consulte con un dermatólogo",
                "abcdeScores": {
                    "asymmetryScore": 1.0,
                    "borderScore": 2.5,
                    "colorScore": 3.0,
                    "diameterScore": 1.5,
                    "totalScore": 8.0
                }
            }
        """.trimIndent()
        val moleId = "test-mole-id"
        val imageUrl = "test-image-url"
        val analysisData = AnalysisDataConverter.fromAiResultString(
            aiResult = aiResult,
            moleId = moleId,
            imageUrl = imageUrl
        )
        assertNotNull("AnalysisData no debe ser null", analysisData)
        analysisData?.let { data ->
            assertEquals("MoleId debe coincidir", moleId, data.moleId)
            assertEquals("ImageUrl debe coincidir", imageUrl, data.imageUrl)
            assertEquals("AI Probability debe coincidir", 0.75f, data.aiProbability, 0.01f)
            assertEquals("AI Confidence debe coincidir", 0.85f, data.aiConfidence, 0.01f)
            assertEquals("Combined Score debe coincidir", 4.2f, data.combinedScore, 0.01f)
            assertEquals("Risk Level debe coincidir", "MODERATE", data.riskLevel)
            assertEquals("Recommendation debe coincidir", "Consulte con un dermatólogo", data.recommendation)
            assertEquals("Asymmetry Score debe coincidir", 1.0f, data.abcdeScores.asymmetryScore, 0.01f)
            assertEquals("Border Score debe coincidir", 2.5f, data.abcdeScores.borderScore, 0.01f)
            assertEquals("Color Score debe coincidir", 3.0f, data.abcdeScores.colorScore, 0.01f)
            assertEquals("Diameter Score debe coincidir", 1.5f, data.abcdeScores.diameterScore, 0.01f)
            assertEquals("Total Score debe coincidir", 8.0f, data.abcdeScores.totalScore, 0.01f)
        }
    }
    @Test
    fun testAnalysisDataConverterFromPlainText() {
        val aiResult = "Análisis realizado: Lunar de riesgo moderado. Se recomienda consulta dermatológica."
        val moleId = "test-mole-id"
        val imageUrl = "test-image-url"
        val analysisData = AnalysisDataConverter.fromAiResultString(
            aiResult = aiResult,
            moleId = moleId,
            imageUrl = imageUrl
        )
        assertNotNull("AnalysisData no debe ser null", analysisData)
        analysisData?.let { data ->
            assertEquals("MoleId debe coincidir", moleId, data.moleId)
            assertEquals("ImageUrl debe coincidir", imageUrl, data.imageUrl)
            assertEquals("Analysis Result debe coincidir", aiResult, data.analysisResult)
            assertEquals("Risk Level debe ser DESCONOCIDO", "DESCONOCIDO", data.riskLevel)
            assertTrue("Recommendation debe contener texto", data.recommendation.isNotEmpty())
        }
    }
    @Test
    fun testAnalysisDataConverterToAiResultString() {
        val analysisData = AnalysisData(
            id = "test-id",
            moleId = "test-mole-id",
            analysisResult = "Test analysis result",
            aiProbability = 0.8f,
            aiConfidence = 0.9f,
            abcdeScores = ABCDEScores(
                asymmetryScore = 1.5f,
                borderScore = 3.0f,
                colorScore = 2.5f,
                diameterScore = 2.0f,
                totalScore = 9.0f
            ),
            combinedScore = 5.5f,
            riskLevel = "HIGH",
            recommendation = "Consulta urgente",
            imageUrl = "test-image-url",
            createdAt = Timestamp.now()
        )
        val aiResultString = AnalysisDataConverter.toAiResultString(analysisData)
        assertFalse("AI Result String no debe estar vacío", aiResultString.isEmpty())
        assertTrue("Debe contener probability", aiResultString.contains("probability"))
        assertTrue("Debe contener riskLevel", aiResultString.contains("riskLevel"))
        assertTrue("Debe contener abcdeScores", aiResultString.contains("abcdeScores"))
    }
    @Test
    fun testAnalysisDataConverterEmptyAiResult() {
        val analysisData = AnalysisDataConverter.fromAiResultString(
            aiResult = "",
            moleId = "test-mole-id",
            imageUrl = "test-image-url"
        )
        assertNull("AnalysisData debe ser null para aiResult vacío", analysisData)
    }
    @Test
    fun testActivityIntentCreation() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MoleViewerActivity::class.java).apply {
            putExtra("LUNAR_TITLE", "Lunar de prueba")
            putExtra("LUNAR_DESCRIPTION", "Descripción de prueba")
            putExtra("LUNAR_ANALYSIS_RESULT", "Resultado de análisis de prueba")
            putExtra("LUNAR_IMAGE_URL", "https://example.com/image.jpg")
            putExtra("MOLE_ID", "test-mole-id")
            putExtra("ANALYSIS_COUNT", 2)
        }
        assertEquals("Título debe coincidir", "Lunar de prueba", intent.getStringExtra("LUNAR_TITLE"))
        assertEquals("Descripción debe coincidir", "Descripción de prueba", intent.getStringExtra("LUNAR_DESCRIPTION"))
        assertEquals("Análisis debe coincidir", "Resultado de análisis de prueba", intent.getStringExtra("LUNAR_ANALYSIS_RESULT"))
        assertEquals("URL debe coincidir", "https://example.com/image.jpg", intent.getStringExtra("LUNAR_IMAGE_URL"))
        assertEquals("Mole ID debe coincidir", "test-mole-id", intent.getStringExtra("MOLE_ID"))
        assertEquals("Analysis Count debe coincidir", 2, intent.getIntExtra("ANALYSIS_COUNT", 0))
    }
}
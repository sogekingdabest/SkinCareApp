package es.monsteraltech.skincare_tfm.body.mole.service
import com.google.firebase.Timestamp
import es.monsteraltech.skincare_tfm.body.mole.model.ABCDEScores
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.validation.AnalysisDataValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
class MoleAnalysisServiceTest {
    @Test
    fun testValidAnalysisDataValidation() {
        val validAnalysis = AnalysisData(
            id = UUID.randomUUID().toString(),
            moleId = "test-mole-id",
            analysisResult = "Análisis completo del lunar",
            aiProbability = 0.75f,
            aiConfidence = 0.85f,
            abcdeScores = ABCDEScores(
                asymmetryScore = 2.5f,
                borderScore = 3.0f,
                colorScore = 1.5f,
                diameterScore = 2.0f,
                evolutionScore = 1.0f,
                totalScore = 5.0f
            ),
            combinedScore = 5.2f,
            riskLevel = "MODERATE",
            recommendation = "Consultar con dermatólogo",
            imageUrl = "https://example.com/image.jpg",
            createdAt = Timestamp.now(),
            analysisMetadata = mapOf("source" to "test")
        )
        val result = AnalysisDataValidator.validateAnalysisData(validAnalysis)
        assertTrue("Los datos válidos deberían pasar la validación", result.isValid)
        assertTrue("No debería haber errores", result.errors.isEmpty())
    }
    @Test
    fun testInvalidAnalysisDataValidation() {
        val invalidAnalysis = AnalysisData(
            id = UUID.randomUUID().toString(),
            moleId = "",
            analysisResult = "",
            aiProbability = 1.5f,
            aiConfidence = -0.1f,
            abcdeScores = ABCDEScores(
                asymmetryScore = 15.0f,
                borderScore = -1.0f,
                colorScore = 2.0f,
                diameterScore = 3.0f,
                totalScore = 10.0f
            ),
            combinedScore = -1.0f,
            riskLevel = "INVALID",
            recommendation = "",
            createdAt = Timestamp.now()
        )
        val result = AnalysisDataValidator.validateAnalysisData(invalidAnalysis)
        assertFalse("Los datos inválidos no deberían pasar la validación", result.isValid)
        assertFalse("Debería haber errores", result.errors.isEmpty())
        val errorMessage = result.getErrorMessage()
        assertTrue("Debería detectar ID de lunar requerido", errorMessage.contains("ID de lunar requerido"))
        assertTrue("Debería detectar resultado requerido", errorMessage.contains("Resultado de análisis requerido"))
        assertTrue("Debería detectar probabilidad fuera de rango", errorMessage.contains("Probabilidad de IA debe estar entre 0 y 1"))
    }
    @Test
    fun testAnalysisDataSanitization() {
        val maliciousAnalysis = AnalysisData(
            id = UUID.randomUUID().toString(),
            moleId = "test-mole-id",
            analysisResult = "Análisis con <script>alert('hack')</script> contenido malicioso",
            riskLevel = "  bajo  ",
            recommendation = "Recomendación con \"comillas\" y 'apostrofes'",
            imageUrl = "javascript:alert('hack')",
            analysisMetadata = mapOf(
                "key<script>" to "value with <tags>",
                "normalKey" to 12345
            )
        )
        val sanitized = AnalysisDataSanitizer.sanitizeAnalysisData(maliciousAnalysis)
        assertFalse("Debería eliminar scripts", sanitized.analysisResult.contains("<script>"))
        assertEquals("Debería normalizar nivel de riesgo", "LOW", sanitized.riskLevel)
        assertFalse("Debería eliminar comillas", sanitized.recommendation.contains("\""))
        assertEquals("Debería limpiar URL maliciosa", "", sanitized.imageUrl)
        assertFalse("Debería limpiar claves de metadatos", sanitized.analysisMetadata.keys.any { it.contains("<") })
    }
    @Test
    fun testRiskLevelNormalization() {
        val testCases = listOf(
            "LOW" to "LOW",
            "MODERATE" to "MODERATE",
            "HIGH" to "HIGH",
            "  BAJO  " to "LOW",
            "invalid" to "LOW"
        )
        testCases.forEach { (input, expected) ->
            val analysis = AnalysisData(
                moleId = "test",
                analysisResult = "test",
                riskLevel = input,
                recommendation = "test"
            )
            val sanitized = AnalysisDataSanitizer.sanitizeAnalysisData(analysis)
            assertEquals("Nivel de riesgo '$input' debería normalizarse a '$expected'",
                        expected, sanitized.riskLevel)
        }
    }
}
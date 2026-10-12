package com.monsteraltech.skincare.analysis

import com.monsteraltech.skincare.body.mole.model.UserAbcdeInput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class AnalysisRecordFactoryTest {
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

    private val annotations = AnalysisAnnotations("Título", "Detalle", "Torso", null, null)
    private val records = AnalysisRecordFactory(AnalysisReportFormatter())

    @Test fun `model provenance is saved only when present in the inference result`() {
        val model = com.monsteraltech.skincare.analysis.model.ModelIdentity(
            "test-model", "version-2", "a".repeat(64), "rgb-v2", "known")
        val record = records.create(AnalysisOutcome.Complete(combinedResult().copy(modelIdentity = model)), "m-1", annotations)
        model.observationMetadata().forEach { (key, value) -> assertEquals(value, record.analysisMetadata[key]) }
        val historical = records.create(AnalysisOutcome.Complete(combinedResult()), "m-1", annotations)
        assertFalse(historical.analysisMetadata.containsKey("aiModelVersion"))
    }

    @Test fun `the persisted record retains all computed scores and complete status`() {
        val result = combinedResult()
        val date = Date(1234567890000L)
        val record = records.create(AnalysisOutcome.Complete(result), "mole-1", annotations, date, "observation-1")
        assertEquals("mole-1", record.moleId)
        assertEquals("observation-1", record.id)
        assertEquals(date.time, record.createdAt.toEpochMilli())
        assertEquals(result.aiProbability, record.aiProbability)
        assertEquals(result.aiConfidence, record.aiConfidence)
        assertEquals(result.combinedScore, record.combinedScore)
        assertEquals(result.abcdeResult.totalScore, record.abcdeScores.totalScore)
        assertNull(record.abcdeScores.evolutionScore)
        assertEquals(result.combinedRiskLevel.name, record.riskLevel)
        assertEquals(result.recommendation, record.recommendation)
        assertEquals("COMPLETE", record.analysisMetadata["analysisStatus"])
        assertEquals(true, record.analysisMetadata["aiAvailable"])
        assertEquals(true, record.analysisMetadata["abcdeAvailable"])
        assertEquals("", record.analysisMetadata["bodyPartColorCode"])
        assertEquals(result.explanations, record.analysisMetadata["explanations"])
        assertTrue(record.analysisResult.contains(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)))
    }

    @Test fun `user annotations survive in metadata and report without overwriting AI scores`() {
        val input = UserAbcdeInput(asymmetry = 1.5f, border = 3f)
        val record = records.create(AnalysisOutcome.Complete(combinedResult()), "m-1",
            annotations.copy(bodyPartColorCode = "#123456", userInput = input))
        input.toMetadataMap().forEach { (key, value) -> assertEquals(value, record.analysisMetadata[key]) }
        assertEquals(1f, record.abcdeScores.asymmetryScore)
        assertTrue(record.analysisResult.contains("ANÁLISIS ABCDE (USUARIO):"))
        assertTrue(record.analysisResult.contains("COMPARACIÓN:"))
        assertEquals("#123456", record.analysisMetadata["bodyPartColorCode"])
    }

    @Test fun `report preserves optional evolution and locale formatting`() {
        val formatter = AnalysisReportFormatter()
        val result = combinedResult()
        val report = formatter.format(result, null, Date(0), Locale.US)
        assertFalse(report.contains("- Evolución:"))
        assertFalse(report.contains("ANÁLISIS ABCDE (USUARIO)"))
        assertTrue(report.contains("- Probabilidad: 30%"))
        assertTrue(report.contains("- Confianza: 80%"))
        assertTrue(report.endsWith("vigilar\n"))
        val withEvolution = result.copy(abcdeResult = result.abcdeResult.copy(evolutionScore = 2f))
        val spanish = formatter.format(withEvolution, UserAbcdeInput(asymmetry = 1.5f), Date(0), Locale("es", "ES"))
        assertTrue(spanish.contains("- Evolución: 2.0/3"))
        assertTrue(spanish.contains("- Asimetría: 1,5/2"))
    }

    @Test fun `separate captures receive distinct identities`() {
        val first = records.create(AnalysisOutcome.Complete(combinedResult()), "m-1", annotations)
        val second = records.create(AnalysisOutcome.Complete(combinedResult()), "m-1", annotations)
        assertNotEquals(first.id, second.id)
        assertEquals(first.moleId, second.moleId)
    }
    @Test fun `stored anatomical identity survives label and palette changes`() {
        val record = records.create(AnalysisOutcome.Complete(combinedResult()), "m-1",
            annotations.copy(bodyPart = "Localized label", bodyPartColorCode = "#123456",
                bodyRegionId = com.monsteraltech.skincare.body.domain.BodyRegionId.TORSO))
        assertEquals("torso", record.analysisMetadata["bodyRegionId"])
        assertEquals("Localized label", record.analysisMetadata["bodyPart"])
    }
}

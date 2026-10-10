package com.monsteraltech.skincare.analysis

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.FileNotFoundException

class MelanomaAIDetectorTest {

    private lateinit var detector: MelanomaAIDetector

    @Before
    fun setUp() {
        // Modelo ausente explícitamente: no se intenta inicializar el runtime
        // nativo de Android en estos tests JVM de combinación de resultados.
        val context = mockk<Context>()
        val assets = mockk<AssetManager>()
        every { context.assets } returns assets
        every { assets.openFd(any()) } throws FileNotFoundException("Test model unavailable")
        detector = MelanomaAIDetector(context)
    }

    private fun abcdeResult(
        totalScore: Float = 0f,
        asymmetry: Float = 0f,
        border: Float = 0f,
        color: Float = 1f,
        diameter: Float = 0f,
        evolution: Float? = null,
        hasBlueWhite: Boolean = false,
        hasRedBlue: Boolean = false,
        borderIrregularity: Float = 0f
    ) = ABCDEAnalyzerOpenCV.ABCDEResult(
        asymmetryScore = asymmetry,
        borderScore = border,
        colorScore = color,
        diameterScore = diameter,
        evolutionScore = evolution,
        totalScore = totalScore,
        riskLevel = ABCDEAnalyzerOpenCV.RiskLevel.LOW,
        details = ABCDEAnalyzerOpenCV.ABCDEDetails(
            asymmetryDetails = ABCDEAnalyzerOpenCV.AsymmetryDetails(0f, 0f, "test"),
            borderDetails = ABCDEAnalyzerOpenCV.BorderDetails(borderIrregularity, 0, "test"),
            colorDetails = ABCDEAnalyzerOpenCV.ColorDetails(
                emptyList(), 1, hasBlueWhite, hasRedBlue, "test"
            ),
            diameterDetails = ABCDEAnalyzerOpenCV.DiameterDetails(0f, 0, "test"),
            evolutionDetails = null
        )
    )

    @Test
    fun `runInference lanza AIModelError cuando el modelo no esta disponible`() {
        // Antes este caso devolvía silenciosamente probabilidad 0.1 y riesgo MUY BAJO:
        // un fallo técnico se presentaba como resultado tranquilizador.
        assertThrows(AnalysisError.AIModelError::class.java) {
            detector.runInference(mockk(relaxed = true))
        }
    }

    @Test
    fun `probabilidad baja y ABCDE benigno dan riesgo muy bajo`() {
        val result = detector.combineResults(
            probability = 0.1f,
            riskLevel = MelanomaAIDetector.RiskLevel.VERY_LOW,
            confidence = 0.9f,
            abcdeResult = abcdeResult(totalScore = 1f)
        )
        assertEquals(MelanomaAIDetector.RiskLevel.VERY_LOW, result.combinedRiskLevel)
        assertEquals(MelanomaAIDetector.UrgencyLevel.ROUTINE, result.urgencyLevel)
    }

    @Test
    fun `probabilidad alta y ABCDE maligno dan riesgo alto o muy alto`() {
        val result = detector.combineResults(
            probability = 0.9f,
            riskLevel = MelanomaAIDetector.RiskLevel.VERY_HIGH,
            confidence = 0.95f,
            abcdeResult = abcdeResult(totalScore = 10f, border = 6f, diameter = 4f)
        )
        assertTrue(
            "Esperaba HIGH o VERY_HIGH, fue ${result.combinedRiskLevel}",
            result.combinedRiskLevel == MelanomaAIDetector.RiskLevel.HIGH ||
                    result.combinedRiskLevel == MelanomaAIDetector.RiskLevel.VERY_HIGH
        )
    }

    @Test
    fun `el velo azul-blanquecino amplifica la puntuacion combinada`() {
        val base = detector.combineResults(
            probability = 0.4f,
            riskLevel = MelanomaAIDetector.RiskLevel.MEDIUM,
            confidence = 0.7f,
            abcdeResult = abcdeResult(totalScore = 4f)
        )
        val withVeil = detector.combineResults(
            probability = 0.4f,
            riskLevel = MelanomaAIDetector.RiskLevel.MEDIUM,
            confidence = 0.7f,
            abcdeResult = abcdeResult(totalScore = 4f, hasBlueWhite = true)
        )
        assertTrue(withVeil.combinedScore > base.combinedScore)
    }

    @Test
    fun `varios factores criticos elevan la urgencia a URGENT`() {
        val result = detector.combineResults(
            probability = 0.5f,
            riskLevel = MelanomaAIDetector.RiskLevel.MEDIUM,
            confidence = 0.7f,
            abcdeResult = abcdeResult(
                totalScore = 6f,
                hasBlueWhite = true,
                hasRedBlue = true,
                diameter = 4f
            )
        )
        assertEquals(MelanomaAIDetector.UrgencyLevel.URGENT, result.urgencyLevel)
    }

    @Test
    fun `la puntuacion combinada esta acotada a 1`() {
        val result = detector.combineResults(
            probability = 1.0f,
            riskLevel = MelanomaAIDetector.RiskLevel.VERY_HIGH,
            confidence = 0.95f,
            abcdeResult = abcdeResult(
                totalScore = 12f,
                hasBlueWhite = true,
                diameter = 5f,
                evolution = 3f,
                borderIrregularity = 0.5f
            )
        )
        assertTrue(result.combinedScore <= 1f)
        assertEquals(MelanomaAIDetector.RiskLevel.VERY_HIGH, result.combinedRiskLevel)
        assertEquals(MelanomaAIDetector.UrgencyLevel.URGENT, result.urgencyLevel)
    }

    @Test
    fun `una evolucion significativa activa el seguimiento`() {
        val result = detector.combineResults(
            probability = 0.05f,
            riskLevel = MelanomaAIDetector.RiskLevel.VERY_LOW,
            confidence = 0.9f,
            abcdeResult = abcdeResult(totalScore = 1f, evolution = 0.5f)
        )
        assertTrue(result.shouldMonitor)
    }
}

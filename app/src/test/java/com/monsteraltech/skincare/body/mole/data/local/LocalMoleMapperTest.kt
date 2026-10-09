package com.monsteraltech.skincare.body.mole.data.local

import com.google.firebase.Timestamp
import com.monsteraltech.skincare.body.mole.model.ABCDEScores
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.model.MoleData
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocalMoleMapperTest {
    private val mapper = LocalMoleMapper()

    @Test
    fun `mole keeps health fields after local round trip`() {
        val timestamp = Timestamp(Date(1_700_000_000_000L))
        val mole = MoleData(
            id = "mole-1",
            userId = "user-1",
            title = "Seguimiento",
            description = "Cambio reciente",
            bodyPart = "Torso",
            bodyPartColorCode = "#FFFFC90E",
            imageUrl = "mole_images/user-1/image.jpg",
            aiResult = "Resultado local",
            createdAt = timestamp,
            updatedAt = timestamp,
            analysisCount = 2,
            lastAnalysisDate = timestamp,
            firstAnalysisDate = timestamp,
            aiProbability = 0.73,
            aiConfidence = 0.82,
            riskLevel = "MEDIUM",
            combinedScore = 0.61,
            abcdeAsymmetry = 1.2,
            abcdeBorder = 2.3,
            abcdeColor = 3.4,
            abcdeDiameter = 1.1,
            abcdeTotalScore = 8.0,
            recommendation = "Consultar cambios",
            analysisMetadata = mapOf("userEvolution" to 2.0, "source" to "on-device")
        )

        val restored = mapper.toModel(mapper.toEntity(mole))

        assertEquals(mole.id, restored.id)
        assertEquals(mole.imageUrl, restored.imageUrl)
        assertEquals(mole.aiProbability, restored.aiProbability)
        assertEquals(mole.abcdeTotalScore, restored.abcdeTotalScore)
        assertEquals(mole.createdAt.toDate(), restored.createdAt.toDate())
        assertEquals("on-device", restored.analysisMetadata["source"])
        assertNotNull(restored.analysisMetadata["userEvolution"])
    }

    @Test
    fun `analysis keeps scores and metadata after local round trip`() {
        val analysis = AnalysisData(
            id = "analysis-1",
            moleId = "mole-1",
            description = "Nueva observación",
            analysisResult = "Resultado",
            aiProbability = 0.45f,
            aiConfidence = 0.76f,
            abcdeScores = ABCDEScores(
                asymmetryScore = 1f,
                borderScore = 2f,
                colorScore = 3f,
                diameterScore = 1f,
                evolutionScore = 2f,
                totalScore = 9f
            ),
            combinedScore = 0.54f,
            riskLevel = "MEDIUM",
            recommendation = "Seguimiento",
            imageUrl = "mole_images/user-1/analysis.jpg",
            analysisMetadata = mapOf("validCapture" to true)
        )

        val restored = mapper.toModel(mapper.toEntity(analysis, "user-1"))

        assertEquals(analysis.id, restored.id)
        assertEquals(analysis.abcdeScores, restored.abcdeScores)
        assertEquals(analysis.combinedScore, restored.combinedScore)
        assertEquals(true, restored.analysisMetadata["validCapture"])
    }
}

package com.monsteraltech.skincare.analysis

import com.monsteraltech.skincare.body.mole.model.ABCDEScores
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import java.util.Date
import java.util.UUID
import javax.inject.Inject

/** Creates a portable observation record from a complete analysis and annotations. */
class AnalysisRecordFactory @Inject constructor(private val reports: AnalysisReportFormatter) {
    fun create(
        complete: AnalysisOutcome.Complete,
        moleId: String,
        annotations: AnalysisAnnotations,
        createdAt: Date = Date(),
        observationId: String = UUID.randomUUID().toString()
    ): AnalysisData {
        val result = complete.result
        val (title, description, bodyPart, bodyPartColorCode, userInput) = annotations
        val abcdeScores = ABCDEScores(
            asymmetryScore = result.abcdeResult.asymmetryScore,
            borderScore = result.abcdeResult.borderScore,
            colorScore = result.abcdeResult.colorScore,
            diameterScore = result.abcdeResult.diameterScore,
            evolutionScore = result.abcdeResult.evolutionScore,
            totalScore = result.abcdeResult.totalScore
        )
        val metadata = mapOf(
            "analysisStatus" to "COMPLETE",
            "aiAvailable" to true,
            "abcdeAvailable" to true,
            "title" to title,
            "description" to description,
            "bodyPart" to bodyPart,
            "bodyPartColorCode" to (bodyPartColorCode ?: ""),
            "urgencyLevel" to result.urgencyLevel.name,
            "explanations" to result.explanations
        ) + (userInput?.toMetadataMap() ?: emptyMap())

        return AnalysisData(
            moleId = moleId,
            description = description,
            analysisResult = reports.format(result, userInput, createdAt),
            aiProbability = result.aiProbability,
            aiConfidence = result.aiConfidence,
            abcdeScores = abcdeScores,
            combinedScore = result.combinedScore,
            riskLevel = result.combinedRiskLevel.name,
            recommendation = result.recommendation,
            createdAt = createdAt.toInstant(),
            id = observationId,
            analysisMetadata = metadata
        )
    }

}

package com.monsteraltech.skincare.body.mole.data.local

import java.time.Instant
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.monsteraltech.skincare.body.mole.model.ABCDEScores
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.model.MoleData
import java.util.Date

internal class LocalMoleMapper(
    private val gson: Gson = Gson()
) {
    private val metadataType = object : TypeToken<Map<String, Any>>() {}.type

    fun toEntity(mole: MoleData): MoleEntity = MoleEntity(
        id = mole.id,
        userId = mole.userId,
        title = mole.title,
        description = mole.description,
        bodyPart = mole.bodyPart,
        bodyPartColorCode = mole.bodyPartColorCode,
        imagePath = mole.imageUrl,
        analysisResult = mole.aiResult,
        createdAtEpochMillis = mole.createdAt.toEpochMilli(),
        updatedAtEpochMillis = mole.updatedAt.toEpochMilli(),
        analysisCount = mole.analysisCount,
        lastAnalysisAtEpochMillis = mole.lastAnalysisDate?.toEpochMilli(),
        firstAnalysisAtEpochMillis = mole.firstAnalysisDate?.toEpochMilli(),
        aiProbability = mole.aiProbability,
        aiConfidence = mole.aiConfidence,
        riskLevel = mole.riskLevel,
        combinedScore = mole.combinedScore,
        abcdeAsymmetry = mole.abcdeAsymmetry,
        abcdeBorder = mole.abcdeBorder,
        abcdeColor = mole.abcdeColor,
        abcdeDiameter = mole.abcdeDiameter,
        abcdeTotalScore = mole.abcdeTotalScore,
        recommendation = mole.recommendation,
        urgency = mole.urgency,
        analysisMetadataJson = gson.toJson(mole.analysisMetadata),
        currentAnalysisId = mole.currentAnalysisId
    )

    fun toModel(entity: MoleEntity): MoleData = MoleData(
        id = entity.id,
        userId = entity.userId,
        title = entity.title,
        description = entity.description,
        bodyPart = entity.bodyPart,
        bodyPartColorCode = entity.bodyPartColorCode,
        imageUrl = entity.imagePath,
        aiResult = entity.analysisResult,
        createdAt = timestamp(entity.createdAtEpochMillis),
        updatedAt = timestamp(entity.updatedAtEpochMillis),
        analysisCount = entity.analysisCount,
        lastAnalysisDate = entity.lastAnalysisAtEpochMillis?.let(::timestamp),
        firstAnalysisDate = entity.firstAnalysisAtEpochMillis?.let(::timestamp),
        aiProbability = entity.aiProbability,
        aiConfidence = entity.aiConfidence,
        riskLevel = entity.riskLevel,
        combinedScore = entity.combinedScore,
        abcdeAsymmetry = entity.abcdeAsymmetry,
        abcdeBorder = entity.abcdeBorder,
        abcdeColor = entity.abcdeColor,
        abcdeDiameter = entity.abcdeDiameter,
        abcdeTotalScore = entity.abcdeTotalScore,
        recommendation = entity.recommendation,
        urgency = entity.urgency,
        analysisMetadata = metadataFromJson(entity.analysisMetadataJson),
        currentAnalysisId = entity.currentAnalysisId
    )

    fun toEntity(analysis: AnalysisData, userId: String): AnalysisEntity = AnalysisEntity(
        id = analysis.id,
        moleId = analysis.moleId,
        userId = userId,
        description = analysis.description,
        analysisResult = analysis.analysisResult,
        aiProbability = analysis.aiProbability,
        aiConfidence = analysis.aiConfidence,
        asymmetryScore = analysis.abcdeScores.asymmetryScore,
        borderScore = analysis.abcdeScores.borderScore,
        colorScore = analysis.abcdeScores.colorScore,
        diameterScore = analysis.abcdeScores.diameterScore,
        evolutionScore = analysis.abcdeScores.evolutionScore,
        totalScore = analysis.abcdeScores.totalScore,
        combinedScore = analysis.combinedScore,
        riskLevel = analysis.riskLevel,
        recommendation = analysis.recommendation,
        imagePath = analysis.imageUrl,
        createdAtEpochMillis = analysis.createdAt.toEpochMilli(),
        analysisMetadataJson = gson.toJson(analysis.analysisMetadata)
    )

    fun toModel(entity: AnalysisEntity): AnalysisData = AnalysisData(
        id = entity.id,
        moleId = entity.moleId,
        description = entity.description,
        analysisResult = entity.analysisResult,
        aiProbability = entity.aiProbability,
        aiConfidence = entity.aiConfidence,
        abcdeScores = ABCDEScores(
            asymmetryScore = entity.asymmetryScore,
            borderScore = entity.borderScore,
            colorScore = entity.colorScore,
            diameterScore = entity.diameterScore,
            evolutionScore = entity.evolutionScore,
            totalScore = entity.totalScore
        ),
        combinedScore = entity.combinedScore,
        riskLevel = entity.riskLevel,
        recommendation = entity.recommendation,
        imageUrl = entity.imagePath,
        createdAt = timestamp(entity.createdAtEpochMillis),
        analysisMetadata = metadataFromJson(entity.analysisMetadataJson)
    )

    private fun timestamp(epochMillis: Long): Instant = Instant.ofEpochMilli(epochMillis)

    private fun metadataFromJson(json: String): Map<String, Any> =
        runCatching { gson.fromJson<Map<String, Any>>(json, metadataType) }
            .getOrNull()
            .orEmpty()
}

package com.monsteraltech.skincare.body.mole.model
import java.time.Instant
import java.io.Serializable
import java.util.UUID
data class AnalysisData(
    val id: String = UUID.randomUUID().toString(),
    val moleId: String = "",
    val description: String = "",
    val analysisResult: String = "",
    val aiProbability: Float = 0f,
    val aiConfidence: Float = 0f,
    val abcdeScores: ABCDEScores = ABCDEScores(),
    val combinedScore: Float = 0f,
    val riskLevel: String = "",
    val recommendation: String = "",
    val imageUrl: String = "",
    val createdAt: Instant = Instant.ofEpochMilli(System.currentTimeMillis()),
    val analysisMetadata: Map<String, Any> = emptyMap()
) : Serializable

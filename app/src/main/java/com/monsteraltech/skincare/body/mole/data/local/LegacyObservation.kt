package com.monsteraltech.skincare.body.mole.data.local

import java.security.MessageDigest

/** Stable identity for the current capture in formats that predate stored observation IDs. */
internal object LegacyObservation {
    fun id(moleId: String, count: Int, capturedAt: Long, imagePath: String): String {
        val key = listOf(moleId, count.toString(), capturedAt.toString(), imagePath)
            .joinToString("") { "${it.length}:$it" }
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return "legacy_" + digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    fun id(mole: MoleEntity) = id(mole.id, mole.analysisCount,
        mole.lastAnalysisAtEpochMillis ?: mole.updatedAtEpochMillis, mole.imagePath)

    fun snapshot(mole: MoleEntity) = AnalysisEntity(
        id = id(mole), moleId = mole.id, userId = mole.userId, description = mole.description,
        analysisResult = mole.analysisResult, aiProbability = mole.aiProbability?.toFloat() ?: 0f,
        aiConfidence = mole.aiConfidence?.toFloat() ?: 0f, asymmetryScore = mole.abcdeAsymmetry?.toFloat() ?: 0f,
        borderScore = mole.abcdeBorder?.toFloat() ?: 0f, colorScore = mole.abcdeColor?.toFloat() ?: 0f,
        diameterScore = mole.abcdeDiameter?.toFloat() ?: 0f, evolutionScore = null,
        totalScore = mole.abcdeTotalScore?.toFloat() ?: 0f, combinedScore = mole.combinedScore?.toFloat() ?: 0f,
        riskLevel = mole.riskLevel, recommendation = mole.recommendation, imagePath = mole.imagePath,
        createdAtEpochMillis = mole.lastAnalysisAtEpochMillis ?: mole.updatedAtEpochMillis,
        analysisMetadataJson = mole.analysisMetadataJson
    )
}

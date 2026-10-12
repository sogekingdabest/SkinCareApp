package com.monsteraltech.skincare.body.mole.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "moles",
    indices = [
        Index(value = ["userId", "updatedAtEpochMillis"]),
        Index(value = ["userId", "bodyRegionId"])
    ]
)
data class MoleEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val bodyPart: String,
    val bodyPartColorCode: String,
    val imagePath: String,
    val analysisResult: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val analysisCount: Int,
    val lastAnalysisAtEpochMillis: Long?,
    val firstAnalysisAtEpochMillis: Long?,
    val aiProbability: Double?,
    val aiConfidence: Double?,
    val riskLevel: String,
    val combinedScore: Double?,
    val abcdeAsymmetry: Double?,
    val abcdeBorder: Double?,
    val abcdeColor: Double?,
    val abcdeDiameter: Double?,
    val abcdeTotalScore: Double?,
    val recommendation: String,
    val urgency: String,
    val analysisMetadataJson: String,
    val currentAnalysisId: String? = null,
    @ColumnInfo(defaultValue = "'unknown'") val bodyRegionId: String = "unknown"
)

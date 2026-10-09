package com.monsteraltech.skincare.body.mole.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mole_analyses",
    foreignKeys = [
        ForeignKey(
            entity = MoleEntity::class,
            parentColumns = ["id"],
            childColumns = ["moleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["moleId", "createdAtEpochMillis"]),
        Index(value = ["userId"])
    ]
)
data class AnalysisEntity(
    @PrimaryKey val id: String,
    val moleId: String,
    val userId: String,
    val description: String,
    val analysisResult: String,
    val aiProbability: Float,
    val aiConfidence: Float,
    val asymmetryScore: Float,
    val borderScore: Float,
    val colorScore: Float,
    val diameterScore: Float,
    val evolutionScore: Float?,
    val totalScore: Float,
    val combinedScore: Float,
    val riskLevel: String,
    val recommendation: String,
    val imagePath: String,
    val createdAtEpochMillis: Long,
    val analysisMetadataJson: String
)

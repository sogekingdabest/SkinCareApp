package com.monsteraltech.skincare.body.mole.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MoleDao {

    @Upsert
    suspend fun upsertMole(mole: MoleEntity)

    @Query("SELECT * FROM moles WHERE id = :moleId AND userId = :userId LIMIT 1")
    suspend fun getMole(userId: String, moleId: String): MoleEntity?

    @Query("SELECT userId FROM moles WHERE id = :moleId LIMIT 1")
    suspend fun getMoleOwner(moleId: String): String?

    @Query(
        """
        SELECT * FROM moles
        WHERE userId = :userId
        ORDER BY updatedAtEpochMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getMoles(userId: String, limit: Int = 50): List<MoleEntity>

    @Query("SELECT * FROM moles WHERE userId = :userId ORDER BY updatedAtEpochMillis DESC")
    suspend fun getAllMoles(userId: String): List<MoleEntity>

    @Query(
        """
        SELECT * FROM moles
        WHERE userId = :userId AND bodyPartColorCode = :bodyPartColorCode
        ORDER BY updatedAtEpochMillis DESC
        """
    )
    suspend fun getMolesForBodyPart(
        userId: String,
        bodyPartColorCode: String
    ): List<MoleEntity>

    @Upsert
    suspend fun upsertAnalysis(analysis: AnalysisEntity)

    @Upsert
    suspend fun upsertMoles(moles: List<MoleEntity>)

    @Upsert
    suspend fun upsertAnalyses(analyses: List<AnalysisEntity>)

    @Query(
        """
        SELECT * FROM mole_analyses
        WHERE moleId = :moleId AND userId = :userId
        ORDER BY createdAtEpochMillis DESC
        """
    )
    suspend fun getAnalysisHistory(userId: String, moleId: String): List<AnalysisEntity>

    @Query(
        "SELECT * FROM mole_analyses WHERE userId = :userId ORDER BY createdAtEpochMillis DESC"
    )
    suspend fun getAllAnalyses(userId: String): List<AnalysisEntity>

    @Query("SELECT userId FROM mole_analyses WHERE id = :analysisId LIMIT 1")
    suspend fun getAnalysisOwner(analysisId: String): String?
}

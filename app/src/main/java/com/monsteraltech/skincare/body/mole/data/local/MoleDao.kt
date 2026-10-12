package com.monsteraltech.skincare.body.mole.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import com.monsteraltech.skincare.body.mole.domain.model.MoleListEntry

@Dao
interface MoleDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMole(mole: MoleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAnalysis(analysis: AnalysisEntity)

    @Query("SELECT * FROM mole_analyses WHERE id = :analysisId AND moleId = :moleId AND userId = :userId LIMIT 1")
    suspend fun getAnalysis(userId: String, moleId: String, analysisId: String): AnalysisEntity?

    @Query("SELECT COUNT(*) FROM mole_analyses WHERE moleId = :moleId AND userId = :userId")
    suspend fun countAnalyses(userId: String, moleId: String): Int

    @Query("UPDATE mole_analyses SET analysisMetadataJson = :metadataJson WHERE id = :analysisId AND moleId = :moleId AND userId = :userId")
    suspend fun updateAnalysisMetadata(userId: String, moleId: String, analysisId: String, metadataJson: String)

    @Query("UPDATE mole_analyses SET imagePath = :imagePath WHERE id = :analysisId AND moleId = :moleId AND userId = :userId")
    suspend fun updateAnalysisImage(userId: String, moleId: String, analysisId: String, imagePath: String)

    @Query(ObservationMigration.NEEDS_CURRENT)
    suspend fun getLegacyCurrentSummaries(): List<MoleEntity>

    @Query(ObservationMigration.INSERT_CURRENT)
    suspend fun insertLegacyCurrentAnalyses()

    @Query(ObservationMigration.UPDATE_COUNTS)
    suspend fun updateAnalysisCounts()

    @Transaction
    suspend fun materializeLegacyCurrentAnalyses() {
        getLegacyCurrentSummaries().forEach { upsertMole(it.copy(currentAnalysisId = LegacyObservation.id(it))) }
        insertLegacyCurrentAnalyses()
        updateAnalysisCounts()
    }

    @Upsert
    suspend fun upsertMole(mole: MoleEntity)

    @Query("SELECT * FROM moles WHERE id = :moleId AND userId = :userId LIMIT 1")
    suspend fun getMole(userId: String, moleId: String): MoleEntity?

    @Query("SELECT userId FROM moles WHERE id = :moleId LIMIT 1")
    suspend fun getMoleOwner(moleId: String): String?

    @Query("SELECT * FROM moles WHERE userId = :userId ORDER BY updatedAtEpochMillis DESC, id ASC")
    suspend fun getAllMoles(userId: String): List<MoleEntity>

    @Query("""SELECT id, title, description, bodyPart, bodyPartColorCode, bodyRegionId,
        imagePath AS imageReference, analysisResult, analysisCount, createdAtEpochMillis,
        lastAnalysisAtEpochMillis FROM moles WHERE userId = :userId
        AND (:regionId IS NULL OR bodyRegionId = :regionId)
        ORDER BY updatedAtEpochMillis DESC, id ASC""")
    fun observeMoleList(userId: String, regionId: String?): Flow<List<MoleListEntry>>

    @Query("""SELECT COUNT(*) FROM mole_analyses a JOIN moles m ON m.id = a.moleId AND m.userId = a.userId
        WHERE m.id = :moleId AND m.userId = :userId""")
    fun observeHistoryChanges(userId: String, moleId: String): Flow<Int>

    @Query("""SELECT a.* FROM mole_analyses a JOIN moles m ON m.id = a.moleId AND m.userId = a.userId
        WHERE a.moleId = :moleId AND a.userId = :userId
        ORDER BY (a.id = m.currentAnalysisId) DESC, a.createdAtEpochMillis DESC, a.id ASC LIMIT :limit""")
    suspend fun getAnalysisHistoryPage(userId: String, moleId: String, limit: Int): List<AnalysisEntity>

    @Query(
        """
        SELECT * FROM moles
        WHERE userId = :userId AND bodyRegionId = :regionId
        ORDER BY updatedAtEpochMillis DESC
        """
    )
    suspend fun getMolesForBodyRegion(
        userId: String,
        regionId: String
    ): List<MoleEntity>

    @Query(
        """
        SELECT a.* FROM mole_analyses a
        JOIN moles m ON m.id = a.moleId AND m.userId = a.userId
        WHERE a.moleId = :moleId AND a.userId = :userId
        ORDER BY (a.id = m.currentAnalysisId) DESC, a.createdAtEpochMillis DESC, a.id ASC
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

package com.monsteraltech.skincare.body.mole.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Freezes the legacy current summary once; previously archived IDs remain untouched. */
object ObservationMigration {
    const val NEEDS_CURRENT = """
        SELECT * FROM moles
        WHERE currentAnalysisId IS NULL AND (analysisCount > 0 OR analysisResult != '')
    """
    const val INSERT_CURRENT = """
        INSERT INTO mole_analyses (
            id, moleId, userId, description, analysisResult, aiProbability, aiConfidence,
            asymmetryScore, borderScore, colorScore, diameterScore, evolutionScore, totalScore,
            combinedScore, riskLevel, recommendation, imagePath, createdAtEpochMillis, analysisMetadataJson
        )
        SELECT currentAnalysisId, id, userId, description, analysisResult,
            COALESCE(aiProbability, 0), COALESCE(aiConfidence, 0),
            COALESCE(abcdeAsymmetry, 0), COALESCE(abcdeBorder, 0), COALESCE(abcdeColor, 0),
            COALESCE(abcdeDiameter, 0), NULL, COALESCE(abcdeTotalScore, 0),
            COALESCE(combinedScore, 0), riskLevel, recommendation, imagePath,
            COALESCE(lastAnalysisAtEpochMillis, updatedAtEpochMillis), analysisMetadataJson
        FROM moles
        WHERE currentAnalysisId IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM mole_analyses WHERE mole_analyses.id = moles.currentAnalysisId)
    """
    const val UPDATE_COUNTS = """
        UPDATE moles SET analysisCount = (
            SELECT COUNT(*) FROM mole_analyses WHERE moleId = moles.id AND userId = moles.userId
        ) WHERE currentAnalysisId IS NOT NULL
    """

    val FROM_1_TO_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE moles ADD COLUMN currentAnalysisId TEXT")
            val assignments = mutableListOf<Pair<String, String>>()
            db.query(NEEDS_CURRENT).use { cursor ->
                while (cursor.moveToNext()) {
                    val moleId = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    val count = cursor.getInt(cursor.getColumnIndexOrThrow("analysisCount"))
                    val lastDate = cursor.getColumnIndexOrThrow("lastAnalysisAtEpochMillis")
                    val capturedAt = if (cursor.isNull(lastDate)) cursor.getLong(cursor.getColumnIndexOrThrow("updatedAtEpochMillis"))
                        else cursor.getLong(lastDate)
                    val imagePath = cursor.getString(cursor.getColumnIndexOrThrow("imagePath"))
                    val id = LegacyObservation.id(moleId, count, capturedAt, imagePath)
                    assignments += id to moleId
                }
            }
            assignments.forEach { (id, moleId) ->
                db.execSQL("UPDATE moles SET currentAnalysisId = ? WHERE id = ?", arrayOf(id, moleId))
            }
            db.execSQL(INSERT_CURRENT)
            db.execSQL(UPDATE_COUNTS)
        }
    }
}

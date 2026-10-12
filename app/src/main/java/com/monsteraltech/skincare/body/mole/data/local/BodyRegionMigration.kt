package com.monsteraltech.skincare.body.mole.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.monsteraltech.skincare.body.data.LegacyBodyRegionMapping

object BodyRegionMigration {
    val FROM_2_TO_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE moles ADD COLUMN bodyRegionId TEXT NOT NULL DEFAULT 'unknown'")
            val assignments = mutableListOf<Pair<String, String>>()
            db.query("SELECT id, bodyPart, bodyPartColorCode FROM moles").use { cursor ->
                while (cursor.moveToNext()) assignments += cursor.getString(0) to
                    LegacyBodyRegionMapping.resolve(null, cursor.getString(2), cursor.getString(1)).value
            }
            assignments.forEach { (id, region) ->
                db.execSQL("UPDATE moles SET bodyRegionId = ? WHERE id = ?", arrayOf(region, id))
            }
            db.execSQL("DROP INDEX IF EXISTS index_moles_userId_bodyPartColorCode")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_moles_userId_bodyRegionId ON moles(userId, bodyRegionId)")
        }
    }
}

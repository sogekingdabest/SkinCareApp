package com.monsteraltech.skincare.body.mole.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MoleEntity::class, AnalysisEntity::class],
    version = 1,
    exportSchema = true
)
abstract class SkinCareDatabase : RoomDatabase() {
    abstract fun moleDao(): MoleDao
}

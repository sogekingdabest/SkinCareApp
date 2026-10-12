package com.monsteraltech.skincare.body.mole.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [MoleEntity::class, AnalysisEntity::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(BodyRegionConverters::class)
abstract class SkinCareDatabase : RoomDatabase() {
    abstract fun moleDao(): MoleDao
}

package com.monsteraltech.skincare.body.mole.data.local

import androidx.room.TypeConverter
import com.monsteraltech.skincare.body.domain.BodyRegionId

class BodyRegionConverters {
    @TypeConverter fun read(value: String): BodyRegionId = BodyRegionId.fromValue(value) ?: BodyRegionId.UNKNOWN
    @TypeConverter fun write(value: BodyRegionId): String = value.value
}

package com.monsteraltech.skincare.body.presentation

import android.content.Intent
import com.monsteraltech.skincare.body.data.LegacyBodyRegionMapping
import com.monsteraltech.skincare.body.domain.BodyRegionId

object BodyRegionContract {
    const val EXTRA_REGION_ID = "BODY_REGION_ID"
    fun read(intent: Intent, legacyColorKey: String = "BODY_PART_COLOR"): BodyRegionId? =
        if (intent.hasExtra(EXTRA_REGION_ID)) BodyRegionId.fromValue(intent.getStringExtra(EXTRA_REGION_ID)) ?: BodyRegionId.UNKNOWN
        else LegacyBodyRegionMapping.fromColor(intent.getStringExtra(legacyColorKey))
}

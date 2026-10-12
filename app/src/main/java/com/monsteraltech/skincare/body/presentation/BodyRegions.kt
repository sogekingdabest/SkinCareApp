package com.monsteraltech.skincare.body.presentation

import android.content.Context
import androidx.annotation.StringRes
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.body.domain.BodyRegionId
import java.util.Locale

/** Names and mask pixels belong to presentation; neither is a persisted identifier. */
object BodyRegions {
    data class Item(val id: BodyRegionId, @StringRes val nameRes: Int, val maskColor: Int) {
        val legacyColor: String get() = String.format(Locale.ROOT, "#%08X", maskColor)
    }
    val selectable = listOf(
        Item(BodyRegionId.HEAD, R.string.body_region_head, 0xFF000000.toInt()),
        Item(BodyRegionId.RIGHT_ARM, R.string.body_region_right_arm, 0xFFED1C24.toInt()),
        Item(BodyRegionId.TORSO, R.string.body_region_torso, 0xFFFFC90E.toInt()),
        Item(BodyRegionId.LEFT_ARM, R.string.body_region_left_arm, 0xFF22B14C.toInt()),
        Item(BodyRegionId.RIGHT_LEG, R.string.body_region_right_leg, 0xFF3F48CC.toInt()),
        Item(BodyRegionId.LEFT_LEG, R.string.body_region_left_leg, 0xFFED00FF.toInt())
    )
    fun fromMaskColor(color: Int): BodyRegionId? = selectable.firstOrNull { it.maskColor == color }?.id
    fun item(id: BodyRegionId?): Item? = selectable.firstOrNull { it.id == id }
    fun label(context: Context, id: BodyRegionId?, legacyLabel: String = ""): String = item(id)?.let {
        context.getString(it.nameRes)
    } ?: legacyLabel.ifBlank { context.getString(R.string.body_region_unknown) }
}

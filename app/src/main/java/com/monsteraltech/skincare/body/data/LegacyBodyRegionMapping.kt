package com.monsteraltech.skincare.body.data

import com.monsteraltech.skincare.body.domain.BodyRegionId
import java.util.Locale

/** Frozen compatibility mapping for records written before stable region IDs. */
object LegacyBodyRegionMapping {
    private val colors = mapOf(
        "#FF000000" to BodyRegionId.HEAD, "#FFED1C24" to BodyRegionId.RIGHT_ARM,
        "#FFFFC90E" to BodyRegionId.TORSO, "#FF22B14C" to BodyRegionId.LEFT_ARM,
        "#FF3F48CC" to BodyRegionId.RIGHT_LEG, "#FFED00FF" to BodyRegionId.LEFT_LEG
    )
    private val names = mapOf(
        "cabeza" to BodyRegionId.HEAD, "brazo derecho" to BodyRegionId.RIGHT_ARM,
        "torso" to BodyRegionId.TORSO, "brazo izquierdo" to BodyRegionId.LEFT_ARM,
        "pierna derecha" to BodyRegionId.RIGHT_LEG, "pierna izquierda" to BodyRegionId.LEFT_LEG
    )
    fun fromColor(color: String?): BodyRegionId? {
        var value = color?.trim()?.uppercase(Locale.ROOT) ?: return null
        if (value.length == 7 && value.startsWith('#')) value = "#FF" + value.drop(1)
        return colors[value]
    }
    fun resolve(id: BodyRegionId?, color: String?, label: String?): BodyRegionId = id
        ?: fromColor(color)
        ?: names[label?.trim()?.lowercase(Locale.ROOT)?.replace(Regex("\\s+"), " ")]
        ?: BodyRegionId.UNKNOWN
}

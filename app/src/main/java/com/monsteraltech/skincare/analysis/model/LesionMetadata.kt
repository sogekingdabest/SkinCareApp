package com.monsteraltech.skincare.analysis.model

import androidx.annotation.Keep
import com.monsteraltech.skincare.body.domain.BodyRegionId

@Keep
enum class AnalysisSex(val trainingCode: Int) { MALE(0), FEMALE(1) }

@Keep
enum class AnatomicalSite(val trainingCode: Int) {
    HEAD_NECK(0), UPPER_EXTREMITY(1), LOWER_EXTREMITY(2), TORSO(3), PALMS_SOLES(4), ORAL_GENITAL(5);

    companion object {
        /** A coarse body region suggests a site; hands, soles and special sites require confirmation. */
        fun suggestion(region: BodyRegionId?): AnatomicalSite? = when (region) {
            BodyRegionId.HEAD -> HEAD_NECK
            BodyRegionId.LEFT_ARM, BodyRegionId.RIGHT_ARM -> UPPER_EXTREMITY
            BodyRegionId.LEFT_LEG, BodyRegionId.RIGHT_LEG -> LOWER_EXTREMITY
            BodyRegionId.TORSO -> TORSO
            BodyRegionId.UNKNOWN, null -> null
        }
    }
}

/** Values deliberately supplied for a capture; null never means a fabricated demographic value. */
@Keep
data class LesionMetadata(
    val ageYears: Int? = null,
    val sex: AnalysisSex? = null,
    val site: AnatomicalSite? = null
) {
    init { require(ageYears == null || ageYears in 0..120) }

    fun encode(): FloatArray = floatArrayOf(
        (ageYears ?: -1) / 100f,
        (sex?.trainingCode ?: -1).toFloat(),
        (site?.trainingCode ?: -1) / 7f
    )

    fun recordMetadata(): Map<String, Any> = buildMap {
        put("recordedContextEncodingVersion", ENCODING_VERSION)
        put("recordedContextRole", "annotation")
        ageYears?.let { put("recordedAgeYears", it) }
        sex?.let { put("recordedSex", it.name) }
        site?.let { put("recordedAnatomicalSite", it.name) }
    }

    companion object {
        const val ENCODING_VERSION = "cdeotte-isic2020-metadata-v1"
    }
}

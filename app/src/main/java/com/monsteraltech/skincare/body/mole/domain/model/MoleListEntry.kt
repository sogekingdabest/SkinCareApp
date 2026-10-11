package com.monsteraltech.skincare.body.mole.domain.model

/** Lightweight list metadata; dates and image references are independent of SDK/UI types. */
data class MoleListEntry(
    val id: String,
    val title: String,
    val description: String,
    val bodyPart: String,
    val bodyPartColorCode: String,
    val imageReference: String,
    val analysisResult: String,
    val analysisCount: Int,
    val createdAtEpochMillis: Long,
    val lastAnalysisAtEpochMillis: Long?
)

data class MoleListQuery(val text: String = "", val bodyPartColor: String? = null, val selector: Boolean = false)
data class MoleListPage(val items: List<MoleListEntry>, val matchingCount: Int, val totalCount: Int) {
    val hasMore get() = items.size < matchingCount
}

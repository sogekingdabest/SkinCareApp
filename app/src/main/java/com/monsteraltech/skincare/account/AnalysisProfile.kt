package com.monsteraltech.skincare.account

import androidx.annotation.Keep
import com.monsteraltech.skincare.analysis.model.AnalysisSex
import com.monsteraltech.skincare.analysis.model.AnatomicalSite
import com.monsteraltech.skincare.analysis.model.LesionMetadata

/** Optional local defaults; the capture can override them without changing the profile. */
@Keep
data class AnalysisProfile(
    val ageYears: Int? = null,
    val ageRecordedYear: Int? = null,
    val sex: AnalysisSex? = null
) {
    fun forCapture(year: Int, confirmedSite: AnatomicalSite? = null): LesionMetadata = LesionMetadata(
        ageYears = ageYears?.takeIf { it in 0..120 && ageRecordedYear == year },
        sex = sex,
        site = confirmedSite
    )
}

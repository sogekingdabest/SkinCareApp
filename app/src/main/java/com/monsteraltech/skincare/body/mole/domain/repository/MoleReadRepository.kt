package com.monsteraltech.skincare.body.mole.domain.repository

import com.monsteraltech.skincare.body.mole.domain.model.MoleListEntry
import com.monsteraltech.skincare.body.mole.domain.model.MoleListPage
import com.monsteraltech.skincare.body.mole.domain.model.MoleListQuery
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import kotlinx.coroutines.flow.Flow

interface MoleReadRepository {
    fun observeMoles(ownerId: String, query: MoleListQuery, limit: Int): Flow<MoleListPage>
    fun observeHistory(ownerId: String, moleId: String, limit: Int): Flow<AnalysisHistoryPage>
}

/** The analysis DTO is retained as a compatibility bridge for the existing history renderer. */
data class AnalysisHistoryPage(val mole: MoleListEntry, val analyses: List<AnalysisData>, val totalCount: Int) {
    val hasMore get() = analyses.size < totalCount
}

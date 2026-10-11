package com.monsteraltech.skincare.body.mole.data.repository

import androidx.room.withTransaction
import com.monsteraltech.skincare.body.mole.data.local.LocalMoleMapper
import com.monsteraltech.skincare.body.mole.data.local.MoleEntity
import com.monsteraltech.skincare.body.mole.data.local.SkinCareDatabase
import com.monsteraltech.skincare.body.mole.domain.model.*
import com.monsteraltech.skincare.body.mole.domain.repository.AnalysisHistoryPage
import com.monsteraltech.skincare.body.mole.domain.repository.MoleReadRepository
import com.monsteraltech.skincare.data.image.PrivateImageStore
import com.monsteraltech.skincare.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*

@Singleton
class LocalMoleReadRepository @Inject constructor(
    private val database: SkinCareDatabase,
    private val images: PrivateImageStore,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : MoleReadRepository {
    private val dao = database.moleDao()
    private val mapper = LocalMoleMapper()
    override fun observeMoles(ownerId: String, query: MoleListQuery, limit: Int): Flow<MoleListPage> = flow {
        require(ownerId.isNotBlank() && limit > 0)
        emitAll(dao.observeMoleList(ownerId, query.bodyPartColor).map { entries ->
            // Search all lightweight metadata before limiting visible rows, preserving Unicode/date matching.
            val matched = entries.filter { MoleListSearch.matches(it, query) }
            val visible = matched.take(limit)
            visible.forEach { currentCoroutineContext().ensureActive(); images.migrateLegacy(it.imageReference) }
            MoleListPage(visible, matched.size, entries.size)
        }.distinctUntilChanged())
    }.flowOn(dispatcher)

    override fun observeHistory(ownerId: String, moleId: String, limit: Int): Flow<AnalysisHistoryPage> = flow {
        require(ownerId.isNotBlank() && moleId.isNotBlank() && limit > 0)
        emitAll(dao.observeHistoryChanges(ownerId, moleId).map {
            // Read header, count and visible rows atomically; equal counts still trigger metadata refreshes.
            val snapshot = database.withTransaction {
                val mole = dao.getMole(ownerId, moleId) ?: throw NoSuchElementException("No se encontró el lunar")
                Triple(mole, dao.getAnalysisHistoryPage(ownerId, moleId, limit), dao.countAnalyses(ownerId, moleId))
            }
            images.migrateLegacy(snapshot.first.imagePath)
            val analyses = snapshot.second.map { currentCoroutineContext().ensureActive(); images.migrateLegacy(it.imagePath); mapper.toModel(it) }
            AnalysisHistoryPage(snapshot.first.toListEntry(), analyses, snapshot.third)
        }.distinctUntilChanged())
    }.flowOn(dispatcher)

    private fun MoleEntity.toListEntry() = MoleListEntry(id, title, description, bodyPart, bodyPartColorCode,
        imagePath, analysisResult, analysisCount, createdAtEpochMillis, lastAnalysisAtEpochMillis)
}

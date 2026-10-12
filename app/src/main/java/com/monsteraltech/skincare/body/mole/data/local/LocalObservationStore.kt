package com.monsteraltech.skincare.body.mole.data.local

import androidx.room.withTransaction
import javax.inject.Inject

/** Writes an observation and its current summary in one Room transaction. */
class LocalObservationStore @Inject constructor(private val database: SkinCareDatabase) {
    private val dao = database.moleDao()
    private val mapper = LocalMoleMapper()

    suspend fun create(mole: MoleEntity, analysis: AnalysisEntity) = database.withTransaction {
        require(analysis.id.isNotBlank() && analysis.moleId == mole.id && analysis.userId == mole.userId)
        dao.insertMole(withSummary(mole, analysis, 1))
        dao.insertAnalysis(analysis)
    }

    suspend fun append(userId: String, moleId: String, analysis: AnalysisEntity) = database.withTransaction {
        require(analysis.id.isNotBlank() && analysis.moleId == moleId && analysis.userId == userId)
        val current = dao.getMole(userId, moleId) ?: throw NoSuchElementException("Lunar no encontrado")
        val model = mapper.toModel(analysis)
        val metadata = model.analysisMetadata + mapOf("bodyRegionId" to current.bodyRegionId,
            "bodyPart" to current.bodyPart, "bodyPartColorCode" to current.bodyPartColorCode)
        val saved = analysis.copy(imagePath = analysis.imagePath.ifBlank { current.imagePath },
            analysisMetadataJson = mapper.toEntity(model.copy(analysisMetadata = metadata), userId).analysisMetadataJson)
        dao.insertAnalysis(saved)
        dao.upsertMole(withSummary(current, saved, dao.countAnalyses(userId, moleId)))
    }

    private fun withSummary(current: MoleEntity, saved: AnalysisEntity, count: Int) = current.copy(
            currentAnalysisId = saved.id,
            description = saved.description,
            imagePath = saved.imagePath,
            analysisResult = saved.analysisResult,
            updatedAtEpochMillis = System.currentTimeMillis(),
            analysisCount = count,
            firstAnalysisAtEpochMillis = current.firstAnalysisAtEpochMillis ?: saved.createdAtEpochMillis,
            lastAnalysisAtEpochMillis = saved.createdAtEpochMillis,
            aiProbability = saved.aiProbability.toDouble(), aiConfidence = saved.aiConfidence.toDouble(),
            riskLevel = saved.riskLevel, combinedScore = saved.combinedScore.toDouble(),
            abcdeAsymmetry = saved.asymmetryScore.toDouble(), abcdeBorder = saved.borderScore.toDouble(),
            abcdeColor = saved.colorScore.toDouble(), abcdeDiameter = saved.diameterScore.toDouble(),
            abcdeTotalScore = saved.totalScore.toDouble(), recommendation = saved.recommendation,
            analysisMetadataJson = saved.analysisMetadataJson
        )

    suspend fun current(userId: String, moleId: String): Snapshot = database.withTransaction {
        val mole = dao.getMole(userId, moleId) ?: throw NoSuchElementException("Lunar no encontrado")
        val analysis = mole.currentAnalysisId?.let {
            dao.getAnalysis(userId, moleId, it) ?: error("La observación actual no existe")
        }
        Snapshot(mole, analysis)
    }

    suspend fun annotate(userId: String, moleId: String, analysisId: String, metadata: Map<String, Any>) =
        database.withTransaction {
            val mole = dao.getMole(userId, moleId) ?: throw NoSuchElementException("Lunar no encontrado")
            val analysis = dao.getAnalysis(userId, moleId, analysisId)
                ?: throw NoSuchElementException("Observación no encontrada")
            val merged = mapper.toModel(analysis).analysisMetadata + metadata
            val json = mapper.toEntity(mapper.toModel(analysis).copy(analysisMetadata = merged), userId).analysisMetadataJson
            dao.updateAnalysisMetadata(userId, moleId, analysisId, json)
            if (mole.currentAnalysisId == analysisId) {
                dao.upsertMole(mole.copy(analysisMetadataJson = json, updatedAtEpochMillis = System.currentTimeMillis()))
            }
        }

    data class Snapshot(val mole: MoleEntity, val analysis: AnalysisEntity?)
}

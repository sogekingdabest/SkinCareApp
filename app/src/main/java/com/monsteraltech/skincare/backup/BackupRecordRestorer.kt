package com.monsteraltech.skincare.backup

import androidx.room.withTransaction
import com.monsteraltech.skincare.body.mole.data.local.AnalysisEntity
import com.monsteraltech.skincare.body.mole.data.local.MoleEntity
import com.monsteraltech.skincare.body.mole.data.local.SkinCareDatabase
import com.monsteraltech.skincare.data.image.PrivateImageStore
import java.security.MessageDigest
import javax.inject.Inject

/** Merges a backup without replacing local observations or the local current selection. */
class BackupRecordRestorer @Inject constructor(
    private val database: SkinCareDatabase,
    private val images: PrivateImageStore
) {
    private val dao = database.moleDao()

    internal suspend fun merge(ownerId: String, payload: BackupPayload, imagePaths: Map<String, String>): CommittedRestore =
        database.withTransaction {
            val records = remap(ownerId, payload, imagePaths)
            val previous = records.moles.associate { it.id to dao.getMole(ownerId, it.id) }
            var insertedMoles = 0
            var insertedAnalyses = 0
            var preserved = false
            val warnings = mutableSetOf<RestoreWarning>()
            if (referencedImages(payload).any { it !in imagePaths }) warnings += RestoreWarning.MISSING_IMAGES
            records.moles.forEach { mole ->
                if (previous[mole.id] == null) {
                    dao.insertMole(mole.copy(currentAnalysisId = null))
                    insertedMoles++
                } else preserved = true
            }
            records.analyses.forEach { analysis ->
                val existing = dao.getAnalysis(ownerId, analysis.moleId, analysis.id)
                if (existing == null) {
                    dao.insertAnalysis(analysis)
                    insertedAnalyses++
                } else {
                    preserved = true
                    if (!hasImage(existing.imagePath) && analysis.imagePath.isNotBlank()) {
                        // Repair missing bytes, retaining the observation's clinical fields and annotations.
                        dao.updateAnalysisImage(ownerId, analysis.moleId, analysis.id, analysis.imagePath)
                    }
                }
            }
            val retained = mutableSetOf<String>()
            records.moles.forEach { incoming ->
                val local = previous[incoming.id]
                val history = dao.getAnalysisHistory(ownerId, incoming.id)
                val currentId = local?.currentAnalysisId?.takeIf { id -> history.any { it.id == id } }
                    ?: incoming.currentAnalysisId
                    ?: history.maxWithOrNull(compareBy<AnalysisEntity> { it.createdAtEpochMillis }.thenBy { it.id })?.id
                val current = history.firstOrNull { it.id == currentId }
                val base = local ?: incoming
                val summary = if (current == null) base.copy(analysisCount = history.size,
                    imagePath = if (hasImage(base.imagePath)) base.imagePath else incoming.imagePath) else base.copy(
                    currentAnalysisId = current.id, imagePath = current.imagePath,
                    description = current.description, analysisResult = current.analysisResult,
                    analysisCount = history.size, firstAnalysisAtEpochMillis = minOf(
                        base.firstAnalysisAtEpochMillis ?: history.minOf { it.createdAtEpochMillis },
                        history.minOf { it.createdAtEpochMillis }),
                    lastAnalysisAtEpochMillis = current.createdAtEpochMillis,
                    aiProbability = current.aiProbability.toDouble(), aiConfidence = current.aiConfidence.toDouble(),
                    riskLevel = current.riskLevel, combinedScore = current.combinedScore.toDouble(),
                    abcdeAsymmetry = current.asymmetryScore.toDouble(), abcdeBorder = current.borderScore.toDouble(),
                    abcdeColor = current.colorScore.toDouble(), abcdeDiameter = current.diameterScore.toDouble(),
                    abcdeTotalScore = current.totalScore.toDouble(), recommendation = current.recommendation,
                    analysisMetadataJson = current.analysisMetadataJson
                )
                dao.upsertMole(summary)
                retained += summary.imagePath
                retained += history.map { it.imagePath }
            }
            if (preserved) warnings += RestoreWarning.EXISTING_DATA_PRESERVED
            val usedNewImages = retained.intersect(imagePaths.values.toSet())
            CommittedRestore(BackupSummary(insertedMoles, insertedAnalyses, usedNewImages.size), usedNewImages, warnings)
        }

    internal suspend fun remap(ownerId: String, payload: BackupPayload, imagePaths: Map<String, String>): Records {
        val moleIds = payload.moles.associate { mole ->
            val owner = dao.getMoleOwner(mole.id)
            val scoped = scopedId("mole", ownerId, mole.id)
            // Keep an earlier import identity even after its original owner's records are removed.
            val id = if ((owner != null && owner != ownerId) || dao.getMoleOwner(scoped) == ownerId) scoped else mole.id
            require(dao.getMoleOwner(id).let { it == null || it == ownerId }) { "Conflicto de identidad del lunar" }
            mole.id to id
        }
        val analysisIds = payload.analyses.associate { analysis ->
            val owner = dao.getAnalysisOwner(analysis.id)
            val parent = moleIds.getValue(analysis.moleId)
            val collision = owner != null && (owner != ownerId || dao.getAnalysis(ownerId, parent, analysis.id) == null)
            val scoped = scopedId("analysis", ownerId, analysis.id, analysis.moleId)
            val id = if (collision || dao.getAnalysisOwner(scoped) == ownerId) scoped else analysis.id
            val mappedOwner = dao.getAnalysisOwner(id)
            require(mappedOwner == null || (mappedOwner == ownerId && dao.getAnalysis(ownerId, parent, id) != null)) {
                "Conflicto de identidad de la observación"
            }
            analysis.id to id
        }
        return Records(payload.moles.map { mole -> mole.copy(
            id = moleIds.getValue(mole.id), userId = ownerId,
            imagePath = imagePaths[mole.imagePath].orEmpty(),
            currentAnalysisId = mole.currentAnalysisId?.let(analysisIds::getValue)
        ) }, payload.analyses.map { analysis -> analysis.copy(
            id = analysisIds.getValue(analysis.id), moleId = moleIds.getValue(analysis.moleId), userId = ownerId,
            imagePath = imagePaths[analysis.imagePath].orEmpty()
        ) })
    }

    private fun hasImage(reference: String) = reference.isNotBlank() &&
        images.resolve(reference)?.let { it.isFile && it.length() > 0 } == true

    private fun scopedId(vararg parts: String): String {
        val key = parts.joinToString("") { "${it.length}:$it" }
        return "import_" + MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun referencedImages(payload: BackupPayload) =
        (payload.moles.map { it.imagePath } + payload.analyses.map { it.imagePath }).filter { it.isNotBlank() }.toSet()

    internal data class Records(val moles: List<MoleEntity>, val analyses: List<AnalysisEntity>)
}

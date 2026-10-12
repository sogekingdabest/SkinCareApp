package com.monsteraltech.skincare.body.mole.repository

import com.monsteraltech.skincare.body.domain.BodyRegionId
import com.monsteraltech.skincare.body.data.LegacyBodyRegionMapping
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.time.Instant
import com.monsteraltech.skincare.data.identity.ActiveProfile
import com.monsteraltech.skincare.body.mole.data.local.LocalMoleMapper
import com.monsteraltech.skincare.body.mole.data.local.SkinCareDatabase
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.model.MoleData
import com.monsteraltech.skincare.body.mole.validation.AnalysisDataValidator
import com.monsteraltech.skincare.data.image.PrivateImageStore
import com.monsteraltech.skincare.data.image.PrivateImagePersistence
import com.monsteraltech.skincare.body.mole.data.local.MoleEntity
import com.monsteraltech.skincare.body.mole.data.local.LocalObservationStore
import kotlinx.coroutines.CancellationException
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuente de verdad local para lunares y análisis.
 *
 * Firebase Auth identifica el espacio local de cada cuenta, pero esta clase no
 * transmite fotografías, valoraciones ni resultados de salud a Firestore.
 */
@Singleton
class MoleRepository @Inject constructor(
    private val database: SkinCareDatabase,
    private val activeProfile: ActiveProfile,
    private val imageStore: PrivateImageStore,
    private val imagePersistence: PrivateImagePersistence,
    private val observations: LocalObservationStore
) {
    private val dao = database.moleDao()
    private val mapper = LocalMoleMapper()

    suspend fun saveMoleWithAnalysis(
        context: Context,
        imageFile: File,
        title: String,
        description: String,
        bodyPart: String,
        bodyPartColorCode: String,
        analysisData: AnalysisData
    ): Result<MoleData> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = requireCurrentUserId()
            require(analysisData.id.isNotBlank()) { "ID de observación no válido" }
            val validation = AnalysisDataValidator.validateAnalysisData(analysisData)
            require(validation.isValid) { validation.getErrorMessage() }
            val compressedImage = compressImage(context, imageFile)
            try {
                imagePersistence.save(compressedImage, userId) { localImagePath ->
                    val now = Instant.ofEpochMilli(System.currentTimeMillis())
                    val mole = MoleData(
                        id = analysisData.moleId,
                        currentAnalysisId = analysisData.id,
                        title = title,
                        description = description,
                        bodyPart = bodyPart,
                        bodyPartColorCode = bodyPartColorCode,
                        bodyRegionId = LegacyBodyRegionMapping.resolve(BodyRegionId.fromValue(analysisData.analysisMetadata["bodyRegionId"] as? String), bodyPartColorCode, bodyPart),
                        imageUrl = localImagePath,
                        aiResult = analysisData.analysisResult,
                        userId = userId,
                        createdAt = now,
                        updatedAt = now,
                        analysisCount = 1,
                        firstAnalysisDate = analysisData.createdAt,
                        lastAnalysisDate = analysisData.createdAt,
                        aiProbability = analysisData.aiProbability.toDouble(),
                        aiConfidence = analysisData.aiConfidence.toDouble(),
                        riskLevel = analysisData.riskLevel,
                        combinedScore = analysisData.combinedScore.toDouble(),
                        abcdeAsymmetry = analysisData.abcdeScores.asymmetryScore.toDouble(),
                        abcdeBorder = analysisData.abcdeScores.borderScore.toDouble(),
                        abcdeColor = analysisData.abcdeScores.colorScore.toDouble(),
                        abcdeDiameter = analysisData.abcdeScores.diameterScore.toDouble(),
                        abcdeTotalScore = analysisData.abcdeScores.totalScore.toDouble(),
                        recommendation = analysisData.recommendation,
                        analysisMetadata = analysisData.analysisMetadata
                    )
                    observations.create(
                        mapper.toEntity(mole),
                        mapper.toEntity(analysisData.copy(imageUrl = localImagePath, description = description), userId)
                    )
                    mole
                }
            } finally {
                deleteTemporaryCompressedCopy(context, compressedImage, imageFile)
            }
        }.logFailure("Error al guardar el lunar localmente")
    }

    suspend fun getMoleById(userId: String, moleId: String): Result<MoleData> =
        withContext(Dispatchers.IO) {
            runCatching {
                requireValidIds(userId, moleId)
                dao.getMole(userId, moleId)?.let(::moleWithImage)
                    ?: throw NoSuchElementException("No se encontró el lunar con ID: $moleId")
            }.logFailure("Error al obtener el lunar local")
        }

    suspend fun getAllMolesForUser(userId: String): Result<List<MoleData>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(userId.isNotBlank()) { "ID de usuario no válido" }
                dao.getAllMoles(userId).map(::moleWithImage)
            }.logFailure("Error al obtener los lunares locales")
        }

    suspend fun getMolesForBodyRegion(regionId: BodyRegionId): Result<List<MoleData>> =
        withContext(Dispatchers.IO) {
            runCatching {
                dao.getMolesForBodyRegion(requireCurrentUserId(), regionId.value)
                    .map(::moleWithImage)
            }.logFailure("Error al obtener los lunares de la zona")
        }

    suspend fun saveAnalysisToMole(
        context: Context,
        moleId: String,
        newAnalysis: AnalysisData,
        imageFile: File? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = requireCurrentUserId()
            require(moleId.isNotBlank()) { "ID de lunar no válido" }
            val validation = AnalysisDataValidator.validateAnalysisData(newAnalysis)
            require(validation.isValid) {
                "Datos de análisis inválidos: ${validation.getErrorMessage()}"
            }

            require(newAnalysis.moleId == moleId) { "La observación pertenece a otro lunar" }
            val commitAnalysis: suspend (String) -> Unit = { imagePath ->
                observations.append(userId, moleId, mapper.toEntity(newAnalysis.copy(imageUrl = imagePath), userId))
            }
            if (imageFile != null) {
                val compressedImage = compressImage(context, imageFile)
                try {
                    imagePersistence.save(compressedImage, userId, commitAnalysis)
                } finally {
                    deleteTemporaryCompressedCopy(context, compressedImage, imageFile)
                }
            } else {
                commitAnalysis(newAnalysis.imageUrl)
            }
        }.logFailure("Error al guardar el análisis local")
    }

    suspend fun getAnalysisHistory(moleId: String): Result<List<AnalysisData>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(moleId.isNotBlank()) { "ID de lunar no válido" }
                dao.getAnalysisHistory(requireCurrentUserId(), moleId).map {
                    imageStore.migrateLegacy(it.imagePath)
                    mapper.toModel(it)
                }
            }.logFailure("Error al obtener el historial local")
        }

    suspend fun getMoleWithCurrentAnalysis(userId: String, moleId: String): Result<CurrentAnalysis> =
        withContext(Dispatchers.IO) {
            runCatching {
                requireValidIds(userId, moleId)
                val snapshot = observations.current(userId, moleId)
                val analysis = snapshot.analysis?.let {
                    imageStore.migrateLegacy(it.imagePath)
                    mapper.toModel(it)
                }
                CurrentAnalysis(moleWithImage(snapshot.mole), analysis)
            }.logFailure("Error al obtener la observación actual")
        }

    suspend fun getCurrentAnalysis(moleId: String): Result<AnalysisData?> = withContext(Dispatchers.IO) {
        runCatching {
            getMoleWithCurrentAnalysis(requireCurrentUserId(), moleId).getOrThrow().analysis
        }.logFailure("Error al obtener el análisis actual")
    }

    suspend fun updateMoleAnalysisMetadata(
        userId: String,
        moleId: String,
        metadata: Map<String, Any>,
        analysisId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireValidIds(userId, moleId)
            require(metadata.isNotEmpty() && analysisId.isNotBlank()) { "Observación o metadatos no válidos" }
            observations.annotate(userId, moleId, analysisId, metadata)
        }.logFailure("Error al actualizar metadatos de la observación")
    }

    data class CurrentAnalysis(val mole: MoleData, val analysis: AnalysisData?)

    private fun <T> Result<T>.logFailure(message: String): Result<T> = onFailure {
        if (it is CancellationException) throw it
        Log.e(TAG, message, it)
    }

    private fun moleWithImage(entity: MoleEntity): MoleData {
        imageStore.migrateLegacy(entity.imagePath)
        return mapper.toModel(entity)
    }

    private suspend fun compressImage(context: Context, imageFile: File): File =
        Compressor.compress(context, imageFile) {
            resolution(1024, 1024)
            quality(80)
            format(Bitmap.CompressFormat.JPEG)
            size(512_000)
        }

    private fun deleteTemporaryCompressedCopy(
        context: Context,
        compressedFile: File,
        sourceFile: File
    ) {
        val isSeparateCopy = compressedFile.canonicalFile != sourceFile.canonicalFile
        val isAppCache = compressedFile.canonicalFile.toPath().startsWith(context.cacheDir.canonicalFile.toPath())
        if (isSeparateCopy && isAppCache) {
            runCatching { compressedFile.delete() }
        }
    }

    private fun requireCurrentUserId(): String =
        activeProfile.requireOwnerId()

    private fun requireValidIds(userId: String, moleId: String) {
        require(userId.isNotBlank() && moleId.isNotBlank()) {
            "ID de usuario o lunar no válido"
        }
    }

    private companion object {
        const val TAG = "MoleRepository"
    }
}

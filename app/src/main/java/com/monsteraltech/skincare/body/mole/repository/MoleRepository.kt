package com.monsteraltech.skincare.body.mole.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.Timestamp
import com.monsteraltech.skincare.data.identity.ActiveProfile
import com.monsteraltech.skincare.body.mole.data.local.LocalMoleMapper
import com.monsteraltech.skincare.body.mole.data.local.SkinCareDatabase
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.model.MoleData
import com.monsteraltech.skincare.body.mole.validation.AnalysisDataValidator
import com.monsteraltech.skincare.data.FirebaseDataManager
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
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
    private val imageManager: FirebaseDataManager
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
            val compressedImage = compressImage(context, imageFile)
            val localImagePath = try {
                imageManager.saveImageLocally(context, compressedImage.absolutePath, userId)
            } finally {
                deleteTemporaryCompressedCopy(context, compressedImage, imageFile)
            }
            val now = Timestamp.now()
            val mole = MoleData(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description,
                bodyPart = bodyPart,
                bodyPartColorCode = bodyPartColorCode,
                imageUrl = localImagePath,
                aiResult = analysisData.analysisResult,
                userId = userId,
                createdAt = now,
                updatedAt = now,
                analysisCount = 1,
                firstAnalysisDate = now,
                lastAnalysisDate = now,
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
            dao.upsertMole(mapper.toEntity(mole))
            mole
        }.onFailure { Log.e(TAG, "Error al guardar el lunar localmente", it) }
    }

    suspend fun getMoleById(userId: String, moleId: String): Result<MoleData> =
        withContext(Dispatchers.IO) {
            runCatching {
                requireValidIds(userId, moleId)
                dao.getMole(userId, moleId)?.let(mapper::toModel)
                    ?: throw NoSuchElementException("No se encontró el lunar con ID: $moleId")
            }.onFailure { Log.e(TAG, "Error al obtener el lunar local", it) }
        }

    suspend fun getAllMolesForUser(userId: String): Result<List<MoleData>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(userId.isNotBlank()) { "ID de usuario no válido" }
                dao.getMoles(userId).map(mapper::toModel)
            }.onFailure { Log.e(TAG, "Error al obtener los lunares locales", it) }
        }

    suspend fun getMolesForBodyPart(bodyPartColorCode: String): Result<List<MoleData>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(bodyPartColorCode.isNotBlank()) { "Código de zona corporal no válido" }
                dao.getMolesForBodyPart(requireCurrentUserId(), bodyPartColorCode)
                    .map(mapper::toModel)
            }.onFailure { Log.e(TAG, "Error al obtener los lunares de la zona", it) }
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

            val processedImagePath = if (imageFile != null) {
                val compressedImage = compressImage(context, imageFile)
                try {
                    imageManager.saveImageLocally(context, compressedImage.absolutePath, userId)
                } finally {
                    deleteTemporaryCompressedCopy(context, compressedImage, imageFile)
                }
            } else {
                newAnalysis.imageUrl
            }

            database.withTransaction {
                val current = dao.getMole(userId, moleId)
                    ?: throw NoSuchElementException("Lunar no encontrado")
                val now = System.currentTimeMillis()

                if (current.analysisCount > 0) {
                    val previousAnalysis = mapper.currentAnalysis(current).copy(
                        id = "${moleId}_analysis_${current.analysisCount}"
                    )
                    dao.upsertAnalysis(mapper.toEntity(previousAnalysis, userId))
                }

                dao.upsertMole(
                    current.copy(
                        description = newAnalysis.description,
                        imagePath = processedImagePath.ifBlank { current.imagePath },
                        analysisResult = newAnalysis.analysisResult,
                        updatedAtEpochMillis = now,
                        analysisCount = current.analysisCount + 1,
                        firstAnalysisAtEpochMillis =
                            current.firstAnalysisAtEpochMillis ?: now,
                        lastAnalysisAtEpochMillis = now,
                        aiProbability = newAnalysis.aiProbability.toDouble(),
                        aiConfidence = newAnalysis.aiConfidence.toDouble(),
                        riskLevel = newAnalysis.riskLevel,
                        combinedScore = newAnalysis.combinedScore.toDouble(),
                        abcdeAsymmetry = newAnalysis.abcdeScores.asymmetryScore.toDouble(),
                        abcdeBorder = newAnalysis.abcdeScores.borderScore.toDouble(),
                        abcdeColor = newAnalysis.abcdeScores.colorScore.toDouble(),
                        abcdeDiameter = newAnalysis.abcdeScores.diameterScore.toDouble(),
                        abcdeTotalScore = newAnalysis.abcdeScores.totalScore.toDouble(),
                        recommendation = newAnalysis.recommendation,
                        analysisMetadataJson = mapper.toEntity(
                            mapper.toModel(current).copy(
                                analysisMetadata = newAnalysis.analysisMetadata
                            )
                        ).analysisMetadataJson
                    )
                )
            }
        }.onFailure { Log.e(TAG, "Error al guardar el análisis local", it) }
    }

    suspend fun getAnalysisHistory(moleId: String): Result<List<AnalysisData>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(moleId.isNotBlank()) { "ID de lunar no válido" }
                dao.getAnalysisHistory(requireCurrentUserId(), moleId).map(mapper::toModel)
            }.onFailure { Log.e(TAG, "Error al obtener el historial local", it) }
        }

    suspend fun getCurrentAnalysis(moleId: String): Result<AnalysisData?> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(moleId.isNotBlank()) { "ID de lunar no válido" }
                val mole = dao.getMole(requireCurrentUserId(), moleId)
                    ?: throw NoSuchElementException("Lunar no encontrado")
                if (mole.analysisCount > 0 || mole.analysisResult.isNotBlank()) {
                    mapper.currentAnalysis(mole)
                } else {
                    null
                }
            }.onFailure { Log.e(TAG, "Error al obtener el análisis actual", it) }
        }

    suspend fun updateMoleAnalysisMetadata(
        userId: String,
        moleId: String,
        metadata: Map<String, Any>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireValidIds(userId, moleId)
            require(metadata.isNotEmpty()) { "Los metadatos no pueden estar vacíos" }
            val current = dao.getMole(userId, moleId)
                ?: throw NoSuchElementException("Lunar no encontrado con ID: $moleId")
            val currentModel = mapper.toModel(current)
            val mergedMetadata = currentModel.analysisMetadata.toMutableMap().apply {
                putAll(metadata)
            }
            dao.upsertMole(
                mapper.toEntity(
                    currentModel.copy(
                        analysisMetadata = mergedMetadata,
                        updatedAt = Timestamp.now()
                    )
                )
            )
        }.onFailure { Log.e(TAG, "Error al actualizar metadatos locales", it) }
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
        val isSeparateCopy = compressedFile.absolutePath != sourceFile.absolutePath
        val isAppCache = compressedFile.absolutePath.startsWith(context.cacheDir.absolutePath)
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

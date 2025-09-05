package es.monsteraltech.skincare_tfm.body.mole.repository
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import es.monsteraltech.skincare_tfm.body.mole.model.ABCDEScores
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import es.monsteraltech.skincare_tfm.body.mole.performance.OptimizedFirebaseQueries
import es.monsteraltech.skincare_tfm.body.mole.validation.AnalysisDataValidator
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
class MoleRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val firebaseDataManager = FirebaseDataManager()
    private val USERS_COLLECTION = "users"
    private val MOLES_SUBCOLLECTION = "moles"
    private val ANALYSIS_SUBCOLLECTION = "mole_analysis_historial"
    suspend fun saveMoleWithAnalysis(
            context: Context,
            imageFile: File,
            title: String,
            description: String,
            bodyPart: String,
            bodyPartColorCode: String,
            analysisData: AnalysisData
    ): Result<MoleData> =
            withContext(Dispatchers.IO) {
                try {
                    val currentUser =
                            auth.currentUser
                                    ?: return@withContext Result.failure(
                                            Exception("Usuario no autenticado")
                                    )
                    val compressedImageFile = compressImage(context, imageFile)
                    val localImagePath =
                            firebaseDataManager.saveImageLocally(
                                    context,
                                    compressedImageFile.absolutePath
                            )
                    val moleId = UUID.randomUUID().toString()
                    val moleData =
                            MoleData(
                                    id = moleId,
                                    title = title,
                                    description = description,
                                    bodyPart = bodyPart,
                                    bodyPartColorCode = bodyPartColorCode,
                                    imageUrl = localImagePath,
                                    userId = currentUser.uid,
                                    createdAt = Timestamp.now(),
                                    updatedAt = Timestamp.now(),
                                    analysisCount = 1,
                                    firstAnalysisDate = Timestamp.now(),
                                    lastAnalysisDate = Timestamp.now(),
                                    aiProbability = analysisData.aiProbability.toDouble(),
                                    aiConfidence = analysisData.aiConfidence.toDouble(),
                                    riskLevel = analysisData.riskLevel,
                                    combinedScore = analysisData.combinedScore.toDouble(),
                                    abcdeAsymmetry =
                                            analysisData.abcdeScores.asymmetryScore.toDouble(),
                                    abcdeBorder = analysisData.abcdeScores.borderScore.toDouble(),
                                    abcdeColor = analysisData.abcdeScores.colorScore.toDouble(),
                                    abcdeDiameter =
                                            analysisData.abcdeScores.diameterScore.toDouble(),
                                    abcdeTotalScore =
                                            analysisData.abcdeScores.totalScore.toDouble(),
                                    recommendation = analysisData.recommendation,
                                    analysisMetadata = analysisData.analysisMetadata
                            )
                    firestore
                            .collection(USERS_COLLECTION)
                            .document(currentUser.uid)
                            .collection(MOLES_SUBCOLLECTION)
                            .document(moleId)
                            .set(moleData.toMap())
                            .await()
                    Result.success(moleData)
                } catch (e: Exception) {
                    Log.e("MoleRepository", "Error al guardar el lunar con análisis", e)
                    Result.failure(e)
                }
            }

    private suspend fun compressImage(context: Context, imageFile: File): File {
        return Compressor.compress(context, imageFile) {
            resolution(1024, 1024)
            quality(80)
            format(Bitmap.CompressFormat.JPEG)
            size(512_000)
        }
    }

    suspend fun getMoleById(userId: String, moleId: String): Result<MoleData> =
            withContext(Dispatchers.IO) {
                try {
                    val docSnapshot =
                            firestore
                                    .collection(USERS_COLLECTION)
                                    .document(userId)
                                    .collection(MOLES_SUBCOLLECTION)
                                    .document(moleId)
                                    .get()
                                    .await()
                    if (docSnapshot.exists()) {
                        val mole = docSnapshot.toObject(MoleData::class.java)
                        if (mole != null) {
                            Result.success(mole.copy(id = docSnapshot.id))
                        } else {
                            Result.failure(Exception("Error al convertir el documento"))
                        }
                    } else {
                        Result.failure(Exception("No se encontró el lunar con ID: $moleId"))
                    }
                } catch (e: Exception) {
                    Log.e("MoleRepository", "Error al obtener el lunar por ID", e)
                    Result.failure(e)
                }
            }
    suspend fun getAllMolesForUser(userId: String): Result<List<MoleData>> =
            withContext(Dispatchers.IO) {
                try {
                    OptimizedFirebaseQueries()
                    val querySnapshot =
                            firestore
                                    .collection(USERS_COLLECTION)
                                    .document(userId)
                                    .collection(MOLES_SUBCOLLECTION)
                                    .orderBy(
                                            "updatedAt",
                                            com.google.firebase.firestore.Query.Direction.DESCENDING
                                    )
                                    .limit(50)
                                    .get()
                                    .await()
                    val moles =
                            querySnapshot.documents.mapNotNull { doc ->
                                try {
                                    val mole = doc.toObject(MoleData::class.java)
                                    mole?.copy(id = doc.id)
                                } catch (e: Exception) {
                                    Log.w("MoleRepository", "Error al parsear lunar ${doc.id}", e)
                                    null
                                }
                            }
                    Result.success(moles)
                } catch (e: Exception) {
                    Log.e("MoleRepository", "Error al obtener todos los lunares del usuario", e)
                    Result.failure(e)
                }
            }
    suspend fun saveAnalysisToMole(
            context: Context,
            moleId: String,
            newAnalysis: AnalysisData,
            imageFile: File? = null
    ): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    val currentUser =
                            auth.currentUser
                                    ?: return@withContext Result.failure(
                                            SecurityException("Usuario no autenticado")
                                    )
                    val validationResult = AnalysisDataValidator.validateAnalysisData(newAnalysis)
                    if (!validationResult.isValid) {
                        return@withContext Result.failure(
                                IllegalArgumentException(
                                        "Datos de análisis inválidos: ${validationResult.getErrorMessage()}"
                                )
                        )
                    }
                    Log.d(
                            "MoleRepository",
                            "Saving new analysis with metadata keys: ${newAnalysis.analysisMetadata.keys}"
                    )
                    val processedImageUrl =
                            if (imageFile != null) {
                                try {
                                    val compressedImageFile = compressImage(context, imageFile)
                                    firebaseDataManager.saveImageLocally(
                                            context,
                                            compressedImageFile.absolutePath
                                    )
                                } catch (e: Exception) {
                                    Log.w(
                                            "MoleRepository",
                                            "Error al procesar nueva imagen, usando la existente",
                                            e
                                    )
                                    newAnalysis.imageUrl
                                }
                            } else {
                                newAnalysis.imageUrl
                            }
                    firestore
                            .runTransaction { transaction ->
                                val moleRef =
                                        firestore
                                                .collection(USERS_COLLECTION)
                                                .document(currentUser.uid)
                                                .collection(MOLES_SUBCOLLECTION)
                                                .document(moleId)
                                val moleSnapshot = transaction.get(moleRef)
                                if (!moleSnapshot.exists()) {
                                    throw Exception("Lunar no encontrado")
                                }
                                val currentCount = moleSnapshot.getLong("analysisCount") ?: 0L
                                val currentTimestamp = Timestamp.now()
                                if (currentCount > 0L) {
                                    val currentAnalysisData =
                                            AnalysisData(
                                                    id = "${moleId}_analysis_${currentCount}",
                                                    moleId = moleId,
                                                    description = moleSnapshot.getString("description") ?: "",
                                                    analysisResult = currentCount.toString(),
                                                    aiProbability =
                                                            moleSnapshot
                                                                    .getDouble("aiProbability")
                                                                    ?.toFloat()
                                                                    ?: 0f,
                                                    aiConfidence =
                                                            moleSnapshot
                                                                    .getDouble("aiConfidence")
                                                                    ?.toFloat()
                                                                    ?: 0f,
                                                    abcdeScores =
                                                            ABCDEScores(
                                                                    asymmetryScore =
                                                                            moleSnapshot
                                                                                    .getDouble(
                                                                                            "abcdeAsymmetry"
                                                                                    )
                                                                                    ?.toFloat()
                                                                                    ?: 0f,
                                                                    borderScore =
                                                                            moleSnapshot
                                                                                    .getDouble(
                                                                                            "abcdeBorder"
                                                                                    )
                                                                                    ?.toFloat()
                                                                                    ?: 0f,
                                                                    colorScore =
                                                                            moleSnapshot
                                                                                    .getDouble(
                                                                                            "abcdeColor"
                                                                                    )
                                                                                    ?.toFloat()
                                                                                    ?: 0f,
                                                                    diameterScore =
                                                                            moleSnapshot
                                                                                    .getDouble(
                                                                                            "abcdeDiameter"
                                                                                    )
                                                                                    ?.toFloat()
                                                                                    ?: 0f,
                                                                    evolutionScore = null,
                                                                    totalScore =
                                                                            moleSnapshot
                                                                                    .getDouble(
                                                                                            "abcdeTotalScore"
                                                                                    )
                                                                                    ?.toFloat()
                                                                                    ?: 0f
                                                            ),
                                                    combinedScore =
                                                            moleSnapshot
                                                                    .getDouble("combinedScore")
                                                                    ?.toFloat()
                                                                    ?: 0f,
                                                    riskLevel = moleSnapshot.getString("riskLevel")
                                                                    ?: "",
                                                    recommendation =
                                                            moleSnapshot.getString("recommendation")
                                                                    ?: "",
                                                    imageUrl = moleSnapshot.getString("imageUrl")
                                                                    ?: "",
                                                    createdAt =
                                                            moleSnapshot.getTimestamp(
                                                                    "lastAnalysisDate"
                                                            )
                                                                    ?: currentTimestamp,
                                                    analysisMetadata =
                                                            moleSnapshot.get("analysisMetadata") as?
                                                                    Map<String, Any>
                                                                    ?: emptyMap()
                                            )
                                    Log.d(
                                            "MoleRepository",
                                            "Moving current analysis to history with metadata keys: ${currentAnalysisData.analysisMetadata.keys}"
                                    )
                                    val historialRef =
                                            firestore
                                                    .collection(USERS_COLLECTION)
                                                    .document(currentUser.uid)
                                                    .collection(MOLES_SUBCOLLECTION)
                                                    .document(moleId)
                                                    .collection(ANALYSIS_SUBCOLLECTION)
                                                    .document(currentAnalysisData.id)
                                    transaction.set(historialRef, currentAnalysisData.toMap())
                                }
                                val updates =
                                        mutableMapOf<String, Any>(
                                                "analysisResult" to newAnalysis.analysisResult,
                                                "aiProbability" to
                                                        newAnalysis.aiProbability.toDouble(),
                                                "aiConfidence" to
                                                        newAnalysis.aiConfidence.toDouble(),
                                                "riskLevel" to newAnalysis.riskLevel,
                                                "combinedScore" to
                                                        newAnalysis.combinedScore.toDouble(),
                                                "abcdeAsymmetry" to
                                                        newAnalysis.abcdeScores.asymmetryScore
                                                                .toDouble(),
                                                "abcdeBorder" to
                                                        newAnalysis.abcdeScores.borderScore
                                                                .toDouble(),
                                                "abcdeColor" to
                                                        newAnalysis.abcdeScores.colorScore
                                                                .toDouble(),
                                                "abcdeDiameter" to
                                                        newAnalysis.abcdeScores.diameterScore
                                                                .toDouble(),
                                                "abcdeTotalScore" to
                                                        newAnalysis.abcdeScores.totalScore
                                                                .toDouble(),
                                                "recommendation" to newAnalysis.recommendation,
                                                "imageUrl" to processedImageUrl,
                                                "analysisCount" to (currentCount + 1),
                                                "lastAnalysisDate" to currentTimestamp,
                                                "updatedAt" to currentTimestamp,
                                                "analysisMetadata" to newAnalysis.analysisMetadata,
                                                "description" to newAnalysis.description
                                        )
                                if (currentCount == 0L) {
                                    updates["firstAnalysisDate"] = currentTimestamp
                                }
                                transaction.update(moleRef, updates)
                            }
                            .await()
                    Result.success(Unit)
                } catch (e: SecurityException) {
                    Log.e("MoleRepository", "Error de autenticación al guardar análisis", e)
                    Result.failure(e)
                } catch (e: IllegalArgumentException) {
                    Log.e("MoleRepository", "Error de validación al guardar análisis", e)
                    Result.failure(e)
                } catch (e: Exception) {
                    Log.e("MoleRepository", "Error al guardar análisis", e)
                    Result.failure(e)
                }
            }
    suspend fun getAnalysisHistory(moleId: String): Result<List<AnalysisData>> =
            withContext(Dispatchers.IO) {
                try {
                    val currentUser =
                            auth.currentUser
                                    ?: return@withContext Result.failure(
                                            SecurityException("Usuario no autenticado")
                                    )
                    if (moleId.isBlank()) {
                        return@withContext Result.failure(
                                IllegalArgumentException("ID de lunar no válido")
                        )
                    }
                    val querySnapshot =
                            firestore
                                    .collection(USERS_COLLECTION)
                                    .document(currentUser.uid)
                                    .collection(MOLES_SUBCOLLECTION)
                                    .document(moleId)
                                    .collection(ANALYSIS_SUBCOLLECTION)
                                    .get()
                                    .await()
                    val historicalAnalyses =
                            querySnapshot.documents.mapNotNull { doc ->
                                try {
                                    AnalysisData.fromMap(doc.id, doc.data ?: emptyMap())
                                } catch (e: Exception) {
                                    Log.w(
                                            "MoleRepository",
                                            "Error al parsear análisis ${doc.id}",
                                            e
                                    )
                                    null
                                }
                            }
                    val sortedAnalyses = historicalAnalyses.sortedByDescending { it.createdAt }
                    if (sortedAnalyses.isEmpty()) {
                        Log.i(
                                "MoleRepository",
                                "No se encontraron análisis históricos para el lunar $moleId"
                        )
                    }
                    Result.success(sortedAnalyses)
                } catch (e: SecurityException) {
                    Log.e("MoleRepository", "Error de autenticación al obtener historial", e)
                    Result.failure(e)
                } catch (e: IllegalArgumentException) {
                    Log.e("MoleRepository", "Error de validación al obtener historial", e)
                    Result.failure(e)
                } catch (e: Exception) {
                    Log.e("MoleRepository", "Error al obtener historial de análisis", e)
                    Result.failure(e)
                }
            }
    suspend fun getCurrentAnalysis(moleId: String): Result<AnalysisData?> =
            withContext(Dispatchers.IO) {
                try {
                    val currentUser =
                            auth.currentUser
                                    ?: return@withContext Result.failure(
                                            SecurityException("Usuario no autenticado")
                                    )
                    if (moleId.isBlank()) {
                        return@withContext Result.failure(
                                IllegalArgumentException("ID de lunar no válido")
                        )
                    }
                    val moleSnapshot =
                            firestore
                                    .collection(USERS_COLLECTION)
                                    .document(currentUser.uid)
                                    .collection(MOLES_SUBCOLLECTION)
                                    .document(moleId)
                                    .get()
                                    .await()
                    if (moleSnapshot.exists()) {
                        val analysisResult = moleSnapshot.getString("analysisResult") ?: ""
                        val analysisCount = (moleSnapshot.getLong("analysisCount") ?: 0L).toInt()
                        val hasAnalysisResult = analysisResult.isNotEmpty()
                        val hasStructuredAnalysis = analysisCount > 0
                        Log.d(
                                "MoleRepository",
                                "getCurrentAnalysis - analysisResult: '$analysisResult'"
                        )
                        Log.d(
                                "MoleRepository",
                                "getCurrentAnalysis - analysisCount: $analysisCount"
                        )
                        Log.d(
                                "MoleRepository",
                                "getCurrentAnalysis - hasAnalysisResult: $hasAnalysisResult"
                        )
                        Log.d(
                                "MoleRepository",
                                "getCurrentAnalysis - hasStructuredAnalysis: $hasStructuredAnalysis"
                        )
                        if (hasAnalysisResult || hasStructuredAnalysis) {
                            val effectiveAnalysisResult =
                                    if (analysisResult.isNotEmpty()) {
                                        analysisResult
                                    } else {
                                        "Análisis estructurado disponible"
                                    }
                            val currentAnalysis =
                                    AnalysisData(
                                            id = "${moleId}_current",
                                            moleId = moleId,
                                            analysisResult = effectiveAnalysisResult,
                                            aiProbability =
                                                    moleSnapshot
                                                            .getDouble("aiProbability")
                                                            ?.toFloat()
                                                            ?: 0f,
                                            aiConfidence =
                                                    moleSnapshot
                                                            .getDouble("aiConfidence")
                                                            ?.toFloat()
                                                            ?: 0f,
                                            abcdeScores =
                                                    ABCDEScores(
                                                            asymmetryScore =
                                                                    moleSnapshot
                                                                            .getDouble(
                                                                                    "abcdeAsymmetry"
                                                                            )
                                                                            ?.toFloat()
                                                                            ?: 0f,
                                                            borderScore =
                                                                    moleSnapshot
                                                                            .getDouble(
                                                                                    "abcdeBorder"
                                                                            )
                                                                            ?.toFloat()
                                                                            ?: 0f,
                                                            colorScore =
                                                                    moleSnapshot
                                                                            .getDouble("abcdeColor")
                                                                            ?.toFloat()
                                                                            ?: 0f,
                                                            diameterScore =
                                                                    moleSnapshot
                                                                            .getDouble(
                                                                                    "abcdeDiameter"
                                                                            )
                                                                            ?.toFloat()
                                                                            ?: 0f,
                                                            evolutionScore = null,
                                                            totalScore =
                                                                    moleSnapshot
                                                                            .getDouble(
                                                                                    "abcdeTotalScore"
                                                                            )
                                                                            ?.toFloat()
                                                                            ?: 0f
                                                    ),
                                            combinedScore =
                                                    moleSnapshot
                                                            .getDouble("combinedScore")
                                                            ?.toFloat()
                                                            ?: 0f,
                                            riskLevel = moleSnapshot.getString("riskLevel") ?: "",
                                            recommendation =
                                                    moleSnapshot.getString("recommendation") ?: "",
                                            imageUrl = moleSnapshot.getString("imageUrl") ?: "",
                                            createdAt =
                                                    moleSnapshot.getTimestamp("lastAnalysisDate")
                                                            ?: Timestamp.now(),
                                            analysisMetadata = emptyMap()
                                    )
                            Result.success(currentAnalysis)
                        } else {
                            Result.success(null)
                        }
                    } else {
                        Result.failure(Exception("Lunar no encontrado"))
                    }
                } catch (e: SecurityException) {
                    Log.e("MoleRepository", "Error de autenticación al obtener análisis actual", e)
                    Result.failure(e)
                } catch (e: IllegalArgumentException) {
                    Log.e("MoleRepository", "Error de validación al obtener análisis actual", e)
                    Result.failure(e)
                } catch (e: Exception) {
                    Log.e("MoleRepository", "Error al obtener análisis actual", e)
                    Result.failure(e)
                }
            }
    suspend fun updateMoleAnalysisMetadata(
            userId: String,
            moleId: String,
            metadata: Map<String, Any>
    ): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    if (userId.isBlank() || moleId.isBlank()) {
                        return@withContext Result.failure(
                                IllegalArgumentException("ID de usuario o lunar no válido")
                        )
                    }
                    if (metadata.isEmpty()) {
                        return@withContext Result.failure(
                                IllegalArgumentException("Los metadatos no pueden estar vacíos")
                        )
                    }
                    Log.d("MoleRepository", "Updating mole analysis metadata for mole: $moleId")
                    Log.d("MoleRepository", "Metadata to update: $metadata")
                    val moleRef =
                            firestore
                                    .collection(USERS_COLLECTION)
                                    .document(userId)
                                    .collection(MOLES_SUBCOLLECTION)
                                    .document(moleId)
                    val moleSnapshot = moleRef.get().await()
                    if (!moleSnapshot.exists()) {
                        return@withContext Result.failure(
                                Exception("Lunar no encontrado con ID: $moleId")
                        )
                    }
                    val existingMetadata =
                            moleSnapshot.get("analysisMetadata") as? Map<String, Any> ?: emptyMap()
                    val updatedMetadata = existingMetadata.toMutableMap().apply { putAll(metadata) }
                    val updates =
                            mapOf(
                                    "analysisMetadata" to updatedMetadata,
                                    "updatedAt" to Timestamp.now()
                            )
                    moleRef.update(updates).await()
                    Log.d("MoleRepository", "Successfully updated mole analysis metadata")
                    Result.success(Unit)
                } catch (e: SecurityException) {
                    Log.e("MoleRepository", "Error de autenticación al actualizar metadatos", e)
                    Result.failure(e)
                } catch (e: IllegalArgumentException) {
                    Log.e("MoleRepository", "Error de validación al actualizar metadatos", e)
                    Result.failure(e)
                } catch (e: Exception) {
                    Log.e("MoleRepository", "Error al actualizar metadatos de análisis", e)
                    Result.failure(e)
                }
            }
}
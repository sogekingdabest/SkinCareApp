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

    // Función para guardar un lunar con análisis estructurado
    suspend fun saveMoleWithAnalysis(
        context: Context,
        imageFile: File,
        title: String,
        description: String,
        bodyPart: String,
        bodyPartColorCode: String,
        analysisData: AnalysisData
    ): Result<MoleData> = withContext(Dispatchers.IO) {
        try {
            // 1. Obtener el usuario actual
            val currentUser = auth.currentUser ?: return@withContext Result.failure(
                Exception("Usuario no autenticado")
            )

            // 2. Comprimir la imagen
            val compressedImageFile = compressImage(context, imageFile)

            // 3. Guardar la imagen localmente y obtener la ruta
            val localImagePath = firebaseDataManager.saveImageLocally(
                context,
                compressedImageFile.absolutePath
            )

            // 4. Crear el objeto MoleData con análisis estructurado
            val moleId = UUID.randomUUID().toString()
            val moleData = MoleData(
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
                abcdeAsymmetry = analysisData.abcdeScores.asymmetryScore.toDouble(),
                abcdeBorder = analysisData.abcdeScores.borderScore.toDouble(),
                abcdeColor = analysisData.abcdeScores.colorScore.toDouble(),
                abcdeDiameter = analysisData.abcdeScores.diameterScore.toDouble(),
                abcdeTotalScore = analysisData.abcdeScores.totalScore.toDouble(),
                recommendation = analysisData.recommendation,
                analysisMetadata = analysisData.analysisMetadata
            )

            // 5. Guardar en Firestore como subcolección del usuario
            firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .collection(MOLES_SUBCOLLECTION)
                .document(moleId)
                .set(moleData.toMap())
                .await()

            // 6. Devolver el objeto con el ID asignado
            Result.success(moleData)

        } catch (e: Exception) {
            Log.e("MoleRepository", "Error al guardar el lunar con análisis", e)
            Result.failure(e)
        }
    }

    // Función auxiliar para comprimir imágenes
    private suspend fun compressImage(context: Context, imageFile: File): File {
        return Compressor.compress(context, imageFile) {
            resolution(1024, 1024)
            quality(80)
            format(Bitmap.CompressFormat.JPEG)
            size(512_000) // 500 KB máximo
        }
    }
    /**
     * Obtiene un lunar específico por su ID
     */
    suspend fun getMoleById(userId: String, moleId: String): Result<MoleData> = withContext(Dispatchers.IO) {
        try {
            val docSnapshot = firestore.collection(USERS_COLLECTION)
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

    /**
     * Obtiene todos los lunares de un usuario específico con optimizaciones
     */
    suspend fun getAllMolesForUser(userId: String): Result<List<MoleData>> = withContext(Dispatchers.IO) {
        try {
            // Usar consultas optimizadas con caché
            OptimizedFirebaseQueries()
            
            val querySnapshot = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(MOLES_SUBCOLLECTION)
                .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50) // Limitar resultados para mejor rendimiento
                .get()
                .await()

            val moles = querySnapshot.documents.mapNotNull { doc ->
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

    /**
     * Guarda un análisis asociado a un lunar específico Mueve el análisis actual al historial y
     * actualiza el lunar con el nuevo análisis
     */
    suspend fun saveAnalysisToMole(
        context: Context,
        moleId: String, 
        newAnalysis: AnalysisData,
        imageFile: File? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
            try {
                val currentUser =
                    auth.currentUser
                        ?: return@withContext Result.failure(
                            SecurityException("Usuario no autenticado")
                        )

                // Validar datos de entrada
                val validationResult =
                    AnalysisDataValidator.validateAnalysisData(newAnalysis)
                if (!validationResult.isValid) {
                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "Datos de análisis inválidos: ${validationResult.getErrorMessage()}"
                        )
                    )
                }


                // Log para debug
                Log.d(
                    "MoleRepository",
                    "Saving new analysis with metadata keys: ${newAnalysis.analysisMetadata.keys}"
                )

                // Procesar imagen si se proporciona una nueva (ANTES de la transacción)
                val processedImageUrl = if (imageFile != null) {
                    try {
                        // Comprimir la nueva imagen
                        val compressedImageFile = compressImage(context, imageFile)
                        // Guardar la imagen localmente
                        firebaseDataManager.saveImageLocally(
                            context,
                            compressedImageFile.absolutePath
                        )
                    } catch (e: Exception) {
                        Log.w("MoleRepository", "Error al procesar nueva imagen, usando la existente", e)
                        newAnalysis.imageUrl
                    }
                } else {
                    newAnalysis.imageUrl
                }

                // Ejecutar transacción para mover análisis actual al historial y actualizar lunar
                firestore
                    .runTransaction { transaction ->
                        val moleRef =
                            firestore
                                .collection(USERS_COLLECTION)
                                .document(currentUser.uid)
                                .collection(MOLES_SUBCOLLECTION)
                                .document(moleId)

                        // Obtener el lunar actual
                        val moleSnapshot = transaction.get(moleRef)
                        if (!moleSnapshot.exists()) {
                            throw Exception("Lunar no encontrado")
                        }

                        val currentCount = moleSnapshot.getLong("analysisCount") ?: 0L
                        val currentTimestamp = Timestamp.now()

                        // Si ya existe un análisis en el lunar, moverlo al historial
                        if (currentCount > 0L) {
                            // Crear AnalysisData del análisis actual para moverlo al historial
                            val currentAnalysisData =
                                AnalysisData(
                                    id = "${moleId}_analysis_${currentCount}",
                                    moleId = moleId,
                                    analysisResult = currentCount.toString(),
                                    aiProbability = moleSnapshot.getDouble("aiProbability")?.toFloat() ?: 0f,
                                    aiConfidence = moleSnapshot.getDouble("aiConfidence")?.toFloat() ?: 0f,
                                    abcdeScores = ABCDEScores(
                                        asymmetryScore = moleSnapshot.getDouble("abcdeAsymmetry")?.toFloat() ?: 0f,
                                        borderScore = moleSnapshot.getDouble("abcdeBorder")?.toFloat() ?: 0f,
                                        colorScore = moleSnapshot.getDouble("abcdeColor")?.toFloat() ?: 0f,
                                        diameterScore = moleSnapshot.getDouble("abcdeDiameter")?.toFloat() ?: 0f,
                                        evolutionScore = null,
                                        totalScore = moleSnapshot.getDouble("abcdeTotalScore")?.toFloat() ?: 0f
                                    ),
                                    combinedScore = moleSnapshot.getDouble("combinedScore")?.toFloat() ?: 0f,
                                    riskLevel = moleSnapshot.getString("riskLevel") ?: "",
                                    recommendation = moleSnapshot.getString("recommendation") ?: "",
                                    imageUrl = moleSnapshot.getString("imageUrl") ?: "",
                                    createdAt = moleSnapshot.getTimestamp("lastAnalysisDate") ?: currentTimestamp,
                                    analysisMetadata = moleSnapshot.get("analysisMetadata") as? Map<String, Any> ?: emptyMap()
                                )

                            // Log para debug
                            Log.d(
                                "MoleRepository",
                                "Moving current analysis to history with metadata keys: ${currentAnalysisData.analysisMetadata.keys}"
                            )

                            // Guardar análisis actual en el historial
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

                        // Actualizar el lunar con el nuevo análisis
                        val updates = mutableMapOf<String, Any>(
                            "analysisResult" to newAnalysis.analysisResult,
                            "aiProbability" to newAnalysis.aiProbability.toDouble(),
                            "aiConfidence" to newAnalysis.aiConfidence.toDouble(),
                            "riskLevel" to newAnalysis.riskLevel,
                            "combinedScore" to newAnalysis.combinedScore.toDouble(),
                            "abcdeAsymmetry" to newAnalysis.abcdeScores.asymmetryScore.toDouble(),
                            "abcdeBorder" to newAnalysis.abcdeScores.borderScore.toDouble(),
                            "abcdeColor" to newAnalysis.abcdeScores.colorScore.toDouble(),
                            "abcdeDiameter" to newAnalysis.abcdeScores.diameterScore.toDouble(),
                            "abcdeTotalScore" to newAnalysis.abcdeScores.totalScore.toDouble(),
                            "recommendation" to newAnalysis.recommendation,
                            "imageUrl" to processedImageUrl,
                            "analysisCount" to (currentCount + 1),
                            "lastAnalysisDate" to currentTimestamp,
                            "updatedAt" to currentTimestamp,
                            "analysisMetadata" to newAnalysis.analysisMetadata
                        )

                        // Actualizar descripción si está disponible en los metadatos del análisis
                        newAnalysis.analysisMetadata["description"]?.let { description ->
                            if (description is String && description.isNotBlank()) {
                                updates["description"] = description
                            }
                        }

                        // Si es el primer análisis, establecer también firstAnalysisDate
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

    /**
     * Recupera solo el historial de análisis anteriores de un lunar específico
     * NO incluye el análisis actual del lunar
     */
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

                // Obtener solo el historial de análisis anteriores desde la subcolección del lunar
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
                            Log.w("MoleRepository", "Error al parsear análisis ${doc.id}", e)
                            null
                        }
                    }

                // Ordenar por fecha descendente (más reciente primero)
                val sortedAnalyses = historicalAnalyses.sortedByDescending { it.createdAt }

                if (sortedAnalyses.isEmpty()) {
                    Log.i("MoleRepository", "No se encontraron análisis históricos para el lunar $moleId")
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

    /**
     * Obtiene el análisis actual de un lunar específico
     */
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

                    // Verificar si hay análisis disponible: ya sea por analysisResult o por analysisCount > 0
                    val hasAnalysisResult = analysisResult.isNotEmpty()
                    val hasStructuredAnalysis = analysisCount > 0

                    Log.d("MoleRepository", "getCurrentAnalysis - analysisResult: '$analysisResult'")
                    Log.d("MoleRepository", "getCurrentAnalysis - analysisCount: $analysisCount")
                    Log.d("MoleRepository", "getCurrentAnalysis - hasAnalysisResult: $hasAnalysisResult")
                    Log.d("MoleRepository", "getCurrentAnalysis - hasStructuredAnalysis: $hasStructuredAnalysis")

                    if (hasAnalysisResult || hasStructuredAnalysis) {
                        // Crear AnalysisData del análisis actual
                        // Si analysisResult está vacío pero hay datos estructurados, usar un valor por defecto
                        val effectiveAnalysisResult = if (analysisResult.isNotEmpty()) {
                            analysisResult
                        } else {
                            "Análisis estructurado disponible"
                        }

                        val currentAnalysis =
                            AnalysisData(
                                id = "${moleId}_current",
                                moleId = moleId,
                                analysisResult = effectiveAnalysisResult,
                                aiProbability = moleSnapshot.getDouble("aiProbability")?.toFloat() ?: 0f,
                                aiConfidence = moleSnapshot.getDouble("aiConfidence")?.toFloat() ?: 0f,
                                abcdeScores = ABCDEScores(
                                    asymmetryScore = moleSnapshot.getDouble("abcdeAsymmetry")?.toFloat() ?: 0f,
                                    borderScore = moleSnapshot.getDouble("abcdeBorder")?.toFloat() ?: 0f,
                                    colorScore = moleSnapshot.getDouble("abcdeColor")?.toFloat() ?: 0f,
                                    diameterScore = moleSnapshot.getDouble("abcdeDiameter")?.toFloat() ?: 0f,
                                    evolutionScore = null,
                                    totalScore = moleSnapshot.getDouble("abcdeTotalScore")?.toFloat() ?: 0f
                                ),
                                combinedScore = moleSnapshot.getDouble("combinedScore")?.toFloat() ?: 0f,
                                riskLevel = moleSnapshot.getString("riskLevel") ?: "",
                                recommendation = moleSnapshot.getString("recommendation") ?: "",
                                imageUrl = moleSnapshot.getString("imageUrl") ?: "",
                                createdAt = moleSnapshot.getTimestamp("lastAnalysisDate") ?: Timestamp.now(),
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
        }}

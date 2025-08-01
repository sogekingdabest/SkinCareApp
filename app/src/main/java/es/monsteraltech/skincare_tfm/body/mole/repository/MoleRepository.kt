package es.monsteraltech.skincare_tfm.body.mole.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
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
    private val ANALYSIS_SUBCOLLECTION = "mole_analysis"

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
                recommendation = analysisData.recommendation
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

    // Función para guardar un lunar con imagen (método legacy para compatibilidad)
    suspend fun saveMole(
        context: Context,
        imageFile: File,
        title: String,
        description: String,
        bodyPart: String,
        bodyPartColorCode: String,
        aiResult: String
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

            // 4. Crear el objeto MoleData
            val moleId = UUID.randomUUID().toString()
            val moleData = MoleData(
                id = moleId,
                title = title,
                description = description,
                bodyPart = bodyPart,
                bodyPartColorCode = bodyPartColorCode,
                imageUrl = localImagePath,
                aiResult = aiResult,
                userId = currentUser.uid,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
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
            Log.e("MoleRepository", "Error al guardar el lunar", e)
            Result.failure(e)
        }
    }

    // Función para obtener lunares por parte del cuerpo
    suspend fun getMolesByBodyPart(userId: String, bodyPartColorCode: String): Result<List<MoleData>> =
        withContext(Dispatchers.IO) {
            try {
                val querySnapshot = firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(MOLES_SUBCOLLECTION)
                    .whereEqualTo("bodyPartColorCode", bodyPartColorCode)
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .await()

                val moles = querySnapshot.documents.mapNotNull { doc ->
                    val mole = doc.toObject(MoleData::class.java)
                    mole?.copy(id = doc.id)
                }

                Result.success(moles)
            } catch (e: Exception) {
                Log.e("MoleRepository", "Error al obtener los lunares por parte del cuerpo", e)
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
            val optimizedQueries = es.monsteraltech.skincare_tfm.body.mole.performance.OptimizedFirebaseQueries()
            // Nota: LocalCacheManager requiere contexto, pero no está disponible en este nivel
            // Se puede implementar una versión que no requiera contexto o pasar el contexto como parámetro
            
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
     * Actualiza el contador de análisis de un lunar
     */
    suspend fun updateMoleAnalysisCount(moleId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser ?: return@withContext Result.failure(
                Exception("Usuario no autenticado")
            )

            val moleRef = firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .collection(MOLES_SUBCOLLECTION)
                .document(moleId)

            firestore.runTransaction { transaction ->
                val moleSnapshot = transaction.get(moleRef)
                if (!moleSnapshot.exists()) {
                    throw Exception("Lunar no encontrado")
                }

                val currentCount = moleSnapshot.getLong("analysisCount") ?: 0L
                val currentTimestamp = com.google.firebase.Timestamp.now()

                val updates = mutableMapOf<String, Any>(
                    "analysisCount" to (currentCount + 1),
                    "lastAnalysisDate" to currentTimestamp,
                    "updatedAt" to currentTimestamp
                )

                // Si es el primer análisis, establecer también firstAnalysisDate
                if (currentCount == 0L) {
                    updates["firstAnalysisDate"] = currentTimestamp
                }

                transaction.update(moleRef, updates)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("MoleRepository", "Error al actualizar contador de análisis", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene un lunar con su análisis más reciente
     */
    suspend fun getMoleWithLatestAnalysis(moleId: String): Result<es.monsteraltech.skincare_tfm.body.mole.model.MoleWithAnalysis> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser ?: return@withContext Result.failure(
                Exception("Usuario no autenticado")
            )

            // Obtener el lunar
            val moleResult = getMoleById(currentUser.uid, moleId)
            if (moleResult.isFailure) {
                return@withContext Result.failure(
                    moleResult.exceptionOrNull() ?: Exception("Error al obtener lunar")
                )
            }

            val mole = moleResult.getOrNull()!!

            // Obtener el análisis más reciente
            val analysisSnapshot = firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .collection(ANALYSIS_SUBCOLLECTION)
                .whereEqualTo("moleId", moleId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            val latestAnalysis = analysisSnapshot.documents.firstOrNull()?.let { doc ->
                try {
                    es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData.fromMap(doc.id, doc.data ?: emptyMap())
                } catch (e: Exception) {
                    Log.w("MoleRepository", "Error al parsear análisis más reciente", e)
                    null
                }
            }

            val moleWithAnalysis = es.monsteraltech.skincare_tfm.body.mole.model.MoleWithAnalysis(
                mole = mole,
                latestAnalysis = latestAnalysis
            )

            Result.success(moleWithAnalysis)
        } catch (e: Exception) {
            Log.e("MoleRepository", "Error al obtener lunar con análisis más reciente", e)
            Result.failure(e)
        }
    }

    /**
     * Guarda un análisis en la colección mole_analysis
     */
    suspend fun saveAnalysisToMole(analysisData: es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData): Result<String> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser ?: return@withContext Result.failure(
                Exception("Usuario no autenticado")
            )

            // Verificar que el lunar existe
            val moleExists = firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .collection(MOLES_SUBCOLLECTION)
                .document(analysisData.moleId)
                .get()
                .await()
                .exists()

            if (!moleExists) {
                return@withContext Result.failure(Exception("El lunar especificado no existe"))
            }

            // Guardar el análisis
            val analysisRef = firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .collection(ANALYSIS_SUBCOLLECTION)
                .document(analysisData.id)

            analysisRef.set(analysisData.toMap()).await()

            Result.success(analysisData.id)
        } catch (e: Exception) {
            Log.e("MoleRepository", "Error al guardar análisis", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene todos los análisis de un lunar específico con optimizaciones
     */
    suspend fun getAnalysisHistoryForMole(moleId: String): Result<List<es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData>> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser ?: return@withContext Result.failure(
                Exception("Usuario no autenticado")
            )

            // Usar consultas optimizadas
            val optimizedQueries = es.monsteraltech.skincare_tfm.body.mole.performance.OptimizedFirebaseQueries()
            val config = es.monsteraltech.skincare_tfm.body.mole.performance.OptimizedFirebaseQueries.QueryConfig(
                useCache = true,
                limit = 50 // Limitar para mejor rendimiento
            )
            
            val result = optimizedQueries.getAnalysisOptimized(
                moleId = moleId,
                config = config
            )
            
            if (result.isSuccess) {
                Result.success(result.getOrNull() ?: emptyList())
            } else {
                // Fallback a consulta tradicional si falla la optimizada
                val querySnapshot = firestore.collection(USERS_COLLECTION)
                    .document(currentUser.uid)
                    .collection(ANALYSIS_SUBCOLLECTION)
                    .whereEqualTo("moleId", moleId)
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .await()

                val analyses = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData.fromMap(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.w("MoleRepository", "Error al parsear análisis ${doc.id}", e)
                        null
                    }
                }

                Result.success(analyses)
            }
        } catch (e: Exception) {
            Log.e("MoleRepository", "Error al obtener histórico de análisis", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene un análisis específico por su ID
     */
    suspend fun getAnalysisById(analysisId: String): Result<es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser ?: return@withContext Result.failure(
                Exception("Usuario no autenticado")
            )

            val docSnapshot = firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .collection(ANALYSIS_SUBCOLLECTION)
                .document(analysisId)
                .get()
                .await()

            if (docSnapshot.exists()) {
                val analysis = es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData.fromMap(
                    docSnapshot.id, 
                    docSnapshot.data ?: emptyMap()
                )
                Result.success(analysis)
            } else {
                Result.failure(Exception("No se encontró el análisis con ID: $analysisId"))
            }
        } catch (e: Exception) {
            Log.e("MoleRepository", "Error al obtener análisis por ID", e)
            Result.failure(e)
        }
    }

    /**
     * Elimina un análisis específico
     */
    suspend fun deleteAnalysis(analysisId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser ?: return@withContext Result.failure(
                Exception("Usuario no autenticado")
            )

            // Obtener el análisis antes de eliminarlo para actualizar el contador del lunar
            val analysisResult = getAnalysisById(analysisId)
            if (analysisResult.isFailure) {
                return@withContext Result.failure(
                    analysisResult.exceptionOrNull() ?: Exception("Error al obtener análisis")
                )
            }

            val analysis = analysisResult.getOrNull()!!

            // Eliminar el análisis
            firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .collection(ANALYSIS_SUBCOLLECTION)
                .document(analysisId)
                .delete()
                .await()

            // Decrementar el contador del lunar
            decrementMoleAnalysisCount(analysis.moleId)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("MoleRepository", "Error al eliminar análisis", e)
            Result.failure(e)
        }
    }

    /**
     * Decrementa el contador de análisis de un lunar
     */
    private suspend fun decrementMoleAnalysisCount(moleId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser ?: return@withContext Result.failure(
                Exception("Usuario no autenticado")
            )

            val moleRef = firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .collection(MOLES_SUBCOLLECTION)
                .document(moleId)

            firestore.runTransaction { transaction ->
                val moleSnapshot = transaction.get(moleRef)
                if (!moleSnapshot.exists()) {
                    throw Exception("Lunar no encontrado")
                }

                val currentCount = moleSnapshot.getLong("analysisCount") ?: 0L
                val newCount = maxOf(0L, currentCount - 1)

                val updates = mutableMapOf<String, Any>(
                    "analysisCount" to newCount,
                    "updatedAt" to com.google.firebase.Timestamp.now()
                )

                // Si no quedan análisis, limpiar las fechas
                if (newCount == 0L) {
                    updates["lastAnalysisDate"] = FieldValue.delete()
                    updates["firstAnalysisDate"] = FieldValue.delete()
                } else {
                    // Recalcular la fecha del último análisis
                    // Esto requeriría una consulta adicional, por simplicidad mantenemos la fecha actual
                    // En una implementación más robusta, se podría hacer una consulta para obtener la fecha real
                }

                transaction.update(moleRef, updates)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("MoleRepository", "Error al decrementar contador de análisis", e)
            Result.failure(e)
        }
    }
}
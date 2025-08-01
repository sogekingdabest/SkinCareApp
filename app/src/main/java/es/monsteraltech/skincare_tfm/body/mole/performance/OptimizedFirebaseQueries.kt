package es.monsteraltech.skincare_tfm.body.mole.performance

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Consultas Firebase optimizadas para reducir tiempo de carga y uso de datos
 * Implementa estrategias de consulta eficientes, batch operations y caché
 */
class OptimizedFirebaseQueries {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val USERS_COLLECTION = "users"
        private const val MOLES_SUBCOLLECTION = "moles"
        private const val ANALYSIS_SUBCOLLECTION = "mole_analysis"
        private const val BATCH_SIZE = 10
        private const val MAX_QUERY_RESULTS = 100
    }

    /**
     * Configuración para consultas optimizadas
     */
    data class QueryConfig(
        val useCache: Boolean = true,
        val source: Source = Source.DEFAULT,
        val limit: Int = 20,
        val enableBatching: Boolean = true,
        val selectFields: List<String>? = null // Campos específicos a seleccionar
    )

    // ==================== CONSULTAS DE LUNARES OPTIMIZADAS ====================

    /**
     * Obtiene lunares con consulta optimizada y campos selectivos
     */
    suspend fun getMolesOptimized(
        userId: String? = null,
        bodyPartColorCode: String? = null,
        config: QueryConfig = QueryConfig()
    ): Result<List<MoleData>> = withContext(Dispatchers.IO) {
        try {
            val currentUser = userId ?: auth.currentUser?.uid 
                ?: return@withContext Result.failure(Exception("Usuario no autenticado"))

            var query: Query = firestore.collection(USERS_COLLECTION)
                .document(currentUser)
                .collection(MOLES_SUBCOLLECTION)

            // Aplicar filtros
            if (bodyPartColorCode != null) {
                query = query.whereEqualTo("bodyPartColorCode", bodyPartColorCode)
            }

            // Ordenar por fecha de actualización para obtener los más recientes primero
            query = query.orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(config.limit.toLong())

            // Ejecutar consulta con configuración de caché
            val querySnapshot = query.get(config.source).await()

            val moles = querySnapshot.documents.mapNotNull { doc ->
                try {
                    // Si se especifican campos selectivos, crear objeto parcial
                    if (config.selectFields != null) {
                        createPartialMoleData(doc.id, doc.data ?: emptyMap(), config.selectFields)
                    } else {
                        val mole = doc.toObject(MoleData::class.java)
                        mole?.copy(id = doc.id)
                    }
                } catch (e: Exception) {
                    Log.w("OptimizedFirebaseQueries", "Error al parsear lunar ${doc.id}", e)
                    null
                }
            }

            Result.success(moles)
        } catch (e: Exception) {
            Log.e("OptimizedFirebaseQueries", "Error en consulta optimizada de lunares", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene resumen de lunares (solo campos esenciales)
     */
    suspend fun getMolesSummary(
        userId: String? = null,
        limit: Int = 50
    ): Result<List<MoleData>> = withContext(Dispatchers.IO) {
        try {
            val currentUser = userId ?: auth.currentUser?.uid 
                ?: return@withContext Result.failure(Exception("Usuario no autenticado"))

            // Consulta solo campos esenciales para mejor rendimiento
            val query = firestore.collection(USERS_COLLECTION)
                .document(currentUser)
                .collection(MOLES_SUBCOLLECTION)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())

            val querySnapshot = query.get(Source.CACHE).await()

            val moles = querySnapshot.documents.mapNotNull { doc ->
                try {
                    createPartialMoleData(
                        doc.id, 
                        doc.data ?: emptyMap(),
                        listOf("title", "bodyPart", "imageUrl", "analysisCount", "lastAnalysisDate", "createdAt")
                    )
                } catch (e: Exception) {
                    Log.w("OptimizedFirebaseQueries", "Error al parsear resumen de lunar ${doc.id}", e)
                    null
                }
            }

            Result.success(moles)
        } catch (e: Exception) {
            Log.e("OptimizedFirebaseQueries", "Error en consulta de resumen de lunares", e)
            Result.failure(e)
        }
    }

    /**
     * Batch operation para obtener múltiples lunares por ID
     */
    suspend fun getMolesBatch(
        moleIds: List<String>,
        userId: String? = null
    ): Result<List<MoleData>> = withContext(Dispatchers.IO) {
        try {
            val currentUser = userId ?: auth.currentUser?.uid 
                ?: return@withContext Result.failure(Exception("Usuario no autenticado"))

            val moles = mutableListOf<MoleData>()
            
            // Procesar en lotes para evitar límites de Firestore
            moleIds.chunked(BATCH_SIZE).forEach { batch ->
                val batchResults = batch.map { moleId ->
                    firestore.collection(USERS_COLLECTION)
                        .document(currentUser)
                        .collection(MOLES_SUBCOLLECTION)
                        .document(moleId)
                        .get()
                }

                // Esperar todos los resultados del lote
                batchResults.forEach { task ->
                    try {
                        val doc = task.await()
                        if (doc.exists()) {
                            val mole = doc.toObject(MoleData::class.java)
                            mole?.let { moles.add(it.copy(id = doc.id)) }
                        }
                    } catch (e: Exception) {
                        Log.w("OptimizedFirebaseQueries", "Error al obtener lunar en lote", e)
                    }
                }
            }

            Result.success(moles)
        } catch (e: Exception) {
            Log.e("OptimizedFirebaseQueries", "Error en consulta batch de lunares", e)
            Result.failure(e)
        }
    }

    // ==================== CONSULTAS DE ANÁLISIS OPTIMIZADAS ====================

    /**
     * Obtiene análisis con consulta optimizada
     */
    suspend fun getAnalysisOptimized(
        moleId: String? = null,
        startDate: Timestamp? = null,
        endDate: Timestamp? = null,
        config: QueryConfig = QueryConfig()
    ): Result<List<AnalysisData>> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser?.uid 
                ?: return@withContext Result.failure(Exception("Usuario no autenticado"))

            var query: Query = firestore.collection(USERS_COLLECTION)
                .document(currentUser)
                .collection(ANALYSIS_SUBCOLLECTION)

            // Aplicar filtros
            if (moleId != null) {
                query = query.whereEqualTo("moleId", moleId)
            }

            if (startDate != null) {
                query = query.whereGreaterThanOrEqualTo("createdAt", startDate)
            }

            if (endDate != null) {
                query = query.whereLessThanOrEqualTo("createdAt", endDate)
            }

            // Ordenar por fecha de creación descendente
            query = query.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(config.limit.toLong())

            // Ejecutar consulta
            val querySnapshot = query.get(config.source).await()

            val analyses = querySnapshot.documents.mapNotNull { doc ->
                try {
                    if (config.selectFields != null) {
                        createPartialAnalysisData(doc.id, doc.data ?: emptyMap(), config.selectFields)
                    } else {
                        AnalysisData.fromMap(doc.id, doc.data ?: emptyMap())
                    }
                } catch (e: Exception) {
                    Log.w("OptimizedFirebaseQueries", "Error al parsear análisis ${doc.id}", e)
                    null
                }
            }

            Result.success(analyses)
        } catch (e: Exception) {
            Log.e("OptimizedFirebaseQueries", "Error en consulta optimizada de análisis", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene resumen de análisis recientes (solo campos esenciales)
     */
    suspend fun getRecentAnalysisSummary(
        limit: Int = 20,
        daysBack: Int = 30
    ): Result<List<AnalysisData>> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser?.uid 
                ?: return@withContext Result.failure(Exception("Usuario no autenticado"))

            val startDate = Timestamp(
                java.util.Date(System.currentTimeMillis() - (daysBack * 24 * 60 * 60 * 1000L))
            )

            val query = firestore.collection(USERS_COLLECTION)
                .document(currentUser)
                .collection(ANALYSIS_SUBCOLLECTION)
                .whereGreaterThanOrEqualTo("createdAt", startDate)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())

            val querySnapshot = query.get(Source.CACHE).await()

            val analyses = querySnapshot.documents.mapNotNull { doc ->
                try {
                    createPartialAnalysisData(
                        doc.id,
                        doc.data ?: emptyMap(),
                        listOf("moleId", "riskLevel", "aiConfidence", "createdAt", "imageUrl")
                    )
                } catch (e: Exception) {
                    Log.w("OptimizedFirebaseQueries", "Error al parsear resumen de análisis ${doc.id}", e)
                    null
                }
            }

            Result.success(analyses)
        } catch (e: Exception) {
            Log.e("OptimizedFirebaseQueries", "Error en consulta de resumen de análisis", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene estadísticas agregadas de análisis
     */
    suspend fun getAnalysisStats(
        moleId: String? = null,
        daysBack: Int = 90
    ): Result<AnalysisStats> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser?.uid 
                ?: return@withContext Result.failure(Exception("Usuario no autenticado"))

            val startDate = Timestamp(
                java.util.Date(System.currentTimeMillis() - (daysBack * 24 * 60 * 60 * 1000L))
            )

            var query: Query = firestore.collection(USERS_COLLECTION)
                .document(currentUser)
                .collection(ANALYSIS_SUBCOLLECTION)
                .whereGreaterThanOrEqualTo("createdAt", startDate)

            if (moleId != null) {
                query = query.whereEqualTo("moleId", moleId)
            }

            val querySnapshot = query.get(Source.CACHE).await()

            // Calcular estadísticas
            val analyses = querySnapshot.documents.mapNotNull { doc ->
                try {
                    createPartialAnalysisData(
                        doc.id,
                        doc.data ?: emptyMap(),
                        listOf("riskLevel", "aiConfidence", "combinedScore", "createdAt")
                    )
                } catch (e: Exception) {
                    null
                }
            }

            val stats = calculateAnalysisStats(analyses)
            Result.success(stats)

        } catch (e: Exception) {
            Log.e("OptimizedFirebaseQueries", "Error al obtener estadísticas de análisis", e)
            Result.failure(e)
        }
    }

    // ==================== OPERACIONES BATCH OPTIMIZADAS ====================

    /**
     * Actualización batch optimizada
     */
    suspend fun batchUpdateMoles(
        updates: Map<String, Map<String, Any>>,
        userId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUser = userId ?: auth.currentUser?.uid 
                ?: return@withContext Result.failure(Exception("Usuario no autenticado"))

            // Procesar en lotes de transacciones
            updates.entries.chunked(BATCH_SIZE).forEach { batch ->
                firestore.runTransaction { transaction ->
                    batch.forEach { (moleId, updateData) ->
                        val moleRef = firestore.collection(USERS_COLLECTION)
                            .document(currentUser)
                            .collection(MOLES_SUBCOLLECTION)
                            .document(moleId)
                        
                        transaction.update(moleRef, updateData)
                    }
                }.await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("OptimizedFirebaseQueries", "Error en actualización batch", e)
            Result.failure(e)
        }
    }

    /**
     * Eliminación batch optimizada
     */
    suspend fun batchDeleteAnalysis(
        analysisIds: List<String>,
        userId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUser = userId ?: auth.currentUser?.uid 
                ?: return@withContext Result.failure(Exception("Usuario no autenticado"))

            // Procesar en lotes
            analysisIds.chunked(BATCH_SIZE).forEach { batch ->
                firestore.runTransaction { transaction ->
                    batch.forEach { analysisId ->
                        val analysisRef = firestore.collection(USERS_COLLECTION)
                            .document(currentUser)
                            .collection(ANALYSIS_SUBCOLLECTION)
                            .document(analysisId)
                        
                        transaction.delete(analysisRef)
                    }
                }.await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("OptimizedFirebaseQueries", "Error en eliminación batch", e)
            Result.failure(e)
        }
    }

    // ==================== MÉTODOS AUXILIARES ====================

    /**
     * Crea un objeto MoleData parcial con solo los campos especificados
     */
    private fun createPartialMoleData(
        id: String,
        data: Map<String, Any>,
        fields: List<String>
    ): MoleData {
        return MoleData(
            id = id,
            title = if ("title" in fields) data["title"] as? String ?: "" else "",
            description = if ("description" in fields) data["description"] as? String ?: "" else "",
            bodyPart = if ("bodyPart" in fields) data["bodyPart"] as? String ?: "" else "",
            bodyPartColorCode = if ("bodyPartColorCode" in fields) data["bodyPartColorCode"] as? String ?: "" else "",
            imageUrl = if ("imageUrl" in fields) data["imageUrl"] as? String ?: "" else "",
            aiResult = if ("aiResult" in fields) data["aiResult"] as? String ?: "" else "",
            userId = if ("userId" in fields) data["userId"] as? String ?: "" else "",
            analysisCount = if ("analysisCount" in fields) (data["analysisCount"] as? Long)?.toInt() ?: 0 else 0,
            lastAnalysisDate = if ("lastAnalysisDate" in fields) data["lastAnalysisDate"] as? Timestamp else null,
            firstAnalysisDate = if ("firstAnalysisDate" in fields) data["firstAnalysisDate"] as? Timestamp else null,
            createdAt = if ("createdAt" in fields) data["createdAt"] as? Timestamp ?: Timestamp.now() else Timestamp.now(),
            updatedAt = if ("updatedAt" in fields) data["updatedAt"] as? Timestamp ?: Timestamp.now() else Timestamp.now()
        )
    }

    /**
     * Crea un objeto AnalysisData parcial con solo los campos especificados
     */
    private fun createPartialAnalysisData(
        id: String,
        data: Map<String, Any>,
        fields: List<String>
    ): AnalysisData {
        return AnalysisData(
            id = id,
            moleId = if ("moleId" in fields) data["moleId"] as? String ?: "" else "",
            analysisResult = if ("analysisResult" in fields) data["analysisResult"] as? String ?: "" else "",
            aiProbability = if ("aiProbability" in fields) (data["aiProbability"] as? Number)?.toFloat() ?: 0f else 0f,
            aiConfidence = if ("aiConfidence" in fields) (data["aiConfidence"] as? Number)?.toFloat() ?: 0f else 0f,
            combinedScore = if ("combinedScore" in fields) (data["combinedScore"] as? Number)?.toFloat() ?: 0f else 0f,
            riskLevel = if ("riskLevel" in fields) data["riskLevel"] as? String ?: "" else "",
            recommendation = if ("recommendation" in fields) data["recommendation"] as? String ?: "" else "",
            imageUrl = if ("imageUrl" in fields) data["imageUrl"] as? String ?: "" else "",
            createdAt = if ("createdAt" in fields) data["createdAt"] as? Timestamp ?: Timestamp.now() else Timestamp.now()
        )
    }

    /**
     * Calcula estadísticas de análisis
     */
    private fun calculateAnalysisStats(analyses: List<AnalysisData>): AnalysisStats {
        if (analyses.isEmpty()) {
            return AnalysisStats()
        }

        val totalAnalyses = analyses.size
        val riskLevelCounts = analyses.groupingBy { it.riskLevel }.eachCount()
        val avgConfidence = analyses.map { it.aiConfidence }.average().toFloat()
        val avgCombinedScore = analyses.map { it.combinedScore }.average().toFloat()

        return AnalysisStats(
            totalAnalyses = totalAnalyses,
            lowRiskCount = riskLevelCounts["LOW"] ?: 0,
            moderateRiskCount = riskLevelCounts["MODERATE"] ?: riskLevelCounts["MEDIO"] ?: 0,
            highRiskCount = riskLevelCounts["HIGH"] ?: 0,
            averageConfidence = avgConfidence,
            averageCombinedScore = avgCombinedScore
        )
    }

    /**
     * Estadísticas de análisis
     */
    data class AnalysisStats(
        val totalAnalyses: Int = 0,
        val lowRiskCount: Int = 0,
        val moderateRiskCount: Int = 0,
        val highRiskCount: Int = 0,
        val averageConfidence: Float = 0f,
        val averageCombinedScore: Float = 0f
    )
}
package es.monsteraltech.skincare_tfm.body.mole.performance
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
class OptimizedFirebaseQueries {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    companion object {
        private const val USERS_COLLECTION = "users"
        private const val MOLES_SUBCOLLECTION = "moles"
        private const val ANALYSIS_SUBCOLLECTION = "mole_analysis"
    }
    data class QueryConfig(
        val useCache: Boolean = true,
        val source: Source = Source.DEFAULT,
        val limit: Int = 20,
        val enableBatching: Boolean = true,
        val selectFields: List<String>? = null
    )
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
            if (bodyPartColorCode != null) {
                query = query.whereEqualTo("bodyPartColorCode", bodyPartColorCode)
            }
            query = query.orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(config.limit.toLong())
            val querySnapshot = query.get(config.source).await()
            val moles = querySnapshot.documents.mapNotNull { doc ->
                try {
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
            if (moleId != null) {
                query = query.whereEqualTo("moleId", moleId)
            }
            if (startDate != null) {
                query = query.whereGreaterThanOrEqualTo("createdAt", startDate)
            }
            if (endDate != null) {
                query = query.whereLessThanOrEqualTo("createdAt", endDate)
            }
            query = query.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(config.limit.toLong())
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
}
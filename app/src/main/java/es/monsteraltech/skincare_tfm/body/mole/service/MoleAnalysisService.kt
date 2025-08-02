package es.monsteraltech.skincare_tfm.body.mole.service

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import es.monsteraltech.skincare_tfm.body.mole.model.ABCDEScores
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.EvolutionComparison
import es.monsteraltech.skincare_tfm.body.mole.model.EvolutionSummary
import es.monsteraltech.skincare_tfm.body.mole.repository.MoleRepository
import es.monsteraltech.skincare_tfm.body.mole.validation.AnalysisDataSanitizer
import es.monsteraltech.skincare_tfm.body.mole.validation.AnalysisDataValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Servicio para gestión de análisis históricos de lunares
 * Implementa funcionalidades para guardar, recuperar y comparar análisis
 */
class MoleAnalysisService {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val moleRepository = MoleRepository()

    private val USERS_COLLECTION = "users"
    private val MOLES_SUBCOLLECTION = "moles"
    private val ANALYSIS_SUBCOLLECTION = "mole_analysis_historial"

    /**
     * Guarda un análisis asociado a un lunar específico
     * Mueve el análisis actual al historial y actualiza el lunar con el nuevo análisis
     */
    suspend fun saveAnalysisToMole(moleId: String, analysis: AnalysisData): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val currentUser = auth.currentUser ?: return@withContext Result.failure(
                    SecurityException("Usuario no autenticado")
                )

                // Validar datos de entrada
                val validationResult = AnalysisDataValidator.validateAnalysisData(analysis)
                if (!validationResult.isValid) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Datos de análisis inválidos: ${validationResult.getErrorMessage()}")
                    )
                }

                // Sanitizar datos
                val sanitizedAnalysis = AnalysisDataSanitizer.sanitizeAnalysisData(analysis.copy(moleId = moleId))

                // Ejecutar transacción para mover análisis actual al historial y actualizar lunar
                firestore.runTransaction { transaction ->
                    val moleRef = firestore.collection(USERS_COLLECTION)
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
                        val currentAnalysisData = AnalysisData(
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
                            analysisMetadata = emptyMap()
                        )

                        // Guardar análisis actual en el historial
                        val historialRef = firestore.collection(USERS_COLLECTION)
                            .document(currentUser.uid)
                            .collection(MOLES_SUBCOLLECTION)
                            .document(moleId)
                            .collection(ANALYSIS_SUBCOLLECTION)
                            .document(currentAnalysisData.id)

                        transaction.set(historialRef, currentAnalysisData.toMap())
                    }

                    // Actualizar el lunar con el nuevo análisis
                    val updates = mutableMapOf<String, Any>(
                        "analysisResult" to sanitizedAnalysis.analysisResult,
                        "aiProbability" to sanitizedAnalysis.aiProbability.toDouble(),
                        "aiConfidence" to sanitizedAnalysis.aiConfidence.toDouble(),
                        "riskLevel" to sanitizedAnalysis.riskLevel,
                        "combinedScore" to sanitizedAnalysis.combinedScore.toDouble(),
                        "abcdeAsymmetry" to sanitizedAnalysis.abcdeScores.asymmetryScore.toDouble(),
                        "abcdeBorder" to sanitizedAnalysis.abcdeScores.borderScore.toDouble(),
                        "abcdeColor" to sanitizedAnalysis.abcdeScores.colorScore.toDouble(),
                        "abcdeDiameter" to sanitizedAnalysis.abcdeScores.diameterScore.toDouble(),
                        "abcdeTotalScore" to sanitizedAnalysis.abcdeScores.totalScore.toDouble(),
                        "recommendation" to sanitizedAnalysis.recommendation,
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

            } catch (e: SecurityException) {
                Log.e("MoleAnalysisService", "Error de autenticación al guardar análisis", e)
                Result.failure(e)
            } catch (e: IllegalArgumentException) {
                Log.e("MoleAnalysisService", "Error de validación al guardar análisis", e)
                Result.failure(e)
            } catch (e: Exception) {
                Log.e("MoleAnalysisService", "Error al guardar análisis", e)
                Result.failure(e)
            }
        }

    /**
     * Recupera el histórico completo de análisis de un lunar específico
     * Incluye el análisis actual del lunar más el historial de análisis anteriores
     */
    suspend fun getAnalysisHistory(moleId: String): Result<List<AnalysisData>> =
        withContext(Dispatchers.IO) {
            try {
                val currentUser = auth.currentUser ?: return@withContext Result.failure(
                    SecurityException("Usuario no autenticado")
                )

                if (moleId.isBlank()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("ID de lunar no válido")
                    )
                }

                val analysisList = mutableListOf<AnalysisData>()

                // 1. Obtener el análisis actual del lunar
                val moleSnapshot = firestore.collection(USERS_COLLECTION)
                    .document(currentUser.uid)
                    .collection(MOLES_SUBCOLLECTION)
                    .document(moleId)
                    .get()
                    .await()

                if (moleSnapshot.exists()) {
                    val analysisResult = moleSnapshot.getString("analysisResult") ?: ""
                    if (analysisResult.isNotEmpty()) {
                        // Crear AnalysisData del análisis actual
                        val currentAnalysis = AnalysisData(
                            id = "${moleId}_current",
                            moleId = moleId,
                            analysisResult = analysisResult,
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
                        analysisList.add(currentAnalysis)
                    }
                }

                // 2. Obtener el historial de análisis anteriores
                val querySnapshot = firestore.collection(USERS_COLLECTION)
                    .document(currentUser.uid)
                    .collection(ANALYSIS_SUBCOLLECTION)
                    .whereEqualTo("moleId", moleId)
                    .get()
                    .await()

                val historicalAnalyses = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        AnalysisData.fromMap(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.w("MoleAnalysisService", "Error al parsear análisis ${doc.id}", e)
                        null
                    }
                }

                analysisList.addAll(historicalAnalyses)

                // 3. Ordenar por fecha descendente (más reciente primero)
                val sortedAnalyses = analysisList.sortedByDescending { it.createdAt }

                if (sortedAnalyses.isEmpty()) {
                    Log.i("MoleAnalysisService", "No se encontraron análisis para el lunar $moleId")
                }

                Result.success(sortedAnalyses)

            } catch (e: SecurityException) {
                Log.e("MoleAnalysisService", "Error de autenticación al obtener historial", e)
                Result.failure(e)
            } catch (e: IllegalArgumentException) {
                Log.e("MoleAnalysisService", "Error de validación al obtener historial", e)
                Result.failure(e)
            } catch (e: Exception) {
                Log.e("MoleAnalysisService", "Error al obtener historial de análisis", e)
                Result.failure(e)
            }
        }

    /**
     * Compara dos análisis consecutivos para calcular evolución
     */
    suspend fun compareAnalyses(current: AnalysisData, previous: AnalysisData): Result<EvolutionComparison> =
        withContext(Dispatchers.IO) {
            try {
                // Validar que ambos análisis pertenezcan al mismo lunar
                if (current.moleId != previous.moleId) {
                    return@withContext Result.failure(
                        Exception("Los análisis no pertenecen al mismo lunar")
                    )
                }

                // Validar datos de análisis
                val currentValidation = AnalysisDataValidator.validateAnalysisData(current)
                val previousValidation = AnalysisDataValidator.validateAnalysisData(previous)

                if (!currentValidation.isValid || !previousValidation.isValid) {
                    return@withContext Result.failure(
                        Exception("Datos de análisis inválidos para comparación")
                    )
                }

                val comparison = EvolutionComparison.create(current, previous)
                Result.success(comparison)

            } catch (e: Exception) {
                Log.e("MoleAnalysisService", "Error al comparar análisis", e)
                Result.failure(e)
            }
        }

    /**
     * Obtiene un resumen de evolución completo de un lunar específico
     */
    suspend fun getEvolutionSummary(moleId: String): Result<EvolutionSummary> =
        withContext(Dispatchers.IO) {
            try {
                // Obtener histórico completo
                val historyResult = getAnalysisHistory(moleId)
                if (historyResult.isFailure) {
                    return@withContext Result.failure(
                        historyResult.exceptionOrNull() ?: Exception("Error al obtener histórico")
                    )
                }

                val analyses = historyResult.getOrNull() ?: emptyList()
                val summary = EvolutionSummary.create(moleId, analyses)
                
                Result.success(summary)

            } catch (e: Exception) {
                Log.e("MoleAnalysisService", "Error al generar resumen de evolución", e)
                Result.failure(e)
            }
        }



}
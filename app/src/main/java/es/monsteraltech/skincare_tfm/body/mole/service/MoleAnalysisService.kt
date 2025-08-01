package es.monsteraltech.skincare_tfm.body.mole.service

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.EvolutionComparison
import es.monsteraltech.skincare_tfm.body.mole.model.EvolutionSummary
import es.monsteraltech.skincare_tfm.body.mole.repository.MoleRepository
import es.monsteraltech.skincare_tfm.body.mole.validation.AnalysisDataValidator
import es.monsteraltech.skincare_tfm.body.mole.validation.AnalysisDataSanitizer
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
    private val ANALYSIS_SUBCOLLECTION = "mole_analysis"

    /**
     * Guarda un análisis asociado a un lunar específico
     * Actualiza los contadores del lunar automáticamente
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

                // Guardar análisis en Firestore con timeout
                firestore.collection(USERS_COLLECTION)
                    .document(currentUser.uid)
                    .collection(ANALYSIS_SUBCOLLECTION)
                    .document(sanitizedAnalysis.id)
                    .set(sanitizedAnalysis.toMap())
                    .await()

                // Actualizar contadores del lunar
                updateMoleAnalysisCount(currentUser.uid, moleId)

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

                val querySnapshot = firestore.collection(USERS_COLLECTION)
                    .document(currentUser.uid)
                    .collection(ANALYSIS_SUBCOLLECTION)
                    .whereEqualTo("moleId", moleId)
                    .get()
                    .await()

                // Ordenar los resultados en memoria después de obtenerlos
                val analysisList = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        AnalysisData.fromMap(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.w("MoleAnalysisService", "Error al parsear análisis ${doc.id}", e)
                        null
                    }
                }.sortedByDescending { it.createdAt }

                if (analysisList.isEmpty() && querySnapshot.isEmpty) {
                    Log.i("MoleAnalysisService", "No se encontraron análisis para el lunar $moleId")
                }

                Result.success(analysisList)

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
    /**
     * Actualiza los contadores de análisis del lunar
     */
    private suspend fun updateMoleAnalysisCount(userId: String, moleId: String) {
        try {
            val moleRef = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(MOLES_SUBCOLLECTION)
                .document(moleId)

            firestore.runTransaction { transaction ->
                val moleSnapshot = transaction.get(moleRef)
                val currentCount = moleSnapshot.getLong("analysisCount") ?: 0L
                val currentTimestamp = Timestamp.now()

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

        } catch (e: Exception) {
            Log.w("MoleAnalysisService", "Error al actualizar contadores del lunar", e)
            // No lanzamos excepción para no fallar el guardado del análisis
        }
    }


}
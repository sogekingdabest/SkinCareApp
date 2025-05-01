package es.monsteraltech.skincare_tfm.body.mole.service

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import es.monsteraltech.skincare_tfm.body.mole.model.MoleAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Servicio para analizar imágenes de lunares y guardar los resultados en Firestore
 */
class MoleAnalysisService {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val analysisCollection = firestore.collection("mole_analysis")

    /**
     * Analiza la imagen de un lunar y guarda los resultados en Firestore
     * @param bitmap La imagen del lunar a analizar
     * @param moleId El ID del lunar (si ya está guardado en Firestore)
     * @return El resultado del análisis
     */
    suspend fun analyzeMoleImage(bitmap: Bitmap, moleId: String = ""): Result<MoleAnalysisResult> =
        withContext(Dispatchers.IO) {
            try {
                // 1. Verificar usuario autenticado
                val currentUser = auth.currentUser ?: return@withContext Result.failure(
                    Exception("Usuario no autenticado")
                )

                // 2. Realizar el análisis de la imagen (simulado)
                val analysisResult = performImageAnalysis(bitmap)

                // 3. Crear el objeto MoleAnalysisResult con el ID del lunar si existe
                val moleAnalysis = MoleAnalysisResult(
                    moleId = moleId,
                    analysisText = analysisResult.analysisDescription,
                    riskLevel = analysisResult.riskLevel,
                    confidence = analysisResult.confidence,
                    characteristics = analysisResult.characteristics,
                    recommendedAction = analysisResult.recommendation
                )

                // 4. Guardar en Firestore
                val docRef = if (moleId.isNotEmpty()) {
                    // Si ya existe el lunar, usamos su ID como parte del ID del análisis
                    val analysisId = "analysis_${moleId}_${UUID.randomUUID()}"
                    analysisCollection.document(analysisId).set(moleAnalysis.toMap()).await()
                    analysisCollection.document(analysisId)
                } else {
                    // Si es un nuevo lunar, creamos un documento con ID automático
                    analysisCollection.add(moleAnalysis.toMap()).await()
                }

                Log.d("MoleAnalysisService", "Análisis guardado con ID: ${docRef.id}")

                // 5. Devolver el resultado
                Result.success(moleAnalysis)

            } catch (e: Exception) {
                Log.e("MoleAnalysisService", "Error al analizar imagen", e)
                Result.failure(e)
            }
        }

    /**
     * Obtiene todos los análisis de un lunar específico
     * @param moleId El ID del lunar
     * @return Lista de análisis ordenados por fecha (más reciente primero)
     */
    suspend fun getMoleAnalysisHistory(moleId: String): Result<List<MoleAnalysisResult>> =
        withContext(Dispatchers.IO) {
            try {
                val querySnapshot = analysisCollection
                    .whereEqualTo("moleId", moleId)
                    .orderBy("analysisDate", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .await()

                val analysisList = querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(MoleAnalysisResult::class.java)
                }

                Result.success(analysisList)

            } catch (e: Exception) {
                Log.e("MoleAnalysisService", "Error al obtener historial de análisis", e)
                Result.failure(e)
            }
        }

    /**
     * Clase para los resultados simulados del análisis
     */
    private data class AnalysisSimulationResult(
        val analysisDescription: String,
        val riskLevel: String,
        val confidence: Double,
        val characteristics: Map<String, Any>,
        val recommendation: String
    )

    /**
     * Simula el análisis de la imagen (reemplazar con la implementación real de IA)
     */
    private fun performImageAnalysis(bitmap: Bitmap): AnalysisSimulationResult {
        // En un escenario real, aquí se enviaría la imagen a un servicio de IA
        // o se utilizaría un modelo local de ML Kit o TensorFlow Lite

        // Simulamos un análisis aleatorio con diferentes resultados
        val random = Random()
        val riskLevels = listOf("Bajo", "Medio", "Alto")
        val selectedRisk = riskLevels[random.nextInt(riskLevels.size)]

        // Características que varían según el nivel de riesgo
        val characteristics = mutableMapOf<String, Any>()

        // Simulamos características diferentes según el riesgo
        when (selectedRisk) {
            "Bajo" -> {
                characteristics["bordeRegular"] = true
                characteristics["colorUniforme"] = true
                characteristics["diametro"] = 4.2 // mm
                characteristics["simetria"] = "Alta"
            }
            "Medio" -> {
                characteristics["bordeRegular"] = random.nextBoolean()
                characteristics["colorUniforme"] = false
                characteristics["diametro"] = 5.8 // mm
                characteristics["simetria"] = "Media"
                characteristics["puntosSospechosos"] = random.nextInt(3) + 1
            }
            "Alto" -> {
                characteristics["bordeRegular"] = false
                characteristics["colorUniforme"] = false
                characteristics["diametro"] = 7.5 // mm
                characteristics["simetria"] = "Baja"
                characteristics["puntosSospechosos"] = random.nextInt(5) + 3
                characteristics["pigmentacionIrregular"] = true
            }
        }

        // Simular una descripción basada en las características
        val description = buildAnalysisDescription(selectedRisk, characteristics)

        // Simular una recomendación basada en el riesgo
        val recommendation = when (selectedRisk) {
            "Bajo" -> "Monitorizar regularmente. Seguimiento normal cada 6 meses."
            "Medio" -> "Se recomienda consultar con un dermatólogo en las próximas semanas."
            "Alto" -> "Consulte con un dermatólogo lo antes posible para una evaluación profesional."
            else -> "Se recomienda seguimiento normal."
        }

        // Simular nivel de confianza
        val confidence = when (selectedRisk) {
            "Bajo" -> 0.85 + (random.nextDouble() * 0.1)
            "Medio" -> 0.75 + (random.nextDouble() * 0.1)
            "Alto" -> 0.80 + (random.nextDouble() * 0.1)
            else -> 0.70 + (random.nextDouble() * 0.15)
        }

        return AnalysisSimulationResult(
            analysisDescription = description,
            riskLevel = selectedRisk,
            confidence = confidence,
            characteristics = characteristics,
            recommendation = recommendation
        )
    }

    /**
     * Construye una descripción textual basada en el análisis
     */
    private fun buildAnalysisDescription(riskLevel: String, characteristics: Map<String, Any>): String {
        val sb = StringBuilder()

        sb.append("Análisis de lunar: Nivel de riesgo $riskLevel.\n\n")

        // Añadir detalles sobre bordes
        if (characteristics["bordeRegular"] == true) {
            sb.append("• Bordes: Regulares y bien definidos.\n")
        } else {
            sb.append("• Bordes: Irregulares o mal definidos.\n")
        }

        // Añadir detalles sobre color
        if (characteristics["colorUniforme"] == true) {
            sb.append("• Color: Uniforme y consistente.\n")
        } else {
            sb.append("• Color: Variaciones o irregularidades en la pigmentación.\n")
        }

        // Añadir detalles sobre diámetro
        val diametro = characteristics["diametro"] as? Double ?: 0.0
        sb.append("• Diámetro: Aproximadamente ${String.format("%.1f", diametro)} mm.\n")

        // Añadir detalles sobre simetría
        val simetria = characteristics["simetria"] as? String ?: "No evaluada"
        sb.append("• Simetría: $simetria.\n")

        // Añadir detalles sobre puntos sospechosos si existen
        if (characteristics.containsKey("puntosSospechosos")) {
            val puntos = characteristics["puntosSospechosos"]
            sb.append("• Puntos irregulares: $puntos identificados.\n")
        }

        // Añadir detalles sobre pigmentación irregular si existe
        if (characteristics["pigmentacionIrregular"] == true) {
            sb.append("• Pigmentación: Irregular con variaciones significativas.\n")
        }

        return sb.toString()
    }
}
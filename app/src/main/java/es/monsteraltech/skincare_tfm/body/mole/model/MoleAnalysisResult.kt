package es.monsteraltech.skincare_tfm.body.mole.model

import com.google.firebase.Timestamp

/**
 * Modelo para los resultados del análisis de IA de un lunar
 */
data class MoleAnalysisResult(
    val id: String = "",                   // ID del analisis
    val analysisText: String = "",             // Texto completo del resultado del análisis
    val riskLevel: String = "",                // Nivel de riesgo estimado (bajo, medio, alto)
    val confidence: Double = 0.0,              // Nivel de confianza del análisis (0-1)
    val characteristics: Map<String, Any> = mapOf(), // Características específicas detectadas
    val recommendedAction: String = "",        // Acción recomendada basada en el análisis
    val analysisDate: Timestamp = Timestamp.now() // Fecha del análisis
) {
    // Constructor vacío requerido por Firestore
    constructor() : this("", "", "", 0.0, mapOf(), "")

    // Método para convertir a Map para Firestore
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "analysisText" to analysisText,
            "riskLevel" to riskLevel,
            "confidence" to confidence,
            "characteristics" to characteristics,
            "recommendedAction" to recommendedAction,
            "analysisDate" to analysisDate
        )
    }
}
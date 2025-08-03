package es.monsteraltech.skincare_tfm.body.mole.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class MoleData(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val bodyPart: String = "",
    val bodyPartColorCode: String = "",
    val imageUrl: String = "",
    val aiResult: String = "", // Mantener para compatibilidad con datos existentes
    val userId: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now(),
    // Nuevos campos para análisis históricos
    val analysisCount: Int = 0, // Contador de análisis realizados
    val lastAnalysisDate: Timestamp? = null, // Fecha del último análisis
    val firstAnalysisDate: Timestamp? = null, // Fecha del primer análisis
    
    // Nuevos campos estructurados para el análisis (reemplazan aiResult)
    val aiProbability: Double? = null, // Probabilidad de la IA (0.0 - 1.0)
    val aiConfidence: Double? = null, // Confianza de la IA (0.0 - 1.0)
    val riskLevel: String = "", // LOW, MODERATE, HIGH
    val combinedScore: Double? = null, // Score combinado (0.0 - 1.0)
    
    // Puntuaciones ABCDE individuales
    val abcdeAsymmetry: Double? = null, // Asimetría (0.0 - 2.0)
    val abcdeBorder: Double? = null, // Bordes (0.0 - 8.0)
    val abcdeColor: Double? = null, // Color (0.0 - 6.0)
    val abcdeDiameter: Double? = null, // Diámetro (0.0 - 5.0)
    val abcdeTotalScore: Double? = null, // Score total ABCDE
    
    // Recomendación y urgencia
    val recommendation: String = "", // Texto de recomendación
    val urgency: String = "", // MONITOR, CONSULT, URGENT
    
    // Metadatos del análisis (incluyendo valores del usuario)
    val analysisMetadata: Map<String, Any> = emptyMap()
) {
    // Constructor vacío requerido por Firestore
    constructor() : this("", "", "", "", "", "", "", "", Timestamp.now(), Timestamp.now(), 0, null, null,
        null, null, "", null, null, null, null, null, null, "", "", emptyMap())

    // Método para convertir a Map para Firestore
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "title" to title,
            "description" to description,
            "bodyPart" to bodyPart,
            "bodyPartColorCode" to bodyPartColorCode,
            "imageUrl" to imageUrl,
            "aiResult" to aiResult, // Mantener para compatibilidad
            "userId" to userId,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "analysisCount" to analysisCount,
            "riskLevel" to riskLevel,
            "recommendation" to recommendation,
            "urgency" to urgency
        )
        
        // Campos opcionales de fechas
        lastAnalysisDate?.let { map["lastAnalysisDate"] = it }
        firstAnalysisDate?.let { map["firstAnalysisDate"] = it }
        
        // Campos opcionales de análisis IA
        aiProbability?.let { map["aiProbability"] = it }
        aiConfidence?.let { map["aiConfidence"] = it }
        combinedScore?.let { map["combinedScore"] = it }
        
        // Campos opcionales ABCDE
        abcdeAsymmetry?.let { map["abcdeAsymmetry"] = it }
        abcdeBorder?.let { map["abcdeBorder"] = it }
        abcdeColor?.let { map["abcdeColor"] = it }
        abcdeDiameter?.let { map["abcdeDiameter"] = it }
        abcdeTotalScore?.let { map["abcdeTotalScore"] = it }
        
        // Metadatos del análisis
        if (analysisMetadata.isNotEmpty()) {
            map["analysisMetadata"] = analysisMetadata
        }
        
        return map
    }

    /**
     * Crea una instancia desde un Map de Firestore
     */
    companion object {
        fun fromMap(id: String, data: Map<String, Any>): MoleData {
            return MoleData(
                id = id,
                title = data["title"] as? String ?: "",
                description = data["description"] as? String ?: "",
                bodyPart = data["bodyPart"] as? String ?: "",
                bodyPartColorCode = data["bodyPartColorCode"] as? String ?: "",
                imageUrl = data["imageUrl"] as? String ?: "",
                aiResult = data["aiResult"] as? String ?: "", // Mantener para compatibilidad
                userId = data["userId"] as? String ?: "",
                createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now(),
                updatedAt = data["updatedAt"] as? Timestamp ?: Timestamp.now(),
                analysisCount = (data["analysisCount"] as? Number)?.toInt() ?: 0,
                lastAnalysisDate = data["lastAnalysisDate"] as? Timestamp,
                firstAnalysisDate = data["firstAnalysisDate"] as? Timestamp,
                
                // Nuevos campos estructurados
                aiProbability = (data["aiProbability"] as? Number)?.toDouble(),
                aiConfidence = (data["aiConfidence"] as? Number)?.toDouble(),
                riskLevel = data["riskLevel"] as? String ?: "",
                combinedScore = (data["combinedScore"] as? Number)?.toDouble(),
                
                // Campos ABCDE
                abcdeAsymmetry = (data["abcdeAsymmetry"] as? Number)?.toDouble(),
                abcdeBorder = (data["abcdeBorder"] as? Number)?.toDouble(),
                abcdeColor = (data["abcdeColor"] as? Number)?.toDouble(),
                abcdeDiameter = (data["abcdeDiameter"] as? Number)?.toDouble(),
                abcdeTotalScore = (data["abcdeTotalScore"] as? Number)?.toDouble(),
                
                // Recomendación y urgencia
                recommendation = data["recommendation"] as? String ?: "",
                urgency = data["urgency"] as? String ?: "",
                
                // Metadatos del análisis
                analysisMetadata = data["analysisMetadata"] as? Map<String, Any> ?: emptyMap()
            )
        }
    }
}
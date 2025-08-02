package es.monsteraltech.skincare_tfm.body.mole.model

import es.monsteraltech.skincare_tfm.analysis.ABCDEAnalyzerOpenCV
import java.io.Serializable

/**
 * Modelo de datos para almacenar las puntuaciones ABCDE de un análisis
 */
data class ABCDEScores(
    val asymmetryScore: Float = 0f,
    val borderScore: Float = 0f,
    val colorScore: Float = 0f,
    val diameterScore: Float = 0f,
    val evolutionScore: Float? = null,
    val totalScore: Float = 0f
) : Serializable {
    // Constructor vacío requerido por Firestore
    constructor() : this(0f, 0f, 0f, 0f, null, 0f)

    /**
     * Convierte el objeto a Map para almacenamiento en Firestore
     */
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "asymmetryScore" to asymmetryScore,
            "borderScore" to borderScore,
            "colorScore" to colorScore,
            "diameterScore" to diameterScore,
            "totalScore" to totalScore
        )
        
        evolutionScore?.let { map["evolutionScore"] = it }
        
        return map
    }

    /**
     * Crea una instancia desde un Map de Firestore
     */
    companion object {
        fun fromMap(data: Map<String, Any>): ABCDEScores {
            return ABCDEScores(
                asymmetryScore = (data["asymmetryScore"] as? Number)?.toFloat() ?: 0f,
                borderScore = (data["borderScore"] as? Number)?.toFloat() ?: 0f,
                colorScore = (data["colorScore"] as? Number)?.toFloat() ?: 0f,
                diameterScore = (data["diameterScore"] as? Number)?.toFloat() ?: 0f,
                evolutionScore = (data["evolutionScore"] as? Number)?.toFloat(),
                totalScore = (data["totalScore"] as? Number)?.toFloat() ?: 0f
            )
        }

        /**
         * Crea una instancia desde un resultado de ABCDEAnalyzer
         */
        fun fromABCDEResult(result: ABCDEAnalyzerOpenCV.ABCDEResult): ABCDEScores {
            return ABCDEScores(
                asymmetryScore = result.asymmetryScore,
                borderScore = result.borderScore,
                colorScore = result.colorScore,
                diameterScore = result.diameterScore,
                evolutionScore = result.evolutionScore,
                totalScore = result.totalScore
            )
        }
    }
}
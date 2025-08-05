package es.monsteraltech.skincare_tfm.body.mole.model
import es.monsteraltech.skincare_tfm.analysis.ABCDEAnalyzerOpenCV
import java.io.Serializable
data class ABCDEScores(
    val asymmetryScore: Float = 0f,
    val borderScore: Float = 0f,
    val colorScore: Float = 0f,
    val diameterScore: Float = 0f,
    val evolutionScore: Float? = null,
    val totalScore: Float = 0f
) : Serializable {
    constructor() : this(0f, 0f, 0f, 0f, null, 0f)
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
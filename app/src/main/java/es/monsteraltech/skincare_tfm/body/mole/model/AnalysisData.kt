package es.monsteraltech.skincare_tfm.body.mole.model
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import java.io.File
import java.io.Serializable
import java.util.UUID
data class AnalysisData(
    @DocumentId
    val id: String = UUID.randomUUID().toString(),
    val moleId: String = "",
    val description: String = "",
    val analysisResult: String = "",
    val aiProbability: Float = 0f,
    val aiConfidence: Float = 0f,
    val abcdeScores: ABCDEScores = ABCDEScores(),
    val combinedScore: Float = 0f,
    val riskLevel: String = "",
    val recommendation: String = "",
    val imageUrl: String = "",
    val imageData: File? = null,
    val createdAt: Timestamp = Timestamp.now(),
    val analysisMetadata: Map<String, Any> = emptyMap()
) : Serializable {
    constructor() : this("", "", "", "", 0f, 0f, ABCDEScores(), 0f, "", "", "", null, Timestamp.now(), emptyMap())
    fun toMap(): Map<String, Any> {
        return mapOf(
            "moleId" to moleId,
            "description" to description,
            "analysisResult" to analysisResult,
            "aiProbability" to aiProbability,
            "aiConfidence" to aiConfidence,
            "abcdeScores" to abcdeScores.toMap(),
            "combinedScore" to combinedScore,
            "riskLevel" to riskLevel,
            "recommendation" to recommendation,
            "imageUrl" to imageUrl,
            "createdAt" to createdAt,
            "analysisMetadata" to analysisMetadata
        )
    }
    companion object {
        fun fromMap(id: String, data: Map<String, Any>): AnalysisData {
            return AnalysisData(
                id = id,
                moleId = data["moleId"] as? String ?: "",
                description = data["description"] as? String ?: "",
                analysisResult = data["analysisResult"] as? String ?: "",
                aiProbability = (data["aiProbability"] as? Number)?.toFloat() ?: 0f,
                aiConfidence = (data["aiConfidence"] as? Number)?.toFloat() ?: 0f,
                abcdeScores = ABCDEScores.fromMap(data["abcdeScores"] as? Map<String, Any> ?: emptyMap()),
                combinedScore = (data["combinedScore"] as? Number)?.toFloat() ?: 0f,
                riskLevel = data["riskLevel"] as? String ?: "",
                recommendation = data["recommendation"] as? String ?: "",
                imageUrl = data["imageUrl"] as? String ?: "",
                createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now(),
                analysisMetadata = data["analysisMetadata"] as? Map<String, Any> ?: emptyMap()
            )
        }
    }
}
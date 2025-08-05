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
    val aiResult: String = "",
    val userId: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now(),
    val analysisCount: Int = 0,
    val lastAnalysisDate: Timestamp? = null,
    val firstAnalysisDate: Timestamp? = null,
    val aiProbability: Double? = null,
    val aiConfidence: Double? = null,
    val riskLevel: String = "",
    val combinedScore: Double? = null,
    val abcdeAsymmetry: Double? = null,
    val abcdeBorder: Double? = null,
    val abcdeColor: Double? = null,
    val abcdeDiameter: Double? = null,
    val abcdeTotalScore: Double? = null,
    val recommendation: String = "",
    val urgency: String = "",
    val analysisMetadata: Map<String, Any> = emptyMap()
) {
    constructor() : this("", "", "", "", "", "", "", "", Timestamp.now(), Timestamp.now(), 0, null, null,
        null, null, "", null, null, null, null, null, null, "", "", emptyMap())
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "title" to title,
            "description" to description,
            "bodyPart" to bodyPart,
            "bodyPartColorCode" to bodyPartColorCode,
            "imageUrl" to imageUrl,
            "aiResult" to aiResult,
            "userId" to userId,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "analysisCount" to analysisCount,
            "riskLevel" to riskLevel,
            "recommendation" to recommendation,
            "urgency" to urgency
        )
        lastAnalysisDate?.let { map["lastAnalysisDate"] = it }
        firstAnalysisDate?.let { map["firstAnalysisDate"] = it }
        aiProbability?.let { map["aiProbability"] = it }
        aiConfidence?.let { map["aiConfidence"] = it }
        combinedScore?.let { map["combinedScore"] = it }
        abcdeAsymmetry?.let { map["abcdeAsymmetry"] = it }
        abcdeBorder?.let { map["abcdeBorder"] = it }
        abcdeColor?.let { map["abcdeColor"] = it }
        abcdeDiameter?.let { map["abcdeDiameter"] = it }
        abcdeTotalScore?.let { map["abcdeTotalScore"] = it }
        if (analysisMetadata.isNotEmpty()) {
            map["analysisMetadata"] = analysisMetadata
        }
        return map
    }
    companion object {
        fun fromMap(id: String, data: Map<String, Any>): MoleData {
            return MoleData(
                id = id,
                title = data["title"] as? String ?: "",
                description = data["description"] as? String ?: "",
                bodyPart = data["bodyPart"] as? String ?: "",
                bodyPartColorCode = data["bodyPartColorCode"] as? String ?: "",
                imageUrl = data["imageUrl"] as? String ?: "",
                aiResult = data["aiResult"] as? String ?: "",
                userId = data["userId"] as? String ?: "",
                createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now(),
                updatedAt = data["updatedAt"] as? Timestamp ?: Timestamp.now(),
                analysisCount = (data["analysisCount"] as? Number)?.toInt() ?: 0,
                lastAnalysisDate = data["lastAnalysisDate"] as? Timestamp,
                firstAnalysisDate = data["firstAnalysisDate"] as? Timestamp,
                aiProbability = (data["aiProbability"] as? Number)?.toDouble(),
                aiConfidence = (data["aiConfidence"] as? Number)?.toDouble(),
                riskLevel = data["riskLevel"] as? String ?: "",
                combinedScore = (data["combinedScore"] as? Number)?.toDouble(),
                abcdeAsymmetry = (data["abcdeAsymmetry"] as? Number)?.toDouble(),
                abcdeBorder = (data["abcdeBorder"] as? Number)?.toDouble(),
                abcdeColor = (data["abcdeColor"] as? Number)?.toDouble(),
                abcdeDiameter = (data["abcdeDiameter"] as? Number)?.toDouble(),
                abcdeTotalScore = (data["abcdeTotalScore"] as? Number)?.toDouble(),
                recommendation = data["recommendation"] as? String ?: "",
                urgency = data["urgency"] as? String ?: "",
                analysisMetadata = data["analysisMetadata"] as? Map<String, Any> ?: emptyMap()
            )
        }
    }
}
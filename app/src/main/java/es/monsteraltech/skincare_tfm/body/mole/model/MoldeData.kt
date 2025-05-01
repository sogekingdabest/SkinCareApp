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
    val updatedAt: Timestamp = Timestamp.now()
) {
    // Constructor vacío requerido por Firestore
    constructor() : this("", "", "", "", "", "", "", "")

    // Método para convertir a Map para Firestore
    fun toMap(): Map<String, Any> {
        return mapOf(
            "title" to title,
            "description" to description,
            "bodyPart" to bodyPart,
            "bodyPartColorCode" to bodyPartColorCode,
            "imageUrl" to imageUrl,
            "aiResult" to aiResult,
            "userId" to userId,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
    }
}
package es.monsteraltech.skincare_tfm.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.util.Date
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class FirebaseDataManager {

    private val TAG = "FirebaseDataManager"
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance().reference

    // Referencias en Firestore y Storage
    private val USERS_COLLECTION = "users"
    private val MOLES_SUBCOLLECTION = "moles"
    private val ANALYSIS_SUBCOLLECTION = "mole_analysis"

    // Modelo de datos para un lunar
    data class MoleData(
        val id: String = UUID.randomUUID().toString(),
        val userId: String = "",
        val title: String = "",
        val description: String = "",
        val bodyPart: String = "",
        val bodyPartColorCode: String = "",
        val analysisResult: String = "",
        val imageUrl: String = "",
        val createdAt: Date = Date(),
        val updatedAt: Date = Date()
    )

    suspend fun saveImageLocally(context: Context, imagePath: String): String = suspendCoroutine { continuation ->
        try {
            val userId = auth.currentUser?.uid ?: throw IllegalStateException("Usuario no autenticado")
            val timestamp = Date().time
            val imageId = UUID.randomUUID().toString()
            val imageName = "mole_${imageId}_$timestamp.jpg"

            // Crear directorio para imágenes si no existe
            val directory = File(context.filesDir, "mole_images/$userId")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            // Copiar el archivo a almacenamiento interno
            val sourceFile = File(imagePath)
            val destinationFile = File(directory, imageName)
            sourceFile.copyTo(destinationFile, true)

            // Devolver la ruta relativa para guardar en Firestore
            val relativePath = "mole_images/$userId/$imageName"
            continuation.resume(relativePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar imagen localmente: ${e.message}")
            continuation.resumeWithException(e)
        }
    }

    // Función para obtener la ruta completa a partir de la relativa
    fun getFullImagePath(context: Context, relativePath: String): String {
        return File(context.filesDir, relativePath).absolutePath
    }

    // Obtener los lunares de un usuario
    suspend fun getMolesForBodyPart(bodyPartColorCode: String): List<MoleData> = suspendCoroutine { continuation ->
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("Usuario no autenticado")

        firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(MOLES_SUBCOLLECTION)
            .whereEqualTo("bodyPartColorCode", bodyPartColorCode)
            .get()
            .addOnSuccessListener { documents ->
                val moles = documents.toObjects(MoleData::class.java)
                continuation.resume(moles)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al obtener lunares: ${e.message}")
                continuation.resumeWithException(e)
            }
    }

}
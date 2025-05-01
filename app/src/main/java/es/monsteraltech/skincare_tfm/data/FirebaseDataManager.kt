package es.monsteraltech.skincare_tfm.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
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
    private val MOLES_COLLECTION = "moles"
    private val MOLE_IMAGES_PATH = "mole_images"

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

    // Función para subir imagen a Firebase Storage
    suspend fun uploadImage(imagePath: String, isMoleThumbnail: Boolean = true): String = suspendCoroutine { continuation ->
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("Usuario no autenticado")
        val timestamp = Date().time
        val imageId = UUID.randomUUID().toString()
        val imageName = if (isMoleThumbnail) "thumbnail_${imageId}_$timestamp.jpg" else "full_${imageId}_$timestamp.jpg"
        val imagesRef = storage.child("$MOLE_IMAGES_PATH/$userId/$imageName")

        // Cargar el archivo de imagen
        val bitmap = BitmapFactory.decodeFile(imagePath)

        // Comprimir la imagen si es un thumbnail
        val compressedBitmap = if (isMoleThumbnail) {
            compressBitmap(bitmap, 480, 80) // Resolución máxima de 480px, calidad 80%
        } else {
            compressBitmap(bitmap, 1280, 90) // Resolución máxima de 1280px, calidad 90%
        }

        // Convertir bitmap a bytes
        val baos = ByteArrayOutputStream()
        compressedBitmap.compress(Bitmap.CompressFormat.JPEG, if (isMoleThumbnail) 80 else 90, baos)
        val data = baos.toByteArray()

        // Subir imagen
        val uploadTask = imagesRef.putBytes(data)

        uploadTask
            .addOnSuccessListener {
                // Obtener URL de descarga
                imagesRef.downloadUrl
                    .addOnSuccessListener { uri ->
                        continuation.resume(uri.toString())
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Error al obtener URL de descarga: ${exception.message}")
                        continuation.resumeWithException(exception)
                    }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al subir imagen: ${exception.message}")
                continuation.resumeWithException(exception)
            }
    }

    // Función para guardar datos de un lunar en Firestore
    suspend fun saveMoleData(
        moleTitle: String,
        moleDescription: String,
        bodyPart: String,
        bodyPartColorCode: String?,
        imageUrl: String,
        analysisResult: String
    ): MoleData = suspendCoroutine { continuation ->
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("Usuario no autenticado")

        val moleData = MoleData(
            userId = userId,
            title = moleTitle,
            description = moleDescription,
            bodyPart = bodyPart,
            bodyPartColorCode = bodyPartColorCode ?: "",
            imageUrl = imageUrl,
            analysisResult = analysisResult
        )

        firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(MOLES_COLLECTION)
            .document(moleData.id)
            .set(moleData)
            .addOnSuccessListener {
                Log.d(TAG, "Lunar guardado correctamente con ID: ${moleData.id}")
                continuation.resume(moleData)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al guardar lunar: ${e.message}")
                continuation.resumeWithException(e)
            }
    }

    // Obtener los lunares de un usuario
    suspend fun getMolesForBodyPart(bodyPartColorCode: String): List<MoleData> = suspendCoroutine { continuation ->
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("Usuario no autenticado")

        firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(MOLES_COLLECTION)
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

    // Función auxiliar para comprimir imágenes manteniendo el aspect ratio
    private fun compressBitmap(originalBitmap: Bitmap, maxDimension: Int, quality: Int): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height

        // Si la imagen ya es más pequeña, la devolvemos tal cual
        if (width <= maxDimension && height <= maxDimension) {
            return originalBitmap
        }

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
    }
}
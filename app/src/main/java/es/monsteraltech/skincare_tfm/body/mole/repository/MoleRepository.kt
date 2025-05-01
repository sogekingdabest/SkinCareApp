package es.monsteraltech.skincare_tfm.body.mole.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MoleRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val molesCollection = firestore.collection("moles")
    private val imagesRef = storage.reference.child("mole_images")

    // Función para guardar un lunar con imagen
    suspend fun saveMole(
        context: Context,
        imageFile: File,
        title: String,
        description: String,
        bodyPart: String,
        bodyPartColorCode: String,
        aiResult: String
    ): Result<MoleData> = withContext(Dispatchers.IO) {
        try {
            // 1. Obtener el usuario actual
            val currentUser = auth.currentUser ?: return@withContext Result.failure(
                Exception("Usuario no autenticado")
            )

            // 2. Comprimir la imagen
            val compressedImageFile = compressImage(context, imageFile)

            // 3. Subir la imagen a Storage
            val imageUrl = uploadImage(compressedImageFile)

            // 4. Crear el objeto MoleData
            val moleData = MoleData(
                title = title,
                description = description,
                bodyPart = bodyPart,
                bodyPartColorCode = bodyPartColorCode,
                imageUrl = imageUrl,
                aiResult = aiResult,
                userId = currentUser.uid,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )

            // 5. Guardar en Firestore
            val documentRef = molesCollection.add(moleData.toMap()).await()

            // 6. Devolver el objeto con el ID asignado
            val savedMole = moleData.copy(id = documentRef.id)
            Result.success(savedMole)

        } catch (e: Exception) {
            Log.e("MoleRepository", "Error al guardar el lunar", e)
            Result.failure(e)
        }
    }

    // Función para obtener todos los lunares de un usuario
    suspend fun getMolesByUser(userId: String): Result<List<MoleData>> = withContext(Dispatchers.IO) {
        try {
            val querySnapshot = molesCollection
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            val moles = querySnapshot.documents.mapNotNull { doc ->
                val mole = doc.toObject(MoleData::class.java)
                mole?.copy(id = doc.id)
            }

            Result.success(moles)
        } catch (e: Exception) {
            Log.e("MoleRepository", "Error al obtener los lunares", e)
            Result.failure(e)
        }
    }

    // Función para obtener lunares por parte del cuerpo
    suspend fun getMolesByBodyPart(userId: String, bodyPartColorCode: String): Result<List<MoleData>> =
        withContext(Dispatchers.IO) {
            try {
                val querySnapshot = molesCollection
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("bodyPartColorCode", bodyPartColorCode)
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .await()

                val moles = querySnapshot.documents.mapNotNull { doc ->
                    val mole = doc.toObject(MoleData::class.java)
                    mole?.copy(id = doc.id)
                }

                Result.success(moles)
            } catch (e: Exception) {
                Log.e("MoleRepository", "Error al obtener los lunares por parte del cuerpo", e)
                Result.failure(e)
            }
        }

    // Función para actualizar un lunar existente
    suspend fun updateMole(moleData: MoleData): Result<MoleData> = withContext(Dispatchers.IO) {
        try {
            val updateData = moleData.copy(updatedAt = Timestamp.now())
            molesCollection.document(moleData.id).update(updateData.toMap()).await()
            Result.success(updateData)
        } catch (e: Exception) {
            Log.e("MoleRepository", "Error al actualizar el lunar", e)
            Result.failure(e)
        }
    }

    // Función para eliminar un lunar
    suspend fun deleteMole(moleId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Obtener datos del lunar para eliminar la imagen también
            val moleDoc = molesCollection.document(moleId).get().await()
            val mole = moleDoc.toObject(MoleData::class.java)

            // Eliminar documento de Firestore
            molesCollection.document(moleId).delete().await()

            // Eliminar imagen de Storage si existe
            mole?.imageUrl?.let { url ->
                if (url.isNotEmpty()) {
                    try {
                        val storageRef = storage.getReferenceFromUrl(url)
                        storageRef.delete().await()
                    } catch (e: Exception) {
                        Log.w("MoleRepository", "Error al eliminar imagen", e)
                    }
                }
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e("MoleRepository", "Error al eliminar el lunar", e)
            Result.failure(e)
        }
    }

    // Función auxiliar para comprimir imágenes
    private suspend fun compressImage(context: Context, imageFile: File): File {
        return Compressor.compress(context, imageFile) {
            resolution(1024, 1024)
            quality(80)
            format(Bitmap.CompressFormat.JPEG)
            size(512_000) // 500 KB máximo
        }
    }

    // Función auxiliar para subir imágenes a Firebase Storage
    private suspend fun uploadImage(imageFile: File): String {
        val fileName = "mole_${UUID.randomUUID()}.jpg"
        val imageRef = imagesRef.child(fileName)

        val uploadTask = imageRef.putFile(Uri.fromFile(imageFile)).await()
        return uploadTask.storage.downloadUrl.await().toString()
    }

    /**
     * Obtiene un lunar específico por su ID
     */
    suspend fun getMoleById(moleId: String): Result<MoleData> = withContext(Dispatchers.IO) {
        try {
            val docSnapshot = molesCollection.document(moleId).get().await()

            if (docSnapshot.exists()) {
                val mole = docSnapshot.toObject(MoleData::class.java)
                if (mole != null) {
                    Result.success(mole.copy(id = docSnapshot.id))
                } else {
                    Result.failure(Exception("Error al convertir el documento"))
                }
            } else {
                Result.failure(Exception("No se encontró el lunar con ID: $moleId"))
            }
        } catch (e: Exception) {
            Log.e("MoleRepository", "Error al obtener el lunar por ID", e)
            Result.failure(e)
        }
    }
}
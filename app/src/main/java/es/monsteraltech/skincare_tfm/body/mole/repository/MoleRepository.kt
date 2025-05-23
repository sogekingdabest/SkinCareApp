package es.monsteraltech.skincare_tfm.body.mole.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
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
    private val auth = FirebaseAuth.getInstance()
    private val firebaseDataManager = FirebaseDataManager()

    private val USERS_COLLECTION = "users"
    private val MOLES_SUBCOLLECTION = "moles"
    private val ANALYSIS_SUBCOLLECTION = "mole_analysis"

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

            // 3. Guardar la imagen localmente y obtener la ruta
            val localImagePath = firebaseDataManager.saveImageLocally(
                context,
                compressedImageFile.absolutePath
            )

            // 4. Crear el objeto MoleData
            val moleId = UUID.randomUUID().toString()
            val moleData = MoleData(
                id = moleId,
                title = title,
                description = description,
                bodyPart = bodyPart,
                bodyPartColorCode = bodyPartColorCode,
                imageUrl = localImagePath,
                aiResult = aiResult,
                userId = currentUser.uid,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )

            // 5. Guardar en Firestore como subcolección del usuario
            firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .collection(MOLES_SUBCOLLECTION)
                .document(moleId)
                .set(moleData.toMap())
                .await()

            // 6. Devolver el objeto con el ID asignado
            Result.success(moleData)

        } catch (e: Exception) {
            Log.e("MoleRepository", "Error al guardar el lunar", e)
            Result.failure(e)
        }
    }

    // Función para obtener lunares por parte del cuerpo
    suspend fun getMolesByBodyPart(userId: String, bodyPartColorCode: String): Result<List<MoleData>> =
        withContext(Dispatchers.IO) {
            try {
                val querySnapshot = firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(MOLES_SUBCOLLECTION)
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

    // Función auxiliar para comprimir imágenes
    private suspend fun compressImage(context: Context, imageFile: File): File {
        return Compressor.compress(context, imageFile) {
            resolution(1024, 1024)
            quality(80)
            format(Bitmap.CompressFormat.JPEG)
            size(512_000) // 500 KB máximo
        }
    }
    /**
     * Obtiene un lunar específico por su ID
     */
    suspend fun getMoleById(userId: String, moleId: String): Result<MoleData> = withContext(Dispatchers.IO) {
        try {
            val docSnapshot = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(MOLES_SUBCOLLECTION)
                .document(moleId)
                .get()
                .await()

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
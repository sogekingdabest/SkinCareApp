package es.monsteraltech.skincare_tfm.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import es.monsteraltech.skincare_tfm.account.AccountSettings
import es.monsteraltech.skincare_tfm.account.UserInfo
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
    private val USER_SETTINGS_SUBCOLLECTION = "settings"

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

    // ========== FUNCIONES DE GESTIÓN DE PERFIL DE USUARIO ==========

    /**
     * Obtiene la información completa del usuario actual desde Firebase Auth
     * @return UserInfo con todos los datos del usuario o null si no está autenticado
     */
    suspend fun getCurrentUserProfile(): UserInfo? = suspendCoroutine { continuation ->
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                continuation.resume(null)
                return@suspendCoroutine
            }

            val userInfo = UserInfo(
                uid = currentUser.uid,
                displayName = currentUser.displayName,
                email = currentUser.email,
                photoUrl = currentUser.photoUrl?.toString(),
                emailVerified = currentUser.isEmailVerified,
                createdAt = currentUser.metadata?.creationTimestamp?.let { Date(it) },
                lastSignIn = currentUser.metadata?.lastSignInTimestamp?.let { Date(it) }
            )

            continuation.resume(userInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener perfil de usuario: ${e.message}")
            continuation.resumeWithException(e)
        }
    }

    /**
     * Actualiza las configuraciones de usuario en Firestore
     * @param settings Configuraciones de cuenta a guardar
     */
    suspend fun updateUserSettings(settings: AccountSettings): Boolean = suspendCoroutine { continuation ->
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "Usuario no autenticado para actualizar configuraciones")
            continuation.resume(false)
            return@suspendCoroutine
        }

        firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(USER_SETTINGS_SUBCOLLECTION)
            .document("account_settings")
            .set(settings)
            .addOnSuccessListener {
                Log.d(TAG, "Configuraciones de usuario actualizadas exitosamente")
                continuation.resume(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al actualizar configuraciones de usuario: ${e.message}")
                continuation.resumeWithException(e)
            }
    }

    /**
     * Obtiene las configuraciones de usuario desde Firestore
     * @return AccountSettings del usuario o configuraciones por defecto si no existen
     */
    suspend fun getUserSettings(): AccountSettings = suspendCoroutine { continuation ->
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "Usuario no autenticado para obtener configuraciones")
            continuation.resume(AccountSettings())
            return@suspendCoroutine
        }

        firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(USER_SETTINGS_SUBCOLLECTION)
            .document("account_settings")
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val settings = document.toObject(AccountSettings::class.java)
                    continuation.resume(settings ?: AccountSettings(userId = userId))
                } else {
                    // Si no existen configuraciones, crear unas por defecto
                    val defaultSettings = AccountSettings(userId = userId)
                    continuation.resume(defaultSettings)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al obtener configuraciones de usuario: ${e.message}")
                continuation.resumeWithException(e)
            }
    }

    /**
     * Cierra la sesión del usuario actual
     */
    fun signOut() {
        try {
            auth.signOut()
            Log.d(TAG, "Usuario desconectado exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar sesión: ${e.message}")
        }
    }

}
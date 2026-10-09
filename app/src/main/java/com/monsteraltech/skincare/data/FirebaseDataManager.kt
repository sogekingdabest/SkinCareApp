package com.monsteraltech.skincare.data
import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.monsteraltech.skincare.account.AccountSettings
import com.monsteraltech.skincare.account.UserInfo
import java.io.File
import java.util.Date
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
class FirebaseDataManager {
    private val TAG = "FirebaseDataManager"
    private val auth = FirebaseAuth.getInstance()
    private val gson = Gson()
    private val settingsPreferences by lazy {
        auth.app.applicationContext.getSharedPreferences(
            "private_account_settings",
            Context.MODE_PRIVATE
        )
    }

    suspend fun saveImageLocally(context: Context, imagePath: String, userId: String): String = suspendCoroutine { continuation ->
        try {
            require(userId.isNotBlank()) { "Perfil local no disponible" }
            val timestamp = Date().time
            val imageId = UUID.randomUUID().toString()
            val imageName = "mole_${imageId}_$timestamp.jpg"
            val directory = File(context.noBackupFilesDir, "mole_images/$userId")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val sourceFile = File(imagePath)
            val destinationFile = File(directory, imageName)
            sourceFile.copyTo(destinationFile, true)
            val relativePath = "mole_images/$userId/$imageName"
            continuation.resume(relativePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar la imagen localmente: ${e.message}")
            continuation.resumeWithException(e)
        }
    }

    fun getFullImagePath(context: Context, relativePath: String): String {
        val privateFile = File(context.noBackupFilesDir, relativePath)
        if (privateFile.exists()) return privateFile.absolutePath

        // Migra de forma local las imágenes creadas por versiones anteriores.
        val legacyFile = File(context.filesDir, relativePath)
        if (legacyFile.exists()) {
            runCatching {
                privateFile.parentFile?.mkdirs()
                legacyFile.copyTo(privateFile, overwrite = true)
                legacyFile.delete()
            }
            if (privateFile.exists()) return privateFile.absolutePath
        }
        return privateFile.absolutePath
    }
    suspend fun getCurrentUserProfile(): UserInfo? = suspendCoroutine { continuation ->
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                continuation.resume(null)
                return@suspendCoroutine
            }
            val providerIds = currentUser.providerData.map { it.providerId }
            val isGoogleUser = providerIds.contains("google.com")
            Log.d(TAG, "Proveedores de autenticación del usuario: $providerIds")
            Log.d(TAG, "¿Es usuario de Google?: $isGoogleUser")
            val userInfo = UserInfo(
                uid = currentUser.uid,
                displayName = currentUser.displayName,
                email = currentUser.email,
                photoUrl = currentUser.photoUrl?.toString(),
                emailVerified = currentUser.isEmailVerified,
                createdAt = currentUser.metadata?.creationTimestamp?.let { Date(it) },
                lastSignIn = currentUser.metadata?.lastSignInTimestamp?.let { Date(it) },
                providerIds = providerIds,
                isGoogleUser = isGoogleUser
            )
            continuation.resume(userInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener perfil de usuario: ${e.message}")
            continuation.resumeWithException(e)
        }
    }
    suspend fun updateUserSettings(settings: AccountSettings, userId: String): Boolean {
        if (userId.isBlank()) return false
        val localSettings = settings.copy(userId = userId, autoBackup = false)
        return settingsPreferences.edit()
            .putString(settingsKey(userId), gson.toJson(localSettings))
            .commit()
    }

    suspend fun getUserSettings(userId: String): AccountSettings {
        if (userId.isBlank()) return AccountSettings(autoBackup = false)
        val json = settingsPreferences.getString(settingsKey(userId), null)
            ?: return AccountSettings(userId = userId, autoBackup = false)
        return runCatching {
            gson.fromJson(json, AccountSettings::class.java)
                .copy(userId = userId, autoBackup = false)
        }.getOrElse {
            Log.w(TAG, "Configuración local inválida; se usarán valores por defecto", it)
            AccountSettings(userId = userId, autoBackup = false)
        }
    }

    suspend fun initializeUserSettings(userId: String): Boolean {
        if (userId.isBlank()) return false
        if (settingsPreferences.contains(settingsKey(userId))) return true
        return settingsPreferences.edit()
            .putString(
                settingsKey(userId),
                gson.toJson(AccountSettings(userId = userId, autoBackup = false))
            )
            .commit()
    }

    private fun settingsKey(userId: String) = "account_settings_$userId"
    fun signOut() {
        try {
            auth.signOut()
            Log.d(TAG, "Usuario desconectado exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar sesión: ${e.message}")
        }
    }
}

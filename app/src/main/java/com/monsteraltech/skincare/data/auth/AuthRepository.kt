package com.monsteraltech.skincare.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import com.monsteraltech.skincare.data.FirebaseDataManager
import com.monsteraltech.skincare.data.SessionManager
import com.monsteraltech.skincare.data.identity.ActiveProfile
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Punto único de acceso a la autenticación: Firebase Auth, persistencia de
 * sesión e inicialización de datos del usuario. Las Activities/ViewModels no
 * hablan directamente con FirebaseAuth.
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val firebaseAuth: FirebaseAuth,
    private val sessionManager: SessionManager,
    private val firebaseDataManager: FirebaseDataManager,
    private val activeProfile: ActiveProfile
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    sealed class RegisterError(message: String) : Exception(message) {
        class EmailAlreadyInUse : RegisterError("El email ya está registrado")
        class Unknown(cause: String?) : RegisterError(cause ?: "Error desconocido")
    }

    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(IllegalStateException("Usuario nulo tras autenticación"))
            finalizeSignIn(user)
            Result.success(user)
        } catch (e: Exception) {
            Log.w(TAG, "Error en login con email: ${e.message}")
            Result.failure(e)
        }
    }

    /** Autentica en Firebase con el idToken obtenido de Credential Manager. */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user ?: return Result.failure(IllegalStateException("Usuario nulo tras autenticación"))
            finalizeSignIn(user)
            Result.success(user)
        } catch (e: Exception) {
            Log.w(TAG, "Error en login con Google: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun registerWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(RegisterError.Unknown("Usuario nulo tras registro"))
            finalizeSignIn(user)
            Result.success(user)
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(RegisterError.EmailAlreadyInUse())
        } catch (e: Exception) {
            Log.w(TAG, "Error en registro: ${e.message}")
            Result.failure(RegisterError.Unknown(e.message))
        }
    }

    /**
     * Trabajo posterior al login: persistir la sesión localmente y asegurar la
     * configuración inicial del usuario. Ninguno de los dos pasos es fatal:
     * si fallan, el usuario sigue autenticado en Firebase.
     */
    private suspend fun finalizeSignIn(user: FirebaseUser) {
        activeProfile.activateAccountProfile()
        try {
            sessionManager.saveSession(user)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo persistir la sesión: ${e.message}")
        }
        try {
            firebaseDataManager.initializeUserSettings(user.uid)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo inicializar la configuración del usuario: ${e.message}")
        }
    }

    /** Activa un perfil exclusivamente local y no requiere red ni cuenta. */
    suspend fun continueWithoutAccount() {
        runCatching { sessionManager.clearSession() }
            .onFailure { Log.w(TAG, "No se pudo limpiar la sesión anterior: ${it.message}") }
        runCatching { firebaseAuth.signOut() }
            .onFailure { Log.w(TAG, "No se pudo cerrar la sesión anterior: ${it.message}") }
        activeProfile.activateLocalProfile()
        runCatching {
            CredentialManager.create(appContext)
                .clearCredentialState(ClearCredentialStateRequest())
        }
    }

    /**
     * Cierra la sesión por completo: sesión local, Firebase y estado de
     * Credential Manager (sin esto, el selector de Google puede auto-elegir
     * la última cuenta al volver a entrar).
     */
    suspend fun signOut() {
        activeProfile.clear()
        try {
            sessionManager.clearSession()
        } catch (e: Exception) {
            Log.w(TAG, "Error limpiando sesión local: ${e.message}")
        }
        try {
            firebaseAuth.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Error cerrando sesión de Firebase: ${e.message}")
        }
        try {
            CredentialManager.create(appContext)
                .clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Error limpiando estado de credenciales: ${e.message}")
        }
    }
}

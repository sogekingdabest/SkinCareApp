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
import com.monsteraltech.skincare.account.AccountSettingsRepository
import com.monsteraltech.skincare.data.SessionManager
import com.monsteraltech.skincare.data.identity.ActiveProfile
import com.monsteraltech.skincare.login.PasswordResetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val settingsRepository: AccountSettingsRepository,
    private val activeProfile: ActiveProfile
) : PasswordResetRepository {
    companion object {
        private const val TAG = "AuthRepository"
    }

    sealed class RegisterError(message: String) : Exception(message) {
        class EmailAlreadyInUse : RegisterError("El email ya está registrado")
        class Unknown(cause: String?) : RegisterError(cause ?: "Error desconocido")
    }

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(IllegalStateException("Usuario nulo tras autenticación"))
            finalizeSignIn(user)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Error en login con email: ${e.message}")
            Result.failure(e)
        }
    }

    /** Autentica en Firebase con el idToken obtenido de Credential Manager. */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user ?: return Result.failure(IllegalStateException("Usuario nulo tras autenticación"))
            finalizeSignIn(user)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Error en login con Google: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun registerWithEmail(email: String, password: String): Result<Unit> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(RegisterError.Unknown("Usuario nulo tras registro"))
            finalizeSignIn(user)
            Result.success(Unit)
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(RegisterError.EmailAlreadyInUse())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Error en registro: ${e.message}")
            Result.failure(RegisterError.Unknown(e.message))
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = try {
        firebaseAuth.sendPasswordResetEmail(email).await()
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo persistir la sesión: ${e.message}")
        }
        try {
            settingsRepository.initializeUserSettings(user.uid)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo inicializar la configuración del usuario: ${e.message}")
        }
    }

    /** Completes a requested local-profile transition even if its screen is destroyed. */
    suspend fun continueWithoutAccount() {
        withContext(NonCancellable) {
            try { sessionManager.clearSession() } catch (error: Exception) { Log.w(TAG, "No se pudo limpiar la sesión anterior", error) }
            try { firebaseAuth.signOut() } catch (error: Exception) { Log.w(TAG, "No se pudo cerrar la sesión anterior", error) }
            activeProfile.activateLocalProfile()
            clearCredentialState()
        }
        currentCoroutineContext().ensureActive()
    }

    /** Finishes local logout before returning cancellation to a destroyed caller. */
    suspend fun signOut() {
        withContext(NonCancellable) {
            activeProfile.clear()
            try { sessionManager.clearSession() } catch (error: Exception) { Log.w(TAG, "Error limpiando sesión local", error) }
            try { firebaseAuth.signOut() } catch (error: Exception) { Log.w(TAG, "Error cerrando sesión de Firebase", error) }
            clearCredentialState()
        }
        currentCoroutineContext().ensureActive()
    }

    private suspend fun clearCredentialState() {
        try {
            withTimeoutOrNull(5000) {
                CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) { Log.w(TAG, "Error limpiando estado de credenciales", error) }
    }
}

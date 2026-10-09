package com.monsteraltech.skincare.login

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import javax.inject.Inject

/**
 * Ceremonia de "Iniciar sesión con Google" mediante Credential Manager
 * (androidx.credentials), el sustituto oficial del deprecado GoogleSignIn +
 * startActivityForResult.
 *
 * Requiere un contexto de Activity (muestra una hoja de selección de cuenta),
 * por eso el contexto se pasa por parámetro y no se inyecta.
 */
class GoogleCredentialHelper @Inject constructor() {

    companion object {
        private const val TAG = "GoogleCredentialHelper"
    }

    sealed interface SignInResult {
        data class Success(val idToken: String) : SignInResult
        /** El usuario cerró la hoja de selección: no es un error. */
        object Cancelled : SignInResult
        /** No hay cuentas de Google disponibles en el dispositivo. */
        object NoCredentialAvailable : SignInResult
        data class Error(val exception: Exception) : SignInResult
    }

    suspend fun signIn(activityContext: Context, serverClientId: String): SignInResult {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            // Mostrar todas las cuentas del dispositivo, no solo las ya autorizadas:
            // permite tanto login recurrente como primer registro.
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                SignInResult.Success(googleCredential.idToken)
            } else {
                Log.w(TAG, "Tipo de credencial inesperado: ${credential.type}")
                SignInResult.Error(IllegalStateException("Credencial de tipo inesperado"))
            }
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No hay credenciales de Google disponibles en el dispositivo")
            SignInResult.NoCredentialAvailable
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Error obteniendo credencial de Google", e)
            SignInResult.Error(e)
        }
    }
}

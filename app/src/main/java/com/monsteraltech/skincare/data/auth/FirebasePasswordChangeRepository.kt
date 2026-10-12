package com.monsteraltech.skincare.data.auth

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.skincare.account.AccountResult
import com.monsteraltech.skincare.account.PasswordChangeRepository
import com.monsteraltech.skincare.account.PasswordValidator
import com.monsteraltech.skincare.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebasePasswordChangeRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val validator: PasswordValidator,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : PasswordChangeRepository {
    override fun isAuthenticated(): Boolean = auth.currentUser != null

    override suspend fun changePassword(currentPassword: String, newPassword: String): AccountResult<Unit> =
        withContext(ioDispatcher) {
            val user = auth.currentUser ?: return@withContext authenticationError()
            val email = user.email ?: return@withContext validationError("Email no disponible")
            if (currentPassword.isBlank()) return@withContext validationError("La contraseña actual no puede estar vacía")
            try {
                user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = when {
                    invalidPassword(error) -> "La contraseña actual es incorrecta"
                    networkError(error) -> "Error de conexión. Inténtalo de nuevo"
                    else -> "Error al validar contraseña"
                }
                return@withContext validationError(message, error)
            }
            currentCoroutineContext().ensureActive()
            if (auth.currentUser?.uid != user.uid) return@withContext authenticationError()
            val validation = validator.validateNewPassword(newPassword, currentPassword)
            if (!validation.isValid) return@withContext validationError(validation.errorMessage.orEmpty())
            try {
                user.updatePassword(newPassword).await()
                AccountResult.success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val (message, type) = when {
                    invalidPassword(error) -> "La contraseña actual es incorrecta" to AccountResult.ErrorType.VALIDATION_ERROR
                    networkError(error) -> "Error de conexión. Inténtalo de nuevo" to AccountResult.ErrorType.NETWORK_ERROR
                    error.message?.contains("auth") == true -> "Error de autenticación. Inicia sesión nuevamente" to AccountResult.ErrorType.AUTHENTICATION_ERROR
                    else -> "Error al cambiar contraseña" to AccountResult.ErrorType.GENERIC_ERROR
                }
                AccountResult.error(error, message, type)
            }
        }

    private fun invalidPassword(error: Exception) = error.message?.let {
        "password is invalid" in it || "wrong-password" in it
    } == true
    private fun networkError(error: Exception) = error.message?.let { "network" in it || "timeout" in it } == true
    private fun authenticationError(): AccountResult<Unit> = AccountResult.error(
        IllegalStateException("Usuario no autenticado"), "Debes iniciar sesión para cambiar la contraseña",
        AccountResult.ErrorType.AUTHENTICATION_ERROR
    )
    private fun validationError(message: String, error: Exception = IllegalArgumentException(message)): AccountResult<Unit> =
        AccountResult.error(error, message, AccountResult.ErrorType.VALIDATION_ERROR)
}

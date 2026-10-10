package com.monsteraltech.skincare.account
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.skincare.data.FirebaseDataManager
import com.monsteraltech.skincare.data.identity.ActiveProfile
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
class UserProfileManager @Inject constructor(
    private val firebaseDataManager: FirebaseDataManager,
    private val auth: FirebaseAuth,
    private val activeProfile: ActiveProfile,
    private val retryManager: RetryManager = RetryManager()
) : AccountProfileRepository {
    private val TAG = "UserProfileManager"
    override suspend fun getCurrentUserInfo(): AccountResult<UserInfo> {
        return retryManager.executeWithRetry(
            config = retryManager.createDataRetryConfig()
        ) {
            try {
                Log.d(TAG, "Iniciando obtención de información de usuario")
                if (!isUserAuthenticated()) {
                    Log.w(TAG, "Usuario no autenticado")
                    return@executeWithRetry AccountResult.error(
                        Exception("Usuario no autenticado"),
                        "Debes iniciar sesión para acceder a tu información",
                        AccountResult.ErrorType.AUTHENTICATION_ERROR
                    )
                }
                val userInfo = if (activeProfile.isLocal) {
                    UserInfo(
                        uid = activeProfile.requireOwnerId(),
                        displayName = "Perfil local",
                        isLocalProfile = true
                    )
                } else {
                    firebaseDataManager.getCurrentUserProfile()
                }
                if (userInfo != null) {
                    Log.d(TAG, "Información de usuario obtenida exitosamente: ${userInfo.email}")
                    AccountResult.success(userInfo)
                } else {
                    Log.w(TAG, "No se pudo obtener información del usuario")
                    AccountResult.error(
                        Exception("Información de usuario no disponible"),
                        "No se pudo cargar tu información de perfil"
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener información del usuario: ${e.message}", e)
                AccountResult.error(
                    e,
                    "Error al cargar información del usuario"
                )
            }
        }
    }
    override suspend fun updateUserSettings(settings: AccountSettings): AccountResult<Boolean> {
        val ownerId = activeProfile.ownerId
        return retryManager.executeWithRetry(
            config = retryManager.createDataRetryConfig()
        ) {
            try {
                Log.d(TAG, "Iniciando actualización de configuraciones de usuario")
                if (ownerId == null || activeProfile.ownerId != ownerId ||
                    (settings.userId.isNotBlank() && settings.userId != ownerId)) {
                    Log.w(TAG, "Usuario no autenticado para actualizar configuraciones")
                    return@executeWithRetry AccountResult.error(
                        Exception("Usuario no autenticado"),
                        "Debes iniciar sesión para actualizar configuraciones",
                        AccountResult.ErrorType.AUTHENTICATION_ERROR
                    )
                }
                val result = firebaseDataManager.updateUserSettings(
                    settings,
                    ownerId
                )
                if (result) {
                    Log.d(TAG, "Configuraciones de usuario actualizadas exitosamente")
                    AccountResult.success(true)
                } else {
                    Log.w(TAG, "No se pudieron actualizar las configuraciones de usuario")
                    AccountResult.error(
                        Exception("Fallo al actualizar configuraciones"),
                        "No se pudieron guardar las configuraciones"
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Error al actualizar configuraciones de usuario: ${e.message}", e)
                AccountResult.error(
                    e,
                    "Error al guardar configuraciones"
                )
            }
        }
    }
    override suspend fun getUserSettings(): AccountResult<AccountSettings> {
        return retryManager.executeWithRetry(
            config = retryManager.createDataRetryConfig()
        ) {
            try {
                Log.d(TAG, "Iniciando obtención de configuraciones de usuario")
                if (!isUserAuthenticated()) {
                    Log.w(TAG, "Usuario no autenticado, usando configuraciones por defecto")
                    return@executeWithRetry AccountResult.success(AccountSettings())
                }
                val settings = firebaseDataManager.getUserSettings(activeProfile.requireOwnerId())
                Log.d(TAG, "Configuraciones de usuario obtenidas exitosamente")
                AccountResult.success(settings)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener configuraciones de usuario: ${e.message}", e)
                if (e.message?.contains("PERMISSION_DENIED") == true) {
                    Log.w(TAG, "Error de permisos detectado, intentando crear configuraciones iniciales")
                    val userId = getCurrentUserId()
                    if (userId != null) {
                        try {
                            firebaseDataManager.initializeUserSettings(userId)
                            Log.d(TAG, "Configuraciones iniciales creadas, usando configuraciones por defecto")
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (initError: Exception) {
                            Log.e(TAG, "Error al crear configuraciones iniciales: ${initError.message}")
                        }
                    }
                }
                Log.d(TAG, "Usando configuraciones por defecto debido al error")
                AccountResult.success(AccountSettings())
            }
        }
    }
    // El cierre de sesión se centraliza en AuthRepository.signOut(), que además
    // limpia la sesión local y el estado de Credential Manager.
    override fun isUserAuthenticated(): Boolean {
        val isAuthenticated = activeProfile.ownerId != null
        Log.d(TAG, "Estado de autenticación: $isAuthenticated")
        return isAuthenticated
    }
    override fun getCurrentUserId(): String? {
        val userId = activeProfile.ownerId
        Log.d(TAG, "UID del usuario actual: $userId")
        return userId
    }
}

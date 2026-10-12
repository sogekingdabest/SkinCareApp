package com.monsteraltech.skincare.account
import android.util.Log
import com.monsteraltech.skincare.data.identity.ActiveProfile
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
class UserProfileManager @Inject constructor(
    private val settingsRepository: AccountSettingsRepository,
    private val profileSource: CurrentAccountProfileSource,
    private val activeProfile: ActiveProfile,
    private val retryManager: RetryManager = RetryManager()
) : AccountProfileRepository {
    private val TAG = "UserProfileManager"
    override suspend fun getCurrentUserInfo(): AccountResult<UserInfo> {
        val ownerId = activeProfile.ownerId
        return retryManager.executeWithRetry(
            config = retryManager.createDataRetryConfig()
        ) {
            try {
                Log.d(TAG, "Iniciando obtención de información de usuario")
                if (ownerId == null || activeProfile.ownerId != ownerId) {
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
                    profileSource.getCurrentUserProfile()
                }
                if (userInfo != null && userInfo.uid == ownerId && activeProfile.ownerId == ownerId) {
                    Log.d(TAG, "Información de usuario obtenida exitosamente")
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
                val result = settingsRepository.updateUserSettings(
                    settings,
                    ownerId
                )
                if (result && activeProfile.ownerId == ownerId) {
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
    override suspend fun applySettingsPatch(patch: AccountSettingsPatch, ownerId: String): AccountResult<AccountSettings> {
        if (activeProfile.ownerId != ownerId) return AccountResult.error(
            SecurityException("El perfil activo ha cambiado"), "Debes iniciar sesión para actualizar configuraciones",
            AccountResult.ErrorType.AUTHENTICATION_ERROR
        )
        return try {
            val saved = settingsRepository.applyPatch(patch, ownerId)
            if (saved != null && activeProfile.ownerId == ownerId) AccountResult.success(saved)
            else AccountResult.error(IllegalStateException("No se pudieron guardar las configuraciones"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) { AccountResult.error(error) }
    }
    override suspend fun getUserSettings(): AccountResult<AccountSettings> {
        val ownerId = activeProfile.ownerId
        return retryManager.executeWithRetry(
            config = retryManager.createDataRetryConfig()
        ) {
            try {
                Log.d(TAG, "Iniciando obtención de configuraciones de usuario")
                if (!isUserAuthenticated()) {
                    Log.w(TAG, "Usuario no autenticado, usando configuraciones por defecto")
                    return@executeWithRetry AccountResult.success(AccountSettings())
                }
                if (ownerId == null || activeProfile.ownerId != ownerId) {
                    return@executeWithRetry AccountResult.success(AccountSettings())
                }
                val settings = settingsRepository.getUserSettings(ownerId)
                if (activeProfile.ownerId != ownerId) return@executeWithRetry AccountResult.success(AccountSettings())
                Log.d(TAG, "Configuraciones de usuario obtenidas exitosamente")
                AccountResult.success(settings)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener configuraciones de usuario: ${e.message}", e)
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
        Log.d(TAG, "Identificador de perfil disponible: ${userId != null}")
        return userId
    }
}

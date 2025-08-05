package es.monsteraltech.skincare_tfm.account
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
class UserProfileManager(
    private val firebaseDataManager: FirebaseDataManager = FirebaseDataManager(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val retryManager: RetryManager = RetryManager()
) {
    private val TAG = "UserProfileManager"
    suspend fun getCurrentUserInfo(): AccountResult<UserInfo> {
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
                val userInfo = firebaseDataManager.getCurrentUserProfile()
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
            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener información del usuario: ${e.message}", e)
                AccountResult.error(
                    e,
                    "Error al cargar información del usuario"
                )
            }
        }
    }
    suspend fun updateUserSettings(settings: AccountSettings): AccountResult<Boolean> {
        return retryManager.executeWithRetry(
            config = retryManager.createDataRetryConfig()
        ) {
            try {
                Log.d(TAG, "Iniciando actualización de configuraciones de usuario")
                if (!isUserAuthenticated()) {
                    Log.w(TAG, "Usuario no autenticado para actualizar configuraciones")
                    return@executeWithRetry AccountResult.error(
                        Exception("Usuario no autenticado"),
                        "Debes iniciar sesión para actualizar configuraciones",
                        AccountResult.ErrorType.AUTHENTICATION_ERROR
                    )
                }
                val result = firebaseDataManager.updateUserSettings(settings)
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
            } catch (e: Exception) {
                Log.e(TAG, "Error al actualizar configuraciones de usuario: ${e.message}", e)
                AccountResult.error(
                    e,
                    "Error al guardar configuraciones"
                )
            }
        }
    }
    suspend fun getUserSettings(): AccountResult<AccountSettings> {
        return retryManager.executeWithRetry(
            config = retryManager.createDataRetryConfig()
        ) {
            try {
                Log.d(TAG, "Iniciando obtención de configuraciones de usuario")
                if (!isUserAuthenticated()) {
                    Log.w(TAG, "Usuario no autenticado, usando configuraciones por defecto")
                    return@executeWithRetry AccountResult.success(AccountSettings())
                }
                val settings = firebaseDataManager.getUserSettings()
                Log.d(TAG, "Configuraciones de usuario obtenidas exitosamente")
                AccountResult.success(settings)
            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener configuraciones de usuario: ${e.message}", e)
                if (e.message?.contains("PERMISSION_DENIED") == true) {
                    Log.w(TAG, "Error de permisos detectado, intentando crear configuraciones iniciales")
                    val userId = getCurrentUserId()
                    if (userId != null) {
                        try {
                            firebaseDataManager.initializeUserSettings(userId)
                            Log.d(TAG, "Configuraciones iniciales creadas, usando configuraciones por defecto")
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
    suspend fun signOut(): AccountResult<Unit> {
        return try {
            Log.d(TAG, "Iniciando proceso de cierre de sesión")
            if (!isUserAuthenticated()) {
                Log.w(TAG, "No hay usuario autenticado para cerrar sesión")
                return AccountResult.error(
                    Exception("No hay sesión activa"),
                    "No hay una sesión activa para cerrar",
                    AccountResult.ErrorType.AUTHENTICATION_ERROR
                )
            }
            firebaseDataManager.signOut()
            Log.d(TAG, "Sesión de usuario cerrada exitosamente")
            AccountResult.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar sesión: ${e.message}", e)
            AccountResult.error(
                e,
                "Error al cerrar sesión"
            )
        }
    }
    fun isUserAuthenticated(): Boolean {
        val isAuthenticated = auth.currentUser != null
        Log.d(TAG, "Estado de autenticación: $isAuthenticated")
        return isAuthenticated
    }
    fun getCurrentUserId(): String? {
        val userId = auth.currentUser?.uid
        Log.d(TAG, "UID del usuario actual: $userId")
        return userId
    }
}
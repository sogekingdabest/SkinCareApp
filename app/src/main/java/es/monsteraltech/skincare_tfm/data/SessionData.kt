package es.monsteraltech.skincare_tfm.data

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import android.util.Log

/**
 * Clase de datos para almacenar información de sesión del usuario
 * Incluye métodos para serialización/deserialización JSON y validación
 */
data class SessionData(
    val userId: String = "",
    val email: String? = null,
    val displayName: String? = null,
    val tokenExpiry: Long = 0L,
    val lastRefresh: Long = System.currentTimeMillis(),
    val authProvider: String = ""
) {
    
    companion object {
        private const val TAG = "SessionData"
        private val gson = Gson()
        
        /**
         * Crea SessionData desde JSON
         * @param json String JSON con los datos de sesión
         * @return SessionData o null si hay error en la deserialización
         */
        fun fromJson(json: String): SessionData? {
            return try {
                gson.fromJson(json, SessionData::class.java)
            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "Error al deserializar SessionData: ${e.message}")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error inesperado al deserializar SessionData: ${e.message}")
                null
            }
        }
    }
    
    /**
     * Convierte SessionData a JSON
     * @return String JSON con los datos de sesión
     */
    fun toJson(): String {
        return try {
            gson.toJson(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error al serializar SessionData: ${e.message}")
            "{}"
        }
    }
    
    /**
     * Valida la integridad de los datos de sesión con verificaciones mejoradas
     * @return true si los datos son válidos, false en caso contrario
     */
    fun isValid(): Boolean {
        return try {
            // Verificar campos obligatorios
            if (userId.isBlank()) {
                Log.w(TAG, "SessionData inválida: userId está vacío")
                return false
            }
            
            if (authProvider.isBlank()) {
                Log.w(TAG, "SessionData inválida: authProvider está vacío")
                return false
            }
            
            // Verificar longitud razonable de userId (Firebase UIDs son típicamente 28 caracteres)
            if (userId.length < 10 || userId.length > 128) {
                Log.w(TAG, "SessionData inválida: userId tiene longitud sospechosa (${userId.length})")
                return false
            }
            
            // Verificar que tokenExpiry sea una fecha futura válida
            if (tokenExpiry <= 0) {
                Log.w(TAG, "SessionData inválida: tokenExpiry no es válido ($tokenExpiry)")
                return false
            }
            
            // Verificar que lastRefresh sea una fecha válida
            if (lastRefresh <= 0) {
                Log.w(TAG, "SessionData inválida: lastRefresh no es válido ($lastRefresh)")
                return false
            }
            
            // Verificar que lastRefresh no sea posterior a tokenExpiry
            if (lastRefresh > tokenExpiry) {
                Log.w(TAG, "SessionData inválida: lastRefresh ($lastRefresh) es posterior a tokenExpiry ($tokenExpiry)")
                return false
            }
            
            // Verificar rangos temporales razonables
            val currentTime = System.currentTimeMillis()
            val oneYearInMs = 365L * 24 * 60 * 60 * 1000
            
            // lastRefresh no debe ser más de 1 año en el pasado
            if (currentTime - lastRefresh > oneYearInMs) {
                Log.w(TAG, "SessionData inválida: lastRefresh es demasiado antiguo")
                return false
            }
            
            // tokenExpiry no debe ser más de 1 año en el futuro
            if (tokenExpiry - currentTime > oneYearInMs) {
                Log.w(TAG, "SessionData inválida: tokenExpiry es demasiado lejano en el futuro")
                return false
            }
            
            // Verificar formato de email si está presente
            if (email != null && email.isNotBlank()) {
                if (!isValidEmailFormat(email)) {
                    Log.w(TAG, "SessionData inválida: formato de email inválido")
                    return false
                }
            }
            
            // Verificar que authProvider sea un valor conocido
            if (!isValidAuthProvider(authProvider)) {
                Log.w(TAG, "SessionData inválida: authProvider desconocido ($authProvider)")
                return false
            }
            
            Log.d(TAG, "SessionData válida para usuario: ${userId.take(8)}...")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al validar SessionData: ${e.message}")
            false
        }
    }
    
    /**
     * Verifica si el formato del email es válido
     */
    private fun isValidEmailFormat(email: String): Boolean {
        return try {
            email.contains("@") && 
            email.contains(".") && 
            email.length >= 5 && 
            email.length <= 254 &&
            !email.startsWith("@") &&
            !email.endsWith("@") &&
            email.count { it == '@' } == 1
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Verifica si el proveedor de autenticación es válido
     */
    private fun isValidAuthProvider(provider: String): Boolean {
        val validProviders = setOf(
            "firebase",
            "google.com",
            "facebook.com",
            "twitter.com",
            "github.com",
            "apple.com",
            "microsoft.com",
            "yahoo.com",
            "password" // Email/password authentication
        )
        return validProviders.contains(provider.lowercase())
    }
    
    /**
     * Verifica si el token ha expirado basándose en la fecha actual
     * @return true si el token ha expirado, false en caso contrario
     */
    fun isExpired(): Boolean {
        val currentTime = System.currentTimeMillis()
        val expired = currentTime >= tokenExpiry
        
        if (expired) {
            Log.d(TAG, "Token expirado para usuario $userId. Actual: $currentTime, Expiry: $tokenExpiry")
        }
        
        return expired
    }
    
    /**
     * Verifica si el token expirará pronto (dentro de los próximos 5 minutos)
     * Útil para renovación proactiva de tokens
     * @return true si el token expirará pronto, false en caso contrario
     */
    fun willExpireSoon(): Boolean {
        val currentTime = System.currentTimeMillis()
        val fiveMinutesInMillis = 5 * 60 * 1000L // 5 minutos
        val willExpire = (tokenExpiry - currentTime) <= fiveMinutesInMillis
        
        if (willExpire) {
            Log.d(TAG, "Token expirará pronto para usuario $userId")
        }
        
        return willExpire
    }
    
    /**
     * Crea una copia de SessionData con token actualizado
     * @param newTokenExpiry Nueva fecha de expiración del token
     * @return Nueva instancia de SessionData con token actualizado
     */
    fun withRefreshedToken(newTokenExpiry: Long): SessionData {
        return copy(
            tokenExpiry = newTokenExpiry,
            lastRefresh = System.currentTimeMillis()
        )
    }
    
    /**
     * Obtiene información resumida de la sesión para logging (sin datos sensibles)
     * @return String con información básica de la sesión
     */
    fun getSummary(): String {
        return "SessionData(userId=${userId.take(8)}..., " +
                "email=${email?.let { "${it.take(3)}***" }}, " +
                "provider=$authProvider, " +
                "expired=${isExpired()})"
    }
}
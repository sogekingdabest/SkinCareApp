package es.monsteraltech.skincare_tfm.data
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
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
    fun toJson(): String {
        return try {
            gson.toJson(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error al serializar SessionData: ${e.message}")
            "{}"
        }
    }
    fun isValid(): Boolean {
        return try {
            if (userId.isBlank()) {
                Log.w(TAG, "SessionData inválida: userId está vacío")
                return false
            }
            if (authProvider.isBlank()) {
                Log.w(TAG, "SessionData inválida: authProvider está vacío")
                return false
            }
            if (userId.length < 10 || userId.length > 128) {
                Log.w(TAG, "SessionData inválida: userId tiene longitud sospechosa (${userId.length})")
                return false
            }
            if (tokenExpiry <= 0) {
                Log.w(TAG, "SessionData inválida: tokenExpiry no es válido ($tokenExpiry)")
                return false
            }
            if (lastRefresh <= 0) {
                Log.w(TAG, "SessionData inválida: lastRefresh no es válido ($lastRefresh)")
                return false
            }
            if (lastRefresh > tokenExpiry) {
                Log.w(TAG, "SessionData inválida: lastRefresh ($lastRefresh) es posterior a tokenExpiry ($tokenExpiry)")
                return false
            }
            val currentTime = System.currentTimeMillis()
            val oneYearInMs = 365L * 24 * 60 * 60 * 1000
            if (currentTime - lastRefresh > oneYearInMs) {
                Log.w(TAG, "SessionData inválida: lastRefresh es demasiado antiguo")
                return false
            }
            if (tokenExpiry - currentTime > oneYearInMs) {
                Log.w(TAG, "SessionData inválida: tokenExpiry es demasiado lejano en el futuro")
                return false
            }
            if (email != null && email.isNotBlank()) {
                if (!isValidEmailFormat(email)) {
                    Log.w(TAG, "SessionData inválida: formato de email inválido")
                    return false
                }
            }
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
            "password"
        )
        return validProviders.contains(provider.lowercase())
    }
    fun isExpired(): Boolean {
        val currentTime = System.currentTimeMillis()
        val expired = currentTime >= tokenExpiry
        if (expired) {
            Log.d(TAG, "Token expirado para usuario $userId. Actual: $currentTime, Expiry: $tokenExpiry")
        }
        return expired
    }
    fun willExpireSoon(): Boolean {
        val currentTime = System.currentTimeMillis()
        val fiveMinutesInMillis = 5 * 60 * 1000L
        val willExpire = (tokenExpiry - currentTime) <= fiveMinutesInMillis
        if (willExpire) {
            Log.d(TAG, "Token expirará pronto para usuario $userId")
        }
        return willExpire
    }
    fun withRefreshedToken(newTokenExpiry: Long): SessionData {
        return copy(
            tokenExpiry = newTokenExpiry,
            lastRefresh = System.currentTimeMillis()
        )
    }
    fun getSummary(): String {
        return "SessionData(userId=${userId.take(8)}..., " +
                "email=${email?.let { "${it.take(3)}***" }}, " +
                "provider=$authProvider, " +
                "expired=${isExpired()})"
    }
}
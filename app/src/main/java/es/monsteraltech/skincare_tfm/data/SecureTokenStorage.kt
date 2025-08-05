package es.monsteraltech.skincare_tfm.data
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
class SecureTokenStorage(private val context: Context) {
    companion object {
        private const val TAG = "SecureTokenStorage"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "SessionTokenKey"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS_NAME = "secure_session_prefs"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }
    }
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    init {
        try {
            generateKeyIfNeeded()
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando SecureTokenStorage: ${e.message}")
        }
    }
    suspend fun storeToken(key: String, token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isKeystoreAvailable()) {
                Log.w(TAG, "Keystore no disponible, usando almacenamiento alternativo")
                return@withContext storeTokenFallback(key, token)
            }
            val encryptedData = encryptData(token)
            if (encryptedData != null) {
                sharedPreferences.edit()
                    .putString(key, encryptedData)
                    .apply()
                Log.d(TAG, "Token almacenado exitosamente para clave: $key")
                true
            } else {
                Log.e(TAG, "Error al encriptar token para clave: $key")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al almacenar token: ${e.message}")
            false
        }
    }
    suspend fun retrieveToken(key: String): String? = withContext(Dispatchers.IO) {
        try {
            if (!isKeystoreAvailable()) {
                Log.w(TAG, "Keystore no disponible, usando almacenamiento alternativo")
                return@withContext retrieveTokenFallback(key)
            }
            val encryptedData = sharedPreferences.getString(key, null)
            if (encryptedData != null) {
                val decryptedToken = decryptData(encryptedData)
                if (decryptedToken != null) {
                    Log.d(TAG, "Token recuperado exitosamente para clave: $key")
                } else {
                    Log.w(TAG, "Error al desencriptar token para clave: $key")
                }
                decryptedToken
            } else {
                Log.d(TAG, "No se encontró token para clave: $key")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al recuperar token: ${e.message}")
            null
        }
    }
    suspend fun deleteToken(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            sharedPreferences.edit()
                .remove(key)
                .apply()
            Log.d(TAG, "Token eliminado exitosamente para clave: $key")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar token: ${e.message}")
            false
        }
    }
    suspend fun tokenExists(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            sharedPreferences.contains(key)
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar existencia de token: ${e.message}")
            false
        }
    }
    private fun generateKeyIfNeeded() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
            Log.d(TAG, "Clave de encriptación generada exitosamente")
        }
    }
    private fun encryptData(data: String): String? {
        try {
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedData.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedData, 0, combined, iv.size, encryptedData.size)
            return Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            Log.e(TAG, "Error al encriptar datos: ${e.message}")
            return null
        }
    }
    private fun decryptData(encryptedData: String): String? {
        try {
            val combined = Base64.getDecoder().decode(encryptedData)
            val iv = ByteArray(GCM_IV_LENGTH)
            val encrypted = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, iv.size)
            System.arraycopy(combined, iv.size, encrypted, 0, encrypted.size)
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedData = cipher.doFinal(encrypted)
            return String(decryptedData, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error al desencriptar datos: ${e.message}")
            return null
        }
    }
    private fun isKeystoreAvailable(): Boolean {
        return try {
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateKeyIfNeeded()
            }
            val key = keyStore.getKey(KEY_ALIAS, null)
            if (key == null) {
                Log.w(TAG, "Clave de keystore es null")
                return false
            }
            val testData = "test_keystore_availability"
            val encrypted = encryptData(testData)
            if (encrypted == null) {
                Log.w(TAG, "Fallo en prueba de encriptación")
                return false
            }
            val decrypted = decryptData(encrypted)
            val isAvailable = decrypted == testData
            if (isAvailable) {
                Log.d(TAG, "Keystore verificado como disponible")
            } else {
                Log.w(TAG, "Keystore falló prueba de funcionalidad")
            }
            isAvailable
        } catch (e: Exception) {
            Log.e(TAG, "Keystore no disponible: ${e.message}")
            logKeystoreError(e)
            false
        }
    }
    private fun storeTokenFallback(key: String, token: String): Boolean {
        return try {
            Log.w(TAG, "Usando almacenamiento fallback sin encriptación para clave: $key")
            val obfuscatedToken = obfuscateToken(token)
            val success = sharedPreferences.edit()
                .putString("${key}_fallback", obfuscatedToken)
                .putBoolean("${key}_is_fallback", true)
                .commit()
            if (success) {
                Log.i(TAG, "Token almacenado exitosamente en modo fallback")
                logSecurityEvent("FALLBACK_STORAGE_USED", key)
            } else {
                Log.e(TAG, "Error al almacenar token en modo fallback")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error en almacenamiento fallback: ${e.message}", e)
            false
        }
    }
    private fun retrieveTokenFallback(key: String): String? {
        return try {
            Log.w(TAG, "Usando recuperación fallback sin encriptación para clave: $key")
            val isFallback = sharedPreferences.getBoolean("${key}_is_fallback", false)
            if (!isFallback) {
                Log.d(TAG, "No hay datos de fallback para clave: $key")
                return null
            }
            val obfuscatedToken = sharedPreferences.getString("${key}_fallback", null)
            if (obfuscatedToken == null) {
                Log.d(TAG, "No se encontró token de fallback para clave: $key")
                return null
            }
            val deobfuscatedToken = deobfuscateToken(obfuscatedToken)
            if (deobfuscatedToken != null) {
                Log.i(TAG, "Token recuperado exitosamente en modo fallback")
                logSecurityEvent("FALLBACK_RETRIEVAL_USED", key)
            }
            deobfuscatedToken
        } catch (e: Exception) {
            Log.e(TAG, "Error en recuperación fallback: ${e.message}", e)
            null
        }
    }
    private fun obfuscateToken(token: String): String {
        return try {
            val bytes = token.toByteArray(Charsets.UTF_8)
            val obfuscated = ByteArray(bytes.size)
            val pattern = "SecureTokenStorage".toByteArray()
            for (i in bytes.indices) {
                obfuscated[i] = (bytes[i].toInt() xor pattern[i % pattern.size].toInt()).toByte()
            }
            Base64.getEncoder().encodeToString(obfuscated)
        } catch (e: Exception) {
            Log.e(TAG, "Error al ofuscar token: ${e.message}")
            token
        }
    }
    private fun deobfuscateToken(obfuscatedToken: String): String? {
        return try {
            val obfuscated = Base64.getDecoder().decode(obfuscatedToken)
            val deobfuscated = ByteArray(obfuscated.size)
            val pattern = "SecureTokenStorage".toByteArray()
            for (i in obfuscated.indices) {
                deobfuscated[i] = (obfuscated[i].toInt() xor pattern[i % pattern.size].toInt()).toByte()
            }
            String(deobfuscated, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error al desofuscar token: ${e.message}")
            null
        }
    }
    private fun logKeystoreError(exception: Exception) {
        val errorType = when (exception) {
            is java.security.KeyStoreException -> "KeyStoreException"
            is java.security.NoSuchAlgorithmException -> "NoSuchAlgorithmException"
            is java.security.cert.CertificateException -> "CertificateException"
            is java.io.IOException -> "IOException"
            is java.security.InvalidKeyException -> "InvalidKeyException"
            is javax.crypto.NoSuchPaddingException -> "NoSuchPaddingException"
            else -> exception.javaClass.simpleName
        }
        logSecurityEvent("KEYSTORE_ERROR", mapOf(
            "error_type" to errorType,
            "error_message" to (exception.message ?: "Unknown error")
        ))
    }
    private fun logSecurityEvent(event: String, key: String) {
        logSecurityEvent(event, mapOf("key_hash" to key.hashCode().toString()))
    }
    private fun logSecurityEvent(event: String, details: Map<String, String>) {
        try {
            val logMessage = buildString {
                append("STORAGE_SECURITY_EVENT: $event")
                if (details.isNotEmpty()) {
                    append(" | Details: ")
                    details.forEach { (k, v) ->
                        append("$k=$v ")
                    }
                }
            }
            Log.i(TAG, logMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Error al registrar evento de seguridad: ${e.message}")
        }
    }
}
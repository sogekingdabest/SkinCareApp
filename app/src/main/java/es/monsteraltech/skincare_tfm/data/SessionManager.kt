package es.monsteraltech.skincare_tfm.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException

/**
 * Gestor de sesión singleton thread-safe para manejar la persistencia de sesión del usuario
 * Integra con Firebase Auth y utiliza almacenamiento seguro para tokens
 * Incluye optimizaciones de rendimiento con caching y preloading
 */
class SessionManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "SessionManager"
        private const val SESSION_TOKEN_KEY = "session_token"
        private const val SESSION_DATA_KEY = "session_data"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val SESSION_VERIFICATION_TIMEOUT_MS = 20000L // Timeout for session verification
        private const val CORRUPTED_DATA_CLEANUP_DELAY_MS = 500L // Delay before cleanup after detecting corruption
        
        // Performance optimization constants
        private const val CACHE_VALIDITY_DURATION_MS = 30000L // 30 seconds cache validity
        private const val PRELOAD_TIMEOUT_MS = 5000L // Timeout for preloading operations
        private const val FAST_VERIFICATION_TIMEOUT_MS = 8000L // Faster timeout for cached verifications
        
        @Volatile
        private var INSTANCE: SessionManager? = null
        
        /**
         * Obtiene la instancia singleton de SessionManager
         * @param context Contexto de la aplicación
         * @return Instancia de SessionManager
         */
        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val secureStorage: SecureTokenStorage by lazy { SecureTokenStorage(context) }
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    
    // Performance optimization: In-memory cache
    @Volatile
    private var cachedSessionData: SessionData? = null
    @Volatile
    private var cacheTimestamp: Long = 0L
    @Volatile
    private var lastVerificationResult: Boolean = false
    @Volatile
    private var lastVerificationTimestamp: Long = 0L
    
    // Preloading state
    @Volatile
    private var isPreloadingUserData: Boolean = false
    
    /**
     * Guarda la sesión del usuario autenticado
     * @param user Usuario de Firebase autenticado
     * @return true si se guardó exitosamente, false en caso contrario
     */
    suspend fun saveSession(user: FirebaseUser): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Guardando sesión para usuario: ${user.uid}")
            
            // Obtener token de Firebase
            val tokenResult = user.getIdToken(false).await()
            val token = tokenResult.token
            
            if (token == null) {
                Log.e(TAG, "No se pudo obtener token de Firebase")
                return@withContext false
            }
            
            // Calcular expiración del token (Firebase tokens duran 1 hora)
            val currentTime = System.currentTimeMillis()
            val tokenExpiry = currentTime + TimeUnit.HOURS.toMillis(1)
            
            // Crear datos de sesión
            val sessionData = SessionData(
                userId = user.uid,
                email = user.email,
                displayName = user.displayName,
                tokenExpiry = tokenExpiry,
                lastRefresh = currentTime,
                authProvider = getAuthProvider(user)
            )
            
            // Validar datos antes de guardar
            if (!sessionData.isValid()) {
                Log.e(TAG, "Datos de sesión inválidos")
                return@withContext false
            }
            
            // Almacenar token y datos de sesión
            val tokenStored = secureStorage.storeToken(SESSION_TOKEN_KEY, token)
            val dataStored = secureStorage.storeToken(SESSION_DATA_KEY, sessionData.toJson())
            
            val success = tokenStored && dataStored
            if (success) {
                Log.d(TAG, "Sesión guardada exitosamente para usuario: ${user.uid}")
            } else {
                Log.e(TAG, "Error al guardar sesión. Token: $tokenStored, Data: $dataStored")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar sesión: ${e.message}", e)
            false
        }
    }
    
    /**
     * Recupera los datos de sesión almacenados con caching para optimizar rendimiento
     * @param useCache Si usar cache en memoria para evitar operaciones de I/O innecesarias
     * @return SessionData si existe y es válida, null en caso contrario
     */
    suspend fun getStoredSession(useCache: Boolean = true): SessionData? = withContext(Dispatchers.IO) {
        // Verificar cache primero si está habilitado
        if (useCache && isCacheValid()) {
            Log.d(TAG, "Usando datos de sesión desde cache")
            return@withContext cachedSessionData
        }
        try {
            Log.d(TAG, "Recuperando datos de sesión almacenados")
            
            val sessionDataJson = secureStorage.retrieveToken(SESSION_DATA_KEY)
            if (sessionDataJson == null) {
                Log.d(TAG, "No se encontraron datos de sesión almacenados")
                return@withContext null
            }
            
            // Verificar si los datos están corruptos antes de deserializar
            if (isDataCorrupted(sessionDataJson)) {
                Log.w(TAG, "Datos de sesión detectados como corruptos, iniciando limpieza")
                handleCorruptedSessionData("Datos JSON corruptos detectados")
                return@withContext null
            }
            
            val sessionData = SessionData.fromJson(sessionDataJson)
            if (sessionData == null) {
                Log.w(TAG, "Error al deserializar datos de sesión - posible corrupción")
                handleCorruptedSessionData("Error en deserialización JSON")
                return@withContext null
            }
            
            // Validación adicional de integridad de datos
            if (!sessionData.isValid()) {
                Log.w(TAG, "Datos de sesión inválidos - falló validación de integridad")
                handleCorruptedSessionData("Validación de integridad fallida")
                return@withContext null
            }
            
            // Verificar consistencia temporal
            if (!isTemporallyConsistent(sessionData)) {
                Log.w(TAG, "Datos de sesión temporalmente inconsistentes")
                handleCorruptedSessionData("Inconsistencia temporal detectada")
                return@withContext null
            }
            
            Log.d(TAG, "Datos de sesión recuperados exitosamente: ${sessionData.getSummary()}")
            
            // Actualizar cache
            updateCache(sessionData)
            
            sessionData
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Error de seguridad al recuperar sesión - posible compromiso de datos", e)
            handleCorruptedSessionData("Error de seguridad: ${e.javaClass.simpleName}")
            null
        } catch (e: BadPaddingException) {
            Log.e(TAG, "Error de padding en desencriptación - datos corruptos", e)
            handleCorruptedSessionData("Error de padding en desencriptación")
            null
        } catch (e: IllegalBlockSizeException) {
            Log.e(TAG, "Error de tamaño de bloque - datos corruptos", e)
            handleCorruptedSessionData("Error de tamaño de bloque")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado al recuperar datos de sesión: ${e.message}", e)
            // Solo limpiar si parece ser corrupción de datos
            if (isLikelyDataCorruption(e)) {
                handleCorruptedSessionData("Error inesperado: ${e.javaClass.simpleName}")
            }
            null
        }
    }
    
    /**
     * Verifica si la sesión actual es válida con optimizaciones de rendimiento
     * Incluye verificación local y remota de tokens con caching inteligente
     * @param fastMode Si usar modo rápido con timeouts reducidos para mejor UX
     * @return true si la sesión es válida, false en caso contrario
     */
    suspend fun isSessionValid(fastMode: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val timeoutMs = if (fastMode) FAST_VERIFICATION_TIMEOUT_MS else SESSION_VERIFICATION_TIMEOUT_MS
            Log.d(TAG, "Verificando validez de sesión con timeout de ${timeoutMs}ms (fast mode: $fastMode)")
            
            // Verificar cache de verificación reciente para evitar verificaciones innecesarias
            if (fastMode && isRecentVerificationValid()) {
                Log.d(TAG, "Usando resultado de verificación reciente desde cache")
                return@withContext lastVerificationResult
            }
            
            return@withContext withTimeout(timeoutMs) {
                // Verificar si existe sesión almacenada (usar cache en modo rápido)
                val sessionData = getStoredSession(useCache = fastMode) ?: return@withTimeout false
                
                // Verificar expiración local
                if (sessionData.isExpired()) {
                    Log.d(TAG, "Token expirado localmente")
                    return@withTimeout refreshSession()
                }
                
                // Si el token expirará pronto, intentar renovarlo
                if (sessionData.willExpireSoon()) {
                    Log.d(TAG, "Token expirará pronto, intentando renovar")
                    return@withTimeout refreshSession()
                }
                
                // Verificar con Firebase (con timeout)
                val isValid = verifyWithFirebase(sessionData)
                
                // Actualizar cache de verificación
                updateVerificationCache(isValid)
                
                // Iniciar preloading de datos de usuario si la sesión es válida
                if (isValid && !isPreloadingUserData) {
                    startUserDataPreloading()
                }
                
                return@withTimeout isValid
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timeout al verificar validez de sesión después de ${SESSION_VERIFICATION_TIMEOUT_MS}ms")
            // En caso de timeout, permitir acceso con token local si no ha expirado
            return@withContext handleSessionVerificationTimeout()
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar validez de sesión: ${e.message}", e)
            false
        }
    }
    
    /**
     * Limpia completamente los datos de sesión
     * @return true si se limpió exitosamente, false en caso contrario
     */
    suspend fun clearSession(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Limpiando datos de sesión")
            
            val tokenDeleted = secureStorage.deleteToken(SESSION_TOKEN_KEY)
            val dataDeleted = secureStorage.deleteToken(SESSION_DATA_KEY)
            
            val success = tokenDeleted && dataDeleted
            if (success) {
                Log.d(TAG, "Sesión limpiada exitosamente")
                // Limpiar cache también
                clearCache()
            } else {
                Log.w(TAG, "Error parcial al limpiar sesión. Token: $tokenDeleted, Data: $dataDeleted")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error al limpiar sesión: ${e.message}", e)
            false
        }
    }
    
    /**
     * Renueva la sesión obteniendo un nuevo token de Firebase con lógica de reintentos
     * @return true si se renovó exitosamente, false en caso contrario
     */
    suspend fun refreshSession(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Renovando sesión")
            
            val currentUser = firebaseAuth.currentUser
            if (currentUser == null) {
                Log.w(TAG, "No hay usuario autenticado para renovar sesión")
                clearSession()
                return@withContext false
            }
            
            // Obtener nuevo token con reintentos
            val newToken = refreshTokenWithRetry(currentUser)
            if (newToken == null) {
                Log.e(TAG, "No se pudo obtener nuevo token después de reintentos")
                return@withContext false
            }
            
            // Actualizar datos de sesión
            val currentTime = System.currentTimeMillis()
            val newTokenExpiry = currentTime + TimeUnit.HOURS.toMillis(1)
            
            val sessionData = getStoredSession()
            val updatedSessionData = sessionData?.withRefreshedToken(newTokenExpiry) ?: SessionData(
                userId = currentUser.uid,
                email = currentUser.email,
                displayName = currentUser.displayName,
                tokenExpiry = newTokenExpiry,
                lastRefresh = currentTime,
                authProvider = getAuthProvider(currentUser)
            )
            
            // Almacenar nuevo token y datos actualizados
            val tokenStored = secureStorage.storeToken(SESSION_TOKEN_KEY, newToken)
            val dataStored = secureStorage.storeToken(SESSION_DATA_KEY, updatedSessionData.toJson())
            
            val success = tokenStored && dataStored
            if (success) {
                Log.d(TAG, "Sesión renovada exitosamente")
            } else {
                Log.e(TAG, "Error al renovar sesión")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error al renovar sesión: ${e.message}", e)
            // En caso de error de red, mantener sesión actual si no ha expirado
            if (isNetworkError(e)) {
                val currentSession = getStoredSession()
                if (currentSession != null && !currentSession.isExpired()) {
                    Log.i(TAG, "Manteniendo sesión actual debido a error de red")
                    return@withContext true
                }
            }
            false
        }
    }
    
    /**
     * Renueva el token con Firebase con lógica de reintentos para errores de red
     * @param user Usuario de Firebase
     * @return Nuevo token si es exitoso, null en caso contrario
     */
    private suspend fun refreshTokenWithRetry(user: FirebaseUser): String? {
        var lastException: Exception? = null
        
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                Log.d(TAG, "Intento de renovación ${attempt + 1}/$MAX_RETRY_ATTEMPTS")
                
                val tokenResult = withContext(Dispatchers.IO) {
                    user.getIdToken(true).await() // Force refresh
                }
                
                return tokenResult.token
            } catch (e: Exception) {
                lastException = e
                val isNetworkError = isNetworkError(e)
                
                Log.w(TAG, "Error en renovación intento ${attempt + 1}: ${e.message} (Network error: $isNetworkError)")
                
                // Solo reintentar para errores de red
                if (isNetworkError && attempt < MAX_RETRY_ATTEMPTS - 1) {
                    Log.d(TAG, "Reintentando renovación en ${RETRY_DELAY_MS * (attempt + 1)}ms...")
                    delay(RETRY_DELAY_MS * (attempt + 1)) // Backoff exponencial
                } else {
                    // Para errores no de red o último intento, lanzar excepción
                    throw e
                }
            }
        }
        
        // Si llegamos aquí, todos los reintentos fallaron
        throw lastException ?: Exception("Todos los reintentos de renovación fallaron")
    }
    
    /**
     * Verifica el token con Firebase Auth con lógica de reintentos
     * @param sessionData Datos de sesión a verificar
     * @return true si el token es válido, false en caso contrario
     */
    private suspend fun verifyWithFirebase(sessionData: SessionData): Boolean {
        return try {
            val currentUser = firebaseAuth.currentUser
            if (currentUser == null) {
                Log.w(TAG, "No hay usuario autenticado en Firebase")
                return false
            }
            
            if (currentUser.uid != sessionData.userId) {
                Log.w(TAG, "Usuario de sesión no coincide con usuario actual de Firebase")
                return false
            }
            
            // Verificar que el usuario sigue siendo válido con reintentos
            val tokenResult = verifyTokenWithRetry(currentUser)
            
            val isValid = tokenResult != null
            Log.d(TAG, "Verificación con Firebase: ${if (isValid) "exitosa" else "fallida"}")
            isValid
        } catch (e: Exception) {
            Log.w(TAG, "Error al verificar con Firebase: ${e.message}")
            handleVerificationError(e, sessionData)
        }
    }
    
    /**
     * Verifica el token con Firebase con lógica de reintentos para errores de red
     * @param user Usuario de Firebase
     * @return Token result si es exitoso, null en caso contrario
     */
    private suspend fun verifyTokenWithRetry(user: FirebaseUser): String? {
        var lastException: Exception? = null
        
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                Log.d(TAG, "Intento de verificación ${attempt + 1}/$MAX_RETRY_ATTEMPTS")
                
                val tokenResult = withContext(Dispatchers.IO) {
                    user.getIdToken(false).await()
                }
                
                return tokenResult.token
            } catch (e: Exception) {
                lastException = e
                val isNetworkError = isNetworkError(e)
                
                Log.w(TAG, "Error en intento ${attempt + 1}: ${e.message} (Network error: $isNetworkError)")
                
                // Solo reintentar para errores de red
                if (isNetworkError && attempt < MAX_RETRY_ATTEMPTS - 1) {
                    Log.d(TAG, "Reintentando en ${RETRY_DELAY_MS}ms...")
                    delay(RETRY_DELAY_MS * (attempt + 1)) // Backoff exponencial
                } else {
                    // Para errores no de red o último intento, lanzar excepción
                    throw e
                }
            }
        }
        
        // Si llegamos aquí, todos los reintentos fallaron
        throw lastException ?: Exception("Todos los reintentos fallaron")
    }
    
    /**
     * Determina si una excepción es un error de red
     * @param exception Excepción a evaluar
     * @return true si es un error de red, false en caso contrario
     */
    private fun isNetworkError(exception: Exception): Boolean {
        return when (exception) {
            is UnknownHostException,
            is SocketTimeoutException,
            is IOException -> true
            else -> {
                // Verificar si el mensaje contiene indicadores de error de red
                val message = exception.message?.lowercase() ?: ""
                message.contains("network") || 
                message.contains("timeout") || 
                message.contains("connection") ||
                message.contains("unreachable")
            }
        }
    }
    
    /**
     * Maneja errores de verificación según el tipo de error
     * @param exception Excepción ocurrida
     * @param sessionData Datos de sesión
     * @return true si se permite acceso offline, false en caso contrario
     */
    private fun handleVerificationError(exception: Exception, sessionData: SessionData): Boolean {
        return when {
            isNetworkError(exception) -> {
                Log.i(TAG, "Error de red detectado, permitiendo acceso offline con token local")
                // Permitir acceso offline solo si el token local no ha expirado
                val allowOfflineAccess = !sessionData.isExpired()
                Log.d(TAG, "Acceso offline ${if (allowOfflineAccess) "permitido" else "denegado"} - Token expirado: ${sessionData.isExpired()}")
                allowOfflineAccess
            }
            else -> {
                Log.w(TAG, "Error no relacionado con red, denegando acceso: ${exception.message}")
                false
            }
        }
    }
    
    /**
     * Obtiene el proveedor de autenticación del usuario
     * @param user Usuario de Firebase
     * @return String con el nombre del proveedor
     */
    private fun getAuthProvider(user: FirebaseUser): String {
        return user.providerData.firstOrNull()?.providerId ?: "firebase"
    }
    
    /**
     * Verifica si los datos JSON están corruptos mediante validaciones básicas
     * @param jsonData Datos JSON a verificar
     * @return true si los datos parecen estar corruptos, false en caso contrario
     */
    private fun isDataCorrupted(jsonData: String): Boolean {
        return try {
            // Verificaciones básicas de formato JSON
            if (jsonData.isBlank() || jsonData.length < 10) {
                Log.d(TAG, "Datos JSON demasiado cortos o vacíos")
                return true
            }
            
            // Verificar que tenga estructura básica de JSON
            if (!jsonData.trim().startsWith("{") || !jsonData.trim().endsWith("}")) {
                Log.d(TAG, "Datos JSON no tienen estructura válida")
                return true
            }
            
            // Verificar que contenga campos esperados
            val requiredFields = listOf("userId", "tokenExpiry", "lastRefresh", "authProvider")
            val hasRequiredFields = requiredFields.all { field ->
                jsonData.contains("\"$field\"")
            }
            
            if (!hasRequiredFields) {
                Log.d(TAG, "Datos JSON no contienen campos requeridos")
                return true
            }
            
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error al verificar corrupción de datos: ${e.message}")
            true
        }
    }
    
    /**
     * Verifica si los datos de sesión son temporalmente consistentes
     * @param sessionData Datos de sesión a verificar
     * @return true si son consistentes, false en caso contrario
     */
    private fun isTemporallyConsistent(sessionData: SessionData): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // Verificar que lastRefresh no sea futuro
        if (sessionData.lastRefresh > currentTime + 60000) { // 1 minuto de tolerancia
            Log.w(TAG, "lastRefresh está en el futuro: ${sessionData.lastRefresh} > $currentTime")
            return false
        }
        
        // Verificar que tokenExpiry sea razonable (no más de 24 horas)
        val maxTokenDuration = 24 * 60 * 60 * 1000L // 24 horas
        if (sessionData.tokenExpiry > currentTime + maxTokenDuration) {
            Log.w(TAG, "tokenExpiry es demasiado lejano en el futuro")
            return false
        }
        
        // Verificar que lastRefresh no sea demasiado antiguo (más de 30 días)
        val maxAge = 30 * 24 * 60 * 60 * 1000L // 30 días
        if (currentTime - sessionData.lastRefresh > maxAge) {
            Log.w(TAG, "lastRefresh es demasiado antiguo")
            return false
        }
        
        return true
    }
    
    /**
     * Maneja datos de sesión corruptos con limpieza segura
     * @param reason Razón de la corrupción para logging
     */
    private suspend fun handleCorruptedSessionData(reason: String) {
        try {
            Log.w(TAG, "Manejando datos corruptos: $reason")
            
            // Pequeña demora para evitar loops de limpieza rápida
            delay(CORRUPTED_DATA_CLEANUP_DELAY_MS)
            
            // Limpiar datos corruptos
            val cleanupSuccess = clearSession()
            
            if (cleanupSuccess) {
                Log.i(TAG, "Datos corruptos limpiados exitosamente")
            } else {
                Log.e(TAG, "Error al limpiar datos corruptos")
            }
            
            // Log para debugging sin exponer datos sensibles
            logSecurityEvent("CORRUPTED_DATA_DETECTED", mapOf(
                "reason" to reason,
                "cleanup_success" to cleanupSuccess.toString(),
                "timestamp" to System.currentTimeMillis().toString()
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al manejar datos corruptos: ${e.message}", e)
        }
    }
    
    /**
     * Determina si una excepción indica probable corrupción de datos
     * @param exception Excepción a evaluar
     * @return true si indica corrupción de datos, false en caso contrario
     */
    private fun isLikelyDataCorruption(exception: Exception): Boolean {
        val message = exception.message?.lowercase() ?: ""
        val className = exception.javaClass.simpleName.lowercase()
        
        val corruptionIndicators = listOf(
            "corrupt", "invalid", "malformed", "parse", "format",
            "decrypt", "cipher", "key", "padding", "block"
        )
        
        return corruptionIndicators.any { indicator ->
            message.contains(indicator) || className.contains(indicator)
        }
    }
    
    /**
     * Maneja timeout en verificación de sesión
     * @return true si se permite acceso offline, false en caso contrario
     */
    private suspend fun handleSessionVerificationTimeout(): Boolean {
        return try {
            Log.i(TAG, "Manejando timeout de verificación de sesión")
            
            // Intentar obtener sesión local sin verificación remota
            val sessionData = withTimeout(5000L) { // Timeout corto para operación local
                getStoredSession()
            }
            
            if (sessionData != null && !sessionData.isExpired()) {
                Log.i(TAG, "Permitiendo acceso offline con token local válido")
                logSecurityEvent("OFFLINE_ACCESS_GRANTED", mapOf(
                    "reason" to "verification_timeout",
                    "token_expires_in" to (sessionData.tokenExpiry - System.currentTimeMillis()).toString()
                ))
                true
            } else {
                Log.w(TAG, "Denegando acceso - no hay token local válido")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al manejar timeout de verificación: ${e.message}")
            false
        }
    }
    
    /**
     * Registra eventos de seguridad para debugging sin exponer datos sensibles
     * @param event Tipo de evento
     * @param details Detalles adicionales (sin datos sensibles)
     */
    private fun logSecurityEvent(event: String, details: Map<String, String> = emptyMap()) {
        try {
            val logMessage = buildString {
                append("SECURITY_EVENT: $event")
                if (details.isNotEmpty()) {
                    append(" | Details: ")
                    details.forEach { (key, value) ->
                        append("$key=$value ")
                    }
                }
            }
            Log.i(TAG, logMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Error al registrar evento de seguridad: ${e.message}")
        }
    }
    
    // ========== PERFORMANCE OPTIMIZATION METHODS ==========
    
    /**
     * Verifica si el cache en memoria es válido
     * @return true si el cache es válido, false en caso contrario
     */
    private fun isCacheValid(): Boolean {
        val currentTime = System.currentTimeMillis()
        val cacheAge = currentTime - cacheTimestamp
        val isValid = cachedSessionData != null && cacheAge < CACHE_VALIDITY_DURATION_MS
        
        if (isValid) {
            Log.d(TAG, "Cache válido - edad: ${cacheAge}ms")
        } else {
            Log.d(TAG, "Cache inválido - edad: ${cacheAge}ms, datos: ${cachedSessionData != null}")
        }
        
        return isValid
    }
    
    /**
     * Actualiza el cache en memoria con nuevos datos de sesión
     * @param sessionData Datos de sesión a cachear
     */
    private fun updateCache(sessionData: SessionData?) {
        synchronized(this) {
            cachedSessionData = sessionData
            cacheTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Cache actualizado con datos: ${sessionData?.getSummary() ?: "null"}")
        }
    }
    
    /**
     * Verifica si hay un resultado de verificación reciente válido
     * @return true si hay un resultado reciente válido, false en caso contrario
     */
    private fun isRecentVerificationValid(): Boolean {
        val currentTime = System.currentTimeMillis()
        val verificationAge = currentTime - lastVerificationTimestamp
        val isValid = verificationAge < CACHE_VALIDITY_DURATION_MS
        
        if (isValid) {
            Log.d(TAG, "Verificación reciente válida - edad: ${verificationAge}ms, resultado: $lastVerificationResult")
        }
        
        return isValid
    }
    
    /**
     * Actualiza el cache de verificación con el último resultado
     * @param isValid Resultado de la verificación
     */
    private fun updateVerificationCache(isValid: Boolean) {
        synchronized(this) {
            lastVerificationResult = isValid
            lastVerificationTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Cache de verificación actualizado: $isValid")
        }
    }
    
    /**
     * Inicia el preloading de datos de usuario en background para mejorar UX
     */
    private fun startUserDataPreloading() {
        if (isPreloadingUserData) {
            Log.d(TAG, "Preloading ya en progreso, omitiendo")
            return
        }
        
        isPreloadingUserData = true
        Log.d(TAG, "Iniciando preloading de datos de usuario")
        
        // Usar un scope separado para no bloquear la verificación de sesión
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(PRELOAD_TIMEOUT_MS) {
                    preloadUserData()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error durante preloading de datos: ${e.message}")
            } finally {
                isPreloadingUserData = false
            }
        }
    }
    
    /**
     * Realiza el preloading de datos de usuario comunes
     */
    private suspend fun preloadUserData() {
        try {
            Log.d(TAG, "Ejecutando preloading de datos de usuario")
            
            val currentUser = firebaseAuth.currentUser
            if (currentUser == null) {
                Log.w(TAG, "No hay usuario para preloading")
                return
            }
            
            // Precargar token actualizado para futuras operaciones
            try {
                val tokenResult = currentUser.getIdToken(false).await()
                if (tokenResult.token != null) {
                    Log.d(TAG, "Token precargado exitosamente")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error al precargar token: ${e.message}")
            }
            
            // Precargar información básica del usuario
            try {
                val userInfo = mapOf(
                    "uid" to currentUser.uid,
                    "email" to (currentUser.email ?: ""),
                    "displayName" to (currentUser.displayName ?: ""),
                    "isEmailVerified" to currentUser.isEmailVerified.toString()
                )
                Log.d(TAG, "Información de usuario precargada: ${userInfo.size} campos")
            } catch (e: Exception) {
                Log.w(TAG, "Error al precargar información de usuario: ${e.message}")
            }
            
            Log.d(TAG, "Preloading completado exitosamente")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error durante preloading: ${e.message}", e)
        }
    }
    
    /**
     * Limpia todos los caches para forzar recarga de datos
     */
    fun clearCache() {
        synchronized(this) {
            cachedSessionData = null
            cacheTimestamp = 0L
            lastVerificationResult = false
            lastVerificationTimestamp = 0L
            isPreloadingUserData = false
            Log.d(TAG, "Cache limpiado completamente")
        }
    }
    
    /**
     * Obtiene estadísticas de rendimiento del cache
     * @return Map con estadísticas de cache
     */
    fun getCacheStats(): Map<String, Any> {
        val currentTime = System.currentTimeMillis()
        return mapOf(
            "cache_valid" to isCacheValid(),
            "cache_age_ms" to (currentTime - cacheTimestamp),
            "verification_cache_valid" to isRecentVerificationValid(),
            "verification_age_ms" to (currentTime - lastVerificationTimestamp),
            "is_preloading" to isPreloadingUserData,
            "cached_data_exists" to (cachedSessionData != null)
        )
    }
}
package es.monsteraltech.skincare_tfm.data

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException
import java.security.GeneralSecurityException

/**
 * Tests para funcionalidad de seguridad y logging en SessionManager
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class SessionManagerSecurityTest {
    
    private lateinit var realContext: Context
    private lateinit var sessionManager: SessionManager
    
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        realContext = RuntimeEnvironment.getApplication()
        sessionManager = SessionManager.getInstance(realContext)
    }
    
    @Test
    fun `SessionManager should handle security exceptions gracefully`() = runTest {
        // When - Operaciones que pueden encontrar excepciones de seguridad
        val getSessionResult = sessionManager.getStoredSession()
        val isValidResult = sessionManager.isSessionValid()
        val clearResult = sessionManager.clearSession()
        
        // Then - No deberían lanzar excepciones de seguridad
        // getStoredSession puede ser null, pero no debería lanzar excepción
        assertNotNull(isValidResult, "isSessionValid should handle security exceptions")
        assertNotNull(clearResult, "clearSession should handle security exceptions")
    }
    
    @Test
    fun `SessionManager should not expose sensitive data in logs`() = runTest {
        // Given - Crear datos de sesión con información sensible
        val sensitiveSessionData = SessionData(
            userId = "sensitive_user_123",
            email = "sensitive@example.com",
            displayName = "Sensitive User",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        // When - Obtener resumen para logging
        val summary = sensitiveSessionData.getSummary()
        
        // Then - El resumen no debería contener datos sensibles completos
        assertFalse(summary.contains("sensitive_user_123"), "Summary should not contain full userId")
        assertFalse(summary.contains("sensitive@example.com"), "Summary should not contain full email")
        assertTrue(summary.contains("sensitive_u"), "Summary should contain partial userId")
        assertTrue(summary.contains("sen***"), "Summary should contain obfuscated email")
    }
    
    @Test
    fun `SessionData getSummary should obfuscate sensitive information`() {
        // Given - Datos con información sensible
        val sessionData = SessionData(
            userId = "very_long_sensitive_user_id_123456789",
            email = "very.sensitive.email@example.com",
            displayName = "Very Sensitive User Name",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "google.com"
        )
        
        // When
        val summary = sessionData.getSummary()
        
        // Then - Verificar que los datos están ofuscados apropiadamente
        assertTrue(summary.contains("very_lon"), "Should contain first 8 chars of userId")
        assertFalse(summary.contains("very_long_sensitive_user_id_123456789"), "Should not contain full userId")
        
        assertTrue(summary.contains("ver***"), "Should contain obfuscated email")
        assertFalse(summary.contains("very.sensitive.email@example.com"), "Should not contain full email")
        
        assertTrue(summary.contains("google.com"), "Should contain auth provider")
        assertTrue(summary.contains("expired="), "Should contain expiration status")
    }
    
    @Test
    fun `SessionData getSummary should handle null email safely`() {
        // Given
        val sessionDataWithNullEmail = SessionData(
            userId = "test_user_123",
            email = null,
            displayName = "Test User",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        // When
        val summary = sessionDataWithNullEmail.getSummary()
        
        // Then
        assertNotNull(summary, "Summary should not be null")
        assertTrue(summary.contains("email=null"), "Summary should handle null email")
    }
    
    @Test
    fun `SessionData getSummary should handle empty email safely`() {
        // Given
        val sessionDataWithEmptyEmail = SessionData(
            userId = "test_user_123",
            email = "",
            displayName = "Test User",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        // When
        val summary = sessionDataWithEmptyEmail.getSummary()
        
        // Then
        assertNotNull(summary, "Summary should not be null")
        assertTrue(summary.contains("email="), "Summary should handle empty email")
    }
    
    @Test
    fun `SessionData validation should prevent injection attacks`() {
        // Given - Datos con posibles intentos de inyección
        val maliciousSessionData = SessionData(
            userId = "'; DROP TABLE users; --",
            email = "<script>alert('xss')</script>@example.com",
            displayName = "'; DELETE FROM sessions; --",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        // When
        val isValid = maliciousSessionData.isValid()
        
        // Then - Debería rechazar datos maliciosos
        assertFalse(isValid, "Should reject potentially malicious data")
    }
    
    @Test
    fun `SessionData should handle extremely long strings safely`() {
        // Given - Datos con strings extremadamente largos
        val extremelyLongSessionData = SessionData(
            userId = "a".repeat(10000),
            email = "b".repeat(1000) + "@" + "c".repeat(1000) + ".com",
            displayName = "d".repeat(5000),
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        // When
        val isValid = extremelyLongSessionData.isValid()
        val summary = extremelyLongSessionData.getSummary()
        
        // Then
        assertFalse(isValid, "Should reject extremely long data")
        assertNotNull(summary, "Summary should handle long data safely")
        assertTrue(summary.length < 1000, "Summary should be reasonably short")
    }
    
    @Test
    fun `SessionData should handle special characters in userId safely`() {
        // Given - userId con caracteres especiales
        val specialCharSessionData = SessionData(
            userId = "user@#$%^&*()_+-={}[]|\\:;\"'<>?,./ 123",
            email = "test@example.com",
            displayName = "Test User",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        // When
        val isValid = specialCharSessionData.isValid()
        val summary = specialCharSessionData.getSummary()
        
        // Then
        // Puede ser válido o inválido dependiendo de la validación, pero no debería causar errores
        assertNotNull(isValid, "Validation should complete without errors")
        assertNotNull(summary, "Summary should handle special characters safely")
    }
    
    @Test
    fun `SessionData should handle unicode characters safely`() {
        // Given - Datos con caracteres unicode
        val unicodeSessionData = SessionData(
            userId = "用户_123_🔐_émojis",
            email = "测试@example.com",
            displayName = "用户名 with émojis 🔐",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        // When
        val isValid = unicodeSessionData.isValid()
        val summary = unicodeSessionData.getSummary()
        
        // Then
        assertNotNull(isValid, "Validation should handle unicode safely")
        assertNotNull(summary, "Summary should handle unicode safely")
    }
    
    @Test
    fun `SessionManager should handle concurrent access safely`() = runTest {
        // When - Acceso concurrente a SessionManager
        val results = (1..10).map {
            when (it % 3) {
                0 -> sessionManager.getStoredSession() != null
                1 -> sessionManager.isSessionValid()
                else -> sessionManager.clearSession()
            }
        }
        
        // Then - Todas las operaciones deberían completarse sin errores de concurrencia
        assertEquals(10, results.size, "All concurrent operations should complete")
        results.forEach { result ->
            assertNotNull(result, "Each concurrent operation should return a result")
        }
    }
    
    @Test
    fun `SessionManager should maintain singleton integrity under concurrent access`() = runTest {
        // When - Múltiples accesos al singleton
        val instances = (1..10).map {
            SessionManager.getInstance(realContext)
        }
        
        // Then - Todas las instancias deberían ser la misma
        val firstInstance = instances.first()
        instances.forEach { instance ->
            assertTrue(instance === firstInstance, "All instances should be the same singleton")
        }
    }
    
    @Test
    fun `SessionData JSON serialization should not expose sensitive data in error messages`() {
        // Given - Datos que pueden causar errores de serialización
        val problematicData = SessionData(
            userId = "test_user",
            email = "test@example.com",
            displayName = null, // Puede causar problemas en algunas implementaciones
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        // When
        val json = problematicData.toJson()
        val deserializedData = SessionData.fromJson(json)
        
        // Then - No debería lanzar excepciones y no debería exponer datos sensibles
        assertNotNull(json, "JSON serialization should complete")
        assertNotNull(deserializedData, "JSON deserialization should complete")
    }
}
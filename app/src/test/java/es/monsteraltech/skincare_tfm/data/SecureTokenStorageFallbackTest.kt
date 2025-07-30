package es.monsteraltech.skincare_tfm.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests para funcionalidad de fallback en SecureTokenStorage
 */
@RunWith(RobolectricTestRunner::class)
class SecureTokenStorageFallbackTest {

    private lateinit var secureTokenStorage: SecureTokenStorage
    private lateinit var realContext: Context

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        realContext = RuntimeEnvironment.getApplication()
        secureTokenStorage = SecureTokenStorage(realContext)
    }

    @Test
    fun `storeToken should work with fallback when keystore unavailable`() = runTest {
        // Given
        val key = "fallback_test_key"
        val token = "fallback_test_token"
        
        // When - Intentar almacenar token (puede usar fallback si keystore no está disponible)
        val result = secureTokenStorage.storeToken(key, token)
        
        // Then
        assertTrue(result, "storeToken should succeed even with fallback")
    }

    @Test
    fun `retrieveToken should work with fallback data`() = runTest {
        // Given
        val key = "fallback_retrieve_key"
        val originalToken = "fallback_original_token"
        
        // When
        val storeResult = secureTokenStorage.storeToken(key, originalToken)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        
        // Then
        assertTrue(storeResult, "Token should be stored successfully")
        assertEquals(originalToken, retrievedToken, "Retrieved token should match original even with fallback")
    }

    @Test
    fun `fallback should handle special characters correctly`() = runTest {
        // Given
        val key = "fallback_special_chars"
        val tokenWithSpecialChars = "token_with_!@#$%^&*()_+-={}[]|\\:;\"'<>?,./"
        
        // When
        val storeResult = secureTokenStorage.storeToken(key, tokenWithSpecialChars)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        
        // Then
        assertTrue(storeResult, "Should store token with special characters in fallback")
        assertEquals(tokenWithSpecialChars, retrievedToken, "Should retrieve special characters correctly from fallback")
    }

    @Test
    fun `fallback should handle unicode characters correctly`() = runTest {
        // Given
        val key = "fallback_unicode"
        val unicodeToken = "token_with_émojis_🔐_and_中文"
        
        // When
        val storeResult = secureTokenStorage.storeToken(key, unicodeToken)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        
        // Then
        assertTrue(storeResult, "Should store unicode token in fallback")
        assertEquals(unicodeToken, retrievedToken, "Should retrieve unicode token correctly from fallback")
    }

    @Test
    fun `fallback should handle long tokens correctly`() = runTest {
        // Given
        val key = "fallback_long_token"
        val longToken = "a".repeat(5000) // Token de 5KB
        
        // When
        val storeResult = secureTokenStorage.storeToken(key, longToken)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        
        // Then
        assertTrue(storeResult, "Should store long token in fallback")
        assertEquals(longToken, retrievedToken, "Should retrieve long token correctly from fallback")
    }

    @Test
    fun `fallback should handle empty tokens`() = runTest {
        // Given
        val key = "fallback_empty_token"
        val emptyToken = ""
        
        // When
        val storeResult = secureTokenStorage.storeToken(key, emptyToken)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        
        // Then
        assertTrue(storeResult, "Should store empty token in fallback")
        assertEquals(emptyToken, retrievedToken, "Should retrieve empty token correctly from fallback")
    }

    @Test
    fun `fallback should handle whitespace-only tokens`() = runTest {
        // Given
        val key = "fallback_whitespace_token"
        val whitespaceToken = "   \t\n\r   "
        
        // When
        val storeResult = secureTokenStorage.storeToken(key, whitespaceToken)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        
        // Then
        assertTrue(storeResult, "Should store whitespace token in fallback")
        assertEquals(whitespaceToken, retrievedToken, "Should retrieve whitespace token correctly from fallback")
    }

    @Test
    fun `deleteToken should work with fallback data`() = runTest {
        // Given
        val key = "fallback_delete_test"
        val token = "token_to_delete"
        
        // When
        secureTokenStorage.storeToken(key, token)
        val deleteResult = secureTokenStorage.deleteToken(key)
        val retrievedAfterDelete = secureTokenStorage.retrieveToken(key)
        
        // Then
        assertTrue(deleteResult, "deleteToken should succeed with fallback")
        assertNull(retrievedAfterDelete, "Token should be null after deletion from fallback")
    }

    @Test
    fun `tokenExists should work with fallback data`() = runTest {
        // Given
        val key = "fallback_exists_test"
        val token = "existing_fallback_token"
        
        // When
        secureTokenStorage.storeToken(key, token)
        val exists = secureTokenStorage.tokenExists(key)
        
        // Then
        assertTrue(exists, "tokenExists should return true for fallback data")
    }

    @Test
    fun `tokenExists should return false for non-existent fallback data`() = runTest {
        // Given
        val nonExistentKey = "non_existent_fallback_key"
        
        // When
        val exists = secureTokenStorage.tokenExists(nonExistentKey)
        
        // Then
        assertFalse(exists, "tokenExists should return false for non-existent fallback data")
    }

    @Test
    fun `multiple fallback tokens should be independent`() = runTest {
        // Given
        val keys = listOf("fallback_key1", "fallback_key2", "fallback_key3")
        val tokens = listOf("fallback_token1", "fallback_token2", "fallback_token3")
        
        // When
        keys.zip(tokens).forEach { (key, token) ->
            secureTokenStorage.storeToken(key, token)
        }
        
        val retrievedTokens = keys.map { key ->
            secureTokenStorage.retrieveToken(key)
        }
        
        // Then
        retrievedTokens.forEachIndexed { index, retrievedToken ->
            assertEquals(tokens[index], retrievedToken, "Fallback token $index should be retrieved correctly")
        }
    }

    @Test
    fun `overwriting fallback token should work correctly`() = runTest {
        // Given
        val key = "fallback_overwrite_key"
        val originalToken = "original_fallback_token"
        val newToken = "new_fallback_token"
        
        // When
        secureTokenStorage.storeToken(key, originalToken)
        val originalRetrieved = secureTokenStorage.retrieveToken(key)
        
        secureTokenStorage.storeToken(key, newToken)
        val newRetrieved = secureTokenStorage.retrieveToken(key)
        
        // Then
        assertEquals(originalToken, originalRetrieved, "Should retrieve original fallback token first")
        assertEquals(newToken, newRetrieved, "Should retrieve new fallback token after overwrite")
    }

    @Test
    fun `fallback should provide basic obfuscation`() = runTest {
        // Given
        val key = "obfuscation_test_key"
        val plainToken = "plain_text_token_123"
        
        // When
        val storeResult = secureTokenStorage.storeToken(key, plainToken)
        
        // Then
        assertTrue(storeResult, "Token should be stored successfully")
        
        // Verificar que el token no se almacena en texto plano en SharedPreferences
        val sharedPrefs = realContext.getSharedPreferences("secure_session_prefs", Context.MODE_PRIVATE)
        val storedValue = sharedPrefs.getString("${key}_fallback", null)
        
        if (storedValue != null) {
            // Si se usa fallback, el valor almacenado no debería ser igual al token original
            // (debería estar ofuscado)
            assertTrue(storedValue != plainToken, "Fallback token should be obfuscated, not stored as plain text")
        }
    }

    @Test
    fun `fallback should handle corrupted obfuscated data gracefully`() = runTest {
        // Given - Simular datos ofuscados corruptos en SharedPreferences
        val key = "corrupted_fallback_key"
        val sharedPrefs = realContext.getSharedPreferences("secure_session_prefs", Context.MODE_PRIVATE)
        
        // Almacenar datos corruptos directamente
        sharedPrefs.edit()
            .putString("${key}_fallback", "corrupted_base64_data_!@#$%")
            .putBoolean("${key}_is_fallback", true)
            .apply()
        
        // When
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        
        // Then
        assertNull(retrievedToken, "Should return null for corrupted fallback data")
    }

    @Test
    fun `fallback should handle invalid base64 data gracefully`() = runTest {
        // Given - Simular datos base64 inválidos
        val key = "invalid_base64_key"
        val sharedPrefs = realContext.getSharedPreferences("secure_session_prefs", Context.MODE_PRIVATE)
        
        // Almacenar datos base64 inválidos
        sharedPrefs.edit()
            .putString("${key}_fallback", "invalid_base64_!@#$%^&*()")
            .putBoolean("${key}_is_fallback", true)
            .apply()
        
        // When
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        
        // Then
        assertNull(retrievedToken, "Should return null for invalid base64 fallback data")
    }
}
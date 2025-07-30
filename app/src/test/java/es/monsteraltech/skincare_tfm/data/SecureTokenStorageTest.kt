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

@RunWith(RobolectricTestRunner::class)
class SecureTokenStorageTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor
    
    private lateinit var secureTokenStorage: SecureTokenStorage
    private lateinit var realContext: Context

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        realContext = RuntimeEnvironment.getApplication()
        
        // Configurar mocks
        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(mockSharedPreferences)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.remove(any())).thenReturn(mockEditor)
        whenever(mockEditor.apply()).then { }
        
        // Usar contexto real para pruebas que requieren Keystore
        secureTokenStorage = SecureTokenStorage(realContext)
    }

    @Test
    fun `storeToken should return true when token is stored successfully`() = runTest {
        // Given
        val key = "test_token_key"
        val token = "test_token_value"
        
        // When
        val result = secureTokenStorage.storeToken(key, token)
        
        // Then
        assertTrue(result, "storeToken should return true for successful storage")
    }

    @Test
    fun `storeToken should handle empty token gracefully`() = runTest {
        // Given
        val key = "test_key"
        val emptyToken = ""
        
        // When
        val result = secureTokenStorage.storeToken(key, emptyToken)
        
        // Then
        assertTrue(result, "storeToken should handle empty token")
    }

    @Test
    fun `retrieveToken should return stored token`() = runTest {
        // Given
        val key = "test_retrieve_key"
        val originalToken = "original_token_value"
        
        // When
        val storeResult = secureTokenStorage.storeToken(key, originalToken)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        
        // Then
        assertTrue(storeResult, "Token should be stored successfully")
        assertEquals(originalToken, retrievedToken, "Retrieved token should match original")
    }

    @Test
    fun `retrieveToken should return null for non-existent key`() = runTest {
        // Given
        val nonExistentKey = "non_existent_key"
        
        // When
        val result = secureTokenStorage.retrieveToken(nonExistentKey)
        
        // Then
        assertNull(result, "retrieveToken should return null for non-existent key")
    }

    @Test
    fun `deleteToken should remove stored token`() = runTest {
        // Given
        val key = "test_delete_key"
        val token = "token_to_delete"
        
        // When
        secureTokenStorage.storeToken(key, token)
        val deleteResult = secureTokenStorage.deleteToken(key)
        val retrievedAfterDelete = secureTokenStorage.retrieveToken(key)
        
        // Then
        assertTrue(deleteResult, "deleteToken should return true")
        assertNull(retrievedAfterDelete, "Token should be null after deletion")
    }

    @Test
    fun `tokenExists should return true for existing token`() = runTest {
        // Given
        val key = "test_exists_key"
        val token = "existing_token"
        
        // When
        secureTokenStorage.storeToken(key, token)
        val exists = secureTokenStorage.tokenExists(key)
        
        // Then
        assertTrue(exists, "tokenExists should return true for existing token")
    }

    @Test
    fun `tokenExists should return false for non-existent token`() = runTest {
        // Given
        val nonExistentKey = "non_existent_key"
        
        // When
        val exists = secureTokenStorage.tokenExists(nonExistentKey)
        
        // Then
        assertFalse(exists, "tokenExists should return false for non-existent token")
    }

    @Test
    fun `storeToken should handle special characters in token`() = runTest {
        // Given
        val key = "special_chars_key"
        val tokenWithSpecialChars = "token_with_!@#$%^&*()_+-={}[]|\\:;\"'<>?,./"
        
        // When
        val storeResult = secureTokenStorage.storeToken(key, tokenWithSpecialChars)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        
        // Then
        assertTrue(storeResult, "Should store token with special characters")
        assertEquals(tokenWithSpecialChars, retrievedToken, "Should retrieve token with special characters correctly")
    }

    @Test
    fun `storeToken should handle unicode characters in token`() = runTest {
        // Given
        val key = "unicode_key"
        val unicodeToken = "token_with_émojis_🔐_and_中文"
        
        // When
        val storeResult = secureTokenStorage.storeToken(key, unicodeToken)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        
        // Then
        assertTrue(storeResult, "Should store token with unicode characters")
        assertEquals(unicodeToken, retrievedToken, "Should retrieve unicode token correctly")
    }

    @Test
    fun `storeToken should handle long tokens`() = runTest {
        // Given
        val key = "long_token_key"
        val longToken = "a".repeat(10000) // Token de 10KB
        
        // When
        val storeResult = secureTokenStorage.storeToken(key, longToken)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        
        // Then
        assertTrue(storeResult, "Should store long token")
        assertEquals(longToken, retrievedToken, "Should retrieve long token correctly")
    }

    @Test
    fun `multiple tokens can be stored and retrieved independently`() = runTest {
        // Given
        val keys = listOf("key1", "key2", "key3")
        val tokens = listOf("token1", "token2", "token3")
        
        // When
        keys.zip(tokens).forEach { (key, token) ->
            secureTokenStorage.storeToken(key, token)
        }
        
        val retrievedTokens = keys.map { key ->
            secureTokenStorage.retrieveToken(key)
        }
        
        // Then
        retrievedTokens.forEachIndexed { index, retrievedToken ->
            assertEquals(tokens[index], retrievedToken, "Token $index should be retrieved correctly")
        }
    }

    @Test
    fun `overwriting existing token should work correctly`() = runTest {
        // Given
        val key = "overwrite_key"
        val originalToken = "original_token"
        val newToken = "new_token"
        
        // When
        secureTokenStorage.storeToken(key, originalToken)
        val originalRetrieved = secureTokenStorage.retrieveToken(key)
        
        secureTokenStorage.storeToken(key, newToken)
        val newRetrieved = secureTokenStorage.retrieveToken(key)
        
        // Then
        assertEquals(originalToken, originalRetrieved, "Should retrieve original token first")
        assertEquals(newToken, newRetrieved, "Should retrieve new token after overwrite")
    }

    @Test
    fun `deleteToken should return true even for non-existent key`() = runTest {
        // Given
        val nonExistentKey = "non_existent_delete_key"
        
        // When
        val result = secureTokenStorage.deleteToken(nonExistentKey)
        
        // Then
        assertTrue(result, "deleteToken should return true even for non-existent key")
    }
}
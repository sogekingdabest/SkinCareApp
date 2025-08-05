package es.monsteraltech.skincare_tfm.data
import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        val key = "fallback_test_key"
        val token = "fallback_test_token"
        val result = secureTokenStorage.storeToken(key, token)
        assertTrue(result, "storeToken should succeed even with fallback")
    }
    @Test
    fun `retrieveToken should work with fallback data`() = runTest {
        val key = "fallback_retrieve_key"
        val originalToken = "fallback_original_token"
        val storeResult = secureTokenStorage.storeToken(key, originalToken)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        assertTrue(storeResult, "Token should be stored successfully")
        assertEquals(originalToken, retrievedToken, "Retrieved token should match original even with fallback")
    }
    @Test
    fun `fallback should handle special characters correctly`() = runTest {
        val key = "fallback_special_chars"
        val tokenWithSpecialChars = "token_with_!@#$%^&*()_+-={}[]|\\:;\"'<>?,./"
        val storeResult = secureTokenStorage.storeToken(key, tokenWithSpecialChars)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        assertTrue(storeResult, "Should store token with special characters in fallback")
        assertEquals(tokenWithSpecialChars, retrievedToken, "Should retrieve special characters correctly from fallback")
    }
    @Test
    fun `fallback should handle unicode characters correctly`() = runTest {
        val key = "fallback_unicode"
        val unicodeToken = "token_with_émojis_🔐_and_中文"
        val storeResult = secureTokenStorage.storeToken(key, unicodeToken)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        assertTrue(storeResult, "Should store unicode token in fallback")
        assertEquals(unicodeToken, retrievedToken, "Should retrieve unicode token correctly from fallback")
    }
    @Test
    fun `fallback should handle long tokens correctly`() = runTest {
        val key = "fallback_long_token"
        val longToken = "a".repeat(5000)
        val storeResult = secureTokenStorage.storeToken(key, longToken)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        assertTrue(storeResult, "Should store long token in fallback")
        assertEquals(longToken, retrievedToken, "Should retrieve long token correctly from fallback")
    }
    @Test
    fun `fallback should handle empty tokens`() = runTest {
        val key = "fallback_empty_token"
        val emptyToken = ""
        val storeResult = secureTokenStorage.storeToken(key, emptyToken)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        assertTrue(storeResult, "Should store empty token in fallback")
        assertEquals(emptyToken, retrievedToken, "Should retrieve empty token correctly from fallback")
    }
    @Test
    fun `fallback should handle whitespace-only tokens`() = runTest {
        val key = "fallback_whitespace_token"
        val whitespaceToken = "   \t\n\r   "
        val storeResult = secureTokenStorage.storeToken(key, whitespaceToken)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        assertTrue(storeResult, "Should store whitespace token in fallback")
        assertEquals(whitespaceToken, retrievedToken, "Should retrieve whitespace token correctly from fallback")
    }
    @Test
    fun `deleteToken should work with fallback data`() = runTest {
        val key = "fallback_delete_test"
        val token = "token_to_delete"
        secureTokenStorage.storeToken(key, token)
        val deleteResult = secureTokenStorage.deleteToken(key)
        val retrievedAfterDelete = secureTokenStorage.retrieveToken(key)
        assertTrue(deleteResult, "deleteToken should succeed with fallback")
        assertNull(retrievedAfterDelete, "Token should be null after deletion from fallback")
    }
    @Test
    fun `tokenExists should work with fallback data`() = runTest {
        val key = "fallback_exists_test"
        val token = "existing_fallback_token"
        secureTokenStorage.storeToken(key, token)
        val exists = secureTokenStorage.tokenExists(key)
        assertTrue(exists, "tokenExists should return true for fallback data")
    }
    @Test
    fun `tokenExists should return false for non-existent fallback data`() = runTest {
        val nonExistentKey = "non_existent_fallback_key"
        val exists = secureTokenStorage.tokenExists(nonExistentKey)
        assertFalse(exists, "tokenExists should return false for non-existent fallback data")
    }
    @Test
    fun `multiple fallback tokens should be independent`() = runTest {
        val keys = listOf("fallback_key1", "fallback_key2", "fallback_key3")
        val tokens = listOf("fallback_token1", "fallback_token2", "fallback_token3")
        keys.zip(tokens).forEach { (key, token) ->
            secureTokenStorage.storeToken(key, token)
        }
        val retrievedTokens = keys.map { key ->
            secureTokenStorage.retrieveToken(key)
        }
        retrievedTokens.forEachIndexed { index, retrievedToken ->
            assertEquals(tokens[index], retrievedToken, "Fallback token $index should be retrieved correctly")
        }
    }
    @Test
    fun `overwriting fallback token should work correctly`() = runTest {
        val key = "fallback_overwrite_key"
        val originalToken = "original_fallback_token"
        val newToken = "new_fallback_token"
        secureTokenStorage.storeToken(key, originalToken)
        val originalRetrieved = secureTokenStorage.retrieveToken(key)
        secureTokenStorage.storeToken(key, newToken)
        val newRetrieved = secureTokenStorage.retrieveToken(key)
        assertEquals(originalToken, originalRetrieved, "Should retrieve original fallback token first")
        assertEquals(newToken, newRetrieved, "Should retrieve new fallback token after overwrite")
    }
    @Test
    fun `fallback should provide basic obfuscation`() = runTest {
        val key = "obfuscation_test_key"
        val plainToken = "plain_text_token_123"
        val storeResult = secureTokenStorage.storeToken(key, plainToken)
        assertTrue(storeResult, "Token should be stored successfully")
        val sharedPrefs = realContext.getSharedPreferences("secure_session_prefs", Context.MODE_PRIVATE)
        val storedValue = sharedPrefs.getString("${key}_fallback", null)
        if (storedValue != null) {
            assertTrue(storedValue != plainToken, "Fallback token should be obfuscated, not stored as plain text")
        }
    }
    @Test
    fun `fallback should handle corrupted obfuscated data gracefully`() = runTest {
        val key = "corrupted_fallback_key"
        val sharedPrefs = realContext.getSharedPreferences("secure_session_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("${key}_fallback", "corrupted_base64_data_!@#$%")
            .putBoolean("${key}_is_fallback", true)
            .apply()
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        assertNull(retrievedToken, "Should return null for corrupted fallback data")
    }
    @Test
    fun `fallback should handle invalid base64 data gracefully`() = runTest {
        val key = "invalid_base64_key"
        val sharedPrefs = realContext.getSharedPreferences("secure_session_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("${key}_fallback", "invalid_base64_!@#$%^&*()")
            .putBoolean("${key}_is_fallback", true)
            .apply()
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        assertNull(retrievedToken, "Should return null for invalid base64 fallback data")
    }
}
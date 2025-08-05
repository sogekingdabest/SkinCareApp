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
        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(mockSharedPreferences)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.remove(any())).thenReturn(mockEditor)
        whenever(mockEditor.apply()).then { }
        secureTokenStorage = SecureTokenStorage(realContext)
    }
    @Test
    fun `storeToken should return true when token is stored successfully`() = runTest {
        val key = "test_token_key"
        val token = "test_token_value"
        val result = secureTokenStorage.storeToken(key, token)
        assertTrue(result, "storeToken should return true for successful storage")
    }
    @Test
    fun `storeToken should handle empty token gracefully`() = runTest {
        val key = "test_key"
        val emptyToken = ""
        val result = secureTokenStorage.storeToken(key, emptyToken)
        assertTrue(result, "storeToken should handle empty token")
    }
    @Test
    fun `retrieveToken should return stored token`() = runTest {
        val key = "test_retrieve_key"
        val originalToken = "original_token_value"
        val storeResult = secureTokenStorage.storeToken(key, originalToken)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        assertTrue(storeResult, "Token should be stored successfully")
        assertEquals(originalToken, retrievedToken, "Retrieved token should match original")
    }
    @Test
    fun `retrieveToken should return null for non-existent key`() = runTest {
        val nonExistentKey = "non_existent_key"
        val result = secureTokenStorage.retrieveToken(nonExistentKey)
        assertNull(result, "retrieveToken should return null for non-existent key")
    }
    @Test
    fun `deleteToken should remove stored token`() = runTest {
        val key = "test_delete_key"
        val token = "token_to_delete"
        secureTokenStorage.storeToken(key, token)
        val deleteResult = secureTokenStorage.deleteToken(key)
        val retrievedAfterDelete = secureTokenStorage.retrieveToken(key)
        assertTrue(deleteResult, "deleteToken should return true")
        assertNull(retrievedAfterDelete, "Token should be null after deletion")
    }
    @Test
    fun `tokenExists should return true for existing token`() = runTest {
        val key = "test_exists_key"
        val token = "existing_token"
        secureTokenStorage.storeToken(key, token)
        val exists = secureTokenStorage.tokenExists(key)
        assertTrue(exists, "tokenExists should return true for existing token")
    }
    @Test
    fun `tokenExists should return false for non-existent token`() = runTest {
        val nonExistentKey = "non_existent_key"
        val exists = secureTokenStorage.tokenExists(nonExistentKey)
        assertFalse(exists, "tokenExists should return false for non-existent token")
    }
    @Test
    fun `storeToken should handle special characters in token`() = runTest {
        val key = "special_chars_key"
        val tokenWithSpecialChars = "token_with_!@#$%^&*()_+-={}[]|\\:;\"'<>?,./"
        val storeResult = secureTokenStorage.storeToken(key, tokenWithSpecialChars)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        assertTrue(storeResult, "Should store token with special characters")
        assertEquals(tokenWithSpecialChars, retrievedToken, "Should retrieve token with special characters correctly")
    }
    @Test
    fun `storeToken should handle unicode characters in token`() = runTest {
        val key = "unicode_key"
        val unicodeToken = "token_with_émojis_🔐_and_中文"
        val storeResult = secureTokenStorage.storeToken(key, unicodeToken)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        assertTrue(storeResult, "Should store token with unicode characters")
        assertEquals(unicodeToken, retrievedToken, "Should retrieve unicode token correctly")
    }
    @Test
    fun `storeToken should handle long tokens`() = runTest {
        val key = "long_token_key"
        val longToken = "a".repeat(10000)
        val storeResult = secureTokenStorage.storeToken(key, longToken)
        val retrievedToken = secureTokenStorage.retrieveToken(key)
        assertTrue(storeResult, "Should store long token")
        assertEquals(longToken, retrievedToken, "Should retrieve long token correctly")
    }
    @Test
    fun `multiple tokens can be stored and retrieved independently`() = runTest {
        val keys = listOf("key1", "key2", "key3")
        val tokens = listOf("token1", "token2", "token3")
        keys.zip(tokens).forEach { (key, token) ->
            secureTokenStorage.storeToken(key, token)
        }
        val retrievedTokens = keys.map { key ->
            secureTokenStorage.retrieveToken(key)
        }
        retrievedTokens.forEachIndexed { index, retrievedToken ->
            assertEquals(tokens[index], retrievedToken, "Token $index should be retrieved correctly")
        }
    }
    @Test
    fun `overwriting existing token should work correctly`() = runTest {
        val key = "overwrite_key"
        val originalToken = "original_token"
        val newToken = "new_token"
        secureTokenStorage.storeToken(key, originalToken)
        val originalRetrieved = secureTokenStorage.retrieveToken(key)
        secureTokenStorage.storeToken(key, newToken)
        val newRetrieved = secureTokenStorage.retrieveToken(key)
        assertEquals(originalToken, originalRetrieved, "Should retrieve original token first")
        assertEquals(newToken, newRetrieved, "Should retrieve new token after overwrite")
    }
    @Test
    fun `deleteToken should return true even for non-existent key`() = runTest {
        val nonExistentKey = "non_existent_delete_key"
        val result = secureTokenStorage.deleteToken(nonExistentKey)
        assertTrue(result, "deleteToken should return true even for non-existent key")
    }
}
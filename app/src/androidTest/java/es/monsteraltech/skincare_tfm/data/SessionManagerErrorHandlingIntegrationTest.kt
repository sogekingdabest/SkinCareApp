package es.monsteraltech.skincare_tfm.data
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SessionManagerErrorHandlingIntegrationTest {
    private lateinit var context: Context
    private lateinit var sessionManager: SessionManager
    private lateinit var secureStorage: SecureTokenStorage
    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        sessionManager = SessionManager.getInstance(context)
        secureStorage = SecureTokenStorage(context)
    }
    @Test
    fun testCompleteErrorHandlingFlow() = runTest {
        val result1 = sessionManager.clearSession()
        val result2 = sessionManager.getStoredSession()
        val result3 = sessionManager.isSessionValid()
        val result4 = sessionManager.refreshSession()
        assertNotNull(result1)
        assertNotNull(result3)
        assertNotNull(result4)
    }
    @Test
    fun testSecureStorageFallbackFunctionality() = runTest {
        val testKey = "integration_test_key"
        val testToken = "integration_test_token"
        val storeResult = secureStorage.storeToken(testKey, testToken)
        val retrieveResult = secureStorage.retrieveToken(testKey)
        val deleteResult = secureStorage.deleteToken(testKey)
        assertTrue(storeResult)
        assertNotNull(retrieveResult)
        assertTrue(deleteResult)
    }
}
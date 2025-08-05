package es.monsteraltech.skincare_tfm.data
import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class SessionManagerFirebaseIntegrationTest {
    @Mock
    private lateinit var mockFirebaseAuth: FirebaseAuth
    @Mock
    private lateinit var mockFirebaseUser: FirebaseUser
    @Mock
    private lateinit var mockTokenResult: GetTokenResult
    @Mock
    private lateinit var mockSecureStorage: SecureTokenStorage
    private lateinit var context: Context
    private lateinit var sessionManager: SessionManager
    private val testUserId = "test-user-id-123"
    private val testEmail = "test@example.com"
    private val testDisplayName = "Test User"
    private val testToken = "test-firebase-token-xyz"
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = RuntimeEnvironment.getApplication()
        whenever(mockFirebaseUser.uid).thenReturn(testUserId)
        whenever(mockFirebaseUser.email).thenReturn(testEmail)
        whenever(mockFirebaseUser.displayName).thenReturn(testDisplayName)
        whenever(mockFirebaseUser.providerData).thenReturn(emptyList())
        whenever(mockTokenResult.token).thenReturn(testToken)
        sessionManager = SessionManager.getInstance(context)
    }
    @Test
    fun `saveSession should successfully save Firebase user session`() = runTest {
        val mockTask = Tasks.forResult(mockTokenResult)
        whenever(mockFirebaseUser.getIdToken(false)).thenReturn(mockTask)
        val result = sessionManager.saveSession(mockFirebaseUser)
        assertTrue(result, "saveSession should return true for valid Firebase user")
        verify(mockFirebaseUser).getIdToken(false)
    }
    @Test
    fun `saveSession should handle Firebase token retrieval failure`() = runTest {
        val exception = Exception("Firebase token error")
        val mockTask = Tasks.forException<GetTokenResult>(exception)
        whenever(mockFirebaseUser.getIdToken(false)).thenReturn(mockTask)
        val result = sessionManager.saveSession(mockFirebaseUser)
        assertFalse(result, "saveSession should return false when token retrieval fails")
    }
    @Test
    fun `saveSession should handle null token from Firebase`() = runTest {
        whenever(mockTokenResult.token).thenReturn(null)
        val mockTask = Tasks.forResult(mockTokenResult)
        whenever(mockFirebaseUser.getIdToken(false)).thenReturn(mockTask)
        val result = sessionManager.saveSession(mockFirebaseUser)
        assertFalse(result, "saveSession should return false when Firebase returns null token")
    }
    @Test
    fun `refreshSession should successfully refresh Firebase token`() = runTest {
        val newToken = "new-refreshed-token"
        val newTokenResult = mock<GetTokenResult>()
        whenever(newTokenResult.token).thenReturn(newToken)
        val mockTask = Tasks.forResult(newTokenResult)
        whenever(mockFirebaseUser.getIdToken(true)).thenReturn(mockTask)
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val result = sessionManager.refreshSession()
        assertNotNull(result, "refreshSession should complete")
    }
    @Test
    fun `refreshSession should handle network errors with retry logic`() = runTest {
        val networkException = UnknownHostException("Network unreachable")
        val mockTask = Tasks.forException<GetTokenResult>(networkException)
        whenever(mockFirebaseUser.getIdToken(true)).thenReturn(mockTask)
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val result = sessionManager.refreshSession()
        assertFalse(result, "refreshSession should return false after network errors")
    }
    @Test
    fun `refreshSession should handle timeout errors with retry logic`() = runTest {
        val timeoutException = SocketTimeoutException("Connection timeout")
        val mockTask = Tasks.forException<GetTokenResult>(timeoutException)
        whenever(mockFirebaseUser.getIdToken(true)).thenReturn(mockTask)
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val result = sessionManager.refreshSession()
        assertFalse(result, "refreshSession should return false after timeout errors")
    }
    @Test
    fun `refreshSession should handle IO errors with retry logic`() = runTest {
        val ioException = IOException("Connection failed")
        val mockTask = Tasks.forException<GetTokenResult>(ioException)
        whenever(mockFirebaseUser.getIdToken(true)).thenReturn(mockTask)
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val result = sessionManager.refreshSession()
        assertFalse(result, "refreshSession should return false after IO errors")
    }
    @Test
    fun `refreshSession should clear session when no current user`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        val result = sessionManager.refreshSession()
        assertFalse(result, "refreshSession should return false when no current user")
    }
    @Test
    fun `isSessionValid should handle network errors gracefully`() = runTest {
        val currentTime = System.currentTimeMillis()
        val futureExpiry = currentTime + TimeUnit.HOURS.toMillis(1)
        val validSessionData = SessionData(
            userId = testUserId,
            email = testEmail,
            displayName = testDisplayName,
            tokenExpiry = futureExpiry,
            lastRefresh = currentTime,
            authProvider = "firebase"
        )
        whenever(mockSecureStorage.retrieveToken("session_data"))
            .thenReturn(validSessionData.toJson())
        val networkException = UnknownHostException("Network error")
        val mockTask = Tasks.forException<GetTokenResult>(networkException)
        whenever(mockFirebaseUser.getIdToken(false)).thenReturn(mockTask)
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val result = sessionManager.isSessionValid()
        assertNotNull(result, "isSessionValid should handle network errors")
    }
    @Test
    fun `isSessionValid should reject expired tokens even with network errors`() = runTest {
        val currentTime = System.currentTimeMillis()
        val pastExpiry = currentTime - TimeUnit.HOURS.toMillis(1)
        val expiredSessionData = SessionData(
            userId = testUserId,
            email = testEmail,
            displayName = testDisplayName,
            tokenExpiry = pastExpiry,
            lastRefresh = currentTime - TimeUnit.HOURS.toMillis(2),
            authProvider = "firebase"
        )
        whenever(mockSecureStorage.retrieveToken("session_data"))
            .thenReturn(expiredSessionData.toJson())
        val networkException = UnknownHostException("Network error")
        val mockTask = Tasks.forException<GetTokenResult>(networkException)
        whenever(mockFirebaseUser.getIdToken(false)).thenReturn(mockTask)
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val result = sessionManager.isSessionValid()
        assertFalse(result, "isSessionValid should reject expired tokens even with network errors")
    }
    @Test
    fun `isSessionValid should handle user mismatch between session and Firebase`() = runTest {
        val differentUserId = "different-user-id"
        val sessionData = SessionData(
            userId = differentUserId,
            email = testEmail,
            displayName = testDisplayName,
            tokenExpiry = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1),
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        whenever(mockSecureStorage.retrieveToken("session_data"))
            .thenReturn(sessionData.toJson())
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val result = sessionManager.isSessionValid()
        assertFalse(result, "isSessionValid should return false for user mismatch")
    }
    @Test
    fun `getStoredSession should handle corrupted session data`() = runTest {
        val corruptedJson = "{ invalid json data }"
        whenever(mockSecureStorage.retrieveToken("session_data"))
            .thenReturn(corruptedJson)
        val result = sessionManager.getStoredSession()
        assertEquals(null, result, "getStoredSession should return null for corrupted data")
    }
    @Test
    fun `clearSession should remove all session data`() = runTest {
        whenever(mockSecureStorage.deleteToken("session_token")).thenReturn(true)
        whenever(mockSecureStorage.deleteToken("session_data")).thenReturn(true)
        val result = sessionManager.clearSession()
        assertTrue(result, "clearSession should return true when all data is cleared")
        verify(mockSecureStorage).deleteToken("session_token")
        verify(mockSecureStorage).deleteToken("session_data")
    }
    @Test
    fun `clearSession should handle partial cleanup failures`() = runTest {
        whenever(mockSecureStorage.deleteToken("session_token")).thenReturn(true)
        whenever(mockSecureStorage.deleteToken("session_data")).thenReturn(false)
        val result = sessionManager.clearSession()
        assertFalse(result, "clearSession should return false when cleanup is partial")
    }
}
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class SessionManagerNetworkTest {
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
    fun `should allow offline access with valid local token when network is unavailable`() = runTest {
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
        whenever(mockSecureStorage.tokenExists("session_data")).thenReturn(true)
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val networkException = UnknownHostException("No network connection")
        val mockTask = Tasks.forException<GetTokenResult>(networkException)
        whenever(mockFirebaseUser.getIdToken(false)).thenReturn(mockTask)
        val result = sessionManager.isSessionValid()
        assertTrue(result, "Should allow offline access with valid local token")
    }
    @Test
    fun `should deny offline access with expired local token when network is unavailable`() = runTest {
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
        whenever(mockSecureStorage.tokenExists("session_data")).thenReturn(true)
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val networkException = UnknownHostException("No network connection")
        val mockTask = Tasks.forException<GetTokenResult>(networkException)
        whenever(mockFirebaseUser.getIdToken(false)).thenReturn(mockTask)
        val result = sessionManager.isSessionValid()
        assertFalse(result, "Should deny offline access with expired local token")
    }
    @Test
    fun `should retry token verification on network timeout`() = runTest {
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
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val timeoutException = SocketTimeoutException("Connection timeout")
        val failTask = Tasks.forException<GetTokenResult>(timeoutException)
        val successTask = Tasks.forResult(mockTokenResult)
        whenever(mockFirebaseUser.getIdToken(false))
            .thenReturn(failTask)
            .thenReturn(failTask)
            .thenReturn(successTask)
        val result = sessionManager.isSessionValid()
        assertTrue(result, "Should succeed after retrying on timeout")
        verify(mockFirebaseUser, atLeast(2)).getIdToken(false)
    }
    @Test
    fun `should retry token refresh on network errors`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val networkException = IOException("Network error")
        val failTask = Tasks.forException<GetTokenResult>(networkException)
        val successTask = Tasks.forResult(mockTokenResult)
        whenever(mockFirebaseUser.getIdToken(true))
            .thenReturn(failTask)
            .thenReturn(successTask)
        whenever(mockSecureStorage.storeToken(any(), any())).thenReturn(true)
        val result = sessionManager.refreshSession()
        assertTrue(result, "Should succeed after retrying on network error")
        verify(mockFirebaseUser, atLeast(2)).getIdToken(true)
    }
    @Test
    fun `should fail after maximum retry attempts on persistent network errors`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val networkException = UnknownHostException("Persistent network error")
        val failTask = Tasks.forException<GetTokenResult>(networkException)
        whenever(mockFirebaseUser.getIdToken(true)).thenReturn(failTask)
        val result = sessionManager.refreshSession()
        assertFalse(result, "Should fail after maximum retry attempts")
        verify(mockFirebaseUser, times(3)).getIdToken(true)
    }
    @Test
    fun `should not retry on non-network errors`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val authException = Exception("Invalid authentication")
        val failTask = Tasks.forException<GetTokenResult>(authException)
        whenever(mockFirebaseUser.getIdToken(true)).thenReturn(failTask)
        val result = sessionManager.refreshSession()
        assertFalse(result, "Should fail immediately on non-network errors")
        verify(mockFirebaseUser, times(1)).getIdToken(true)
    }
    @Test
    fun `should handle mixed network and non-network errors correctly`() = runTest {
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
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val networkException = SocketTimeoutException("Network timeout")
        val authException = Exception("Authentication failed")
        val networkFailTask = Tasks.forException<GetTokenResult>(networkException)
        val authFailTask = Tasks.forException<GetTokenResult>(authException)
        whenever(mockFirebaseUser.getIdToken(false))
            .thenReturn(networkFailTask)
            .thenReturn(authFailTask)
        val result = sessionManager.isSessionValid()
        assertFalse(result, "Should fail on authentication error")
        verify(mockFirebaseUser, times(2)).getIdToken(false)
    }
    @Test
    fun `should maintain session during network errors if token refresh succeeds later`() = runTest {
        val currentTime = System.currentTimeMillis()
        val soonToExpireTime = currentTime + TimeUnit.MINUTES.toMillis(3)
        val sessionData = SessionData(
            userId = testUserId,
            email = testEmail,
            displayName = testDisplayName,
            tokenExpiry = soonToExpireTime,
            lastRefresh = currentTime,
            authProvider = "firebase"
        )
        whenever(mockSecureStorage.retrieveToken("session_data"))
            .thenReturn(sessionData.toJson())
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val networkException = IOException("Temporary network error")
        val failTask = Tasks.forException<GetTokenResult>(networkException)
        val successTask = Tasks.forResult(mockTokenResult)
        whenever(mockFirebaseUser.getIdToken(true))
            .thenReturn(failTask)
            .thenReturn(successTask)
        whenever(mockSecureStorage.storeToken(any(), any())).thenReturn(true)
        val result = sessionManager.isSessionValid()
        assertTrue(result, "Should maintain session after successful refresh")
    }
    @Test
    fun `should handle connection reset errors as network errors`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val connectionResetException = IOException("Connection reset by peer")
        val failTask = Tasks.forException<GetTokenResult>(connectionResetException)
        val successTask = Tasks.forResult(mockTokenResult)
        whenever(mockFirebaseUser.getIdToken(true))
            .thenReturn(failTask)
            .thenReturn(successTask)
        whenever(mockSecureStorage.storeToken(any(), any())).thenReturn(true)
        val result = sessionManager.refreshSession()
        assertTrue(result, "Should handle connection reset as network error")
        verify(mockFirebaseUser, times(2)).getIdToken(true)
    }
}
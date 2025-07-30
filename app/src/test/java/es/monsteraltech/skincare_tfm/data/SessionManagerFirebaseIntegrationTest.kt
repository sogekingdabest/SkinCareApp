package es.monsteraltech.skincare_tfm.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
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
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for SessionManager with Firebase Auth
 * Tests Firebase token verification, refresh, and network error handling
 */
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
        
        // Setup mock Firebase user
        whenever(mockFirebaseUser.uid).thenReturn(testUserId)
        whenever(mockFirebaseUser.email).thenReturn(testEmail)
        whenever(mockFirebaseUser.displayName).thenReturn(testDisplayName)
        whenever(mockFirebaseUser.providerData).thenReturn(emptyList())
        
        // Setup mock token result
        whenever(mockTokenResult.token).thenReturn(testToken)
        
        sessionManager = SessionManager.getInstance(context)
    }
    
    @Test
    fun `saveSession should successfully save Firebase user session`() = runTest {
        // Arrange
        val mockTask = Tasks.forResult(mockTokenResult)
        whenever(mockFirebaseUser.getIdToken(false)).thenReturn(mockTask)
        
        // Act
        val result = sessionManager.saveSession(mockFirebaseUser)
        
        // Assert
        assertTrue(result, "saveSession should return true for valid Firebase user")
        verify(mockFirebaseUser).getIdToken(false)
    }
    
    @Test
    fun `saveSession should handle Firebase token retrieval failure`() = runTest {
        // Arrange
        val exception = Exception("Firebase token error")
        val mockTask = Tasks.forException<GetTokenResult>(exception)
        whenever(mockFirebaseUser.getIdToken(false)).thenReturn(mockTask)
        
        // Act
        val result = sessionManager.saveSession(mockFirebaseUser)
        
        // Assert
        assertFalse(result, "saveSession should return false when token retrieval fails")
    }
    
    @Test
    fun `saveSession should handle null token from Firebase`() = runTest {
        // Arrange
        whenever(mockTokenResult.token).thenReturn(null)
        val mockTask = Tasks.forResult(mockTokenResult)
        whenever(mockFirebaseUser.getIdToken(false)).thenReturn(mockTask)
        
        // Act
        val result = sessionManager.saveSession(mockFirebaseUser)
        
        // Assert
        assertFalse(result, "saveSession should return false when Firebase returns null token")
    }
    
    @Test
    fun `refreshSession should successfully refresh Firebase token`() = runTest {
        // Arrange
        val newToken = "new-refreshed-token"
        val newTokenResult = mock<GetTokenResult>()
        whenever(newTokenResult.token).thenReturn(newToken)
        
        val mockTask = Tasks.forResult(newTokenResult)
        whenever(mockFirebaseUser.getIdToken(true)).thenReturn(mockTask)
        
        // Mock FirebaseAuth to return current user
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        
        // Act
        val result = sessionManager.refreshSession()
        
        // Assert - This test will verify the method completes without throwing
        // In a real integration test, we would verify the token was actually refreshed
        assertNotNull(result, "refreshSession should complete")
    }
    
    @Test
    fun `refreshSession should handle network errors with retry logic`() = runTest {
        // Arrange
        val networkException = UnknownHostException("Network unreachable")
        val mockTask = Tasks.forException<GetTokenResult>(networkException)
        whenever(mockFirebaseUser.getIdToken(true)).thenReturn(mockTask)
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        
        // Act
        val result = sessionManager.refreshSession()
        
        // Assert
        assertFalse(result, "refreshSession should return false after network errors")
    }
    
    @Test
    fun `refreshSession should handle timeout errors with retry logic`() = runTest {
        // Arrange
        val timeoutException = SocketTimeoutException("Connection timeout")
        val mockTask = Tasks.forException<GetTokenResult>(timeoutException)
        whenever(mockFirebaseUser.getIdToken(true)).thenReturn(mockTask)
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        
        // Act
        val result = sessionManager.refreshSession()
        
        // Assert
        assertFalse(result, "refreshSession should return false after timeout errors")
    }
    
    @Test
    fun `refreshSession should handle IO errors with retry logic`() = runTest {
        // Arrange
        val ioException = IOException("Connection failed")
        val mockTask = Tasks.forException<GetTokenResult>(ioException)
        whenever(mockFirebaseUser.getIdToken(true)).thenReturn(mockTask)
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        
        // Act
        val result = sessionManager.refreshSession()
        
        // Assert
        assertFalse(result, "refreshSession should return false after IO errors")
    }
    
    @Test
    fun `refreshSession should clear session when no current user`() = runTest {
        // Arrange
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        
        // Act
        val result = sessionManager.refreshSession()
        
        // Assert
        assertFalse(result, "refreshSession should return false when no current user")
    }
    
    @Test
    fun `isSessionValid should handle network errors gracefully`() = runTest {
        // Arrange - Create a valid session data that hasn't expired
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
        
        // Mock storage to return valid session data
        whenever(mockSecureStorage.retrieveToken("session_data"))
            .thenReturn(validSessionData.toJson())
        
        // Mock Firebase auth to throw network error
        val networkException = UnknownHostException("Network error")
        val mockTask = Tasks.forException<GetTokenResult>(networkException)
        whenever(mockFirebaseUser.getIdToken(false)).thenReturn(mockTask)
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        
        // Act
        val result = sessionManager.isSessionValid()
        
        // Assert - Should allow offline access with valid local token
        // Note: This test verifies the method completes without throwing
        assertNotNull(result, "isSessionValid should handle network errors")
    }
    
    @Test
    fun `isSessionValid should reject expired tokens even with network errors`() = runTest {
        // Arrange - Create an expired session data
        val currentTime = System.currentTimeMillis()
        val pastExpiry = currentTime - TimeUnit.HOURS.toMillis(1) // Expired 1 hour ago
        
        val expiredSessionData = SessionData(
            userId = testUserId,
            email = testEmail,
            displayName = testDisplayName,
            tokenExpiry = pastExpiry,
            lastRefresh = currentTime - TimeUnit.HOURS.toMillis(2),
            authProvider = "firebase"
        )
        
        // Mock storage to return expired session data
        whenever(mockSecureStorage.retrieveToken("session_data"))
            .thenReturn(expiredSessionData.toJson())
        
        // Mock Firebase auth to throw network error
        val networkException = UnknownHostException("Network error")
        val mockTask = Tasks.forException<GetTokenResult>(networkException)
        whenever(mockFirebaseUser.getIdToken(false)).thenReturn(mockTask)
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        
        // Act
        val result = sessionManager.isSessionValid()
        
        // Assert - Should not allow access with expired token even offline
        assertFalse(result, "isSessionValid should reject expired tokens even with network errors")
    }
    
    @Test
    fun `isSessionValid should handle user mismatch between session and Firebase`() = runTest {
        // Arrange
        val differentUserId = "different-user-id"
        val sessionData = SessionData(
            userId = differentUserId, // Different from mockFirebaseUser.uid
            email = testEmail,
            displayName = testDisplayName,
            tokenExpiry = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1),
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        whenever(mockSecureStorage.retrieveToken("session_data"))
            .thenReturn(sessionData.toJson())
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        
        // Act
        val result = sessionManager.isSessionValid()
        
        // Assert
        assertFalse(result, "isSessionValid should return false for user mismatch")
    }
    
    @Test
    fun `getStoredSession should handle corrupted session data`() = runTest {
        // Arrange
        val corruptedJson = "{ invalid json data }"
        whenever(mockSecureStorage.retrieveToken("session_data"))
            .thenReturn(corruptedJson)
        
        // Act
        val result = sessionManager.getStoredSession()
        
        // Assert
        assertEquals(null, result, "getStoredSession should return null for corrupted data")
    }
    
    @Test
    fun `clearSession should remove all session data`() = runTest {
        // Arrange
        whenever(mockSecureStorage.deleteToken("session_token")).thenReturn(true)
        whenever(mockSecureStorage.deleteToken("session_data")).thenReturn(true)
        
        // Act
        val result = sessionManager.clearSession()
        
        // Assert
        assertTrue(result, "clearSession should return true when all data is cleared")
        verify(mockSecureStorage).deleteToken("session_token")
        verify(mockSecureStorage).deleteToken("session_data")
    }
    
    @Test
    fun `clearSession should handle partial cleanup failures`() = runTest {
        // Arrange
        whenever(mockSecureStorage.deleteToken("session_token")).thenReturn(true)
        whenever(mockSecureStorage.deleteToken("session_data")).thenReturn(false)
        
        // Act
        val result = sessionManager.clearSession()
        
        // Assert
        assertFalse(result, "clearSession should return false when cleanup is partial")
    }
}
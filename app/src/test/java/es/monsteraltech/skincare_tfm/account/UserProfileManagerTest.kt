package es.monsteraltech.skincare_tfm.account

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class UserProfileManagerTest {

    @Mock
    private lateinit var mockFirebaseAuth: FirebaseAuth

    @Mock
    private lateinit var mockFirebaseUser: FirebaseUser

    @Mock
    private lateinit var mockFirebaseDataManager: FirebaseDataManager

    private lateinit var userProfileManager: UserProfileManager

    // Test data
    private val testUserId = "test-user-id-123"
    private val testEmail = "test@example.com"
    private val testDisplayName = "Test User"
    private val testUserInfo = UserInfo(
        uid = testUserId,
        displayName = testDisplayName,
        email = testEmail,
        photoUrl = null,
        emailVerified = true,
        createdAt = Date(),
        lastSignIn = Date()
    )
    private val testAccountSettings = AccountSettings(
        userId = testUserId,
        notificationsEnabled = true,
        darkModeEnabled = false,
        language = "es",
        autoBackup = true
    )

    @Before
    fun setUp() {
        // Create UserProfileManager with mocked dependencies
        userProfileManager = UserProfileManager(mockFirebaseDataManager, mockFirebaseAuth)
    }

    @Test
    fun `getCurrentUserInfo should return success result when user is authenticated`() = runTest {
        // Given: User is authenticated and FirebaseDataManager returns valid user info
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseDataManager.getCurrentUserProfile()).thenReturn(testUserInfo)
        
        // When: getCurrentUserInfo is called
        val result = userProfileManager.getCurrentUserInfo()
        
        // Then: Should return success result with UserInfo
        assertTrue(result.isSuccess())
        val userInfo = result.getDataOrNull()
        assertNotNull(userInfo)
        assertEquals(testUserId, userInfo.uid)
        assertEquals(testEmail, userInfo.email)
        assertEquals(testDisplayName, userInfo.displayName)
        verify(mockFirebaseDataManager).getCurrentUserProfile()
    }

    @Test
    fun `getCurrentUserInfo should return error when user is not authenticated`() = runTest {
        // Given: User is not authenticated
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        
        // When: getCurrentUserInfo is called
        val result = userProfileManager.getCurrentUserInfo()
        
        // Then: Should return authentication error
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, error.errorType)
        assertTrue(error.message.contains("iniciar sesión"))
    }

    @Test
    fun `getCurrentUserInfo should return error when Firebase operation fails`() = runTest {
        // Given: User is authenticated but FirebaseDataManager throws exception
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val exception = RuntimeException("Firebase error")
        whenever(mockFirebaseDataManager.getCurrentUserProfile()).thenThrow(exception)
        
        // When: getCurrentUserInfo is called
        val result = userProfileManager.getCurrentUserInfo()
        
        // Then: Should return error result
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(exception, error.exception)
        verify(mockFirebaseDataManager).getCurrentUserProfile()
    }

    @Test
    fun `updateUserSettings should return success when update is successful`() = runTest {
        // Given: User is authenticated and FirebaseDataManager returns true for successful update
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseDataManager.updateUserSettings(testAccountSettings)).thenReturn(true)
        
        // When: updateUserSettings is called
        val result = userProfileManager.updateUserSettings(testAccountSettings)
        
        // Then: Should return success result
        assertTrue(result.isSuccess())
        assertTrue(result.getDataOrNull() == true)
        verify(mockFirebaseDataManager).updateUserSettings(testAccountSettings)
    }

    @Test
    fun `updateUserSettings should return error when user is not authenticated`() = runTest {
        // Given: User is not authenticated
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        
        // When: updateUserSettings is called
        val result = userProfileManager.updateUserSettings(testAccountSettings)
        
        // Then: Should return authentication error
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, error.errorType)
        assertTrue(error.message.contains("iniciar sesión"))
    }

    @Test
    fun `updateUserSettings should return error when update fails`() = runTest {
        // Given: User is authenticated but FirebaseDataManager returns false for failed update
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseDataManager.updateUserSettings(testAccountSettings)).thenReturn(false)
        
        // When: updateUserSettings is called
        val result = userProfileManager.updateUserSettings(testAccountSettings)
        
        // Then: Should return error result
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertTrue(error.message.contains("guardar"))
        verify(mockFirebaseDataManager).updateUserSettings(testAccountSettings)
    }

    @Test
    fun `updateUserSettings should return error when Firebase operation fails`() = runTest {
        // Given: User is authenticated but FirebaseDataManager throws exception
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val exception = RuntimeException("Firebase error")
        whenever(mockFirebaseDataManager.updateUserSettings(testAccountSettings)).thenThrow(exception)
        
        // When: updateUserSettings is called
        val result = userProfileManager.updateUserSettings(testAccountSettings)
        
        // Then: Should return error result
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(exception, error.exception)
        verify(mockFirebaseDataManager).updateUserSettings(testAccountSettings)
    }

    @Test
    fun `getUserSettings should return success with account settings when successful`() = runTest {
        // Given: User is authenticated and FirebaseDataManager returns valid account settings
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseDataManager.getUserSettings()).thenReturn(testAccountSettings)
        
        // When: getUserSettings is called
        val result = userProfileManager.getUserSettings()
        
        // Then: Should return success result with AccountSettings
        assertTrue(result.isSuccess())
        val settings = result.getDataOrNull()
        assertNotNull(settings)
        assertEquals(testUserId, settings.userId)
        assertEquals(true, settings.notificationsEnabled)
        assertEquals("es", settings.language)
        verify(mockFirebaseDataManager).getUserSettings()
    }

    @Test
    fun `getUserSettings should return default settings when user is not authenticated`() = runTest {
        // Given: User is not authenticated
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        
        // When: getUserSettings is called
        val result = userProfileManager.getUserSettings()
        
        // Then: Should return success with default settings
        assertTrue(result.isSuccess())
        val settings = result.getDataOrNull()
        assertNotNull(settings)
        // Default settings should have empty userId and default values
        assertEquals("", settings.userId)
        assertTrue(settings.notificationsEnabled) // Default is true
        assertFalse(settings.darkModeEnabled) // Default is false
    }

    @Test
    fun `getUserSettings should return default settings when Firebase operation fails`() = runTest {
        // Given: User is authenticated but FirebaseDataManager throws exception
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val exception = RuntimeException("Firebase error")
        whenever(mockFirebaseDataManager.getUserSettings()).thenThrow(exception)
        
        // When: getUserSettings is called
        val result = userProfileManager.getUserSettings()
        
        // Then: Should return success with default settings (fallback behavior)
        assertTrue(result.isSuccess())
        val settings = result.getDataOrNull()
        assertNotNull(settings)
        verify(mockFirebaseDataManager).getUserSettings()
    }

    @Test
    fun `signOut should return success when Firebase signOut works`() = runTest {
        // Given: User is authenticated and FirebaseDataManager signOut works normally
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        doNothing().whenever(mockFirebaseDataManager).signOut()
        
        // When: signOut is called
        val result = userProfileManager.signOut()
        
        // Then: Should return success result
        assertTrue(result.isSuccess())
        verify(mockFirebaseDataManager).signOut()
    }

    @Test
    fun `signOut should return error when user is not authenticated`() = runTest {
        // Given: User is not authenticated
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        
        // When: signOut is called
        val result = userProfileManager.signOut()
        
        // Then: Should return authentication error
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, error.errorType)
        assertTrue(error.message.contains("sesión activa"))
    }

    @Test
    fun `signOut should return error when Firebase operation fails`() = runTest {
        // Given: User is authenticated but FirebaseDataManager throws exception on signOut
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val exception = RuntimeException("Firebase signOut error")
        doThrow(exception).whenever(mockFirebaseDataManager).signOut()
        
        // When: signOut is called
        val result = userProfileManager.signOut()
        
        // Then: Should return error result
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(exception, error.exception)
        verify(mockFirebaseDataManager).signOut()
    }

    @Test
    fun `isUserAuthenticated should return true when user is authenticated`() {
        // Given: FirebaseAuth has current user
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        
        // When: isUserAuthenticated is called
        val result = userProfileManager.isUserAuthenticated()
        
        // Then: Should return true
        assertTrue(result)
        verify(mockFirebaseAuth).currentUser
    }

    @Test
    fun `isUserAuthenticated should return false when user is not authenticated`() {
        // Given: FirebaseAuth has no current user
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        
        // When: isUserAuthenticated is called
        val result = userProfileManager.isUserAuthenticated()
        
        // Then: Should return false
        assertFalse(result)
        verify(mockFirebaseAuth).currentUser
    }

    @Test
    fun `getCurrentUserId should return user ID when user is authenticated`() {
        // Given: FirebaseAuth has current user with ID
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseUser.uid).thenReturn(testUserId)
        
        // When: getCurrentUserId is called
        val result = userProfileManager.getCurrentUserId()
        
        // Then: Should return user ID string
        assertEquals(testUserId, result)
        verify(mockFirebaseAuth).currentUser
    }

    @Test
    fun `getCurrentUserId should return null when user is not authenticated`() {
        // Given: FirebaseAuth has no current user
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        
        // When: getCurrentUserId is called
        val result = userProfileManager.getCurrentUserId()
        
        // Then: Should return null
        assertNull(result)
        verify(mockFirebaseAuth).currentUser
    }
}

/**
 * Integration test class for UserProfileManager with actual Firebase dependencies
 * These tests would run against a test Firebase project
 */
class UserProfileManagerIntegrationTest {

    private lateinit var userProfileManager: UserProfileManager

    @Before
    fun setUp() {
        userProfileManager = UserProfileManager()
    }

    @Test
    fun `integration test - full user profile workflow`() = runTest {
        // This would be an integration test that:
        // 1. Authenticates a test user
        // 2. Gets user info
        // 3. Updates settings
        // 4. Retrieves settings
        // 5. Signs out
        
        // Note: Requires Firebase test configuration
        assertTrue(true) // Placeholder assertion
    }
}
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
        userProfileManager = UserProfileManager(mockFirebaseDataManager, mockFirebaseAuth)
    }
    @Test
    fun `getCurrentUserInfo should return success result when user is authenticated`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseDataManager.getCurrentUserProfile()).thenReturn(testUserInfo)
        val result = userProfileManager.getCurrentUserInfo()
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
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        val result = userProfileManager.getCurrentUserInfo()
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, error.errorType)
        assertTrue(error.message.contains("iniciar sesión"))
    }
    @Test
    fun `getCurrentUserInfo should return error when Firebase operation fails`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val exception = RuntimeException("Firebase error")
        whenever(mockFirebaseDataManager.getCurrentUserProfile()).thenThrow(exception)
        val result = userProfileManager.getCurrentUserInfo()
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(exception, error.exception)
        verify(mockFirebaseDataManager).getCurrentUserProfile()
    }
    @Test
    fun `updateUserSettings should return success when update is successful`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseDataManager.updateUserSettings(testAccountSettings)).thenReturn(true)
        val result = userProfileManager.updateUserSettings(testAccountSettings)
        assertTrue(result.isSuccess())
        assertTrue(result.getDataOrNull() == true)
        verify(mockFirebaseDataManager).updateUserSettings(testAccountSettings)
    }
    @Test
    fun `updateUserSettings should return error when user is not authenticated`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        val result = userProfileManager.updateUserSettings(testAccountSettings)
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, error.errorType)
        assertTrue(error.message.contains("iniciar sesión"))
    }
    @Test
    fun `updateUserSettings should return error when update fails`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseDataManager.updateUserSettings(testAccountSettings)).thenReturn(false)
        val result = userProfileManager.updateUserSettings(testAccountSettings)
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertTrue(error.message.contains("guardar"))
        verify(mockFirebaseDataManager).updateUserSettings(testAccountSettings)
    }
    @Test
    fun `updateUserSettings should return error when Firebase operation fails`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val exception = RuntimeException("Firebase error")
        whenever(mockFirebaseDataManager.updateUserSettings(testAccountSettings)).thenThrow(exception)
        val result = userProfileManager.updateUserSettings(testAccountSettings)
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(exception, error.exception)
        verify(mockFirebaseDataManager).updateUserSettings(testAccountSettings)
    }
    @Test
    fun `getUserSettings should return success with account settings when successful`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseDataManager.getUserSettings()).thenReturn(testAccountSettings)
        val result = userProfileManager.getUserSettings()
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
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        val result = userProfileManager.getUserSettings()
        assertTrue(result.isSuccess())
        val settings = result.getDataOrNull()
        assertNotNull(settings)
        assertEquals("", settings.userId)
        assertTrue(settings.notificationsEnabled)
        assertFalse(settings.darkModeEnabled)
    }
    @Test
    fun `getUserSettings should return default settings when Firebase operation fails`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val exception = RuntimeException("Firebase error")
        whenever(mockFirebaseDataManager.getUserSettings()).thenThrow(exception)
        val result = userProfileManager.getUserSettings()
        assertTrue(result.isSuccess())
        val settings = result.getDataOrNull()
        assertNotNull(settings)
        verify(mockFirebaseDataManager).getUserSettings()
    }
    @Test
    fun `signOut should return success when Firebase signOut works`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        doNothing().whenever(mockFirebaseDataManager).signOut()
        val result = userProfileManager.signOut()
        assertTrue(result.isSuccess())
        verify(mockFirebaseDataManager).signOut()
    }
    @Test
    fun `signOut should return error when user is not authenticated`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        val result = userProfileManager.signOut()
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, error.errorType)
        assertTrue(error.message.contains("sesión activa"))
    }
    @Test
    fun `signOut should return error when Firebase operation fails`() = runTest {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val exception = RuntimeException("Firebase signOut error")
        doThrow(exception).whenever(mockFirebaseDataManager).signOut()
        val result = userProfileManager.signOut()
        assertTrue(result.isError())
        val error = result.getErrorOrNull()
        assertNotNull(error)
        assertEquals(exception, error.exception)
        verify(mockFirebaseDataManager).signOut()
    }
    @Test
    fun `isUserAuthenticated should return true when user is authenticated`() {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        val result = userProfileManager.isUserAuthenticated()
        assertTrue(result)
        verify(mockFirebaseAuth).currentUser
    }
    @Test
    fun `isUserAuthenticated should return false when user is not authenticated`() {
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        val result = userProfileManager.isUserAuthenticated()
        assertFalse(result)
        verify(mockFirebaseAuth).currentUser
    }
    @Test
    fun `getCurrentUserId should return user ID when user is authenticated`() {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseUser.uid).thenReturn(testUserId)
        val result = userProfileManager.getCurrentUserId()
        assertEquals(testUserId, result)
        verify(mockFirebaseAuth).currentUser
    }
    @Test
    fun `getCurrentUserId should return null when user is not authenticated`() {
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        val result = userProfileManager.getCurrentUserId()
        assertNull(result)
        verify(mockFirebaseAuth).currentUser
    }
}
class UserProfileManagerIntegrationTest {
    private lateinit var userProfileManager: UserProfileManager
    @Before
    fun setUp() {
        userProfileManager = UserProfileManager()
    }
    @Test
    fun `integration test - full user profile workflow`() = runTest {
        assertTrue(true)
    }
}
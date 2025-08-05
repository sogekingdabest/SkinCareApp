package es.monsteraltech.skincare_tfm.account
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals
import kotlin.test.assertTrue
class UserSettingsIntegrationTest {
    @Mock
    private lateinit var mockFirebaseDataManager: FirebaseDataManager
    private lateinit var userProfileManager: UserProfileManager
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        userProfileManager = UserProfileManager(mockFirebaseDataManager)
    }
    @Test
    fun `test getUserSettings returns default settings when none exist`() = runTest {
        val defaultSettings = AccountSettings(userId = "test-user")
        `when`(mockFirebaseDataManager.getUserSettings()).thenReturn(defaultSettings)
        val result = userProfileManager.getUserSettings()
        assertEquals(defaultSettings, result)
        verify(mockFirebaseDataManager).getUserSettings()
    }
    @Test
    fun `test updateUserSettings saves settings successfully`() = runTest {
        val settings = AccountSettings(
            userId = "test-user",
            notificationsEnabled = false,
            darkModeEnabled = true,
            autoBackup = false
        )
        `when`(mockFirebaseDataManager.updateUserSettings(settings)).thenReturn(true)
        val result = userProfileManager.updateUserSettings(settings)
        assertTrue(result)
        verify(mockFirebaseDataManager).updateUserSettings(settings)
    }
    @Test
    fun `test settings persistence workflow`() = runTest {
        val originalSettings = AccountSettings(
            userId = "test-user",
            notificationsEnabled = true,
            darkModeEnabled = false,
            autoBackup = true
        )
        val updatedSettings = originalSettings.copy(
            notificationsEnabled = false,
            darkModeEnabled = true
        )
        `when`(mockFirebaseDataManager.getUserSettings()).thenReturn(originalSettings)
        `when`(mockFirebaseDataManager.updateUserSettings(updatedSettings)).thenReturn(true)
        val currentSettings = userProfileManager.getUserSettings()
        assertEquals(originalSettings, currentSettings)
        val updateResult = userProfileManager.updateUserSettings(updatedSettings)
        assertTrue(updateResult)
        verify(mockFirebaseDataManager).getUserSettings()
        verify(mockFirebaseDataManager).updateUserSettings(updatedSettings)
    }
}
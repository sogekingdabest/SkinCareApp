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

/**
 * Integration tests for user settings functionality
 */
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
        // Arrange
        val defaultSettings = AccountSettings(userId = "test-user")
        `when`(mockFirebaseDataManager.getUserSettings()).thenReturn(defaultSettings)

        // Act
        val result = userProfileManager.getUserSettings()

        // Assert
        assertEquals(defaultSettings, result)
        verify(mockFirebaseDataManager).getUserSettings()
    }

    @Test
    fun `test updateUserSettings saves settings successfully`() = runTest {
        // Arrange
        val settings = AccountSettings(
            userId = "test-user",
            notificationsEnabled = false,
            darkModeEnabled = true,
            autoBackup = false
        )
        `when`(mockFirebaseDataManager.updateUserSettings(settings)).thenReturn(true)

        // Act
        val result = userProfileManager.updateUserSettings(settings)

        // Assert
        assertTrue(result)
        verify(mockFirebaseDataManager).updateUserSettings(settings)
    }

    @Test
    fun `test settings persistence workflow`() = runTest {
        // Arrange
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

        // Act - Get original settings
        val currentSettings = userProfileManager.getUserSettings()
        assertEquals(originalSettings, currentSettings)

        // Act - Update settings
        val updateResult = userProfileManager.updateUserSettings(updatedSettings)
        assertTrue(updateResult)

        // Assert
        verify(mockFirebaseDataManager).getUserSettings()
        verify(mockFirebaseDataManager).updateUserSettings(updatedSettings)
    }
}
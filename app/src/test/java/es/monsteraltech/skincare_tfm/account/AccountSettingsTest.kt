package es.monsteraltech.skincare_tfm.account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class AccountSettingsTest {
    @Test
    fun `test default AccountSettings values`() {
        val settings = AccountSettings()
        assertEquals("", settings.userId)
        assertTrue(settings.notificationsEnabled)
        assertFalse(settings.darkModeEnabled)
        assertEquals("es", settings.language)
        assertTrue(settings.autoBackup)
    }
    @Test
    fun `test AccountSettings with custom values`() {
        val userId = "test-user-123"
        val settings = AccountSettings(
            userId = userId,
            notificationsEnabled = false,
            darkModeEnabled = true,
            language = "en",
            autoBackup = false
        )
        assertEquals(userId, settings.userId)
        assertFalse(settings.notificationsEnabled)
        assertTrue(settings.darkModeEnabled)
        assertEquals("en", settings.language)
        assertFalse(settings.autoBackup)
    }
    @Test
    fun `test AccountSettings copy functionality`() {
        val originalSettings = AccountSettings(
            userId = "user-123",
            notificationsEnabled = true,
            darkModeEnabled = false,
            language = "es",
            autoBackup = true
        )
        val modifiedSettings = originalSettings.copy(
            notificationsEnabled = false,
            darkModeEnabled = true
        )
        assertEquals(originalSettings.userId, modifiedSettings.userId)
        assertFalse(modifiedSettings.notificationsEnabled)
        assertTrue(modifiedSettings.darkModeEnabled)
        assertEquals(originalSettings.language, modifiedSettings.language)
        assertEquals(originalSettings.autoBackup, modifiedSettings.autoBackup)
    }
}
package es.monsteraltech.skincare_tfm.account

/**
 * Data class representing user account settings stored in Firestore
 */
data class AccountSettings(
    val userId: String = "",
    val notificationsEnabled: Boolean = true,
    val darkModeEnabled: Boolean = false,
    val language: String = "es",
    val autoBackup: Boolean = true
)
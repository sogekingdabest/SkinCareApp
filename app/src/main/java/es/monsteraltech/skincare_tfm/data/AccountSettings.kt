package es.monsteraltech.skincare_tfm.data

/**
 * Data class que representa las configuraciones de cuenta del usuario
 * Estas configuraciones se almacenan en Firestore y permiten personalizar la experiencia
 */
data class AccountSettings(
    val userId: String = "",
    val notificationsEnabled: Boolean = true,
    val darkModeEnabled: Boolean = false,
    val language: String = "es",
    val autoBackup: Boolean = true
)
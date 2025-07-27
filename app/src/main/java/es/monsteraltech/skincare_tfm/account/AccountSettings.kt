package es.monsteraltech.skincare_tfm.account

import com.google.firebase.firestore.Exclude

/**
 * Data class representing user account settings stored in Firestore
 */
data class AccountSettings(
    val userId: String = "",
    val notificationsEnabled: Boolean = true,
    val darkModeEnabled: Boolean = false,
    val language: String = "es",
    
    // Propiedad temporal para evitar warnings de Firestore
    // Se ignora al serializar/deserializar
    @get:Exclude
    @set:Exclude
    var autoBackup: Boolean = true
)
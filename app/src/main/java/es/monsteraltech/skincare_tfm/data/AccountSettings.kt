package es.monsteraltech.skincare_tfm.data

import com.google.firebase.firestore.Exclude

/**
 * Data class que representa las configuraciones de cuenta del usuario
 * Estas configuraciones se almacenan en Firestore y permiten personalizar la experiencia
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
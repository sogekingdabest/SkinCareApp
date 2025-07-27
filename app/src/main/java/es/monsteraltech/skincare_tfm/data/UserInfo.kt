package es.monsteraltech.skincare_tfm.data

import java.util.Date

/**
 * Data class que representa la información completa del usuario
 * Contiene todos los datos relevantes del perfil de usuario obtenidos de Firebase Auth
 */
data class UserInfo(
    val uid: String = "",
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val emailVerified: Boolean = false,
    val createdAt: Date? = null,
    val lastSignIn: Date? = null
)
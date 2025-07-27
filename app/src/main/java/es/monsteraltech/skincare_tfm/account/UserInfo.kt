package es.monsteraltech.skincare_tfm.account

import java.util.Date

/**
 * Data class representing user information from Firebase Auth
 */
data class UserInfo(
    val uid: String = "",
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val emailVerified: Boolean = false,
    val createdAt: Date? = null,
    val lastSignIn: Date? = null,
    val providerIds: List<String> = emptyList(), // Lista de proveedores de autenticación
    val isGoogleUser: Boolean = false // Indica si el usuario se autenticó con Google
)
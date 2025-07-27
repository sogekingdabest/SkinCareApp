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
    val lastSignIn: Date? = null
)
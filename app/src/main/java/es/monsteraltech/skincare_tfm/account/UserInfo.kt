package es.monsteraltech.skincare_tfm.account
import java.util.Date
data class UserInfo(
    val uid: String = "",
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val emailVerified: Boolean = false,
    val createdAt: Date? = null,
    val lastSignIn: Date? = null,
    val providerIds: List<String> = emptyList(),
    val isGoogleUser: Boolean = false
)
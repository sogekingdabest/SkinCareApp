package com.monsteraltech.skincare.data.account

import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.skincare.account.CurrentAccountProfileSource
import com.monsteraltech.skincare.account.UserInfo
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Firebase values are converted at the SDK boundary and never escape to presentation. */
class FirebaseAccountProfileSource @Inject constructor(private val auth: FirebaseAuth) : CurrentAccountProfileSource {
    override suspend fun getCurrentUserProfile(): UserInfo? {
        currentCoroutineContext().ensureActive()
        val user = auth.currentUser ?: return null
        val providers = user.providerData.map { it.providerId }
        return UserInfo(
            uid = user.uid, displayName = user.displayName, email = user.email,
            photoUrl = user.photoUrl?.toString(), emailVerified = user.isEmailVerified,
            createdAt = user.metadata?.creationTimestamp?.let(::Date),
            lastSignIn = user.metadata?.lastSignInTimestamp?.let(::Date),
            providerIds = providers, isGoogleUser = "google.com" in providers
        )
    }
}

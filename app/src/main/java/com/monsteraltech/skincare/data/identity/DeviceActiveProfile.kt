package com.monsteraltech.skincare.data.identity

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceActiveProfile @Inject constructor(
    @ApplicationContext context: Context,
    private val firebaseAuth: FirebaseAuth
) : ActiveProfile {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override val isLocal: Boolean
        get() = preferences.getBoolean(KEY_LOCAL_ACTIVE, false)

    override val ownerId: String?
        get() = if (isLocal) localOwnerId() else firebaseAuth.currentUser?.uid

    override fun activateLocalProfile(): String {
        val ownerId = localOwnerId()
        preferences.edit().putBoolean(KEY_LOCAL_ACTIVE, true).apply()
        return ownerId
    }

    override fun activateAccountProfile() {
        preferences.edit().putBoolean(KEY_LOCAL_ACTIVE, false).apply()
    }

    override fun clear() {
        // Conservamos el ID para que volver al modo local recupere sus datos.
        preferences.edit().putBoolean(KEY_LOCAL_ACTIVE, false).apply()
    }

    private fun localOwnerId(): String {
        preferences.getString(KEY_LOCAL_OWNER_ID, null)?.let { return it }
        val generated = "local:${UUID.randomUUID()}"
        check(preferences.edit().putString(KEY_LOCAL_OWNER_ID, generated).commit()) {
            "No se pudo persistir el perfil local"
        }
        return generated
    }

    private companion object {
        const val PREFERENCES = "private_active_profile"
        const val KEY_LOCAL_ACTIVE = "local_profile_active"
        const val KEY_LOCAL_OWNER_ID = "local_owner_id"
    }
}

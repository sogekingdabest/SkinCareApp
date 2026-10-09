package com.monsteraltech.skincare.camera.guidance

import android.content.Context
import android.content.SharedPreferences

/**
 * Preferencias locales de las ayudas de captura experimentales.
 *
 * No forman parte del perfil ni se sincronizan: afectan solamente a este
 * dispositivo y nunca habilitan una función que la build no incluya.
 */
class ExperimentalCapturePreferences internal constructor(
    private val preferences: SharedPreferences
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    )

    var captureValidityEnabled: Boolean
        get() = preferences.getBoolean(
            KEY_CAPTURE_VALIDITY_ENABLED,
            DEFAULT_CAPTURE_VALIDITY_ENABLED
        )
        set(value) {
            preferences.edit()
                .putBoolean(KEY_CAPTURE_VALIDITY_ENABLED, value)
                .apply()
        }

    companion object {
        private const val PREFERENCES_NAME = "experimental_capture_features"
        private const val KEY_CAPTURE_VALIDITY_ENABLED = "capture_validity_enabled"

        /** Mantiene el comportamiento de las builds de prueba ya instaladas. */
        const val DEFAULT_CAPTURE_VALIDITY_ENABLED = true

        fun isCaptureValidityEffective(
            modelAvailable: Boolean,
            userEnabled: Boolean
        ): Boolean = modelAvailable && userEnabled
    }
}

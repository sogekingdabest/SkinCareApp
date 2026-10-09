package com.monsteraltech.skincare

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SkinCareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Antes de que exista un perfil, la pantalla de acceso también debe
        // respetar el tema configurado en Android.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
}

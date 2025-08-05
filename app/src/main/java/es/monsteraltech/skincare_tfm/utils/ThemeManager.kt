package es.monsteraltech.skincare_tfm.utils
import android.app.Activity
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import es.monsteraltech.skincare_tfm.account.UserProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class ThemeManager private constructor() {
    companion object {
        private const val TAG = "ThemeManager"
        @Volatile
        private var INSTANCE: ThemeManager? = null
        fun getInstance(): ThemeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager().also { INSTANCE = it }
            }
        }
    }
    private var userProfileManager: UserProfileManager? = null
    private var currentThemeMode: Int = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    fun initialize(userProfileManager: UserProfileManager) {
        this.userProfileManager = userProfileManager
        Log.d(TAG, "ThemeManager inicializado")
    }
    fun applyThemeFromUserSettings(activity: Activity) {
        userProfileManager?.let { manager ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d(TAG, "Obteniendo configuración de tema del usuario")
                    val settingsResult = manager.getUserSettings()
                    settingsResult.onSuccess { settings ->
                        val newThemeMode = if (settings.darkModeEnabled) {
                            AppCompatDelegate.MODE_NIGHT_YES
                        } else {
                            AppCompatDelegate.MODE_NIGHT_NO
                        }
                        withContext(Dispatchers.Main) {
                            applyThemeMode(activity, newThemeMode)
                        }
                        Log.d(TAG, "Tema aplicado: ${if (settings.darkModeEnabled) "OSCURO" else "CLARO"}")
                    }.onError { error ->
                        Log.e(TAG, "Error al obtener configuración de tema: ${error.message}")
                        withContext(Dispatchers.Main) {
                            applyThemeMode(activity, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al aplicar tema: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        applyThemeMode(activity, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    }
                }
            }
        } ?: run {
            Log.w(TAG, "UserProfileManager no inicializado, usando tema del sistema")
            applyThemeMode(activity, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
    private fun applyThemeMode(activity: Activity, themeMode: Int) {
        if (currentThemeMode != themeMode) {
            currentThemeMode = themeMode
            AppCompatDelegate.setDefaultNightMode(themeMode)
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.recreate()
                    Log.d(TAG, "Tema cambiado a: ${
                        when (themeMode) {
                            AppCompatDelegate.MODE_NIGHT_YES -> "OSCURO"
                            AppCompatDelegate.MODE_NIGHT_NO -> "CLARO"
                            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> "SISTEMA"
                            else -> "DESCONOCIDO"
                        }
                    }")
                } else {
                    Log.w(TAG, "Actividad ya destruida, no se puede recrear")
                }
            }
        }
    }
    fun updateThemeFromSettings(activity: Activity, darkModeEnabled: Boolean) {
        val newThemeMode = if (darkModeEnabled) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        activity.runOnUiThread {
            applyThemeMode(activity, newThemeMode)
        }
    }
}
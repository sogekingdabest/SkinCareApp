package es.monsteraltech.skincare_tfm.utils

import android.app.Activity
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import es.monsteraltech.skincare_tfm.account.UserProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestor de temas de la aplicación que respeta la configuración del usuario
 */
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
    
    /**
     * Inicializa el ThemeManager con el UserProfileManager
     */
    fun initialize(userProfileManager: UserProfileManager) {
        this.userProfileManager = userProfileManager
        Log.d(TAG, "ThemeManager inicializado")
    }
    
    /**
     * Aplica el tema basado en la configuración del usuario
     * @param activity Actividad donde aplicar el tema
     */
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
                        // En caso de error, usar el tema del sistema
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
    
    /**
     * Aplica un modo de tema específico
     * @param activity Actividad donde aplicar el tema
     * @param themeMode Modo de tema a aplicar
     */
    private fun applyThemeMode(activity: Activity, themeMode: Int) {
        if (currentThemeMode != themeMode) {
            currentThemeMode = themeMode
            AppCompatDelegate.setDefaultNightMode(themeMode)
            
            // Usar post para asegurar que la recreación ocurra después de que las operaciones actuales terminen
            activity.runOnUiThread {
                // Verificar que la actividad aún existe antes de recrearla
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

    /**
     * Actualiza el tema cuando cambia la configuración del usuario
     * @param activity Actividad donde aplicar el cambio
     * @param darkModeEnabled Nuevo valor para el modo oscuro
     */
    fun updateThemeFromSettings(activity: Activity, darkModeEnabled: Boolean) {
        val newThemeMode = if (darkModeEnabled) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        
        // Usar post para asegurar que la recreación ocurra después de que las coroutines actuales terminen
        activity.runOnUiThread {
            applyThemeMode(activity, newThemeMode)
        }
    }
} 
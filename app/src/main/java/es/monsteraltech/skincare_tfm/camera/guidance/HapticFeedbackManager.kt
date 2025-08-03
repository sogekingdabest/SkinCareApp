package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Gestor de retroalimentación háptica para diferentes estados de guía de captura.
 * Proporciona patrones de vibración específicos para cada estado de la guía.
 */
class HapticFeedbackManager(private val context: Context) {

    companion object {
        private const val TAG = "HapticFeedbackManager"
        
        // Duraciones de vibración en milisegundos
        private const val SHORT_VIBRATION = 50L
        private const val MEDIUM_VIBRATION = 100L
        private const val LONG_VIBRATION = 200L
        
        // Patrones de vibración (duración, pausa, duración, pausa...)
        private val SEARCHING_PATTERN = longArrayOf(0, 100, 200, 100)
        private val CENTERING_PATTERN = longArrayOf(0, 50, 100, 50)
        private val TOO_FAR_PATTERN = longArrayOf(0, 200, 100, 200)
        private val TOO_CLOSE_PATTERN = longArrayOf(0, 50, 50, 50, 50, 50)
        private val POOR_LIGHTING_PATTERN = longArrayOf(0, 150, 50, 150)
        private val BLURRY_PATTERN = longArrayOf(0, 100, 50, 100, 50, 100)
        private val READY_PATTERN = longArrayOf(0, 300)
        private val CAPTURE_SUCCESS_PATTERN = longArrayOf(0, 100, 50, 100, 50, 200)
    }

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var isEnabled: Boolean = true
    private var lastFeedbackTime: Long = 0
    private val feedbackThrottleMs = 500L // Evitar vibración excesiva

    /**
     * Proporciona retroalimentación háptica basada en el estado de la guía
     */
    fun provideFeedbackForState(state: CaptureValidationManager.GuideState) {
        if (!isEnabled || !canProvideFeedback()) {
            return
        }

        Log.d(TAG, "Proporcionando retroalimentación háptica para estado: $state")

        when (state) {
            CaptureValidationManager.GuideState.SEARCHING -> vibratePattern(SEARCHING_PATTERN)
            CaptureValidationManager.GuideState.CENTERING -> vibratePattern(CENTERING_PATTERN)
            CaptureValidationManager.GuideState.TOO_FAR -> vibratePattern(TOO_FAR_PATTERN)
            CaptureValidationManager.GuideState.TOO_CLOSE -> vibratePattern(TOO_CLOSE_PATTERN)
            CaptureValidationManager.GuideState.POOR_LIGHTING -> vibratePattern(POOR_LIGHTING_PATTERN)
            CaptureValidationManager.GuideState.BLURRY -> vibratePattern(BLURRY_PATTERN)
            CaptureValidationManager.GuideState.READY -> vibratePattern(READY_PATTERN)
        }

        lastFeedbackTime = System.currentTimeMillis()
    }

    /**
     * Proporciona retroalimentación específica para eventos de captura
     */
    fun provideCaptureSuccessFeedback() {
        if (!isEnabled) return
        
        Log.d(TAG, "Proporcionando retroalimentación de captura exitosa")
        vibratePattern(CAPTURE_SUCCESS_PATTERN)
    }

    /**
     * Proporciona retroalimentación simple para eventos de UI
     */
    fun provideSimpleFeedback() {
        if (!isEnabled) return
        
        vibrateSimple(SHORT_VIBRATION)
    }

    /**
     * Proporciona retroalimentación de error
     */
    fun provideErrorFeedback() {
        if (!isEnabled) return
        
        val errorPattern = longArrayOf(0, 100, 50, 100, 50, 100)
        vibratePattern(errorPattern)
    }

    /**
     * Vibra con un patrón específico
     */
    fun vibratePattern(pattern: LongArray) {
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "Dispositivo no tiene vibrador")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al vibrar con patrón", e)
        }
    }

    /**
     * Vibra por una duración específica
     */
    private fun vibrateSimple(duration: Long) {
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "Dispositivo no tiene vibrador")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al vibrar", e)
        }
    }

    /**
     * Verifica si se puede proporcionar retroalimentación (throttling)
     */
    private fun canProvideFeedback(): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - lastFeedbackTime >= feedbackThrottleMs
    }

    /**
     * Habilita o deshabilita la retroalimentación háptica
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Log.d(TAG, "Retroalimentación háptica ${if (enabled) "habilitada" else "deshabilitada"}")
    }

    /**
     * Verifica si la retroalimentación háptica está habilitada
     */
    fun isEnabled(): Boolean = isEnabled

    /**
     * Verifica si el dispositivo soporta vibración
     */
    fun hasVibrator(): Boolean = vibrator.hasVibrator()



    /**
     * Cancela cualquier vibración en curso
     */
    fun cancelVibration() {
        try {
            vibrator.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error al cancelar vibración", e)
        }
    }
}
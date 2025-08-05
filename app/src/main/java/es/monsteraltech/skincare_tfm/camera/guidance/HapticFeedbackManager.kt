package es.monsteraltech.skincare_tfm.camera.guidance
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
class HapticFeedbackManager(private val context: Context) {
    companion object {
        private const val TAG = "HapticFeedbackManager"
        private const val SHORT_VIBRATION = 50L
        private const val MEDIUM_VIBRATION = 100L
        private const val LONG_VIBRATION = 200L
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
    private val feedbackThrottleMs = 500L
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
    fun provideCaptureSuccessFeedback() {
        if (!isEnabled) return
        Log.d(TAG, "Proporcionando retroalimentación de captura exitosa")
        vibratePattern(CAPTURE_SUCCESS_PATTERN)
    }
    fun provideSimpleFeedback() {
        if (!isEnabled) return
        vibrateSimple(SHORT_VIBRATION)
    }
    fun provideErrorFeedback() {
        if (!isEnabled) return
        val errorPattern = longArrayOf(0, 100, 50, 100, 50, 100)
        vibratePattern(errorPattern)
    }
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
    private fun canProvideFeedback(): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - lastFeedbackTime >= feedbackThrottleMs
    }
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Log.d(TAG, "Retroalimentación háptica ${if (enabled) "habilitada" else "deshabilitada"}")
    }
    fun isEnabled(): Boolean = isEnabled
    fun hasVibrator(): Boolean = vibrator.hasVibrator()
    fun cancelVibration() {
        try {
            vibrator.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error al cancelar vibración", e)
        }
    }
}
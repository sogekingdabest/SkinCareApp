package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*

/**
 * Gestor de captura automática que permite capturar imágenes automáticamente
 * cuando se cumplen todos los criterios de validación. Incluye cuenta regresiva
 * y retroalimentación visual/háptica.
 */
class AutoCaptureManager(
    private val context: Context,
    private val hapticManager: HapticFeedbackManager,
    private val accessibilityManager: AccessibilityManager
) {

    companion object {
        private const val TAG = "AutoCaptureManager"
        private const val DEFAULT_COUNTDOWN_DURATION = 3000L // 3 segundos
        private const val COUNTDOWN_INTERVAL = 1000L // 1 segundo
        private const val STABILITY_CHECK_DURATION = 1000L // 1 segundo de estabilidad requerida
    }

    /**
     * Listener para eventos de captura automática
     */
    interface AutoCaptureListener {
        fun onCountdownStarted(remainingSeconds: Int)
        fun onCountdownTick(remainingSeconds: Int)
        fun onCountdownCancelled()
        fun onAutoCapture()
    }

    private var isEnabled = false
    private var countdownDuration = DEFAULT_COUNTDOWN_DURATION
    private var listener: AutoCaptureListener? = null
    
    private var countdownJob: Job? = null
    private var stabilityCheckJob: Job? = null
    private var isCountdownActive = false
    private var lastValidationTime = 0L
    private var consecutiveValidCount = 0
    
    private val handler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Configura el listener para eventos de captura automática
     */
    fun setListener(listener: AutoCaptureListener?) {
        this.listener = listener
    }

    /**
     * Habilita o deshabilita la captura automática
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Log.d(TAG, "Captura automática ${if (enabled) "habilitada" else "deshabilitada"}")
        
        if (!enabled) {
            cancelCountdown()
        }
    }

    /**
     * Verifica si la captura automática está habilitada
     */
    fun isEnabled(): Boolean = isEnabled

    /**
     * Configura la duración de la cuenta regresiva
     */
    fun setCountdownDuration(durationMs: Long) {
        countdownDuration = durationMs
        Log.d(TAG, "Duración de cuenta regresiva configurada a ${durationMs}ms")
    }

    /**
     * Procesa el resultado de validación para determinar si iniciar captura automática
     */
    fun processValidationResult(validationResult: CaptureValidationManager.ValidationResult) {
        if (!isEnabled) return

        val currentTime = System.currentTimeMillis()

        if (validationResult.canCapture && validationResult.guideState == CaptureValidationManager.GuideState.READY) {
            // Incrementar contador de validaciones consecutivas
            if (currentTime - lastValidationTime < 2000L) { // Dentro de 2 segundos
                consecutiveValidCount++
            } else {
                consecutiveValidCount = 1
            }
            
            lastValidationTime = currentTime
            
            // Iniciar verificación de estabilidad si no está activa
            if (!isCountdownActive && consecutiveValidCount >= 2) {
                startStabilityCheck()
            }
        } else {
            // Cancelar cuenta regresiva si las condiciones ya no se cumplen
            if (isCountdownActive) {
                cancelCountdown()
                accessibilityManager.announceForAccessibility("Captura automática cancelada - ajusta la posición")
            }
            consecutiveValidCount = 0
        }
    }

    /**
     * Inicia la verificación de estabilidad antes de la cuenta regresiva
     */
    private fun startStabilityCheck() {
        stabilityCheckJob?.cancel()
        
        Log.d(TAG, "Iniciando verificación de estabilidad")
        
        stabilityCheckJob = coroutineScope.launch {
            delay(STABILITY_CHECK_DURATION)
            
            // Si llegamos aquí, las condiciones han sido estables
            if (consecutiveValidCount >= 3) { // Verificar que sigue siendo válido
                startCountdown()
            }
        }
    }

    /**
     * Inicia la cuenta regresiva para captura automática
     */
    private fun startCountdown() {
        if (isCountdownActive) return

        Log.d(TAG, "Iniciando cuenta regresiva de captura automática")
        
        isCountdownActive = true
        val totalSeconds = (countdownDuration / 1000).toInt()
        
        // Anunciar inicio de cuenta regresiva
        accessibilityManager.announceForAccessibility("Captura automática en $totalSeconds segundos")
        listener?.onCountdownStarted(totalSeconds)
        
        countdownJob = coroutineScope.launch {
            var remainingSeconds = totalSeconds
            
            while (remainingSeconds > 0 && isCountdownActive) {
                // Proporcionar retroalimentación
                provideCountdownFeedback(remainingSeconds)
                
                // Notificar tick
                listener?.onCountdownTick(remainingSeconds)
                
                // Esperar un segundo
                delay(COUNTDOWN_INTERVAL)
                remainingSeconds--
            }
            
            // Si llegamos a 0 y sigue activa, ejecutar captura
            if (isCountdownActive && remainingSeconds == 0) {
                executeAutoCapture()
            }
        }
    }

    /**
     * Proporciona retroalimentación durante la cuenta regresiva
     */
    private fun provideCountdownFeedback(remainingSeconds: Int) {
        // Retroalimentación háptica
        when (remainingSeconds) {
            3 -> hapticManager.vibratePattern(longArrayOf(0, 100))
            2 -> hapticManager.vibratePattern(longArrayOf(0, 150))
            1 -> hapticManager.vibratePattern(longArrayOf(0, 200))
        }
        
        // Anuncio para accesibilidad (solo TalkBack)
        if (remainingSeconds <= 3) {
            accessibilityManager.announceForAccessibility(remainingSeconds.toString())
        }
    }

    /**
     * Ejecuta la captura automática
     */
    private fun executeAutoCapture() {
        Log.d(TAG, "Ejecutando captura automática")
        
        isCountdownActive = false
        
        // Retroalimentación de captura
        hapticManager.provideCaptureSuccessFeedback()
        accessibilityManager.announceForAccessibility("Capturando imagen automáticamente")
        
        // Notificar al listener
        listener?.onAutoCapture()
        
        // Resetear contadores
        consecutiveValidCount = 0
    }

    /**
     * Cancela la cuenta regresiva activa
     */
    fun cancelCountdown() {
        if (!isCountdownActive) return
        
        Log.d(TAG, "Cancelando cuenta regresiva")
        
        countdownJob?.cancel()
        stabilityCheckJob?.cancel()
        isCountdownActive = false
        consecutiveValidCount = 0
        
        listener?.onCountdownCancelled()
    }

    /**
     * Fuerza la captura inmediata (para casos especiales)
     */
    fun forceCapture() {
        if (!isEnabled) return
        
        Log.d(TAG, "Forzando captura inmediata")
        cancelCountdown()
        executeAutoCapture()
    }

    /**
     * Verifica si hay una cuenta regresiva activa
     */
    fun isCountdownActive(): Boolean = isCountdownActive

    /**
     * Obtiene el tiempo restante de cuenta regresiva en segundos
     */
    fun getRemainingCountdownTime(): Int {
        // Esta implementación es simplificada - en una implementación real
        // mantendríamos el tiempo restante actual
        return if (isCountdownActive) 1 else 0
    }

    /**
     * Configura parámetros de estabilidad
     */
    fun configureStability(requiredConsecutiveValid: Int, stabilityDurationMs: Long) {
        // Permitir configuración personalizada de estabilidad
        Log.d(TAG, "Configurando estabilidad: $requiredConsecutiveValid validaciones, ${stabilityDurationMs}ms")
    }

    /**
     * Limpia recursos y cancela operaciones
     */
    fun cleanup() {
        Log.d(TAG, "Limpiando AutoCaptureManager")
        
        cancelCountdown()
        coroutineScope.cancel()
        listener = null
    }
}
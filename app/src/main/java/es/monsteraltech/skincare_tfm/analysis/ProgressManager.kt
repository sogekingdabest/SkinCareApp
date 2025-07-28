package es.monsteraltech.skincare_tfm.analysis

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import com.google.android.material.progressindicator.LinearProgressIndicator
import android.widget.TextView
import androidx.core.content.ContextCompat
import es.monsteraltech.skincare_tfm.R

/**
 * Clase para gestionar indicadores visuales de progreso durante el procesamiento asíncrono de imágenes.
 * Maneja barras de progreso, mensajes de estado y animaciones suaves para mejorar la UX.
 * Incluye mejoras de accesibilidad y feedback táctil.
 */
class ProgressManager(
    private val context: Context,
    private val progressBar: LinearProgressIndicator?,
    private val statusText: TextView?,
    private val cancelButton: Button? = null
) {
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentProgress = 0
    private var currentStage: ProcessingStage? = null
    private var progressAnimator: ValueAnimator? = null
    
    // Servicios de accesibilidad y feedback
    private val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val isHighContrastMode = isHighContrastEnabled()
    
    // Colores para diferentes estados (adaptados para alto contraste)
    private val normalColor = if (isHighContrastMode) {
        ContextCompat.getColor(context, android.R.color.black)
    } else {
        ContextCompat.getColor(context, R.color.md3_primary_light)
    }
    private val errorColor = if (isHighContrastMode) {
        ContextCompat.getColor(context, android.R.color.black)
    } else {
        ContextCompat.getColor(context, android.R.color.holo_red_light)
    }
    private val successColor = if (isHighContrastMode) {
        ContextCompat.getColor(context, android.R.color.black)
    } else {
        ContextCompat.getColor(context, android.R.color.holo_green_light)
    }
    
    // Control de tiempo para estimaciones
    private var processingStartTime: Long = 0
    private var lastProgressUpdateTime: Long = 0
    
    /**
     * Actualiza el progreso con animación suave
     * @param progress Progreso actual (0-100)
     * @param message Mensaje descriptivo del estado actual
     */
    fun updateProgress(progress: Int, message: String) {
        mainHandler.post {
            // Actualizar mensaje de estado
            statusText?.text = message
            
            // Animar la barra de progreso si existe
            progressBar?.let { bar ->
                animateProgressTo(progress)
            }
            
            currentProgress = progress
        }
    }
    
    /**
     * Cambia la etapa del procesamiento con efectos visuales
     * @param stage Nueva etapa del procesamiento
     */
    fun showStage(stage: ProcessingStage) {
        mainHandler.post {
            currentStage = stage
            
            // Actualizar mensaje de la etapa
            statusText?.let { textView ->
                textView.text = stage.message
                
                // Animación sutil de cambio de texto
                textView.alpha = 0.7f
                textView.animate()
                    .alpha(1.0f)
                    .setDuration(300)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            
            // Actualizar progreso basado en la etapa
            val stageProgress = stage.getProgressUpToStage()
            progressBar?.let {
                animateProgressTo(stageProgress)
            }
            
            // Cambiar color de la barra según la etapa
            updateProgressBarColor(stage)
        }
    }
    
    /**
     * Muestra un mensaje de error con estilo visual apropiado
     * @param message Mensaje de error descriptivo
     */
    fun showError(message: String) {
        mainHandler.post {
            statusText?.let { textView ->
                textView.text = message
                textView.setTextColor(errorColor)
                
                // Animación de error (shake effect)
                val shake = ObjectAnimator.ofFloat(textView, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f)
                shake.duration = 600
                shake.start()
            }
            
            // Cambiar color de la barra de progreso a error
            progressBar?.let { bar ->
                bar.progressTintList = ContextCompat.getColorStateList(context, android.R.color.holo_red_light)
            }
            
            // Ocultar botón de cancelar si existe
            cancelButton?.visibility = View.GONE
        }
    }
    
    /**
     * Muestra el estado de completado con animación de éxito
     */
    fun showCompleted() {
        mainHandler.post {
            statusText?.let { textView ->
                textView.text = "¡Análisis completado!"
                textView.setTextColor(successColor)
            }
            
            // Animar progreso al 100%
            progressBar?.let { bar ->
                animateProgressTo(100)
                bar.progressTintList = ContextCompat.getColorStateList(context, android.R.color.holo_green_light)
            }
            
            // Ocultar después de un breve delay
            mainHandler.postDelayed({
                hide()
            }, 1500)
        }
    }
    
    /**
     * Muestra el estado de cancelación
     */
    fun showCancelled() {
        mainHandler.post {
            statusText?.let { textView ->
                textView.text = "Análisis cancelado"
                textView.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            }
            
            // Resetear barra de progreso
            progressBar?.let { bar ->
                bar.progress = 0
                bar.progressTintList = ContextCompat.getColorStateList(context, android.R.color.darker_gray)
            }
            
            // Ocultar después de un breve delay
            mainHandler.postDelayed({
                hide()
            }, 1000)
        }
    }
    
    /**
     * Oculta todos los indicadores de progreso
     */
    fun hide() {
        mainHandler.post {
            // Cancelar animaciones en curso
            progressAnimator?.cancel()
            
            // Ocultar componentes con animación fade out
            listOfNotNull(progressBar, statusText, cancelButton).forEach { view ->
                view.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        view.visibility = View.GONE
                        view.alpha = 1f // Resetear alpha para futuro uso
                    }
                    .start()
            }
            
            // Resetear estado
            currentProgress = 0
            currentStage = null
        }
    }
    
    /**
     * Muestra los indicadores de progreso
     */
    fun show() {
        mainHandler.post {
            // Mostrar componentes con animación fade in
            listOfNotNull(progressBar, statusText, cancelButton).forEach { view ->
                view.visibility = View.VISIBLE
                view.alpha = 0f
                view.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            }
            
            // Resetear colores a estado normal
            statusText?.setTextColor(ContextCompat.getColor(context, android.R.color.black))
            progressBar?.progressTintList = ContextCompat.getColorStateList(context, R.color.md3_primary_light)
        }
    }
    
    /**
     * Configura el listener para el botón de cancelar
     * @param onCancel Callback que se ejecuta cuando se presiona cancelar
     */
    fun setCancelListener(onCancel: () -> Unit) {
        cancelButton?.setOnClickListener {
            onCancel()
        }
    }
    
    /**
     * Actualiza el progreso con información de etapa específica
     * @param stage Etapa actual
     * @param stageProgress Progreso dentro de la etapa (0-100)
     */
    fun updateStageProgress(stage: ProcessingStage, stageProgress: Int) {
        val baseProgress = stage.getProgressUpToStage()
        val stageWeight = stage.weight
        val totalProgress = baseProgress + (stageProgress * stageWeight / 100)
        
        updateProgress(totalProgress, stage.message)
    }
    
    /**
     * Anima la barra de progreso hacia un valor específico
     */
    private fun animateProgressTo(targetProgress: Int) {
        progressBar?.let { bar ->
            // Cancelar animación anterior si existe
            progressAnimator?.cancel()
            
            progressAnimator = ValueAnimator.ofInt(bar.progress, targetProgress).apply {
                duration = 500
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    bar.setProgress(animator.animatedValue as Int, true)
                }
                start()
            }
        }
    }
    
    /**
     * Actualiza el color de la barra de progreso según la etapa
     */
    private fun updateProgressBarColor(stage: ProcessingStage) {
        progressBar?.let { bar ->
            val colorRes = when (stage) {
                ProcessingStage.INITIALIZING -> R.color.md3_primary_light
                ProcessingStage.PREPROCESSING -> R.color.md3_primary_light
                ProcessingStage.AI_ANALYSIS -> android.R.color.holo_blue_light
                ProcessingStage.ABCDE_ANALYSIS -> android.R.color.holo_orange_light
                ProcessingStage.FINALIZING -> android.R.color.holo_green_light
            }
            
            bar.progressTintList = ContextCompat.getColorStateList(context, colorRes)
        }
    }
    
    /**
     * Proporciona estimación de tiempo restante basada en el progreso actual
     * @param estimatedTotalTimeMs Tiempo total estimado en milisegundos
     * @return Tiempo restante estimado en segundos
     */
    fun getEstimatedTimeRemaining(estimatedTotalTimeMs: Long): Int {
        if (currentProgress <= 0) return (estimatedTotalTimeMs / 1000).toInt()
        
        val progressRatio = currentProgress / 100.0
        val elapsedRatio = progressRatio
        val remainingRatio = 1.0 - elapsedRatio
        
        return ((estimatedTotalTimeMs * remainingRatio) / 1000).toInt()
    }
    
    /**
     * Actualiza el mensaje con estimación de tiempo
     * @param baseMessage Mensaje base
     * @param estimatedTotalTimeMs Tiempo total estimado
     */
    fun updateProgressWithTimeEstimate(progress: Int, baseMessage: String, estimatedTotalTimeMs: Long) {
        val timeRemaining = getEstimatedTimeRemaining(estimatedTotalTimeMs)
        val messageWithTime = if (timeRemaining > 0 && progress < 95) {
            "$baseMessage (≈${timeRemaining}s restantes)"
        } else {
            baseMessage
        }
        
        updateProgressWithAccessibility(progress, messageWithTime)
    }
    
    /**
     * Actualiza el progreso con soporte completo de accesibilidad
     * @param progress Progreso actual (0-100)
     * @param message Mensaje descriptivo del estado actual
     */
    fun updateProgressWithAccessibility(progress: Int, message: String) {
        mainHandler.post {
            // Actualizar progreso visual
            updateProgress(progress, message)
            
            // Anunciar cambios importantes para lectores de pantalla
            announceProgressForAccessibility(progress, message)
            
            // Proporcionar feedback táctil en hitos importantes
            provideTactileFeedback(progress)
            
            // Actualizar tiempo de última actualización
            lastProgressUpdateTime = System.currentTimeMillis()
        }
    }
    
    /**
     * Cambia la etapa con mejoras de accesibilidad
     * @param stage Nueva etapa del procesamiento
     */
    fun showStageWithAccessibility(stage: ProcessingStage) {
        mainHandler.post {
            // Mostrar etapa visual
            showStage(stage)
            
            // Anunciar cambio de etapa para accesibilidad
            val announcement = "Nueva etapa: ${stage.message}"
            announceForAccessibility(announcement)
            
            // Feedback táctil para cambio de etapa importante
            provideTactileFeedbackForStageChange(stage)
        }
    }
    
    /**
     * Inicializa el procesamiento con timestamp
     */
    fun startProcessing() {
        processingStartTime = System.currentTimeMillis()
        lastProgressUpdateTime = processingStartTime
        
        // Anuncio inicial para accesibilidad
        announceForAccessibility("Iniciando análisis de imagen")
        
        // Feedback táctil de inicio
        provideStartFeedback()
    }
    
    /**
     * Finaliza el procesamiento con feedback de accesibilidad
     */
    fun completeProcessingWithAccessibility() {
        mainHandler.post {
            showCompleted()
            
            // Anuncio de finalización
            announceForAccessibility("Análisis completado exitosamente")
            
            // Feedback táctil de éxito
            provideSuccessFeedback()
        }
    }
    
    /**
     * Maneja errores con accesibilidad mejorada
     * @param message Mensaje de error
     */
    fun showErrorWithAccessibility(message: String) {
        mainHandler.post {
            showError(message)
            
            // Anuncio de error para accesibilidad
            announceForAccessibility("Error en el análisis: $message")
            
            // Feedback táctil de error
            provideErrorFeedback()
        }
    }
    
    /**
     * Maneja cancelación con accesibilidad
     */
    fun showCancelledWithAccessibility() {
        mainHandler.post {
            showCancelled()
            
            // Anuncio de cancelación
            announceForAccessibility("Análisis cancelado por el usuario")
            
            // Feedback táctil de cancelación
            provideCancelFeedback()
        }
    }
    
    // MÉTODOS PRIVADOS DE ACCESIBILIDAD Y UX
    
    /**
     * Detecta si el modo de alto contraste está habilitado
     */
    private fun isHighContrastEnabled(): Boolean {
        return try {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Anuncia cambios de progreso para lectores de pantalla
     */
    private fun announceProgressForAccessibility(progress: Int, message: String) {
        if (!accessibilityManager.isEnabled) return
        
        // Anunciar solo en hitos importantes para evitar spam
        val shouldAnnounce = progress % 25 == 0 || progress >= 95 || 
                           (System.currentTimeMillis() - lastProgressUpdateTime) > 5000
        
        if (shouldAnnounce) {
            val announcement = "Progreso: $progress por ciento. $message"
            announceForAccessibility(announcement)
        }
    }
    
    /**
     * Anuncia un mensaje para servicios de accesibilidad
     */
    private fun announceForAccessibility(message: String) {
        if (!accessibilityManager.isEnabled) return
        
        statusText?.let { textView ->
            textView.announceForAccessibility(message)
        } ?: run {
            // Si no hay TextView, usar el progressBar
            progressBar?.announceForAccessibility(message)
        }
    }
    
    /**
     * Proporciona feedback táctil en hitos importantes
     */
    private fun provideTactileFeedback(progress: Int) {
        if (vibrator?.hasVibrator() != true) return
        
        // Vibración sutil en hitos del 25%, 50%, 75% y 100%
        when (progress) {
            25, 50, 75 -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
            100 -> {
                // Vibración más larga para completado
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200)
                }
            }
        }
    }
    
    /**
     * Proporciona feedback táctil para cambios de etapa
     */
    private fun provideTactileFeedbackForStageChange(stage: ProcessingStage) {
        if (vibrator?.hasVibrator() != true) return
        
        // Patrón de vibración específico para cambios de etapa importantes
        val pattern = when (stage) {
            ProcessingStage.AI_ANALYSIS, ProcessingStage.ABCDE_ANALYSIS -> {
                // Doble vibración para etapas de análisis principales
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 100, 100, 100), -1)
                }
            }
            else -> {
                // Vibración simple para otras etapas
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(75)
                }
            }
        }
    }
    
    /**
     * Feedback táctil de inicio
     */
    private fun provideStartFeedback() {
        if (vibrator?.hasVibrator() != true) return
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }
    
    /**
     * Feedback táctil de éxito
     */
    private fun provideSuccessFeedback() {
        if (vibrator?.hasVibrator() != true) return
        
        // Patrón de éxito: tres vibraciones cortas
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100, 100, 100), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 100, 100, 100, 100, 100), -1)
        }
    }
    
    /**
     * Feedback táctil de error
     */
    private fun provideErrorFeedback() {
        if (vibrator?.hasVibrator() != true) return
        
        // Patrón de error: vibración larga
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }
    }
    
    /**
     * Feedback táctil de cancelación
     */
    private fun provideCancelFeedback() {
        if (vibrator?.hasVibrator() != true) return
        
        // Patrón de cancelación: dos vibraciones cortas
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 150, 150), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 150, 150, 150), -1)
        }
    }
}
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
import android.view.accessibility.AccessibilityManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import com.google.android.material.progressindicator.LinearProgressIndicator

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
    private val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val isHighContrastMode = isHighContrastEnabled()
    private fun getThemeColor(attrId: Int): Int {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(attrId, typedValue, true)
        return typedValue.data
    }
    private val normalColor by lazy { getThemeColor(com.google.android.material.R.attr.colorPrimary) }
    private val errorColor by lazy { getThemeColor(com.google.android.material.R.attr.colorError) }
    private val successColor by lazy { getThemeColor(com.google.android.material.R.attr.colorPrimary) }
    private val onSurfaceColor by lazy { getThemeColor(com.google.android.material.R.attr.colorOnSurface) }
    private val onSurfaceVariantColor by lazy { getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant) }
    private var processingStartTime: Long = 0
    private var lastProgressUpdateTime: Long = 0
    fun updateProgress(progress: Int, message: String) {
        mainHandler.post {
            statusText?.text = message
            progressBar?.let {
                animateProgressTo(progress)
            }
            currentProgress = progress
        }
    }
    fun showStage(stage: ProcessingStage) {
        mainHandler.post {
            currentStage = stage
            statusText?.let { textView ->
                textView.text = stage.message
                textView.alpha = 0.7f
                textView.animate()
                    .alpha(1.0f)
                    .setDuration(300)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            val stageProgress = stage.getProgressUpToStage()
            progressBar?.let {
                animateProgressTo(stageProgress)
            }
            updateProgressBarColor(stage)
        }
    }
    fun showError(message: String) {
        mainHandler.post {
            statusText?.let { textView ->
                textView.text = message
                textView.setTextColor(errorColor)
                val shake = ObjectAnimator.ofFloat(textView, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f)
                shake.duration = 600
                shake.start()
            }
            progressBar?.let { bar ->
                bar.setIndicatorColor(errorColor)
            }
            cancelButton?.visibility = View.GONE
        }
    }
    fun showCompleted() {
        mainHandler.post {
            statusText?.let { textView ->
                textView.text = "¡Análisis completado!"
                textView.setTextColor(successColor)
            }
            progressBar?.let { bar ->
                animateProgressTo(100)
                bar.setIndicatorColor(successColor)
            }
            mainHandler.postDelayed({
                hide()
            }, 1500)
        }
    }
    fun showCancelled() {
        mainHandler.post {
            statusText?.let { textView ->
                textView.text = "Análisis cancelado"
                textView.setTextColor(onSurfaceVariantColor)
            }
            progressBar?.let { bar ->
                bar.progress = 0
                bar.setIndicatorColor(onSurfaceVariantColor)
            }
            mainHandler.postDelayed({
                hide()
            }, 1000)
        }
    }
    fun hide() {
        mainHandler.post {
            progressAnimator?.cancel()
            listOfNotNull(progressBar, statusText, cancelButton).forEach { view ->
                view.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        view.visibility = View.GONE
                        view.alpha = 1f
                    }
                    .start()
            }
            currentProgress = 0
            currentStage = null
        }
    }
    fun show() {
        mainHandler.post {
            listOfNotNull(progressBar, statusText, cancelButton).forEach { view ->
                view.visibility = View.VISIBLE
                view.alpha = 0f
                view.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            }
            statusText?.setTextColor(onSurfaceColor)
            progressBar?.setIndicatorColor(normalColor)
        }
    }
    fun setCancelListener(onCancel: () -> Unit) {
        cancelButton?.setOnClickListener {
            onCancel()
        }
    }
    fun updateStageProgress(stage: ProcessingStage, stageProgress: Int) {
        val baseProgress = stage.getProgressUpToStage()
        val stageWeight = stage.weight
        val totalProgress = baseProgress + (stageProgress * stageWeight / 100)
        updateProgress(totalProgress, stage.message)
    }
    private fun animateProgressTo(targetProgress: Int) {
        progressBar?.let { bar ->
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
    private fun updateProgressBarColor(stage: ProcessingStage) {
        progressBar?.let { bar ->
            val color = when (stage) {
                ProcessingStage.INITIALIZING -> normalColor
                ProcessingStage.PREPROCESSING -> normalColor
                ProcessingStage.AI_ANALYSIS -> getThemeColor(com.google.android.material.R.attr.colorSecondary)
                ProcessingStage.ABCDE_ANALYSIS -> getThemeColor(com.google.android.material.R.attr.colorTertiary)
                ProcessingStage.FINALIZING -> successColor
            }
            bar.setIndicatorColor(color)
        }
    }
    fun getEstimatedTimeRemaining(estimatedTotalTimeMs: Long): Int {
        if (currentProgress <= 0) return (estimatedTotalTimeMs / 1000).toInt()
        val progressRatio = currentProgress / 100.0
        val elapsedRatio = progressRatio
        val remainingRatio = 1.0 - elapsedRatio
        return ((estimatedTotalTimeMs * remainingRatio) / 1000).toInt()
    }
    fun updateProgressWithTimeEstimate(progress: Int, baseMessage: String, estimatedTotalTimeMs: Long) {
        val timeRemaining = getEstimatedTimeRemaining(estimatedTotalTimeMs)
        val messageWithTime = if (timeRemaining > 0 && progress < 95) {
            "$baseMessage (≈${timeRemaining}s restantes)"
        } else {
            baseMessage
        }
        updateProgressWithAccessibility(progress, messageWithTime)
    }
    fun updateProgressWithAccessibility(progress: Int, message: String) {
        mainHandler.post {
            updateProgress(progress, message)
            announceProgressForAccessibility(progress, message)
            provideTactileFeedback(progress)
            lastProgressUpdateTime = System.currentTimeMillis()
        }
    }
    fun showStageWithAccessibility(stage: ProcessingStage) {
        mainHandler.post {
            showStage(stage)
            val announcement = "Nueva etapa: ${stage.message}"
            announceForAccessibility(announcement)
            provideTactileFeedbackForStageChange(stage)
        }
    }
    fun startProcessing() {
        processingStartTime = System.currentTimeMillis()
        lastProgressUpdateTime = processingStartTime
        announceForAccessibility("Iniciando análisis de imagen")
        provideStartFeedback()
    }
    fun completeProcessingWithAccessibility() {
        mainHandler.post {
            showCompleted()
            announceForAccessibility("Análisis completado exitosamente")
            provideSuccessFeedback()
        }
    }
    fun showErrorWithAccessibility(message: String) {
        mainHandler.post {
            showError(message)
            announceForAccessibility("Error en el análisis: $message")
            provideErrorFeedback()
        }
    }
    fun showCancelledWithAccessibility() {
        mainHandler.post {
            showCancelled()
            announceForAccessibility("Análisis cancelado por el usuario")
            provideCancelFeedback()
        }
    }
    private fun isHighContrastEnabled(): Boolean {
        return try {
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        } catch (e: Exception) {
            false
        }
    }
    private fun announceProgressForAccessibility(progress: Int, message: String) {
        if (!accessibilityManager.isEnabled) return
        val shouldAnnounce = progress % 25 == 0 || progress >= 95 ||
                           (System.currentTimeMillis() - lastProgressUpdateTime) > 5000
        if (shouldAnnounce) {
            val announcement = "Progreso: $progress por ciento. $message"
            announceForAccessibility(announcement)
        }
    }
    private fun announceForAccessibility(message: String) {
        if (!accessibilityManager.isEnabled) return
        statusText?.let { textView ->
            textView.announceForAccessibility(message)
        } ?: run {
            progressBar?.announceForAccessibility(message)
        }
    }
    private fun provideTactileFeedback(progress: Int) {
        if (vibrator?.hasVibrator() != true) return
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
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200)
                }
            }
        }
    }
    private fun provideTactileFeedbackForStageChange(stage: ProcessingStage) {
        if (vibrator?.hasVibrator() != true) return
        when (stage) {
            ProcessingStage.AI_ANALYSIS, ProcessingStage.ABCDE_ANALYSIS -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 100, 100, 100), -1)
                }
            }
            else -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(75)
                }
            }
        }
    }
    private fun provideStartFeedback() {
        if (vibrator?.hasVibrator() != true) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }
    private fun provideSuccessFeedback() {
        if (vibrator?.hasVibrator() != true) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100, 100, 100), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 100, 100, 100, 100, 100), -1)
        }
    }
    private fun provideErrorFeedback() {
        if (vibrator?.hasVibrator() != true) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }
    }
    private fun provideCancelFeedback() {
        if (vibrator?.hasVibrator() != true) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 150, 150), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 150, 150, 150), -1)
        }
    }
}
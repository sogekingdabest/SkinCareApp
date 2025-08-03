package es.monsteraltech.skincare_tfm.camera.guidance

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import es.monsteraltech.skincare_tfm.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vista personalizada que se superpone al preview de la cámara para mostrar guías visuales
 * de captura de lunares. Proporciona retroalimentación visual en tiempo real sobre el
 * estado de la captura y guía al usuario para obtener imágenes de calidad óptima.
 */
class CaptureGuidanceOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Configuración
    private var config: CaptureGuidanceConfig = CaptureGuidanceConfig()
    
    // Estado actual
    private var currentState: CaptureValidationManager.GuideState = CaptureValidationManager.GuideState.SEARCHING
    private var guidanceMessage: String = ""
    
    // Posición del lunar detectado
    private var molePosition: PointF? = null
    private var moleConfidence: Float = 0f
    
    // Geometría de la guía
    private val guideCircle = RectF()
    private val centerPoint = PointF()
    
    // Paints para dibujo
    private val guideCirclePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    private val guideCircleFillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val moleIndicatorPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 48f
        color = Color.WHITE
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }
    
    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.processing_overlay_background)
    }
    
    // Animaciones
    private var pulseAnimator: ValueAnimator? = null
    private var pulseScale: Float = 1f
    private var colorTransitionAnimator: ValueAnimator? = null
    private var currentColor: Int = Color.WHITE
    
    init {
        setupColors()
    }
    
    private fun setupColors() {
        updateColorsForState(currentState)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGeometry()
    }
    
    private fun updateGeometry() {
        centerPoint.set(width / 2f, height / 2f)
        
        val radius = config.guideCircleRadius
        guideCircle.set(
            centerPoint.x - radius,
            centerPoint.y - radius,
            centerPoint.x + radius,
            centerPoint.y + radius
        )
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Dibujar fondo semi-transparente fuera del círculo guía
        drawBackground(canvas)
        
        // Dibujar círculo guía (sin cruz ni punto central)
        drawGuideCircle(canvas)
        
        // No dibujar mensaje de guía en la parte baja
        
        // Dibujar indicadores adicionales según el estado
        drawStateIndicators(canvas)
    }
    
    private fun drawBackground(canvas: Canvas) {
        // Dibujar fondo completo
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        
        // Crear un agujero transparente en el área del círculo guía
        val transparentPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        
        canvas.drawCircle(
            centerPoint.x,
            centerPoint.y,
            config.guideCircleRadius * pulseScale,
            transparentPaint
        )
    }
    
    private fun drawGuideCircle(canvas: Canvas) {
        val radius = config.guideCircleRadius * pulseScale
        
        // Dibujar círculo de relleno con transparencia
        guideCircleFillPaint.alpha = 30
        canvas.drawCircle(centerPoint.x, centerPoint.y, radius, guideCircleFillPaint)
        
        // Dibujar borde del círculo
        canvas.drawCircle(centerPoint.x, centerPoint.y, radius, guideCirclePaint)
        // No dibujar líneas de cruz ni punto central
    }
    
    private fun drawGuidanceMessage(canvas: Canvas) {
        val textY = height - 100f // Posición cerca del fondo
        
        // Dibujar fondo del texto
        val textBounds = Rect()
        textPaint.getTextBounds(guidanceMessage, 0, guidanceMessage.length, textBounds)
        
        val backgroundRect = RectF(
            centerPoint.x - textBounds.width() / 2f - 20f,
            textY - textBounds.height() - 10f,
            centerPoint.x + textBounds.width() / 2f + 20f,
            textY + 10f
        )
        
        val textBackgroundPaint = Paint().apply {
            color = Color.BLACK
            alpha = 180
        }
        
        canvas.drawRoundRect(backgroundRect, 10f, 10f, textBackgroundPaint)
        
        // Dibujar texto
        canvas.drawText(guidanceMessage, centerPoint.x, textY, textPaint)
    }
    
    private fun drawStateIndicators(canvas: Canvas) {
        when (currentState) {
            CaptureValidationManager.GuideState.TOO_FAR -> drawDistanceIndicator(canvas, false)
            CaptureValidationManager.GuideState.TOO_CLOSE -> drawDistanceIndicator(canvas, true)
            CaptureValidationManager.GuideState.BLURRY -> drawBlurIndicator(canvas)
            CaptureValidationManager.GuideState.POOR_LIGHTING -> drawLightingIndicator(canvas)
            else -> { /* No hay indicadores adicionales */ }
        }
    }
    
    private fun drawDistanceIndicator(canvas: Canvas, tooClose: Boolean) {
        val arrowPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.risk_high)
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
        }
        
        val arrowSize = 30f
        val arrowY = centerPoint.y - config.guideCircleRadius - 60f
        
        if (tooClose) {
            // Flechas hacia afuera (alejarse)
            drawArrow(canvas, centerPoint.x - 40f, arrowY, -arrowSize, 0f, arrowPaint)
            drawArrow(canvas, centerPoint.x + 40f, arrowY, arrowSize, 0f, arrowPaint)
        } else {
            // Flechas hacia adentro (acercarse)
            drawArrow(canvas, centerPoint.x - 40f, arrowY, arrowSize, 0f, arrowPaint)
            drawArrow(canvas, centerPoint.x + 40f, arrowY, -arrowSize, 0f, arrowPaint)
        }
    }
    
    private fun drawArrow(canvas: Canvas, x: Float, y: Float, dx: Float, dy: Float, paint: Paint) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + dx, y + dy)
            lineTo(x + dx * 0.7f, y + dy - 10f)
            moveTo(x + dx, y + dy)
            lineTo(x + dx * 0.7f, y + dy + 10f)
        }
        canvas.drawPath(path, paint)
    }
    
    private fun drawBlurIndicator(canvas: Canvas) {
        val indicatorPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.risk_high)
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        
        // Dibujar líneas onduladas para indicar desenfoque
        val waveY = centerPoint.y + config.guideCircleRadius + 40f
        val path = Path()
        path.moveTo(centerPoint.x - 60f, waveY)
        
        for (i in 0..6) {
            val x = centerPoint.x - 60f + i * 20f
            val y = waveY + if (i % 2 == 0) -10f else 10f
            path.lineTo(x, y)
        }
        
        canvas.drawPath(path, indicatorPaint)
    }
    
    private fun drawLightingIndicator(canvas: Canvas) {
        val indicatorPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.risk_moderate)
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }
        
        // Dibujar símbolo de sol/bombilla
        val sunY = centerPoint.y + config.guideCircleRadius + 50f
        canvas.drawCircle(centerPoint.x, sunY, 15f, indicatorPaint)
        
        // Rayos del sol
        for (i in 0..7) {
            val angle = i * 45f * Math.PI / 180
            val startX = centerPoint.x + 20f * cos(angle).toFloat()
            val startY = sunY + 20f * sin(angle).toFloat()
            val endX = centerPoint.x + 30f * cos(angle).toFloat()
            val endY = sunY + 30f * sin(angle).toFloat()
            
            canvas.drawLine(startX, startY, endX, endY, indicatorPaint)
        }
    }
    
    /**
     * Actualiza la posición detectada del lunar
     */
    fun updateMolePosition(position: PointF?, confidence: Float) {
        this.molePosition = position
        this.moleConfidence = confidence
        invalidate()
    }
    
    /**
     * Actualiza el estado de la guía y aplica cambios visuales
     */
    fun updateGuideState(state: CaptureValidationManager.GuideState) {
        if (currentState != state) {
            currentState = state
            updateColorsForState(state)
            updateAnimationForState(state)
            updateGuidanceMessageForState(state)
            invalidate()
        }
    }
    
    /**
     * Establece el mensaje de guía personalizado
     */
    fun setGuidanceMessage(message: String) {
        guidanceMessage = message
        invalidate()
    }
    
    /**
     * Actualiza la configuración del overlay
     */
    fun updateConfig(newConfig: CaptureGuidanceConfig) {
        config = newConfig
        updateGeometry()
        invalidate()
    }
    
    private fun updateColorsForState(state: CaptureValidationManager.GuideState) {
        val targetColor = when (state) {
            CaptureValidationManager.GuideState.SEARCHING -> Color.WHITE
            CaptureValidationManager.GuideState.CENTERING -> ContextCompat.getColor(context, R.color.risk_moderate)
            CaptureValidationManager.GuideState.TOO_FAR -> ContextCompat.getColor(context, R.color.risk_high)
            CaptureValidationManager.GuideState.TOO_CLOSE -> ContextCompat.getColor(context, R.color.risk_high)
            CaptureValidationManager.GuideState.POOR_LIGHTING -> ContextCompat.getColor(context, R.color.risk_moderate)
            CaptureValidationManager.GuideState.BLURRY -> ContextCompat.getColor(context, R.color.risk_high)
            CaptureValidationManager.GuideState.READY -> ContextCompat.getColor(context, R.color.risk_very_low)
        }
        
        animateColorTransition(targetColor)
    }
    
    private fun animateColorTransition(targetColor: Int) {
        colorTransitionAnimator?.cancel()
        
        colorTransitionAnimator = ValueAnimator.ofArgb(currentColor, targetColor).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                currentColor = animator.animatedValue as Int
                guideCirclePaint.color = currentColor
                guideCircleFillPaint.color = currentColor
                invalidate()
            }
            start()
        }
    }
    
    private fun updateAnimationForState(state: CaptureValidationManager.GuideState) {
        pulseAnimator?.cancel()
        
        when (state) {
            CaptureValidationManager.GuideState.SEARCHING -> startPulseAnimation()
            CaptureValidationManager.GuideState.READY -> startReadyAnimation()
            else -> stopPulseAnimation()
        }
    }
    
    private fun startPulseAnimation() {
        pulseAnimator = ValueAnimator.ofFloat(1f, 1.1f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                pulseScale = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    private fun startReadyAnimation() {
        pulseAnimator = ValueAnimator.ofFloat(1f, 1.05f, 1f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                pulseScale = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseScale = 1f
        invalidate()
    }
    
    private fun updateGuidanceMessageForState(state: CaptureValidationManager.GuideState) {
        guidanceMessage = when (state) {
            CaptureValidationManager.GuideState.SEARCHING -> "Busca un lunar en el área circular"
            CaptureValidationManager.GuideState.CENTERING -> "Centra el lunar en el círculo"
            CaptureValidationManager.GuideState.TOO_FAR -> "Acércate más al lunar"
            CaptureValidationManager.GuideState.TOO_CLOSE -> "Aléjate un poco del lunar"
            CaptureValidationManager.GuideState.POOR_LIGHTING -> "Mejora la iluminación"
            CaptureValidationManager.GuideState.BLURRY -> "Mantén firme la cámara"
            CaptureValidationManager.GuideState.READY -> "¡Listo para capturar!"
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
        colorTransitionAnimator?.cancel()
    }
}
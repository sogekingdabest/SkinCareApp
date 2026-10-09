package com.monsteraltech.skincare.camera.guidance
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.monsteraltech.skincare.R
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class CaptureGuidanceOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var config: CaptureGuidanceConfig = CaptureGuidanceConfig()
    private var currentState: GuideState = GuideState.SEARCHING
    private var molePosition: PointF? = null
    private var moleConfidence: Float = 0f
    private var analysisFrameWidth: Int = 0
    private var analysisFrameHeight: Int = 0

    private val centerPoint = PointF()
    private var guideRadius: Float = 0f

    private val guideCirclePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val guideCircleFillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val moleMarkerPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.processing_overlay_background)
    }
    private val transparentHolePaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val indicatorPaint = Paint().apply {
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private var pulseAnimator: ValueAnimator? = null
    private var pulseScale: Float = 1f
    private var colorTransitionAnimator: ValueAnimator? = null
    private var currentColor: Int = Color.WHITE

    init {
        // El recorte transparente (PorterDuff.CLEAR) requiere capa software.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        updateColorsForState(currentState)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGeometry()
    }

    private fun updateGeometry() {
        centerPoint.set(width / 2f, height / 2f)
        guideRadius = min(width, height) * config.guideCircleRadiusFraction
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas)
        drawGuideCircle(canvas)
        drawMoleMarker(canvas)
        drawStateIndicators(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        canvas.drawCircle(centerPoint.x, centerPoint.y, guideRadius * pulseScale, transparentHolePaint)
    }

    private fun drawGuideCircle(canvas: Canvas) {
        val radius = guideRadius * pulseScale
        guideCircleFillPaint.alpha = 30
        canvas.drawCircle(centerPoint.x, centerPoint.y, radius, guideCircleFillPaint)
        canvas.drawCircle(centerPoint.x, centerPoint.y, radius, guideCirclePaint)
    }

    /** Marca la posición del lunar detectado, mapeada del frame de análisis a la vista. */
    private fun drawMoleMarker(canvas: Canvas) {
        val position = molePosition ?: return
        if (moleConfidence < 0.2f) return
        val mapped = mapFramePointToView(position) ?: return

        moleMarkerPaint.color = currentColor
        moleMarkerPaint.alpha = (100 + 155 * moleConfidence.coerceIn(0f, 1f)).toInt()
        val markerRadius = 18f
        canvas.drawCircle(mapped.x, mapped.y, markerRadius, moleMarkerPaint)
        canvas.drawLine(mapped.x - markerRadius, mapped.y, mapped.x + markerRadius, mapped.y, moleMarkerPaint)
        canvas.drawLine(mapped.x, mapped.y - markerRadius, mapped.x, mapped.y + markerRadius, moleMarkerPaint)
    }

    /**
     * Mapea un punto del frame de análisis a coordenadas de esta vista asumiendo
     * el escalado FILL_CENTER de PreviewView (escala máxima + recorte centrado).
     */
    private fun mapFramePointToView(point: PointF): PointF? {
        if (analysisFrameWidth <= 0 || analysisFrameHeight <= 0 || width <= 0 || height <= 0) return null
        val scale = max(width.toFloat() / analysisFrameWidth, height.toFloat() / analysisFrameHeight)
        val offsetX = (width - analysisFrameWidth * scale) / 2f
        val offsetY = (height - analysisFrameHeight * scale) / 2f
        return PointF(point.x * scale + offsetX, point.y * scale + offsetY)
    }

    private fun drawStateIndicators(canvas: Canvas) {
        when (currentState) {
            GuideState.POOR_LIGHTING -> drawLightingIndicator(canvas)
            GuideState.CENTERING -> drawCenteringIndicator(canvas)
            else -> { }
        }
    }

    private fun drawCenteringIndicator(canvas: Canvas) {
        indicatorPaint.color = ContextCompat.getColor(context, R.color.risk_moderate)
        indicatorPaint.style = Paint.Style.STROKE

        val arrowSize = 25f
        val distance = guideRadius + 50f

        drawArrow(canvas, centerPoint.x, centerPoint.y - distance, 0f, arrowSize, indicatorPaint)
        drawArrow(canvas, centerPoint.x, centerPoint.y + distance, 0f, -arrowSize, indicatorPaint)
        drawArrow(canvas, centerPoint.x - distance, centerPoint.y, arrowSize, 0f, indicatorPaint)
        drawArrow(canvas, centerPoint.x + distance, centerPoint.y, -arrowSize, 0f, indicatorPaint)
    }

    private fun drawArrow(canvas: Canvas, x: Float, y: Float, dx: Float, dy: Float, paint: Paint) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + dx, y + dy)
            lineTo(x + dx * 0.7f - dy * 0.3f, y + dy * 0.7f + dx * 0.3f)
            moveTo(x + dx, y + dy)
            lineTo(x + dx * 0.7f + dy * 0.3f, y + dy * 0.7f - dx * 0.3f)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawLightingIndicator(canvas: Canvas) {
        indicatorPaint.color = ContextCompat.getColor(context, R.color.risk_moderate)
        indicatorPaint.style = Paint.Style.STROKE
        val sunY = centerPoint.y + guideRadius + 50f
        canvas.drawCircle(centerPoint.x, sunY, 15f, indicatorPaint)
        for (i in 0..7) {
            val angle = i * 45f * Math.PI / 180
            val startX = centerPoint.x + 20f * cos(angle).toFloat()
            val startY = sunY + 20f * sin(angle).toFloat()
            val endX = centerPoint.x + 30f * cos(angle).toFloat()
            val endY = sunY + 30f * sin(angle).toFloat()
            canvas.drawLine(startX, startY, endX, endY, indicatorPaint)
        }
    }

    /** Dimensiones del frame que produce las coordenadas de [updateMolePosition]. */
    fun setAnalysisFrameSize(width: Int, height: Int) {
        if (width != analysisFrameWidth || height != analysisFrameHeight) {
            analysisFrameWidth = width
            analysisFrameHeight = height
            invalidate()
        }
    }

    fun updateMolePosition(position: PointF?, confidence: Float) {
        this.molePosition = position
        this.moleConfidence = confidence
        invalidate()
    }

    fun updateGuideState(state: GuideState) {
        if (currentState != state) {
            currentState = state
            updateColorsForState(state)
            updateAnimationForState(state)
            invalidate()
        }
    }

    fun updateConfig(newConfig: CaptureGuidanceConfig) {
        config = newConfig
        updateGeometry()
        invalidate()
    }

    private fun updateColorsForState(state: GuideState) {
        val targetColor = when (state) {
            GuideState.SEARCHING -> Color.WHITE
            GuideState.CENTERING -> ContextCompat.getColor(context, R.color.guidance_centering)
            GuideState.POOR_LIGHTING -> ContextCompat.getColor(context, R.color.guidance_poor_lighting)
            GuideState.BLURRY -> ContextCompat.getColor(context, R.color.guidance_blurry)
            GuideState.READY -> ContextCompat.getColor(context, R.color.guidance_ready)
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

    private fun updateAnimationForState(state: GuideState) {
        pulseAnimator?.cancel()
        when (state) {
            GuideState.SEARCHING -> startPulseAnimation(1.1f, 1500)
            GuideState.READY -> startPulseAnimation(1.05f, 800)
            else -> stopPulseAnimation()
        }
    }

    private fun startPulseAnimation(peakScale: Float, durationMs: Long) {
        pulseAnimator = ValueAnimator.ofFloat(1f, peakScale, 1f).apply {
            duration = durationMs
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
        colorTransitionAnimator?.cancel()
    }
}

package es.monsteraltech.skincare_tfm.body.mole.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.model.ABCDEScores

/**
 * Vista personalizada para mostrar gráficos de evolución de puntuaciones ABCDE
 * Muestra barras comparativas entre dos análisis
 */
class AbcdeEvolutionChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var currentScores: ABCDEScores? = null
    private var previousScores: ABCDEScores? = null
    
    private val barHeight = 40f
    private val barSpacing = 60f
    private val labelWidth = 120f
    private val chartPadding = 20f
    
    private val currentColor = ContextCompat.getColor(context, R.color.md_theme_primary)
    private val previousColor = ContextCompat.getColor(context, android.R.color.darker_gray)
    private val improvementColor = ContextCompat.getColor(context, android.R.color.holo_green_dark)
    private val worseningColor = ContextCompat.getColor(context, android.R.color.holo_red_dark)

    init {
        textPaint.textSize = 32f
        textPaint.color = ContextCompat.getColor(context, android.R.color.black)
    }

    fun setScores(current: ABCDEScores, previous: ABCDEScores) {
        currentScores = current
        previousScores = previous
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (barSpacing * 6 + chartPadding * 2).toInt() // 5 scores + total + padding
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            desiredHeight
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val current = currentScores ?: return
        val previous = previousScores ?: return
        
        val maxWidth = width - labelWidth - chartPadding * 2
        val maxScore = 5f // Máximo score ABCDE individual
        
        var yPosition = chartPadding + barHeight
        
        // Dibujar cada puntuación ABCDE
        drawScoreComparison(canvas, "Asimetría", current.asymmetryScore, previous.asymmetryScore, yPosition, maxWidth, maxScore)
        yPosition += barSpacing
        
        drawScoreComparison(canvas, "Bordes", current.borderScore, previous.borderScore, yPosition, maxWidth, maxScore)
        yPosition += barSpacing
        
        drawScoreComparison(canvas, "Color", current.colorScore, previous.colorScore, yPosition, maxWidth, maxScore)
        yPosition += barSpacing
        
        drawScoreComparison(canvas, "Diámetro", current.diameterScore, previous.diameterScore, yPosition, maxWidth, maxScore)
        yPosition += barSpacing
        
        // Evolución (si existe)
        if (current.evolutionScore != null && previous.evolutionScore != null) {
            drawScoreComparison(canvas, "Evolución", current.evolutionScore!!, previous.evolutionScore!!, yPosition, maxWidth, maxScore)
            yPosition += barSpacing
        }
        
        // Total (usar escala diferente para el total)
        drawScoreComparison(canvas, "Total", current.totalScore, previous.totalScore, yPosition, maxWidth, 20f)
    }

    private fun drawScoreComparison(
        canvas: Canvas,
        label: String,
        currentScore: Float,
        previousScore: Float,
        yPosition: Float,
        maxWidth: Float,
        maxScore: Float
    ) {
        // Dibujar etiqueta
        canvas.drawText(label, chartPadding, yPosition, textPaint)
        
        val barStartX = chartPadding + labelWidth
        val currentBarWidth = (currentScore / maxScore) * maxWidth
        val previousBarWidth = (previousScore / maxScore) * maxWidth
        
        // Dibujar barra anterior (más clara)
        paint.color = previousColor
        paint.alpha = 128
        canvas.drawRect(
            barStartX,
            yPosition - barHeight / 2 - 5,
            barStartX + previousBarWidth,
            yPosition - 5,
            paint
        )
        
        // Dibujar barra actual
        val change = currentScore - previousScore
        paint.color = when {
            change > 0.2f -> worseningColor // Empeoramiento (mayor score es peor)
            change < -0.2f -> improvementColor // Mejora
            else -> currentColor
        }
        paint.alpha = 255
        canvas.drawRect(
            barStartX,
            yPosition + 5,
            barStartX + currentBarWidth,
            yPosition + barHeight / 2 + 5,
            paint
        )
        
        // Dibujar valores
        val currentText = String.format("%.1f", currentScore)
        val previousText = String.format("%.1f", previousScore)
        val changeText = if (change > 0) "+%.1f" else "%.1f"
        
        textPaint.textSize = 24f
        canvas.drawText(currentText, barStartX + currentBarWidth + 10, yPosition + 5, textPaint)
        canvas.drawText(previousText, barStartX + previousBarWidth + 10, yPosition - 10, textPaint)
        
        // Dibujar indicador de cambio
        textPaint.color = when {
            change > 0.2f -> worseningColor
            change < -0.2f -> improvementColor
            else -> ContextCompat.getColor(context, android.R.color.darker_gray)
        }
        canvas.drawText(String.format(changeText, change), barStartX + maxWidth - 80, yPosition, textPaint)
        
        // Restaurar color del texto
        textPaint.color = ContextCompat.getColor(context, android.R.color.black)
    }
}
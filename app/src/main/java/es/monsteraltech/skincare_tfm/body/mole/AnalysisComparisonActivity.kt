package es.monsteraltech.skincare_tfm.body.mole

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.EvolutionComparison
import es.monsteraltech.skincare_tfm.databinding.ActivityAnalysisComparisonBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Actividad para mostrar comparación visual entre dos análisis
 * Implementa gráficos de evolución de puntuaciones ABCDE y resumen de cambios
 */
class AnalysisComparisonActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisComparisonBinding
    private lateinit var comparison: EvolutionComparison

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisComparisonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar la toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Comparación de Análisis"

        // Obtener datos de comparación
        val currentAnalysis = reconstructCurrentAnalysisFromIntent()
        val previousAnalysis = reconstructPreviousAnalysisFromIntent()

        if (currentAnalysis == null || previousAnalysis == null) {
            Toast.makeText(this, "Error: Datos de comparación no válidos", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Crear comparación
        comparison = EvolutionComparison.create(currentAnalysis, previousAnalysis)

        // Configurar la interfaz
        setupComparison()
    }

    private fun reconstructCurrentAnalysisFromIntent(): AnalysisData? {
        return try {
            val id = intent.getStringExtra("CURRENT_ID") ?: return null
            val analysisResult = intent.getStringExtra("CURRENT_RESULT") ?: ""
            val aiProbability = intent.getFloatExtra("CURRENT_AI_PROBABILITY", 0f)
            val aiConfidence = intent.getFloatExtra("CURRENT_AI_CONFIDENCE", 0f)
            val combinedScore = intent.getFloatExtra("CURRENT_COMBINED_SCORE", 0f)
            val riskLevel = intent.getStringExtra("CURRENT_RISK_LEVEL") ?: ""
            val imageUrl = intent.getStringExtra("CURRENT_IMAGE_URL") ?: ""
            val createdAtMillis = intent.getLongExtra("CURRENT_CREATED_AT", System.currentTimeMillis())
            
            val createdAt = com.google.firebase.Timestamp(java.util.Date(createdAtMillis))
            
            AnalysisData(
                id = id,
                analysisResult = analysisResult,
                aiProbability = aiProbability,
                aiConfidence = aiConfidence,
                combinedScore = combinedScore,
                riskLevel = riskLevel,
                imageUrl = imageUrl,
                createdAt = createdAt
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun reconstructPreviousAnalysisFromIntent(): AnalysisData? {
        return try {
            val id = intent.getStringExtra("PREVIOUS_ID") ?: return null
            val analysisResult = intent.getStringExtra("PREVIOUS_RESULT") ?: ""
            val aiProbability = intent.getFloatExtra("PREVIOUS_AI_PROBABILITY", 0f)
            val aiConfidence = intent.getFloatExtra("PREVIOUS_AI_CONFIDENCE", 0f)
            val combinedScore = intent.getFloatExtra("PREVIOUS_COMBINED_SCORE", 0f)
            val riskLevel = intent.getStringExtra("PREVIOUS_RISK_LEVEL") ?: ""
            val imageUrl = intent.getStringExtra("PREVIOUS_IMAGE_URL") ?: ""
            val createdAtMillis = intent.getLongExtra("PREVIOUS_CREATED_AT", System.currentTimeMillis())
            
            val createdAt = com.google.firebase.Timestamp(java.util.Date(createdAtMillis))
            
            AnalysisData(
                id = id,
                analysisResult = analysisResult,
                aiProbability = aiProbability,
                aiConfidence = aiConfidence,
                combinedScore = combinedScore,
                riskLevel = riskLevel,
                imageUrl = imageUrl,
                createdAt = createdAt
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun setupComparison() {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        // Configurar fechas
        binding.currentDateText.text = dateFormat.format(comparison.currentAnalysis.createdAt.toDate())
        binding.previousDateText.text = dateFormat.format(comparison.previousAnalysis.createdAt.toDate())

        // Configurar imágenes
        loadAnalysisImage(comparison.currentAnalysis.imageUrl, binding.currentImageView)
        loadAnalysisImage(comparison.previousAnalysis.imageUrl, binding.previousImageView)

        // Configurar niveles de riesgo
        setupRiskLevels()

        // Configurar puntuaciones ABCDE
        setupAbcdeScores()

        // Configurar gráficos de evolución
        setupEvolutionCharts()

        // Configurar resumen de cambios
        setupChangesSummary()

        // Configurar información de tiempo
        binding.timeDifferenceText.text = "${comparison.timeDifferenceInDays} días"
    }

    private fun loadAnalysisImage(imageUrl: String, imageView: android.widget.ImageView) {
        if (imageUrl.isNotEmpty()) {
            if (imageUrl.startsWith("http")) {
                // URL remota
                Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_launcher_background)
                    .into(imageView)
            } else {
                // Archivo local
                Glide.with(this)
                    .load(File(imageUrl))
                    .placeholder(R.drawable.ic_launcher_background)
                    .into(imageView)
            }
        }
    }

    private fun setupRiskLevels() {
        // Configurar nivel de riesgo actual
        binding.currentRiskText.text = comparison.currentAnalysis.riskLevel
        binding.currentRiskText.setBackgroundResource(getRiskBackgroundColor(comparison.currentAnalysis.riskLevel))

        // Configurar nivel de riesgo anterior
        binding.previousRiskText.text = comparison.previousAnalysis.riskLevel
        binding.previousRiskText.setBackgroundResource(getRiskBackgroundColor(comparison.previousAnalysis.riskLevel))

        // Mostrar indicador de cambio
        if (comparison.riskLevelChange.hasChanged) {
            binding.riskChangeIndicator.text = when {
                comparison.riskLevelChange.hasImproved -> "↗ Mejoría"
                comparison.riskLevelChange.hasWorsened -> "↘ Empeoramiento"
                else -> "→ Cambio"
            }
            binding.riskChangeIndicator.setTextColor(
                getColor(when {
                    comparison.riskLevelChange.hasImproved -> android.R.color.holo_green_dark
                    comparison.riskLevelChange.hasWorsened -> android.R.color.holo_red_dark
                    else -> android.R.color.darker_gray
                })
            )
        } else {
            binding.riskChangeIndicator.text = "Sin cambios"
            binding.riskChangeIndicator.setTextColor(getColor(android.R.color.darker_gray))
        }
    }

    private fun setupAbcdeScores() {
        val current = comparison.currentAnalysis.abcdeScores
        val previous = comparison.previousAnalysis.abcdeScores
        val changes = comparison.scoreChanges

        // Asimetría
        setupScoreComparison(
            binding.currentAsymmetryText, binding.previousAsymmetryText, binding.asymmetryChangeText,
            current.asymmetryScore, previous.asymmetryScore, changes.asymmetryChange
        )

        // Bordes
        setupScoreComparison(
            binding.currentBorderText, binding.previousBorderText, binding.borderChangeText,
            current.borderScore, previous.borderScore, changes.borderChange
        )

        // Color
        setupScoreComparison(
            binding.currentColorText, binding.previousColorText, binding.colorChangeText,
            current.colorScore, previous.colorScore, changes.colorChange
        )

        // Diámetro
        setupScoreComparison(
            binding.currentDiameterText, binding.previousDiameterText, binding.diameterChangeText,
            current.diameterScore, previous.diameterScore, changes.diameterChange
        )

        // Total ABCDE
        setupScoreComparison(
            binding.currentTotalText, binding.previousTotalText, binding.totalChangeText,
            current.totalScore, previous.totalScore, changes.totalScoreChange
        )

        // Puntuación combinada
        setupScoreComparison(
            binding.currentCombinedText, binding.previousCombinedText, binding.combinedChangeText,
            comparison.currentAnalysis.combinedScore, comparison.previousAnalysis.combinedScore, changes.combinedScoreChange
        )
    }

    private fun setupScoreComparison(
        currentView: android.widget.TextView,
        previousView: android.widget.TextView,
        changeView: android.widget.TextView,
        currentScore: Float,
        previousScore: Float,
        change: Float
    ) {
        currentView.text = String.format("%.1f", currentScore)
        previousView.text = String.format("%.1f", previousScore)

        val changeText = if (change > 0) "+%.1f" else "%.1f"
        changeView.text = String.format(changeText, change)

        // Colorear según el cambio
        val color = when {
            change > 0.2f -> getColor(android.R.color.holo_red_dark)
            change < -0.2f -> getColor(android.R.color.holo_green_dark)
            else -> getColor(android.R.color.darker_gray)
        }
        changeView.setTextColor(color)
    }

    private fun setupEvolutionCharts() {
        // Configurar barras de progreso para mostrar evolución visual
        val current = comparison.currentAnalysis.abcdeScores
        val previous = comparison.previousAnalysis.abcdeScores

        // Asimetría
        binding.asymmetryCurrentBar.progress = (current.asymmetryScore * 20).toInt()
        binding.asymmetryPreviousBar.progress = (previous.asymmetryScore * 20).toInt()

        // Bordes
        binding.borderCurrentBar.progress = (current.borderScore * 20).toInt()
        binding.borderPreviousBar.progress = (previous.borderScore * 20).toInt()

        // Color
        binding.colorCurrentBar.progress = (current.colorScore * 20).toInt()
        binding.colorPreviousBar.progress = (previous.colorScore * 20).toInt()

        // Diámetro
        binding.diameterCurrentBar.progress = (current.diameterScore * 20).toInt()
        binding.diameterPreviousBar.progress = (previous.diameterScore * 20).toInt()

        // Total
        binding.totalCurrentBar.progress = (current.totalScore * 10).toInt()
        binding.totalPreviousBar.progress = (previous.totalScore * 10).toInt()

        // Combinada
        binding.combinedCurrentBar.progress = (comparison.currentAnalysis.combinedScore * 100).toInt()
        binding.combinedPreviousBar.progress = (comparison.previousAnalysis.combinedScore * 100).toInt()
    }

    private fun setupChangesSummary() {
        // Resumen general de evolución
        binding.evolutionSummaryText.text = comparison.getEvolutionSummary()

        // Tendencia general
        val trendText = when (comparison.overallTrend) {
            EvolutionComparison.EvolutionTrend.IMPROVING -> "Tendencia: Mejorando ↗"
            EvolutionComparison.EvolutionTrend.WORSENING -> "Tendencia: Empeorando ↘"
            EvolutionComparison.EvolutionTrend.STABLE -> "Tendencia: Estable →"
            EvolutionComparison.EvolutionTrend.MIXED -> "Tendencia: Cambios mixtos ↕"
        }
        binding.overallTrendText.text = trendText

        val trendColor = when (comparison.overallTrend) {
            EvolutionComparison.EvolutionTrend.IMPROVING -> getColor(android.R.color.holo_green_dark)
            EvolutionComparison.EvolutionTrend.WORSENING -> getColor(android.R.color.holo_red_dark)
            else -> getColor(android.R.color.darker_gray)
        }
        binding.overallTrendText.setTextColor(trendColor)

        // Cambios significativos
        if (comparison.significantChanges.isNotEmpty()) {
            val changesText = comparison.significantChanges.joinToString("\n• ", "• ")
            binding.significantChangesText.text = changesText
        } else {
            binding.significantChangesText.text = "No se detectaron cambios significativos"
        }

        // Confianza de IA
        val currentConfidence = comparison.currentAnalysis.aiConfidence
        val previousConfidence = comparison.previousAnalysis.aiConfidence
        val confidenceChange = currentConfidence - previousConfidence

        binding.currentConfidenceText.text = "${(currentConfidence * 100).toInt()}%"
        binding.previousConfidenceText.text = "${(previousConfidence * 100).toInt()}%"

        val confidenceChangeText = if (confidenceChange > 0) "+${(confidenceChange * 100).toInt()}%" else "${(confidenceChange * 100).toInt()}%"
        binding.confidenceChangeText.text = confidenceChangeText

        val confidenceColor = when {
            confidenceChange > 0.1f -> getColor(android.R.color.holo_green_dark)
            confidenceChange < -0.1f -> getColor(android.R.color.holo_red_dark)
            else -> getColor(android.R.color.darker_gray)
        }
        binding.confidenceChangeText.setTextColor(confidenceColor)
    }

    private fun getRiskBackgroundColor(riskLevel: String): Int {
        return when (riskLevel.uppercase()) {
            "LOW" -> R.drawable.rounded_risk_low
            "MODERATE" -> R.drawable.rounded_risk_medium
            "HIGH" -> R.drawable.rounded_risk_high
            else -> R.drawable.rounded_risk_background
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
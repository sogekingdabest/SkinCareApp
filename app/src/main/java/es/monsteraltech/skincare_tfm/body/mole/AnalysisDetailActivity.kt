package es.monsteraltech.skincare_tfm.body.mole

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import es.monsteraltech.skincare_tfm.databinding.ActivityAnalysisDetailBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class AnalysisDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisDetailBinding
    private val firebaseDataManager = FirebaseDataManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar la toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalle del Análisis"

        // Obtener datos del análisis desde el intent
        val analysisData = intent.getSerializableExtra("ANALYSIS_DATA") as? AnalysisData
        if (analysisData == null) {
            finish()
            return
        }

        displayAnalysisDetails(analysisData)
    }

    private fun displayAnalysisDetails(analysis: AnalysisData) {
        // Cargar imagen del análisis
        if (analysis.imageUrl.isNotEmpty()) {
            if (analysis.imageUrl.startsWith("http")) {
                // URL remota
                Glide.with(this)
                    .load(analysis.imageUrl)
                    .placeholder(R.drawable.ic_launcher_background)
                    .into(binding.analysisImageView)
            } else {
                // Ruta local
                val fullPath = firebaseDataManager.getFullImagePath(this, analysis.imageUrl)
                Glide.with(this)
                    .load(File(fullPath))
                    .placeholder(R.drawable.ic_launcher_background)
                    .into(binding.analysisImageView)
            }
        }

        // Fecha del análisis
        val date = analysis.createdAt.toDate()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        binding.analysisDateText.text = dateFormat.format(date)

        // Nivel de riesgo
        binding.riskLevelText.text = analysis.riskLevel
        when (analysis.riskLevel.uppercase()) {
            "LOW" -> {
                binding.riskLevelText.setBackgroundResource(R.drawable.rounded_risk_background)
                binding.riskLevelText.background.setTint(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
            "MODERATE", "MEDIO" -> {
                binding.riskLevelText.setBackgroundResource(R.drawable.rounded_risk_background)
                binding.riskLevelText.background.setTint(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            }
            "HIGH" -> {
                binding.riskLevelText.setBackgroundResource(R.drawable.rounded_risk_background)
                binding.riskLevelText.background.setTint(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
            else -> {
                binding.riskLevelText.setBackgroundResource(R.drawable.rounded_risk_background)
                binding.riskLevelText.background.setTint(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        }

        // Confianza de IA
        val aiConfidencePercent = (analysis.aiConfidence * 100).toInt()
        binding.aiConfidenceText.text = "$aiConfidencePercent%"
        binding.aiConfidenceProgressBar.progress = aiConfidencePercent

        // Probabilidad de IA
        val aiProbabilityPercent = (analysis.aiProbability * 100).toInt()
        binding.aiProbabilityText.text = "$aiProbabilityPercent%"
        binding.aiProbabilityProgressBar.progress = aiProbabilityPercent

        // Puntuaciones ABCDE individuales
        binding.asymmetryScoreText.text = String.format("%.1f", analysis.abcdeScores.asymmetryScore)
        binding.asymmetryProgressBar.progress = (analysis.abcdeScores.asymmetryScore * 20).toInt() // Escala 0-5 a 0-100

        binding.borderScoreText.text = String.format("%.1f", analysis.abcdeScores.borderScore)
        binding.borderProgressBar.progress = (analysis.abcdeScores.borderScore * 20).toInt()

        binding.colorScoreText.text = String.format("%.1f", analysis.abcdeScores.colorScore)
        binding.colorProgressBar.progress = (analysis.abcdeScores.colorScore * 20).toInt()

        binding.diameterScoreText.text = String.format("%.1f", analysis.abcdeScores.diameterScore)
        binding.diameterProgressBar.progress = (analysis.abcdeScores.diameterScore * 20).toInt()

        // Puntuación de evolución (si existe)
        if (analysis.abcdeScores.evolutionScore != null) {
            binding.evolutionScoreText.text = String.format("%.1f", analysis.abcdeScores.evolutionScore)
            binding.evolutionProgressBar.progress = (analysis.abcdeScores.evolutionScore * 20).toInt()
            binding.evolutionScoreLayout.visibility = View.VISIBLE
        } else {
            binding.evolutionScoreLayout.visibility = View.GONE
        }

        // Puntuación total ABCDE
        binding.totalAbcdeScoreText.text = String.format("%.1f", analysis.abcdeScores.totalScore)
        binding.totalAbcdeProgressBar.progress = (analysis.abcdeScores.totalScore * 4).toInt() // Escala 0-25 a 0-100

        // Puntuación combinada
        binding.combinedScoreText.text = String.format("%.1f", analysis.combinedScore)
        binding.combinedScoreProgressBar.progress = (analysis.combinedScore * 100).toInt()

        // Resultado completo del análisis
        if (analysis.analysisResult.isNotEmpty()) {
            binding.analysisResultText.text = analysis.analysisResult
            binding.analysisResultText.visibility = View.VISIBLE
        } else {
            binding.analysisResultText.visibility = View.GONE
        }

        // Recomendación
        if (analysis.recommendation.isNotEmpty()) {
            binding.recommendationText.text = analysis.recommendation
            binding.recommendationText.visibility = View.VISIBLE
        } else {
            binding.recommendationText.visibility = View.GONE
        }

        // Metadatos adicionales
        if (analysis.analysisMetadata.isNotEmpty()) {
            val metadataText = buildString {
                analysis.analysisMetadata.forEach { (key, value) ->
                    append("$key: $value\n")
                }
            }
            binding.metadataText.text = metadataText.trim()
            binding.metadataLayout.visibility = View.VISIBLE
        } else {
            binding.metadataLayout.visibility = View.GONE
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
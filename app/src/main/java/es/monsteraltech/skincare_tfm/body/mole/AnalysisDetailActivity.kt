package es.monsteraltech.skincare_tfm.body.mole

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.util.ImageLoadingUtil
import es.monsteraltech.skincare_tfm.databinding.ActivityAnalysisDetailBinding
import java.text.SimpleDateFormat
import java.util.Locale

class AnalysisDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar la toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalle del Análisis"

        // Obtener datos del análisis desde el intent
        val analysisData = reconstructAnalysisDataFromIntent()
        if (analysisData == null) {
            finish()
            return
        }

        displayAnalysisDetails(analysisData)
    }

    private fun reconstructAnalysisDataFromIntent(): AnalysisData? {
        return try {
            val id = intent.getStringExtra("ANALYSIS_ID") ?: return null
            val moleId = intent.getStringExtra("MOLE_ID") ?: ""
            val analysisResult = intent.getStringExtra("ANALYSIS_RESULT") ?: ""
            val aiProbability = intent.getFloatExtra("AI_PROBABILITY", 0f)
            val aiConfidence = intent.getFloatExtra("AI_CONFIDENCE", 0f)
            val combinedScore = intent.getFloatExtra("COMBINED_SCORE", 0f)
            val riskLevel = intent.getStringExtra("RISK_LEVEL") ?: ""
            val recommendation = intent.getStringExtra("RECOMMENDATION") ?: ""
            val imageUrl = intent.getStringExtra("IMAGE_URL") ?: ""
            val createdAtMillis = intent.getLongExtra("CREATED_AT", System.currentTimeMillis())
            
            // Reconstruir ABCDE Scores
            val asymmetryScore = intent.getFloatExtra("ASYMMETRY_SCORE", 0f)
            val borderScore = intent.getFloatExtra("BORDER_SCORE", 0f)
            val colorScore = intent.getFloatExtra("COLOR_SCORE", 0f)
            val diameterScore = intent.getFloatExtra("DIAMETER_SCORE", 0f)
            val evolutionScore = intent.getFloatExtra("EVOLUTION_SCORE", -1f).let { 
                if (it == -1f) null else it 
            }
            val totalScore = intent.getFloatExtra("TOTAL_SCORE", 0f)
            
            val abcdeScores = es.monsteraltech.skincare_tfm.body.mole.model.ABCDEScores(
                asymmetryScore = asymmetryScore,
                borderScore = borderScore,
                colorScore = colorScore,
                diameterScore = diameterScore,
                evolutionScore = evolutionScore,
                totalScore = totalScore
            )
            
            // Crear Timestamp desde milisegundos
            val createdAt = com.google.firebase.Timestamp(java.util.Date(createdAtMillis))
            
            AnalysisData(
                id = id,
                moleId = moleId,
                analysisResult = analysisResult,
                aiProbability = aiProbability,
                aiConfidence = aiConfidence,
                abcdeScores = abcdeScores,
                combinedScore = combinedScore,
                riskLevel = riskLevel,
                recommendation = recommendation,
                imageUrl = imageUrl,
                createdAt = createdAt
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun displayAnalysisDetails(analysis: AnalysisData) {
        // Cargar imagen del análisis usando ImageLoadingUtil
        ImageLoadingUtil.loadImageWithFallback(
            context = this,
            imageView = binding.resultImageView,
            imageUrl = analysis.imageUrl,
            config = ImageLoadingUtil.Configs.fullSize(),
            coroutineScope = lifecycleScope
        )

        // Mostrar score combinado
        val combinedScore = analysis.combinedScore
        if (combinedScore > 0f) {
            binding.combinedScoreText.text = String.format("%.1f%%", combinedScore * 100)
        } else {
            binding.combinedScoreText.text = "--"
        }

        // Mostrar probabilidad y confianza de IA
        if (analysis.aiProbability > 0f && analysis.aiConfidence > 0f) {
            binding.aiProbabilityText.text = "IA: ${String.format("%.1f%%", analysis.aiProbability * 100)} (Confianza: ${String.format("%.1f%%", analysis.aiConfidence * 100)})"
            binding.aiProbabilityText.visibility = View.VISIBLE
        } else {
            binding.aiProbabilityText.visibility = View.GONE
        }

        // Mostrar nivel de riesgo y configurar indicador
        displayRiskLevel(analysis.riskLevel)

        // Mostrar puntuaciones ABCDE detalladas
        displayABCDEScoresDetailed(analysis)

        // Mostrar recomendación
        if (analysis.recommendation.isNotEmpty()) {
            binding.recommendationText.text = analysis.recommendation
        } else {
            binding.recommendationText.text = "Análisis completado. Consulte los resultados detallados."
        }

        // Mostrar fecha del análisis en formato español
        val date = analysis.createdAt.toDate()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))
        binding.analysisDateText.text = "Fecha: ${dateFormat.format(date)}"

    }

    private fun displayRiskLevel(riskLevel: String) {
        if (riskLevel.isNotEmpty()) {
            binding.riskLevelText.text = "Riesgo: $riskLevel"
            binding.riskIndicator.visibility = View.VISIBLE
            
            // Cambiar color según el nivel de riesgo
            val colorRes = when (riskLevel.uppercase()) {
                "LOW", "BAJO" -> android.R.color.holo_green_dark
                "MODERATE", "MODERADO" -> android.R.color.holo_orange_dark
                "HIGH", "ALTO" -> android.R.color.holo_red_dark
                else -> android.R.color.darker_gray
            }
            val color = ContextCompat.getColor(this, colorRes)
            binding.riskLevelText.setTextColor(color)
            binding.riskIndicator.setBackgroundColor(color)
        } else {
            binding.riskLevelText.text = "Riesgo: --"
            binding.riskIndicator.visibility = View.GONE
        }
    }

    private fun displayABCDEScoresDetailed(analysis: AnalysisData) {
        val scores = analysis.abcdeScores
        
        // Solo mostrar si hay puntuaciones válidas
        if (scores.totalScore > 0f || scores.asymmetryScore > 0f || 
            scores.borderScore > 0f || scores.colorScore > 0f || scores.diameterScore > 0f) {
            
            // A - Asimetría
            if (scores.asymmetryScore >= 0f) {
                binding.asymmetryLayout.visibility = View.VISIBLE
                binding.asymmetryScore.text = "${String.format("%.1f", scores.asymmetryScore)}/2"
                binding.asymmetryProgress.progress = ((scores.asymmetryScore / 2f) * 100).toInt()
            } else {
                binding.asymmetryLayout.visibility = View.GONE
            }
            
            // B - Bordes
            if (scores.borderScore >= 0f) {
                binding.borderLayout.visibility = View.VISIBLE
                binding.borderScore.text = "${String.format("%.1f", scores.borderScore)}/8"
                binding.borderProgress.progress = ((scores.borderScore / 8f) * 100).toInt()
            } else {
                binding.borderLayout.visibility = View.GONE
            }
            
            // C - Color
            if (scores.colorScore >= 0f) {
                binding.colorLayout.visibility = View.VISIBLE
                binding.colorScore.text = "${String.format("%.1f", scores.colorScore)}/6"
                binding.colorProgress.progress = ((scores.colorScore / 6f) * 100).toInt()
            } else {
                binding.colorLayout.visibility = View.GONE
            }
            
            // D - Diámetro
            if (scores.diameterScore >= 0f) {
                binding.diameterLayout.visibility = View.VISIBLE
                binding.diameterScore.text = "${String.format("%.1f", scores.diameterScore)}/5"
                binding.diameterProgress.progress = ((scores.diameterScore / 5f) * 100).toInt()
            } else {
                binding.diameterLayout.visibility = View.GONE
            }
            
            // E - Evolución (opcional)
            scores.evolutionScore?.let { evolutionScoreValue ->
                if (evolutionScoreValue > 0f) {
                    binding.evolutionLayout.visibility = View.VISIBLE
                    binding.evolutionScore.text = "${String.format("%.1f", evolutionScoreValue)}/3"
                    binding.evolutionProgress.progress = ((evolutionScoreValue / 3f) * 100).toInt()
                } else {
                    binding.evolutionLayout.visibility = View.GONE
                }
            } ?: run {
                binding.evolutionLayout.visibility = View.GONE
            }
            
            // Score total ABCDE
            if (scores.totalScore > 0f) {
                binding.abcdeTotalScore.text = "Score ABCDE Total: ${String.format("%.1f", scores.totalScore)}"
            } else {
                binding.abcdeTotalScore.text = "Score ABCDE Total: --"
            }
            
        } else {
            // Ocultar todos los layouts si no hay datos
            binding.asymmetryLayout.visibility = View.GONE
            binding.borderLayout.visibility = View.GONE
            binding.colorLayout.visibility = View.GONE
            binding.diameterLayout.visibility = View.GONE
            binding.evolutionLayout.visibility = View.GONE
            binding.abcdeTotalScore.text = "Score ABCDE Total: --"
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
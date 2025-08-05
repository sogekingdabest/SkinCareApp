package es.monsteraltech.skincare_tfm.body.mole
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.util.ImageLoadingUtil
import es.monsteraltech.skincare_tfm.body.mole.util.RiskLevelTranslator
import es.monsteraltech.skincare_tfm.databinding.ActivityAnalysisDetailBinding
import java.text.SimpleDateFormat
import java.util.Locale
class AnalysisDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnalysisDetailBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalle del Análisis"
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
            val createdAt = com.google.firebase.Timestamp(java.util.Date(createdAtMillis))
            val metadataBundle = intent.getBundleExtra("ANALYSIS_METADATA")
            val analysisMetadata = metadataBundle?.let { bundle ->
                val map = mutableMapOf<String, Any>()
                for (key in bundle.keySet()) {
                    bundle.get(key)?.let { value ->
                        map[key] = value
                    }
                }
                map.toMap()
            } ?: emptyMap()
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
                createdAt = createdAt,
                analysisMetadata = analysisMetadata
            )
        } catch (e: Exception) {
            null
        }
    }
    private fun displayAnalysisDetails(analysis: AnalysisData) {
        ImageLoadingUtil.loadImageWithFallback(
            context = this,
            imageView = binding.resultImageView,
            imageUrl = analysis.imageUrl,
            config = ImageLoadingUtil.Configs.fullSize(),
            coroutineScope = lifecycleScope
        )
        val combinedScore = analysis.combinedScore
        if (combinedScore > 0f) {
            binding.combinedScoreText.text = String.format("%.1f%%", combinedScore * 100)
        } else {
            binding.combinedScoreText.text = "--"
        }
        if (analysis.aiProbability > 0f && analysis.aiConfidence > 0f) {
            binding.aiProbabilityText.text = "IA: ${String.format("%.1f%%", analysis.aiProbability * 100)} (Confianza: ${String.format("%.1f%%", analysis.aiConfidence * 100)})"
            binding.aiProbabilityText.visibility = View.VISIBLE
        } else {
            binding.aiProbabilityText.visibility = View.GONE
        }
        displayRiskLevel(analysis.riskLevel)
        displayABCDEScoresDetailed(analysis)
        if (analysis.recommendation.isNotEmpty()) {
            binding.recommendationText.text = analysis.recommendation
        } else {
            binding.recommendationText.text = "Análisis completado. Consulte los resultados detallados."
        }
        val date = analysis.createdAt.toDate()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))
        binding.analysisDateText.text = "Fecha: ${dateFormat.format(date)}"
        displayUserABCDEValues(analysis)
    }
    private fun displayRiskLevel(riskLevel: String) {
        if (riskLevel.isNotEmpty()) {
            val translatedRiskLevel = RiskLevelTranslator.translateRiskLevel(this, riskLevel)
            binding.riskLevelText.text = "Riesgo: $translatedRiskLevel"
            binding.riskIndicator.visibility = View.VISIBLE
            val colorRes = RiskLevelTranslator.getRiskLevelColor(riskLevel)
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
        if (scores.totalScore > 0f || scores.asymmetryScore > 0f ||
            scores.borderScore > 0f || scores.colorScore > 0f || scores.diameterScore > 0f) {
            if (scores.asymmetryScore >= 0f) {
                binding.asymmetryLayout.visibility = View.VISIBLE
                binding.asymmetryScore.text = "${String.format("%.1f", scores.asymmetryScore)}/2"
                binding.asymmetryProgress.progress = ((scores.asymmetryScore / 2f) * 100).toInt()
            } else {
                binding.asymmetryLayout.visibility = View.GONE
            }
            if (scores.borderScore >= 0f) {
                binding.borderLayout.visibility = View.VISIBLE
                binding.borderScore.text = "${String.format("%.1f", scores.borderScore)}/8"
                binding.borderProgress.progress = ((scores.borderScore / 8f) * 100).toInt()
            } else {
                binding.borderLayout.visibility = View.GONE
            }
            if (scores.colorScore >= 0f) {
                binding.colorLayout.visibility = View.VISIBLE
                binding.colorScore.text = "${String.format("%.1f", scores.colorScore)}/6"
                binding.colorProgress.progress = ((scores.colorScore / 6f) * 100).toInt()
            } else {
                binding.colorLayout.visibility = View.GONE
            }
            if (scores.diameterScore >= 0f) {
                binding.diameterLayout.visibility = View.VISIBLE
                binding.diameterScore.text = "${String.format("%.1f", scores.diameterScore)}/5"
                binding.diameterProgress.progress = ((scores.diameterScore / 5f) * 100).toInt()
            } else {
                binding.diameterLayout.visibility = View.GONE
            }
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
            if (scores.totalScore > 0f) {
                binding.abcdeTotalScore.text = "Score ABCDE Total: ${String.format("%.1f", scores.totalScore)}"
            } else {
                binding.abcdeTotalScore.text = "Score ABCDE Total: --"
            }
        } else {
            binding.asymmetryLayout.visibility = View.GONE
            binding.borderLayout.visibility = View.GONE
            binding.colorLayout.visibility = View.GONE
            binding.diameterLayout.visibility = View.GONE
            binding.evolutionLayout.visibility = View.GONE
            binding.abcdeTotalScore.text = "Score ABCDE Total: --"
        }
    }
    private fun displayUserABCDEValues(analysis: AnalysisData) {
        val metadata = analysis.analysisMetadata
        val userAsymmetry = metadata["userAsymmetry"] as? Float
        val userBorder = metadata["userBorder"] as? Float
        val userColor = metadata["userColor"] as? Float
        val userDiameter = metadata["userDiameter"] as? Float
        val userEvolution = metadata["userEvolution"] as? Float
        val userTotal = metadata["userTotal"] as? Float
        if (userAsymmetry != null || userBorder != null || userColor != null ||
            userDiameter != null || userEvolution != null) {
            binding.userAbcdeCard.visibility = View.VISIBLE
            userAsymmetry?.let { value ->
                binding.userAsymmetryLayout.visibility = View.VISIBLE
                binding.userAsymmetryScore.text = "${String.format("%.1f", value)}/2"
                binding.userAsymmetryProgress.progress = ((value / 2f) * 100).toInt()
            } ?: run {
                binding.userAsymmetryLayout.visibility = View.GONE
            }
            userBorder?.let { value ->
                binding.userBorderLayout.visibility = View.VISIBLE
                binding.userBorderScore.text = "${String.format("%.1f", value)}/8"
                binding.userBorderProgress.progress = ((value / 8f) * 100).toInt()
            } ?: run {
                binding.userBorderLayout.visibility = View.GONE
            }
            userColor?.let { value ->
                binding.userColorLayout.visibility = View.VISIBLE
                binding.userColorScore.text = "${String.format("%.1f", value)}/6"
                binding.userColorProgress.progress = ((value / 6f) * 100).toInt()
            } ?: run {
                binding.userColorLayout.visibility = View.GONE
            }
            userDiameter?.let { value ->
                binding.userDiameterLayout.visibility = View.VISIBLE
                binding.userDiameterScore.text = "${String.format("%.1f", value)}/5"
                binding.userDiameterProgress.progress = ((value / 5f) * 100).toInt()
            } ?: run {
                binding.userDiameterLayout.visibility = View.GONE
            }
            userEvolution?.let { value ->
                if (value > 0f) {
                    binding.userEvolutionLayout.visibility = View.VISIBLE
                    binding.userEvolutionScore.text = "${String.format("%.1f", value)}/3"
                    binding.userEvolutionProgress.progress = ((value / 3f) * 100).toInt()
                } else {
                    binding.userEvolutionLayout.visibility = View.GONE
                }
            } ?: run {
                binding.userEvolutionLayout.visibility = View.GONE
            }
            userTotal?.let { total ->
                binding.userAbcdeTotalScore.text = "Score ABCDE Total (Usuario): ${String.format("%.1f", total)}"
                val appTotal = analysis.abcdeScores.totalScore
                val difference = kotlin.math.abs(appTotal - total)
                val comparisonMessage = when {
                    difference < 1.0f -> "¡Muy similar! Diferencia: ${String.format("%.1f", difference)} puntos"
                    difference < 3.0f -> "Bastante similar. Diferencia: ${String.format("%.1f", difference)} puntos"
                    difference < 5.0f -> "Algunas diferencias. Diferencia: ${String.format("%.1f", difference)} puntos"
                    else -> "Diferencias significativas. Diferencia: ${String.format("%.1f", difference)} puntos"
                }
                binding.userComparisonText.text = comparisonMessage
            } ?: run {
                binding.userAbcdeTotalScore.text = "Score ABCDE Total (Usuario): --"
                binding.userComparisonText.text = "No se pudo calcular la comparación"
            }
        } else {
            binding.userAbcdeCard.visibility = View.GONE
        }
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    companion object {
        fun createIntent(context: android.content.Context, analysisData: AnalysisData): Intent {
            val intent = Intent(context, AnalysisDetailActivity::class.java)
            intent.putExtra("ANALYSIS_ID", analysisData.id)
            intent.putExtra("MOLE_ID", analysisData.moleId)
            intent.putExtra("ANALYSIS_RESULT", analysisData.analysisResult)
            intent.putExtra("AI_PROBABILITY", analysisData.aiProbability)
            intent.putExtra("AI_CONFIDENCE", analysisData.aiConfidence)
            intent.putExtra("COMBINED_SCORE", analysisData.combinedScore)
            intent.putExtra("RISK_LEVEL", analysisData.riskLevel)
            intent.putExtra("RECOMMENDATION", analysisData.recommendation)
            intent.putExtra("IMAGE_URL", analysisData.imageUrl)
            intent.putExtra("CREATED_AT", analysisData.createdAt.toDate().time)
            intent.putExtra("ASYMMETRY_SCORE", analysisData.abcdeScores.asymmetryScore)
            intent.putExtra("BORDER_SCORE", analysisData.abcdeScores.borderScore)
            intent.putExtra("COLOR_SCORE", analysisData.abcdeScores.colorScore)
            intent.putExtra("DIAMETER_SCORE", analysisData.abcdeScores.diameterScore)
            intent.putExtra("EVOLUTION_SCORE", analysisData.abcdeScores.evolutionScore ?: -1f)
            intent.putExtra("TOTAL_SCORE", analysisData.abcdeScores.totalScore)
            val metadataBundle = Bundle()
            analysisData.analysisMetadata.forEach { (key, value) ->
                when (value) {
                    is String -> metadataBundle.putString(key, value)
                    is Float -> metadataBundle.putFloat(key, value)
                    is Int -> metadataBundle.putInt(key, value)
                    is Boolean -> metadataBundle.putBoolean(key, value)
                    is Long -> metadataBundle.putLong(key, value)
                    is Double -> metadataBundle.putDouble(key, value)
                }
            }
            intent.putExtra("ANALYSIS_METADATA", metadataBundle)
            return intent
        }
    }
}
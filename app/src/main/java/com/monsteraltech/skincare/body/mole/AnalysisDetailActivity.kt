package com.monsteraltech.skincare.body.mole
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.util.ImageLoadingUtil
import com.monsteraltech.skincare.body.mole.util.RiskLevelTranslator
import com.monsteraltech.skincare.databinding.ActivityAnalysisDetailBinding
import java.text.SimpleDateFormat
import java.util.Locale
@AndroidEntryPoint
class AnalysisDetailActivity : AppCompatActivity() {
    private val viewModel: MoleViewerViewModel by viewModels()
    private lateinit var binding: ActivityAnalysisDetailBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalle del Análisis"
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is MoleViewerViewModel.UiState.Analysis -> displayAnalysisDetails(state.data)
                        is MoleViewerViewModel.UiState.Error, MoleViewerViewModel.UiState.NoAnalysis -> finish()
                        MoleViewerViewModel.UiState.Loading -> Unit
                    }
                }
            }
        }
        viewModel.initialize(MoleViewerArgs.fromIntent(intent))
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
            binding.aiProbabilityText.text = com.monsteraltech.skincare.analysis.ExperimentalScorePresentation.ai(analysis.aiProbability)
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
        val date = java.util.Date.from(analysis.createdAt)
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
        fun createIntent(context: android.content.Context, analysisData: AnalysisData): Intent =
            MoleViewerArgs(analysisData.moleId, analysisData.id).toIntent(context, AnalysisDetailActivity::class.java)
    }
}

package com.monsteraltech.skincare.body.mole
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.body.mole.error.ErrorHandler
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.model.UserAbcdeInput
import com.monsteraltech.skincare.body.mole.util.ImageLoadingUtil
import com.monsteraltech.skincare.body.mole.util.RiskLevelTranslator
import com.monsteraltech.skincare.body.mole.view.EmptyStateView
import com.monsteraltech.skincare.camera.CameraActivity
import com.monsteraltech.skincare.camera.MoleCaptureContract
import com.monsteraltech.skincare.databinding.ActivityMoleViewerBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Pantalla de detalle de un lunar. La lógica de carga/guardado vive en
 * [MoleViewerViewModel]; aquí solo se pintan estados y se recogen gestos.
 */
@AndroidEntryPoint
class MoleViewerActivity : AppCompatActivity() {

    private val viewModel: MoleViewerViewModel by viewModels()
    private lateinit var binding: ActivityMoleViewerBinding
    private lateinit var args: MoleViewerArgs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMoleViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        args = MoleViewerArgs.fromIntent(intent)
        setupToolbar()
        setupHeader()
        setupUserInputSection()
        binding.viewHistoryButton.setOnClickListener { openAnalysisHistory() }
        binding.captureFollowUpButton.setOnClickListener {
            openFollowUpCapture()
        }
        binding.captureFollowUpButton.visibility =
            if (
                args.moleId.isNotBlank() &&
                args.historicalAnalysis == null
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        observeViewModel()
        viewModel.initialize(args)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupHeader() {
        binding.detailTitle.text = args.title
        if (args.description.isNotEmpty()) {
            binding.detailDescription.text = args.description
            binding.detailDescription.visibility = View.VISIBLE
        } else {
            binding.detailDescription.visibility = View.GONE
        }
        ImageLoadingUtil.loadImageWithFallback(
            context = this,
            imageView = binding.resultImageView,
            imageUrl = args.imageUrl.ifEmpty { null },
            config = ImageLoadingUtil.Configs.fullSize(),
            coroutineScope = lifecycleScope
        )
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it) } }
                launch { viewModel.isSavingUserValues.collect { renderSaving(it) } }
                launch { viewModel.events.collect { handleEvent(it) } }
            }
        }
    }

    private fun handleEvent(event: MoleViewerViewModel.Event) {
        when (event) {
            is MoleViewerViewModel.Event.RetryAttempt -> {
                binding.emptyStateView.showRetryIndicators(event.attempt, event.maxAttempts)
                Toast.makeText(
                    this,
                    getString(R.string.retry_attempting, event.attempt, event.maxAttempts),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is MoleViewerViewModel.Event.RetrySucceeded ->
                Toast.makeText(
                    this,
                    getString(R.string.retry_success_after_attempts, event.attemptsMade),
                    Toast.LENGTH_SHORT
                ).show()
            is MoleViewerViewModel.Event.RetriesFailed ->
                Toast.makeText(
                    this,
                    getString(R.string.retry_failed_all_attempts, event.attemptsMade),
                    Toast.LENGTH_LONG
                ).show()
            is MoleViewerViewModel.Event.UserValuesSaved -> {
                Toast.makeText(this, "Valores guardados correctamente", Toast.LENGTH_SHORT).show()
                collapseUserInput()
            }
            is MoleViewerViewModel.Event.UserValuesSaveFailed ->
                Toast.makeText(this, "Error al guardar los valores", Toast.LENGTH_SHORT).show()
        }
    }

    private fun render(state: MoleViewerViewModel.UiState) {
        binding.emptyStateView.hideRetryIndicators()
        when (state) {
            is MoleViewerViewModel.UiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.analysisContainer.visibility = View.GONE
                binding.emptyStateView.hide()
            }
            is MoleViewerViewModel.UiState.Analysis ->
                renderAnalysis(state.data, state.analysisCount)
            is MoleViewerViewModel.UiState.RawResult ->
                renderRawResult(state.text, state.analysisCount)
            is MoleViewerViewModel.UiState.NoAnalysis -> {
                binding.progressBar.visibility = View.GONE
                binding.analysisContainer.visibility = View.GONE
                binding.viewHistoryButton.visibility = View.GONE
                binding.emptyStateView.setEmptyState(
                    type = EmptyStateView.EmptyStateType.NO_ANALYSIS,
                    primaryAction = { finish() }
                )
            }
            is MoleViewerViewModel.UiState.Error -> renderError(state.result)
        }
    }

    private fun renderAnalysis(analysisData: AnalysisData, analysisCount: Int) {
        binding.progressBar.visibility = View.GONE
        binding.analysisContainer.visibility = View.VISIBLE
        binding.emptyStateView.hide()

        binding.combinedScoreText.text = if (analysisData.combinedScore > 0f) {
            String.format("%.1f%%", analysisData.combinedScore * 100)
        } else {
            "--"
        }

        if (analysisData.aiProbability > 0f && analysisData.aiConfidence > 0f) {
            binding.aiProbabilityText.text = "IA: ${String.format("%.1f%%", analysisData.aiProbability * 100)} " +
                    "(Confianza: ${String.format("%.1f%%", analysisData.aiConfidence * 100)})"
            binding.aiProbabilityText.visibility = View.VISIBLE
        } else {
            binding.aiProbabilityText.visibility = View.GONE
        }

        renderRiskLevel(analysisData.riskLevel)
        renderAbcdeScores(analysisData)

        binding.recommendationText.text = analysisData.recommendation.ifEmpty {
            "Análisis completado. Consulte los resultados detallados."
        }
        binding.analysisDateText.text = "Fecha: ${formatDate(analysisData.createdAt.toDate())}"
        binding.analysisDateText.visibility = View.VISIBLE

        renderHistoryButton(analysisCount)
        renderUserValues(analysisData)
    }

    private fun renderRiskLevel(riskLevel: String) {
        if (riskLevel.isNotEmpty()) {
            val translated = RiskLevelTranslator.translateRiskLevel(this, riskLevel)
            binding.riskLevelText.text = "Riesgo: $translated"
            binding.riskIndicator.visibility = View.VISIBLE
            val color = getColor(RiskLevelTranslator.getRiskLevelColor(riskLevel))
            binding.riskLevelText.setTextColor(color)
            binding.riskIndicator.setBackgroundColor(color)
        } else {
            binding.riskLevelText.text = "Riesgo: --"
            binding.riskIndicator.visibility = View.GONE
        }
    }

    private fun renderAbcdeScores(analysisData: AnalysisData) {
        val scores = analysisData.abcdeScores
        val hasScores = scores.totalScore > 0f || scores.asymmetryScore > 0f ||
                scores.borderScore > 0f || scores.colorScore > 0f || scores.diameterScore > 0f
        if (!hasScores) {
            binding.asymmetryLayout.visibility = View.GONE
            binding.borderLayout.visibility = View.GONE
            binding.colorLayout.visibility = View.GONE
            binding.diameterLayout.visibility = View.GONE
            binding.evolutionLayout.visibility = View.GONE
            binding.abcdeTotalScore.text = "Score ABCDE Total: --"
            return
        }
        binding.asymmetryLayout.visibility = View.VISIBLE
        binding.asymmetryScore.text = "${String.format("%.1f", scores.asymmetryScore)}/2"
        binding.asymmetryProgress.progress = ((scores.asymmetryScore / 2f) * 100).toInt()

        binding.borderLayout.visibility = View.VISIBLE
        binding.borderScore.text = "${String.format("%.1f", scores.borderScore)}/8"
        binding.borderProgress.progress = ((scores.borderScore / 8f) * 100).toInt()

        binding.colorLayout.visibility = View.VISIBLE
        binding.colorScore.text = "${String.format("%.1f", scores.colorScore)}/6"
        binding.colorProgress.progress = ((scores.colorScore / 6f) * 100).toInt()

        binding.diameterLayout.visibility = View.VISIBLE
        binding.diameterScore.text = "${String.format("%.1f", scores.diameterScore)}/5"
        binding.diameterProgress.progress = ((scores.diameterScore / 5f) * 100).toInt()

        binding.abcdeTotalScore.text = if (scores.totalScore > 0f) {
            "Score ABCDE Total: ${String.format("%.1f", scores.totalScore)}"
        } else {
            "Score ABCDE Total: --"
        }
    }

    private fun renderRawResult(text: String, analysisCount: Int) {
        binding.progressBar.visibility = View.GONE
        binding.analysisContainer.visibility = View.VISIBLE
        binding.emptyStateView.hide()
        binding.recommendationText.text = text
        binding.combinedScoreText.text = "--"
        binding.riskLevelText.text = "Riesgo: --"
        binding.riskIndicator.visibility = View.GONE
        binding.aiProbabilityText.visibility = View.GONE
        binding.asymmetryLayout.visibility = View.GONE
        binding.borderLayout.visibility = View.GONE
        binding.colorLayout.visibility = View.GONE
        binding.diameterLayout.visibility = View.GONE
        binding.evolutionLayout.visibility = View.GONE
        binding.abcdeTotalScore.text = "Score ABCDE Total: --"
        binding.analysisDateText.visibility = View.GONE
        renderHistoryButton(analysisCount)
    }

    private fun renderHistoryButton(analysisCount: Int) {
        if (analysisCount > 0 && args.moleId.isNotEmpty() && args.historicalAnalysis == null) {
            binding.viewHistoryButton.visibility = View.VISIBLE
            binding.viewHistoryButton.text = resources.getQuantityString(R.plurals.mole_observation_history, analysisCount, analysisCount)
        } else {
            binding.viewHistoryButton.visibility = View.GONE
        }
    }

    private fun renderError(errorResult: ErrorHandler.ErrorResult) {
        binding.progressBar.visibility = View.GONE
        binding.analysisContainer.visibility = View.GONE
        binding.viewHistoryButton.visibility = View.GONE
        val emptyStateType = when (errorResult.type) {
            ErrorHandler.ErrorType.NETWORK_ERROR -> EmptyStateView.EmptyStateType.NETWORK_ERROR
            ErrorHandler.ErrorType.AUTHENTICATION_ERROR -> EmptyStateView.EmptyStateType.AUTHENTICATION_ERROR
            ErrorHandler.ErrorType.DATA_NOT_FOUND -> EmptyStateView.EmptyStateType.NO_ANALYSIS
            else -> EmptyStateView.EmptyStateType.LOADING_FAILED
        }
        binding.emptyStateView.setEmptyState(
            type = emptyStateType,
            primaryAction = if (errorResult.isRetryable) {
                { viewModel.retry() }
            } else null,
            secondaryAction = { finish() }
        )
        Toast.makeText(this, errorResult.userMessage, Toast.LENGTH_LONG).show()
    }

    // --- Valoración ABCDE manual del usuario ---

    private fun renderUserValues(analysisData: AnalysisData) {
        val savedValues = UserAbcdeInput.fromMetadata(analysisData.analysisMetadata)
        if (savedValues != null) {
            binding.userInputCard.visibility = View.GONE
            binding.userAbcdeCard.visibility = View.VISIBLE
            renderSavedUserValues(savedValues, analysisData.abcdeScores.totalScore)
        } else {
            binding.userAbcdeCard.visibility = View.GONE
            binding.userInputCard.visibility = View.VISIBLE
            val appTotal = analysisData.abcdeScores.totalScore
            binding.aiTotalScore.text = if (appTotal > 0f) String.format("%.1f", appTotal) else "--"
            resetUserInputValues()
        }
    }

    private fun renderSavedUserValues(values: UserAbcdeInput, appTotal: Float) {
        renderSavedCriterion(binding.userAsymmetryLayout, binding.userAsymmetryScore, binding.userAsymmetryProgress, values.asymmetry, 2f)
        renderSavedCriterion(binding.userBorderLayout, binding.userBorderScore, binding.userBorderProgress, values.border, 8f)
        renderSavedCriterion(binding.userColorLayout, binding.userColorScore, binding.userColorProgress, values.color, 6f)
        renderSavedCriterion(binding.userDiameterLayout, binding.userDiameterScore, binding.userDiameterProgress, values.diameter, 5f)
        if (values.evolution > 0f) {
            renderSavedCriterion(binding.userEvolutionLayout, binding.userEvolutionScore, binding.userEvolutionProgress, values.evolution, 3f)
        }
        val userTotal = values.total()
        binding.userAbcdeTotalScore.text = "Score ABCDE Total (Usuario): ${String.format("%.1f", userTotal)}"
        binding.userComparisonText.text = comparisonMessage(appTotal, userTotal)
    }

    private fun renderSavedCriterion(
        layout: View,
        scoreText: TextView,
        progress: com.google.android.material.progressindicator.LinearProgressIndicator,
        value: Float,
        maxValue: Float
    ) {
        layout.visibility = View.VISIBLE
        scoreText.text = "${String.format("%.1f", value)}/${maxValue.toInt()}"
        progress.progress = ((value / maxValue) * 100).toInt()
    }

    private fun setupUserInputSection() {
        binding.toggleUserInputButton.setOnClickListener {
            val isVisible = binding.userInputContainer.visibility == View.VISIBLE
            setUserInputExpanded(!isVisible)
        }
        binding.cancelButton.setOnClickListener {
            setUserInputExpanded(false)
            resetUserInputValues()
        }
        binding.saveButton.setOnClickListener {
            viewModel.saveUserValues(currentUserInput())
        }
        setupSliderListener(binding.userAsymmetrySlider, binding.userAsymmetryValue, 2.0f)
        setupSliderListener(binding.userBorderSlider, binding.userBorderValue, 8.0f)
        setupSliderListener(binding.userColorSlider, binding.userColorValue, 6.0f)
        setupSliderListener(binding.userDiameterSlider, binding.userDiameterValue, 5.0f)
        setupSliderListener(binding.userEvolutionSlider, binding.userEvolutionValue, 3.0f)
        setupInfoButtons()
    }

    private fun setUserInputExpanded(expanded: Boolean) {
        binding.userInputContainer.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.actionButtonsContainer.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.toggleUserInputButton.text = getString(
            if (expanded) R.string.user_abcde_hide_button else R.string.user_abcde_add_button
        )
    }

    private fun collapseUserInput() {
        setUserInputExpanded(false)
        binding.toggleUserInputButton.visibility = View.VISIBLE
    }

    private fun currentUserInput() = UserAbcdeInput(
        asymmetry = binding.userAsymmetrySlider.value,
        border = binding.userBorderSlider.value,
        color = binding.userColorSlider.value,
        diameter = binding.userDiameterSlider.value,
        evolution = binding.userEvolutionSlider.value
    )

    private fun setupSliderListener(slider: Slider, valueText: TextView, maxValue: Float) {
        slider.addOnChangeListener { _, value, _ ->
            valueText.text = String.format("%.1f/%.0f", value, maxValue)
            updateUserTotalAndComparison()
        }
    }

    private fun updateUserTotalAndComparison() {
        val userTotal = currentUserInput().total()
        binding.userTotalScore.text = String.format("%.1f", userTotal)
        val aiTotal = binding.aiTotalScore.text.toString().replace("--", "0").toFloatOrNull() ?: 0f
        binding.comparisonText.text = if (aiTotal == 0f) {
            "Introduce valores para ver la comparación"
        } else {
            comparisonMessage(aiTotal, userTotal)
        }
    }

    private fun comparisonMessage(aiTotal: Float, userTotal: Float): String {
        val difference = UserAbcdeInput.difference(aiTotal, userTotal)
        val messageRes = when (UserAbcdeInput.comparisonBucket(aiTotal, userTotal)) {
            UserAbcdeInput.Companion.ComparisonBucket.SIMILAR -> R.string.user_abcde_comparison_similar
            UserAbcdeInput.Companion.ComparisonBucket.SOMEWHAT_SIMILAR -> R.string.user_abcde_comparison_somewhat
            UserAbcdeInput.Companion.ComparisonBucket.SOME_DIFFERENCES -> R.string.user_abcde_comparison_different
            UserAbcdeInput.Companion.ComparisonBucket.VERY_DIFFERENT -> R.string.user_abcde_comparison_very_different
        }
        return getString(messageRes, difference)
    }

    private fun renderSaving(isSaving: Boolean) {
        binding.saveButton.isEnabled = !isSaving
        binding.saveButton.text = getString(
            if (isSaving) R.string.user_abcde_saving else R.string.user_abcde_save
        )
    }

    private fun resetUserInputValues() {
        binding.userAsymmetrySlider.value = 0f
        binding.userBorderSlider.value = 0f
        binding.userColorSlider.value = 0f
        binding.userDiameterSlider.value = 0f
        binding.userEvolutionSlider.value = 0f
        binding.userAsymmetryValue.text = "0.0/2"
        binding.userBorderValue.text = "0.0/8"
        binding.userColorValue.text = "0.0/6"
        binding.userDiameterValue.text = "0.0/5"
        binding.userEvolutionValue.text = "0.0/3"
        binding.userTotalScore.text = "0.0"
        binding.comparisonText.text = "Ajusta los valores para comparar con el análisis de la IA"
        setUserInputExpanded(false)
        binding.toggleUserInputButton.visibility = View.VISIBLE
    }

    private fun setupInfoButtons() {
        binding.asymmetryInfoButton.setOnClickListener {
            showABCDECriterionInfo(
                getString(R.string.abcde_asymmetry_info_title),
                getString(R.string.abcde_asymmetry_info_content)
            )
        }
        binding.borderInfoButton.setOnClickListener {
            showABCDECriterionInfo(
                getString(R.string.abcde_border_info_title),
                getString(R.string.abcde_border_info_content)
            )
        }
        binding.colorInfoButton.setOnClickListener {
            showABCDECriterionInfo(
                getString(R.string.abcde_color_info_title),
                getString(R.string.abcde_color_info_content)
            )
        }
        binding.diameterInfoButton.setOnClickListener {
            showABCDECriterionInfo(
                getString(R.string.abcde_diameter_info_title),
                getString(R.string.abcde_diameter_info_content)
            )
        }
        binding.evolutionInfoButton.setOnClickListener {
            showABCDECriterionInfo(
                getString(R.string.abcde_evolution_info_title),
                getString(R.string.abcde_evolution_info_content)
            )
        }
    }

    private fun showABCDECriterionInfo(title: String, content: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(content)
            .setPositiveButton("Entendido", null)
            .setIcon(R.drawable.ic_info)
            .show()
    }

    private fun openAnalysisHistory() {
        if (args.moleId.isNotEmpty()) {
            val intent = Intent(this, MoleAnalysisHistoryActivity::class.java).apply {
                putExtra("MOLE_ID", args.moleId)
                putExtra("MOLE_TITLE", args.title)
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, getString(R.string.error_data_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFollowUpCapture() {
        if (args.moleId.isBlank()) {
            Toast.makeText(
                this,
                getString(R.string.error_data_not_found),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        startActivity(
            Intent(this, CameraActivity::class.java).apply {
                putExtra(
                    MoleCaptureContract.EXTRA_MOLE_ID,
                    args.moleId
                )
                putExtra(
                    MoleCaptureContract.EXTRA_MOLE_TITLE,
                    args.title
                )
                putExtra(
                    MoleCaptureContract.EXTRA_MOLE_DESCRIPTION,
                    args.description
                )
                putExtra(
                    MoleCaptureContract.EXTRA_BODY_PART_COLOR,
                    args.bodyPartColorCode
                )
            }
        )
    }

    private fun formatDate(date: java.util.Date): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))
        return formatter.format(date)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

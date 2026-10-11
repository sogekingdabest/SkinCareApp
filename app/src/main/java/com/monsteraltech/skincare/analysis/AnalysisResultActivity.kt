package com.monsteraltech.skincare.analysis
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import com.monsteraltech.skincare.MainActivity
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.body.BodyPartActivity
import com.monsteraltech.skincare.body.mole.dialog.MoleSelectorDialog
import com.monsteraltech.skincare.body.mole.model.UserAbcdeInput
import com.monsteraltech.skincare.body.mole.util.RiskLevelTranslator
import com.monsteraltech.skincare.camera.MoleCaptureContract
import com.monsteraltech.skincare.databinding.ActivityAnalysisResultEnhancedBinding
import com.monsteraltech.skincare.utils.MedicalDisclaimerHelper
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Pantalla de resultado del análisis de una captura. La orquestación del
 * análisis y el guardado viven en [AnalysisResultViewModel]; esta Activity
 * pinta estados, recoge el formulario y navega.
 */
@AndroidEntryPoint
class AnalysisResultActivity : AppCompatActivity() {

    private val viewModel: AnalysisResultViewModel by viewModels()
    private lateinit var binding: ActivityAnalysisResultEnhancedBinding
    private lateinit var explanationAdapter: ExplanationAdapter
    private lateinit var progressManager: ProgressManager
    private lateinit var disclaimerHelper: MedicalDisclaimerHelper
    private var bodyPartColorCode: String? = null
    private var selectedBodyPart: String = ""
    private var overlayShown = false
    private var targetMoleId: String? = null
    private var targetMoleTitle: String? = null
    private var targetMoleDescription: String? = null

    private val bodyPartToColorMap = mapOf(
        "Cabeza" to "#FF000000",
        "Brazo derecho" to "#FFED1C24",
        "Torso" to "#FFFFC90E",
        "Brazo izquierdo" to "#FF22B14C",
        "Pierna derecha" to "#FF3F48CC",
        "Pierna izquierda" to "#FFED00FF"
    )
    private val colorToBodyPartMap = bodyPartToColorMap.entries.associate { (k, v) -> v to k }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisResultEnhancedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bodyPartColorCode =
            intent.getStringExtra(
                MoleCaptureContract.EXTRA_BODY_PART_COLOR
            )
        targetMoleId =
            intent.getStringExtra(MoleCaptureContract.EXTRA_MOLE_ID)
                ?.takeIf(String::isNotBlank)
        targetMoleTitle =
            intent.getStringExtra(MoleCaptureContract.EXTRA_MOLE_TITLE)
        targetMoleDescription =
            intent.getStringExtra(
                MoleCaptureContract.EXTRA_MOLE_DESCRIPTION
            )
        setupUI()
        setupBodyPart()
        setupBackNavigation()
        observeViewModel()

        val photoPath =
            intent.getStringExtra(MoleCaptureContract.EXTRA_PHOTO_PATH)
        if (photoPath != null) {
            viewModel.startAnalysis(
                photoPath = photoPath,
                isFrontCamera =
                    intent.getBooleanExtra(
                        MoleCaptureContract.EXTRA_IS_FRONT_CAMERA,
                        false
                    ),
                pixelDensity = resources.displayMetrics.density,
                targetMoleId = targetMoleId
            )
        } else {
            showError(getString(R.string.analysis_image_load_error))
        }
    }

    private fun setupUI() {
        disclaimerHelper = MedicalDisclaimerHelper(this)
        explanationAdapter = ExplanationAdapter()
        binding.explanationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AnalysisResultActivity)
            adapter = explanationAdapter
        }
        progressManager = ProgressManager(
            context = this,
            progressBar = binding.processingProgressBar,
            statusText = binding.processingStatusText,
            cancelButton = binding.cancelProcessingButton
        )
        progressManager.setCancelListener { viewModel.cancelAnalysis() }

        binding.saveButton.setOnClickListener {
            saveCurrentObservation()
        }
        binding.assignToExistingMoleButton.setOnClickListener { showMoleSelectorDialog() }
        if (targetMoleId != null) {
            binding.saveButton.setText(R.string.save_follow_up)
            binding.assignToExistingMoleButton.visibility = View.GONE
            binding.titleEditText.setText(targetMoleTitle.orEmpty())
            binding.descriptionEditText.setText(
                targetMoleDescription.orEmpty()
            )
        }
        binding.historyButton.setOnClickListener { showHistoryDialog() }
        binding.infoButton.setOnClickListener { showABCDEInfo() }
        binding.disclaimerInfoButton.setOnClickListener {
            disclaimerHelper.showInformationalDisclaimer(this)
        }
        setupBodyPartSpinner()
        setupUserInputSection()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.analysisState.collect { renderAnalysisState(it) } }
                launch {
                    viewModel.capturedBitmap.collect { bitmap ->
                        bitmap?.let { binding.resultImageView.setImageBitmap(it) }
                    }
                }
                launch { viewModel.isSaving.collect { renderSaving(it) } }
                launch { viewModel.events.collect { handleEvent(it) } }
            }
        }
    }

    private fun renderAnalysisState(state: AnalysisResultViewModel.AnalysisState) {
        renderSaving(viewModel.isSaving.value)
        when (state) {
            is AnalysisResultViewModel.AnalysisState.Idle -> Unit
            is AnalysisResultViewModel.AnalysisState.Processing -> {
                if (!overlayShown) {
                    overlayShown = true
                    showProcessingOverlay()
                    progressManager.startProcessing()
                }
                progressManager.updateProgressWithAccessibility(state.progress, state.message)
                state.stage?.let { stage ->
                    progressManager.showStageWithAccessibility(stage)
                    val stageText = "Etapa ${stage.ordinal + 1}/5: ${stage.message}"
                    binding.processingStageText.text = stageText
                    binding.processingStageText.contentDescription = "Progreso del análisis: $stageText"
                }
            }
            is AnalysisResultViewModel.AnalysisState.Ready -> {
                if (overlayShown) {
                    progressManager.completeProcessingWithAccessibility()
                }
                displayResults(state.result)
                binding.processingOverlay.postDelayed({ hideProcessingOverlay() }, 1500)
            }
            is AnalysisResultViewModel.AnalysisState.Partial -> {
                hideProcessingOverlay()
                displayPartialResult(state.result)
            }
            is AnalysisResultViewModel.AnalysisState.Cancelled -> {
                hideProcessingOverlay()
                displayUnavailable(getString(R.string.analysis_cancelled))
            }
            is AnalysisResultViewModel.AnalysisState.Failed -> {
                progressManager.showErrorWithAccessibility(state.message)
                hideProcessingOverlay()
                showError("Error en análisis: ${state.message}")
            }
        }
    }

    private fun handleEvent(event: AnalysisResultViewModel.Event) {
        when (event) {
            is AnalysisResultViewModel.Event.ValidationFailed ->
                Toast.makeText(this, getString(event.messageRes), Toast.LENGTH_SHORT).show()
            is AnalysisResultViewModel.Event.SaveCompleted -> {
                val message = event.existingMoleTitle?.let {
                    getString(R.string.save_associated_success, it)
                } ?: getString(R.string.save_success)
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                navigateBack()
            }
            is AnalysisResultViewModel.Event.SaveFailed ->
                Toast.makeText(
                    this,
                    getString(R.string.save_error, event.detail ?: getString(R.string.error_unknown)),
                    Toast.LENGTH_LONG
                ).show()
            is AnalysisResultViewModel.Event.AnalysisCancelled -> {
                progressManager.showCancelledWithAccessibility()
                hideProcessingOverlay()
                Toast.makeText(this, getString(R.string.analysis_cancelled), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderSaving(isSaving: Boolean) {
        val canSave = viewModel.analysisState.value is AnalysisResultViewModel.AnalysisState.Ready && !isSaving
        binding.saveButton.isEnabled = canSave
        binding.assignToExistingMoleButton.isEnabled = canSave
        binding.saveButton.text = getString(
            if (isSaving) R.string.user_abcde_saving else if (targetMoleId != null) R.string.save_follow_up else R.string.save_as_new_mole
        )
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.isProcessing) {
                    AlertDialog.Builder(this@AnalysisResultActivity)
                        .setTitle("Cancelar análisis")
                        .setMessage("¿Deseas cancelar el análisis en curso?")
                        .setPositiveButton("Sí") { _, _ ->
                            viewModel.cancelAnalysis()
                            finish()
                        }
                        .setNegativeButton("No", null)
                        .show()
                } else {
                    finish()
                }
            }
        })
    }

    private fun showProcessingOverlay() {
        binding.processingOverlay.visibility = View.VISIBLE
        binding.mainContent.visibility = View.GONE
        progressManager.show()
    }

    private fun hideProcessingOverlay() {
        binding.processingOverlay.visibility = View.GONE
        binding.mainContent.visibility = View.VISIBLE
        progressManager.hide()
    }

    private fun setupBodyPart() {
        if (bodyPartColorCode != null && colorToBodyPartMap.containsKey(bodyPartColorCode)) {
            selectedBodyPart = colorToBodyPartMap[bodyPartColorCode] ?: ""
            binding.bodyPartTextView.text = selectedBodyPart
            binding.bodyPartTextView.visibility = View.VISIBLE
            binding.bodyPartInputLayout.visibility = View.GONE
        } else {
            binding.bodyPartTextView.visibility = View.GONE
            binding.bodyPartInputLayout.visibility = View.VISIBLE
        }
    }

    private fun setupBodyPartSpinner() {
        val bodyPartsList = arrayListOf("Seleccionar parte del cuerpo") + bodyPartToColorMap.keys.toList()
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, bodyPartsList)
        binding.bodyPartSpinner.setAdapter(adapter)
        binding.bodyPartSpinner.setOnItemClickListener { _, _, position, _ ->
            if (position > 0) {
                selectedBodyPart = bodyPartsList[position]
                bodyPartColorCode = bodyPartToColorMap[selectedBodyPart]
            } else {
                selectedBodyPart = ""
                bodyPartColorCode = null
            }
        }
    }

    // --- Valoración ABCDE manual del usuario ---

    private fun setupUserInputSection() {
        binding.toggleUserInputButton.setOnClickListener {
            val isVisible = binding.userInputContainer.visibility == View.VISIBLE
            binding.userInputContainer.visibility = if (isVisible) View.GONE else View.VISIBLE
            binding.toggleUserInputButton.text = getString(
                if (isVisible) R.string.user_abcde_add_button else R.string.user_abcde_hide_button
            )
        }
        setupSliderListener(binding.userAsymmetrySlider, binding.userAsymmetryValue, 2.0f)
        setupSliderListener(binding.userBorderSlider, binding.userBorderValue, 8.0f)
        setupSliderListener(binding.userColorSlider, binding.userColorValue, 6.0f)
        setupSliderListener(binding.userDiameterSlider, binding.userDiameterValue, 5.0f)
        setupSliderListener(binding.userEvolutionSlider, binding.userEvolutionValue, 3.0f)
        setupInfoButtons()
    }

    private fun currentUserInput() = UserAbcdeInput(
        asymmetry = binding.userAsymmetrySlider.value,
        border = binding.userBorderSlider.value,
        color = binding.userColorSlider.value,
        diameter = binding.userDiameterSlider.value,
        evolution = binding.userEvolutionSlider.value
    )

    /** Los valores del usuario solo se guardan si desplegó la sección para introducirlos. */
    private fun currentUserInputIfVisible(): UserAbcdeInput? =
        if (binding.userInputContainer.isVisible) currentUserInput() else null

    private fun setupSliderListener(
        slider: com.google.android.material.slider.Slider,
        valueText: android.widget.TextView,
        maxValue: Float
    ) {
        slider.addOnChangeListener { _, value, _ ->
            valueText.text = String.format("%.1f/%.0f", value, maxValue)
            updateUserTotalAndComparison()
        }
    }

    private fun updateUserTotalAndComparison() {
        val userTotal = currentUserInput().total()
        binding.userTotalScore.text = String.format("%.1f", userTotal)

        val aiTotal = when (val state = viewModel.analysisState.value) {
            is AnalysisResultViewModel.AnalysisState.Ready -> state.result.abcdeResult.totalScore
            is AnalysisResultViewModel.AnalysisState.Partial -> (state.result.abcde as? AnalysisSignal.Available)?.value?.totalScore
            else -> null
        }
        if (aiTotal == null) {
            binding.comparisonText.setText(R.string.analysis_abcde_unavailable)
            return
        }
        val difference = UserAbcdeInput.difference(aiTotal, userTotal)
        val messageRes = when (UserAbcdeInput.comparisonBucket(aiTotal, userTotal)) {
            UserAbcdeInput.Companion.ComparisonBucket.SIMILAR -> R.string.user_abcde_comparison_similar
            UserAbcdeInput.Companion.ComparisonBucket.SOMEWHAT_SIMILAR -> R.string.user_abcde_comparison_somewhat
            UserAbcdeInput.Companion.ComparisonBucket.SOME_DIFFERENCES -> R.string.user_abcde_comparison_different
            UserAbcdeInput.Companion.ComparisonBucket.VERY_DIFFERENT -> R.string.user_abcde_comparison_very_different
        }
        binding.comparisonText.text = getString(messageRes, difference)
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

    // --- Presentación de resultados ---

    private fun displayResults(result: MelanomaAIDetector.CombinedAnalysisResult) {
        binding.abcdeCard.isVisible = true
        binding.riskIndicator.isVisible = true
        val scorePercentage = (result.combinedScore * 100).toInt()
        binding.combinedScoreText.text = "$scorePercentage%"
        updateRiskIndicator(result.combinedRiskLevel)
        binding.recommendationText.text = result.recommendation
        updateCardColor(result.urgencyLevel)
        displayABCDEScores(result.abcdeResult)
        binding.aiProbabilityText.text =
            "IA: ${(result.aiProbability * 100).toInt()}% (Confianza: ${(result.aiConfidence * 100).toInt()}%)"
        explanationAdapter.setExplanations(result.explanations)
        binding.historyButton.visibility =
            if (result.abcdeResult.evolutionScore != null) View.VISIBLE else View.GONE
        binding.aiTotalScore.text = String.format("%.1f", result.abcdeResult.totalScore)
        updateUserTotalAndComparison()
    }

    private fun displayPartialResult(result: AnalysisOutcome.Partial) {
        clearCombinedResult(getString(R.string.analysis_partial))
        binding.recommendationText.setText(R.string.analysis_partial_guidance)
        binding.aiProbabilityText.text = when (val ai = result.ai) {
            is AnalysisSignal.Available -> getString(R.string.analysis_ai_values, (ai.value.probability * 100).toInt(), (ai.value.confidence * 100).toInt())
            is AnalysisSignal.Unavailable -> ai.message
        }
        val abcde = (result.abcde as? AnalysisSignal.Available)?.value
        binding.abcdeCard.isVisible = abcde != null
        if (abcde != null) displayABCDEScores(abcde)
        binding.aiTotalScore.text = abcde?.let { String.format(Locale.getDefault(), "%.1f", it.totalScore) } ?: getString(R.string.analysis_unavailable)
        explanationAdapter.setExplanations(listOfNotNull(
            (result.ai as? AnalysisSignal.Unavailable)?.message,
            (result.abcde as? AnalysisSignal.Unavailable)?.message
        ))
        updateUserTotalAndComparison()
    }

    private fun clearCombinedResult(status: String) {
        binding.combinedScoreText.setText(R.string.analysis_unavailable)
        binding.riskLevelText.text = status
        binding.riskLevelText.setTextColor(binding.recommendationText.currentTextColor)
        binding.riskIndicator.isVisible = false
        binding.analysisCard.setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(binding.analysisCard, com.google.android.material.R.attr.colorSurface))
        binding.historyButton.isVisible = false
    }

    private fun displayUnavailable(status: String) {
        clearCombinedResult(status)
        binding.abcdeCard.isVisible = false
        binding.aiProbabilityText.setText(R.string.analysis_unavailable)
        binding.aiTotalScore.setText(R.string.analysis_unavailable)
        binding.comparisonText.setText(R.string.analysis_abcde_unavailable)
        binding.recommendationText.text = status
        explanationAdapter.setExplanations(emptyList())
    }

    private fun updateRiskIndicator(riskLevel: MelanomaAIDetector.RiskLevel) {
        val translatedText = RiskLevelTranslator.translateRiskLevel(this, riskLevel.name)
        val color = getColor(RiskLevelTranslator.getRiskLevelColor(riskLevel.name))
        binding.riskLevelText.setTextColor(color)
        binding.riskIndicator.setBackgroundColor(color)
        binding.riskLevelText.text = "Riesgo: $translatedText"
    }

    private fun updateCardColor(urgencyLevel: MelanomaAIDetector.UrgencyLevel) {
        val color = when (urgencyLevel) {
            MelanomaAIDetector.UrgencyLevel.ROUTINE -> getColor(R.color.card_routine)
            MelanomaAIDetector.UrgencyLevel.MONITOR -> getColor(R.color.card_monitor)
            MelanomaAIDetector.UrgencyLevel.CONSULT -> getColor(R.color.card_consult)
            MelanomaAIDetector.UrgencyLevel.URGENT -> getColor(R.color.card_urgent)
        }
        binding.analysisCard.setCardBackgroundColor(color)
    }

    private fun displayABCDEScores(abcdeResult: ABCDEAnalyzerOpenCV.ABCDEResult) {
        binding.asymmetryScore.text = String.format("%.1f/2", abcdeResult.asymmetryScore)
        binding.asymmetryProgress.setProgress((abcdeResult.asymmetryScore / 2f * 100).toInt(), true)
        binding.borderScore.text = String.format("%.1f/8", abcdeResult.borderScore)
        binding.borderProgress.setProgress((abcdeResult.borderScore / 8f * 100).toInt(), true)
        binding.colorScore.text = String.format("%.1f/6", abcdeResult.colorScore)
        binding.colorProgress.setProgress((abcdeResult.colorScore / 6f * 100).toInt(), true)
        binding.diameterScore.text = String.format("%.1f/5", abcdeResult.diameterScore)
        binding.diameterProgress.setProgress((abcdeResult.diameterScore / 5f * 100).toInt(), true)
        if (abcdeResult.evolutionScore != null) {
            binding.evolutionLayout.visibility = View.VISIBLE
            binding.evolutionScore.text = String.format("%.1f/3", abcdeResult.evolutionScore)
            binding.evolutionProgress.setProgress((abcdeResult.evolutionScore / 3f * 100).toInt(), true)
        } else {
            binding.evolutionLayout.visibility = View.GONE
        }
        binding.abcdeTotalScore.text = String.format("Score ABCDE: %.1f", abcdeResult.totalScore)
    }

    // --- Guardado y navegación ---

    private fun showMoleSelectorDialog() {
        val dialog = MoleSelectorDialog.newInstance()
        dialog.setOnMoleSelectedListener { selectedMole ->
            val title = binding.titleEditText.text.toString()
            val description = binding.descriptionEditText.text.toString()
            if (selectedMole != null) {
                viewModel.saveToExistingMole(
                    moleId = selectedMole.id,
                    existingMoleTitle = selectedMole.title,
                    title = title,
                    description = description,
                    bodyPart = selectedBodyPart,
                    bodyPartColorCode = bodyPartColorCode,
                    userInput = currentUserInputIfVisible()
                )
            } else {
                viewModel.saveAsNewMole(
                    title = title,
                    description = description,
                    bodyPart = selectedBodyPart,
                    bodyPartColorCode = bodyPartColorCode,
                    userInput = currentUserInputIfVisible()
                )
            }
        }
        dialog.show(supportFragmentManager, MoleSelectorDialog.TAG)
    }

    private fun saveCurrentObservation() {
        val moleId = targetMoleId
        if (moleId == null) {
            viewModel.saveAsNewMole(
                title = binding.titleEditText.text.toString(),
                description = binding.descriptionEditText.text.toString(),
                bodyPart = selectedBodyPart,
                bodyPartColorCode = bodyPartColorCode,
                userInput = currentUserInputIfVisible()
            )
            return
        }

        viewModel.saveToExistingMole(
            moleId = moleId,
            existingMoleTitle = targetMoleTitle,
            title = binding.titleEditText.text.toString(),
            description = binding.descriptionEditText.text.toString(),
            bodyPart = selectedBodyPart,
            bodyPartColorCode = bodyPartColorCode,
            userInput = currentUserInputIfVisible()
        )
    }

    private fun navigateBack() {
        val intent = if (bodyPartColorCode != null) {
            Intent(this, BodyPartActivity::class.java).apply {
                putExtra("COLOR_VALUE", bodyPartColorCode)
            }
        } else {
            Intent(this, MainActivity::class.java)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }

    private fun showHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle("Historial de Evolución")
            .setMessage("Función en desarrollo. Aquí se mostrará el historial de cambios del lunar.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showABCDEInfo() {
        AlertDialog.Builder(this)
            .setTitle("Criterios ABCDE")
            .setMessage(
                """
                La regla ABCDE evalúa:
                A - Asimetría: Si una mitad no coincide con la otra
                B - Bordes: Irregulares, desiguales o poco definidos
                C - Color: Múltiples colores o distribución desigual
                D - Diámetro: Mayor a 6mm (tamaño de un borrador de lápiz)
                E - Evolución: Cambios en tamaño, forma o color
                Este análisis combina estos criterios tradicionales con inteligencia artificial para una evaluación más precisa.
                """.trimIndent()
            )
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun showError(message: String) {
        displayUnavailable(message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        // Detach the drawable before the ViewModel releases its bitmap on final destruction.
        if (::binding.isInitialized) binding.resultImageView.setImageDrawable(null)
        super.onDestroy()
    }
}

class ExplanationAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<ExplanationAdapter.ViewHolder>() {
    private var explanations: List<String> = emptyList()

    @Suppress("NotifyDataSetChanged")
    fun setExplanations(newExplanations: List<String>) {
        explanations = newExplanations
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_explanation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(explanations[position])
    }

    override fun getItemCount() = explanations.size

    class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        private val textView: android.widget.TextView = itemView.findViewById(R.id.explanationText)
        fun bind(explanation: String) {
            textView.text = explanation
        }
    }
}

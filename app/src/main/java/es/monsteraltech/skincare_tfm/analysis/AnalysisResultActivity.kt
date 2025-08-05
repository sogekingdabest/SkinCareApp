package es.monsteraltech.skincare_tfm.analysis
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.BodyPartActivity
import es.monsteraltech.skincare_tfm.body.mole.dialog.MoleSelectorDialog
import es.monsteraltech.skincare_tfm.body.mole.model.ABCDEScores
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import es.monsteraltech.skincare_tfm.body.mole.repository.MoleRepository
import es.monsteraltech.skincare_tfm.body.mole.util.RiskLevelTranslator
import es.monsteraltech.skincare_tfm.databinding.ActivityAnalysisResultEnhancedBinding
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
class AnalysisResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnalysisResultEnhancedBinding
    private lateinit var melanomaDetector: MelanomaAIDetector
    private lateinit var explanationAdapter: ExplanationAdapter
    private lateinit var asyncImageProcessor: AsyncImageProcessor
    private lateinit var progressManager: ProgressManager
    private var photoFile: File? = null
    private var bodyPartColorCode: String? = null
    private var selectedBodyPart: String = ""
    private var analysisResult: MelanomaAIDetector.CombinedAnalysisResult? = null
    private val moleRepository = MoleRepository()
    private val auth = FirebaseAuth.getInstance()
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
        melanomaDetector = MelanomaAIDetector(this)
        setupUI()
        val photoPath = intent.getStringExtra("PHOTO_PATH")
        val isFrontCamera = intent.getBooleanExtra("IS_FRONT_CAMERA", false)
        bodyPartColorCode = intent.getStringExtra("BODY_PART_COLOR")
        val preprocessingApplied = intent.getBooleanExtra("PREPROCESSING_APPLIED", false)
        val processingMetadata = intent.getStringExtra("PROCESSING_METADATA") ?: ""
        if (preprocessingApplied) {
            android.util.Log.d("AnalysisResultActivity", "Imagen preprocesada recibida: $processingMetadata")
        }
        setupBodyPart()
        if (photoPath != null) {
            photoFile = File(photoPath)
            if (photoFile!!.exists()) {
                processImage(photoPath, isFrontCamera)
            } else {
                showError("Error: Imagen no encontrada")
            }
        } else {
            showError("Error: Ruta de imagen no válida")
        }
    }
    private fun setupUI() {
        explanationAdapter = ExplanationAdapter()
        binding.explanationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AnalysisResultActivity)
            adapter = explanationAdapter
        }
        setupAsyncProcessing()
        binding.saveButton.setOnClickListener {
            saveAnalysisAsNewMole()
        }
        binding.assignToExistingMoleButton.setOnClickListener {
            showMoleSelectorDialog()
        }
        binding.historyButton.setOnClickListener {
            showHistoryDialog()
        }
        binding.infoButton.setOnClickListener {
            showABCDEInfo()
        }
        setupBodyPartSpinner()
        setupUserInputSection()
    }
    private fun setupAsyncProcessing() {
        progressManager = ProgressManager(
            context = this,
            progressBar = binding.processingProgressBar,
            statusText = binding.processingStatusText,
            cancelButton = binding.cancelProcessingButton
        )
        val progressCallback = object : ProgressCallback {
            override fun onProgressUpdate(progress: Int, message: String) {
                progressManager.updateProgressWithAccessibility(progress, message)
            }
            override fun onProgressUpdateWithTimeEstimate(progress: Int, message: String, estimatedTotalTimeMs: Long) {
                progressManager.updateProgressWithTimeEstimate(progress, message, estimatedTotalTimeMs)
            }
            override fun onStageChanged(stage: ProcessingStage) {
                progressManager.showStageWithAccessibility(stage)
                runOnUiThread {
                    val stageText = "Etapa ${stage.ordinal + 1}/5: ${stage.message}"
                    binding.processingStageText.text = stageText
                    binding.processingStageText.contentDescription = "Progreso del análisis: $stageText"
                }
            }
            override fun onError(error: String) {
                progressManager.showErrorWithAccessibility(error)
                runOnUiThread {
                    hideProcessingOverlay()
                    showError("Error en análisis: $error")
                }
            }
            override fun onCompleted(result: MelanomaAIDetector.CombinedAnalysisResult) {
                progressManager.completeProcessingWithAccessibility()
                analysisResult = result
                runOnUiThread {
                    displayResults(result)
                    binding.processingOverlay.postDelayed({
                        hideProcessingOverlay()
                    }, 1500)
                }
            }
            override fun onCancelled() {
                progressManager.showCancelledWithAccessibility()
                runOnUiThread {
                    hideProcessingOverlay()
                    Toast.makeText(this@AnalysisResultActivity, "Análisis cancelado", Toast.LENGTH_SHORT).show()
                }
            }
        }
        asyncImageProcessor = AsyncImageProcessor(this, progressCallback)
        progressManager.setCancelListener {
            asyncImageProcessor.cancelProcessing()
        }
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
    private fun setupUserInputSection() {
        binding.toggleUserInputButton.setOnClickListener {
            val isVisible = binding.userInputContainer.visibility == View.VISIBLE
            if (isVisible) {
                binding.userInputContainer.visibility = View.GONE
                binding.toggleUserInputButton.text = getString(R.string.user_abcde_add_button)
            } else {
                binding.userInputContainer.visibility = View.VISIBLE
                binding.toggleUserInputButton.text = getString(R.string.user_abcde_hide_button)
            }
        }
        setupSliderListener(binding.userAsymmetrySlider, binding.userAsymmetryValue, 2.0f)
        setupSliderListener(binding.userBorderSlider, binding.userBorderValue, 8.0f)
        setupSliderListener(binding.userColorSlider, binding.userColorValue, 6.0f)
        setupSliderListener(binding.userDiameterSlider, binding.userDiameterValue, 5.0f)
        setupSliderListener(binding.userEvolutionSlider, binding.userEvolutionValue, 3.0f)
        setupInfoButtons()
    }
    private fun setupSliderListener(slider: com.google.android.material.slider.Slider, valueText: android.widget.TextView, maxValue: Float) {
        slider.addOnChangeListener { _, value, _ ->
            valueText.text = String.format("%.1f/%.0f", value, maxValue)
            updateUserTotalScore()
            updateComparison()
        }
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
    private fun calculateUserTotalScore(): Float {
        val userAsymmetry = binding.userAsymmetrySlider.value
        val userBorder = binding.userBorderSlider.value
        val userColor = binding.userColorSlider.value
        val userDiameter = binding.userDiameterSlider.value
        val userEvolution = binding.userEvolutionSlider.value
        var userTotal = (userAsymmetry * 1.3f) + (userBorder * 0.1f) + (userColor * 0.5f) + (userDiameter * 0.5f)
        if (userEvolution > 0) {
            userTotal *= (1 + userEvolution * 0.2f)
        }
        return userTotal
    }
    private fun updateUserTotalScore() {
        val userTotal = calculateUserTotalScore()
        binding.userTotalScore.text = String.format("%.1f", userTotal)
    }
    private fun updateComparison() {
        val analysisResult = this.analysisResult ?: return
        val aiTotal = analysisResult.abcdeResult.totalScore
        val userTotal = calculateUserTotalScore()
        val difference = kotlin.math.abs(aiTotal - userTotal)
        val comparisonMessage = when {
            difference < 1.0 -> getString(R.string.user_abcde_comparison_similar, difference)
            difference < 3.0 -> getString(R.string.user_abcde_comparison_somewhat, difference)
            difference < 5.0 -> getString(R.string.user_abcde_comparison_different, difference)
            else -> getString(R.string.user_abcde_comparison_very_different, difference)
        }
        binding.comparisonText.text = comparisonMessage
    }
    private fun processImage(photoPath: String, isFrontCamera: Boolean) {
        android.util.Log.d("AnalysisResultActivity", "Iniciando processImage asíncrono - photoPath: $photoPath")
        showProcessingOverlay()
        progressManager.startProcessing()
        lifecycleScope.launch {
            try {
                android.util.Log.d("AnalysisResultActivity", "Decodificando imagen...")
                var bitmap = BitmapFactory.decodeFile(photoPath)
                if (bitmap == null) {
                    android.util.Log.e("AnalysisResultActivity", "Error: bitmap es null después de decodificar")
                    hideProcessingOverlay()
                    showError("Error: No se pudo cargar la imagen")
                    return@launch
                }
                android.util.Log.d("AnalysisResultActivity", "Imagen decodificada: ${bitmap.width}x${bitmap.height}")
                if (isFrontCamera) {
                    android.util.Log.d("AnalysisResultActivity", "Aplicando transformación para cámara frontal")
                    val matrix = Matrix()
                    matrix.preScale(-1.0f, 1.0f)
                    val flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    bitmap = flippedBitmap
                }
                android.util.Log.d("AnalysisResultActivity", "Mostrando imagen en UI")
                binding.resultImageView.setImageBitmap(bitmap)
                android.util.Log.d("AnalysisResultActivity", "Buscando imagen previa...")
                val previousBitmap = loadPreviousImageIfExists()
                val config = AnalysisConfiguration.default()
                android.util.Log.d("AnalysisResultActivity", "Iniciando análisis asíncrono...")
                asyncImageProcessor.processImage(
                    bitmap = bitmap,
                    previousBitmap = previousBitmap,
                    pixelDensity = resources.displayMetrics.density,
                    config = config
                )
                android.util.Log.d("AnalysisResultActivity", "Análisis asíncrono completado exitosamente")
            } catch (e: Exception) {
                android.util.Log.e("AnalysisResultActivity", "Error durante el análisis asíncrono", e)
                hideProcessingOverlay()
                showError("Error en análisis: ${e.message}")
            }
        }
    }
    private fun displayResults(result: MelanomaAIDetector.CombinedAnalysisResult) {
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
        updateComparison()
    }
    private fun updateRiskIndicator(riskLevel: MelanomaAIDetector.RiskLevel) {
        val riskLevelString = when (riskLevel) {
            MelanomaAIDetector.RiskLevel.VERY_LOW -> "VERY_LOW"
            MelanomaAIDetector.RiskLevel.LOW -> "LOW"
            MelanomaAIDetector.RiskLevel.MEDIUM -> "MEDIUM"
            MelanomaAIDetector.RiskLevel.HIGH -> "HIGH"
            MelanomaAIDetector.RiskLevel.VERY_HIGH -> "VERY_HIGH"
        }
        val translatedText = RiskLevelTranslator.translateRiskLevel(this, riskLevelString)
        val colorRes = RiskLevelTranslator.getRiskLevelColor(riskLevelString)
        val color = getColor(colorRes)
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
    private fun validateInputs(requireTitle: Boolean = true): Boolean {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Por favor, inicia sesión para guardar", Toast.LENGTH_SHORT).show()
            return false
        }
        if (requireTitle) {
            val title = binding.titleEditText.text.toString()
            if (title.isEmpty()) {
                Toast.makeText(this, "Por favor, añade un título", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        if (selectedBodyPart.isEmpty()) {
            Toast.makeText(this, "Por favor, selecciona una parte del cuerpo", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
    private fun showMoleSelectorDialog() {
        if (!validateInputs(requireTitle = false)) return
        val dialog = MoleSelectorDialog.newInstance()
        dialog.setOnMoleSelectedListener { selectedMole ->
            if (selectedMole != null) {
                val title = binding.titleEditText.text.toString()
                val description = binding.descriptionEditText.text.toString()
                saveAnalysisToExistingMole(selectedMole, title, description)
            } else {
                saveAnalysisAsNewMole()
            }
        }
        dialog.show(supportFragmentManager, MoleSelectorDialog.TAG)
    }
    private fun saveAnalysisAsNewMole() {
        if (!validateInputs(requireTitle = true)) return
        val title = binding.titleEditText.text.toString()
        val description = binding.descriptionEditText.text.toString()
        saveAnalysisAsNewMole(title, description)
    }
    private fun saveAnalysisToExistingMole(mole: MoleData, title: String, description: String) {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Asociando análisis al lunar existente...")
            setCancelable(false)
            show()
        }
        lifecycleScope.launch {
            try {
                val analysisData = createAnalysisDataFromResult(mole.id, title, description)
                val result = moleRepository.saveAnalysisToMole(applicationContext, mole.id, analysisData, photoFile!!)
                progressDialog.dismiss()
                if (result.isSuccess) {
                    Toast.makeText(this@AnalysisResultActivity, "Análisis asociado al lunar '${mole.title}' correctamente", Toast.LENGTH_SHORT).show()
                    navigateBack()
                } else {
                    Toast.makeText(this@AnalysisResultActivity, "Error al asociar análisis: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(this@AnalysisResultActivity, "Error al asociar análisis: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun saveAnalysisAsNewMole(title: String, description: String) {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Guardando análisis...")
            setCancelable(false)
            show()
        }
        lifecycleScope.launch {
            try {
                val moleId = UUID.randomUUID().toString()
                val analysisData = createAnalysisDataFromResult(moleId, title, description)
                val result = moleRepository.saveMoleWithAnalysis(
                    context = applicationContext,
                    imageFile = photoFile!!,
                    title = title,
                    description = description,
                    bodyPart = selectedBodyPart,
                    bodyPartColorCode = bodyPartColorCode ?: "",
                    analysisData = analysisData
                )
                progressDialog.dismiss()
                if (result.isSuccess) {
                    Toast.makeText(this@AnalysisResultActivity, "Análisis guardado correctamente", Toast.LENGTH_SHORT).show()
                    navigateBack()
                } else {
                    Toast.makeText(this@AnalysisResultActivity, "Error al guardar: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(this@AnalysisResultActivity, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun createAnalysisSummary(): String {
        val result = analysisResult ?: return ""
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return buildString {
            appendLine("=== ANÁLISIS COMBINADO IA + ABCDE ===")
            appendLine("Fecha: ${dateFormat.format(Date())}")
            appendLine()
            appendLine("RESULTADO IA:")
            appendLine("- Probabilidad: ${(result.aiProbability * 100).toInt()}%")
            appendLine("- Confianza: ${(result.aiConfidence * 100).toInt()}%")
            appendLine()
            appendLine("ANÁLISIS ABCDE (IA):")
            appendLine("- Asimetría: ${result.abcdeResult.asymmetryScore}/2")
            appendLine("- Bordes: ${result.abcdeResult.borderScore}/8")
            appendLine("- Color: ${result.abcdeResult.colorScore}/6")
            appendLine("- Diámetro: ${result.abcdeResult.diameterScore}/5")
            result.abcdeResult.evolutionScore?.let {
                appendLine("- Evolución: $it/3")
            }
            appendLine("- Score Total ABCDE (IA): ${result.abcdeResult.totalScore}")
            appendLine()
            if (binding.userInputContainer.visibility == View.VISIBLE) {
                appendLine("ANÁLISIS ABCDE (USUARIO):")
                appendLine("- Asimetría: ${String.format("%.1f", binding.userAsymmetrySlider.value)}/2")
                appendLine("- Bordes: ${String.format("%.1f", binding.userBorderSlider.value)}/8")
                appendLine("- Color: ${String.format("%.1f", binding.userColorSlider.value)}/6")
                appendLine("- Diámetro: ${String.format("%.1f", binding.userDiameterSlider.value)}/5")
                appendLine("- Evolución: ${String.format("%.1f", binding.userEvolutionSlider.value)}/3")
                val userTotal = calculateUserTotalScore()
                appendLine("- Score Total ABCDE (Usuario): ${String.format("%.1f", userTotal)}")
                appendLine()
                val difference = kotlin.math.abs(result.abcdeResult.totalScore - userTotal)
                appendLine("COMPARACIÓN:")
                appendLine("- Diferencia entre IA y Usuario: ${String.format("%.1f", difference)} puntos")
                appendLine()
            }
            appendLine("RESULTADO COMBINADO:")
            appendLine("- Score: ${(result.combinedScore * 100).toInt()}%")
            appendLine("- Nivel de Riesgo: ${result.combinedRiskLevel}")
            appendLine("- Urgencia: ${result.urgencyLevel}")
            appendLine()
            appendLine("RECOMENDACIÓN:")
            appendLine(result.recommendation)
        }
    }
    private fun createAnalysisDataFromResult(moleId: String, title: String, description: String): AnalysisData {
        val result = analysisResult ?: throw IllegalStateException("No hay resultado de análisis disponible")
        val abcdeScores = ABCDEScores(
            asymmetryScore = result.abcdeResult.asymmetryScore,
            borderScore = result.abcdeResult.borderScore,
            colorScore = result.abcdeResult.colorScore,
            diameterScore = result.abcdeResult.diameterScore,
            evolutionScore = result.abcdeResult.evolutionScore,
            totalScore = result.abcdeResult.totalScore
        )
        val userValues = if (binding.userInputContainer.isVisible) {
            android.util.Log.d("AnalysisResultActivity", "Guardando valores del usuario - Container visible")
            val values = mapOf(
                "userAsymmetry" to binding.userAsymmetrySlider.value,
                "userBorder" to binding.userBorderSlider.value,
                "userColor" to binding.userColorSlider.value,
                "userDiameter" to binding.userDiameterSlider.value,
                "userEvolution" to binding.userEvolutionSlider.value,
                "userTotal" to calculateUserTotalScore()
            )
            android.util.Log.d("AnalysisResultActivity", "Valores del usuario: $values")
            values
        } else {
            android.util.Log.d("AnalysisResultActivity", "Container del usuario no visible - No se guardan valores")
            emptyMap()
        }
        val metadata = mapOf(
            "title" to title,
            "description" to description,
            "bodyPart" to selectedBodyPart,
            "bodyPartColorCode" to (bodyPartColorCode ?: ""),
            "urgencyLevel" to result.urgencyLevel.name,
            "explanations" to result.explanations
        ) + userValues
        return AnalysisData(
            moleId = moleId,
            analysisResult = createAnalysisSummary(),
            aiProbability = result.aiProbability,
            aiConfidence = result.aiConfidence,
            abcdeScores = abcdeScores,
            combinedScore = result.combinedScore,
            riskLevel = result.combinedRiskLevel.name,
            recommendation = result.recommendation,
            imageData = photoFile!!,
            analysisMetadata = metadata
        )
    }
    private fun navigateBack() {
        val intent = if (bodyPartColorCode != null) {
            Intent(this@AnalysisResultActivity, BodyPartActivity::class.java).apply {
                putExtra("COLOR_VALUE", bodyPartColorCode)
            }
        } else {
            Intent(this@AnalysisResultActivity, es.monsteraltech.skincare_tfm.MainActivity::class.java)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }
    private fun loadPreviousImageIfExists(): Bitmap? {
        return null
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
            .setMessage("""
                La regla ABCDE evalúa:
                A - Asimetría: Si una mitad no coincide con la otra
                B - Bordes: Irregulares, desiguales o poco definidos
                C - Color: Múltiples colores o distribución desigual
                D - Diámetro: Mayor a 6mm (tamaño de un borrador de lápiz)
                E - Evolución: Cambios en tamaño, forma o color
                Este análisis combina estos criterios tradicionales con inteligencia artificial para una evaluación más precisa.
            """.trimIndent())
            .setPositiveButton("Entendido", null)
            .show()
    }
    private fun showError(message: String) {
        binding.resultImageView.setImageResource(R.drawable.cat)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    override fun onDestroy() {
        super.onDestroy()
        if (::asyncImageProcessor.isInitialized) {
            asyncImageProcessor.cancelProcessing()
        }
        melanomaDetector.close()
    }
    override fun onBackPressed() {
        if (::asyncImageProcessor.isInitialized && asyncImageProcessor.isProcessing()) {
            AlertDialog.Builder(this)
                .setTitle("Cancelar análisis")
                .setMessage("¿Deseas cancelar el análisis en curso?")
                .setPositiveButton("Sí") { _, _ ->
                    asyncImageProcessor.cancelProcessing()
                    super.onBackPressed()
                }
                .setNegativeButton("No", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }
}
class ExplanationAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<ExplanationAdapter.ViewHolder>() {
    private var explanations: List<String> = emptyList()
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
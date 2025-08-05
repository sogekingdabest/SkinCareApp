// AnalysisResultActivity.kt - Actividad mejorada con análisis IA + ABCDE
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
    
    // Componentes para procesamiento asíncrono
    private lateinit var asyncImageProcessor: AsyncImageProcessor
    private lateinit var progressManager: ProgressManager

    private var photoFile: File? = null
    private var bodyPartColorCode: String? = null
    private var selectedBodyPart: String = ""
    private var analysisResult: MelanomaAIDetector.CombinedAnalysisResult? = null

    private val moleRepository = MoleRepository()
    private val auth = FirebaseAuth.getInstance()

    // Mapeo entre nombres de partes del cuerpo y códigos de color
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

        // Inicializar detector
        melanomaDetector = MelanomaAIDetector(this)

        // Configurar UI
        setupUI()

        // Obtener datos del intent
        val photoPath = intent.getStringExtra("PHOTO_PATH")
        val isFrontCamera = intent.getBooleanExtra("IS_FRONT_CAMERA", false)
        bodyPartColorCode = intent.getStringExtra("BODY_PART_COLOR")
        
        // Obtener información de preprocesado (nueva funcionalidad)
        val preprocessingApplied = intent.getBooleanExtra("PREPROCESSING_APPLIED", false)
        val processingMetadata = intent.getStringExtra("PROCESSING_METADATA") ?: ""
        
        // Log información de preprocesado para debugging
        if (preprocessingApplied) {
            android.util.Log.d("AnalysisResultActivity", "Imagen preprocesada recibida: $processingMetadata")
        }

        // Configurar parte del cuerpo
        setupBodyPart()

        // Procesar imagen si existe
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
        // Configurar RecyclerView para explicaciones
        explanationAdapter = ExplanationAdapter()
        binding.explanationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AnalysisResultActivity)
            adapter = explanationAdapter
        }

        // Inicializar componentes de procesamiento asíncrono
        setupAsyncProcessing()

        // Configurar listeners
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

        // Configurar spinner de partes del cuerpo
        setupBodyPartSpinner()
        
        // Configurar sección de valores del usuario
        setupUserInputSection()
    }

    private fun setupAsyncProcessing() {
        // Inicializar ProgressManager con los componentes del layout
        progressManager = ProgressManager(
            context = this,
            progressBar = binding.processingProgressBar,
            statusText = binding.processingStatusText,
            cancelButton = binding.cancelProcessingButton
        )

        // Crear callback para recibir actualizaciones de progreso con mejoras de accesibilidad
        val progressCallback = object : ProgressCallback {
            override fun onProgressUpdate(progress: Int, message: String) {
                progressManager.updateProgressWithAccessibility(progress, message)
            }
            
            override fun onProgressUpdateWithTimeEstimate(progress: Int, message: String, estimatedTotalTimeMs: Long) {
                progressManager.updateProgressWithTimeEstimate(progress, message, estimatedTotalTimeMs)
            }

            override fun onStageChanged(stage: ProcessingStage) {
                progressManager.showStageWithAccessibility(stage)
                // Actualizar texto de etapa con información más detallada en el hilo principal
                runOnUiThread {
                    val stageText = "Etapa ${stage.ordinal + 1}/5: ${stage.message}"
                    binding.processingStageText.text = stageText
                    binding.processingStageText.contentDescription = "Progreso del análisis: $stageText"
                }
            }

            override fun onError(error: String) {
                progressManager.showErrorWithAccessibility(error)
                // Ocultar overlay después de mostrar error en el hilo principal
                runOnUiThread {
                    hideProcessingOverlay()
                    showError("Error en análisis: $error")
                }
            }

            override fun onCompleted(result: MelanomaAIDetector.CombinedAnalysisResult) {
                progressManager.completeProcessingWithAccessibility()
                analysisResult = result
                
                // Actualizar UI con resultados en el hilo principal
                runOnUiThread {
                    displayResults(result)
                    
                    // Ocultar overlay después de un breve delay
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

        // Inicializar AsyncImageProcessor
        asyncImageProcessor = AsyncImageProcessor(this, progressCallback)

        // Configurar botón de cancelar
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
        // Configurar botón de toggle
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

        // Configurar sliders con listeners
        setupSliderListener(binding.userAsymmetrySlider, binding.userAsymmetryValue, 2.0f)
        setupSliderListener(binding.userBorderSlider, binding.userBorderValue, 8.0f)
        setupSliderListener(binding.userColorSlider, binding.userColorValue, 6.0f)
        setupSliderListener(binding.userDiameterSlider, binding.userDiameterValue, 5.0f)
        setupSliderListener(binding.userEvolutionSlider, binding.userEvolutionValue, 3.0f)
        
        // Configurar botones de información
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

        // Aplicar los mismos pesos que usa la app en ABCDEAnalyzerOpenCV.calculateTotalScore()
        var userTotal = (userAsymmetry * 1.3f) + (userBorder * 0.1f) + (userColor * 0.5f) + (userDiameter * 0.5f)
        
        // Si hay evolución, aplicar el multiplicador como en la app
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
        
        // Mostrar overlay de procesamiento
        showProcessingOverlay()
        
        // Inicializar procesamiento con mejoras de accesibilidad
        progressManager.startProcessing()

        lifecycleScope.launch {
            try {
                android.util.Log.d("AnalysisResultActivity", "Decodificando imagen...")
                // Decodificar y corregir orientación
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

                // Mostrar imagen inmediatamente
                android.util.Log.d("AnalysisResultActivity", "Mostrando imagen en UI")
                binding.resultImageView.setImageBitmap(bitmap)

                // Buscar imagen previa si existe (para análisis de evolución)
                android.util.Log.d("AnalysisResultActivity", "Buscando imagen previa...")
                val previousBitmap = loadPreviousImageIfExists()

                // Crear configuración de análisis
                val config = AnalysisConfiguration.default()

                // Realizar análisis asíncrono usando AsyncImageProcessor
                android.util.Log.d("AnalysisResultActivity", "Iniciando análisis asíncrono...")
                asyncImageProcessor.processImage(
                    bitmap = bitmap,
                    previousBitmap = previousBitmap,
                    pixelDensity = resources.displayMetrics.density,
                    config = config
                )
                
                android.util.Log.d("AnalysisResultActivity", "Análisis asíncrono completado exitosamente")
                // El resultado se maneja en el callback onCompleted

            } catch (e: Exception) {
                android.util.Log.e("AnalysisResultActivity", "Error durante el análisis asíncrono", e)
                hideProcessingOverlay()
                showError("Error en análisis: ${e.message}")
            }
        }
    }

    private fun displayResults(result: MelanomaAIDetector.CombinedAnalysisResult) {
        // Mostrar score combinado
        val scorePercentage = (result.combinedScore * 100).toInt()
        binding.combinedScoreText.text = "$scorePercentage%"

        // Actualizar indicador de riesgo
        updateRiskIndicator(result.combinedRiskLevel)

        // Mostrar recomendación principal
        binding.recommendationText.text = result.recommendation

        // Actualizar color de tarjeta según urgencia
        updateCardColor(result.urgencyLevel)

        // Mostrar detalles ABCDE
        displayABCDEScores(result.abcdeResult)

        // Mostrar probabilidad IA
        binding.aiProbabilityText.text =
            "IA: ${(result.aiProbability * 100).toInt()}% (Confianza: ${(result.aiConfidence * 100).toInt()}%)"

        // Mostrar explicaciones detalladas
        explanationAdapter.setExplanations(result.explanations)

        // Mostrar/ocultar botón de historial si hay evolución
        binding.historyButton.visibility =
            if (result.abcdeResult.evolutionScore != null) View.VISIBLE else View.GONE

        // Actualizar score ABCDE de la app en la comparación
        binding.aiTotalScore.text = String.format("%.1f", result.abcdeResult.totalScore)
        
        // Inicializar comparación
        updateComparison()
    }

    private fun updateRiskIndicator(riskLevel: MelanomaAIDetector.RiskLevel) {
        // Convertir el enum a string para usar con el traductor
        val riskLevelString = when (riskLevel) {
            MelanomaAIDetector.RiskLevel.VERY_LOW -> "VERY_LOW"
            MelanomaAIDetector.RiskLevel.LOW -> "LOW"
            MelanomaAIDetector.RiskLevel.MEDIUM -> "MEDIUM"
            MelanomaAIDetector.RiskLevel.HIGH -> "HIGH"
            MelanomaAIDetector.RiskLevel.VERY_HIGH -> "VERY_HIGH"
        }
        
        // Traducir el nivel de riesgo
        val translatedText = RiskLevelTranslator.translateRiskLevel(this, riskLevelString)
        
        // Obtener el color usando la función de utilidad
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
        // A - Asimetría
        binding.asymmetryScore.text = String.format("%.1f/2", abcdeResult.asymmetryScore)
        binding.asymmetryProgress.setProgress((abcdeResult.asymmetryScore / 2f * 100).toInt(), true)

        // B - Bordes
        binding.borderScore.text = String.format("%.1f/8", abcdeResult.borderScore)
        binding.borderProgress.setProgress((abcdeResult.borderScore / 8f * 100).toInt(), true)

        // C - Color
        binding.colorScore.text = String.format("%.1f/6", abcdeResult.colorScore)
        binding.colorProgress.setProgress((abcdeResult.colorScore / 6f * 100).toInt(), true)

        // D - Diámetro
        binding.diameterScore.text = String.format("%.1f/5", abcdeResult.diameterScore)
        binding.diameterProgress.setProgress((abcdeResult.diameterScore / 5f * 100).toInt(), true)

        // E - Evolución
        if (abcdeResult.evolutionScore != null) {
            binding.evolutionLayout.visibility = View.VISIBLE
            binding.evolutionScore.text = String.format("%.1f/3", abcdeResult.evolutionScore)
            binding.evolutionProgress.setProgress((abcdeResult.evolutionScore / 3f * 100).toInt(), true)
        } else {
            binding.evolutionLayout.visibility = View.GONE
        }

        // Score total ABCDE
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
        // Para asignar a lunar existente, no requerimos título
        if (!validateInputs(requireTitle = false)) return

        val dialog = MoleSelectorDialog.newInstance()
        
        dialog.setOnMoleSelectedListener { selectedMole ->
            if (selectedMole != null) {
                // Usuario seleccionó un lunar existente - asociar análisis
                val title = binding.titleEditText.text.toString()
                val description = binding.descriptionEditText.text.toString()
                saveAnalysisToExistingMole(selectedMole, title, description)
            } else {
                // Usuario eligió crear nuevo lunar - comportamiento actual
                saveAnalysisAsNewMole()
            }
        }
        
        dialog.show(supportFragmentManager, MoleSelectorDialog.TAG)
    }

    private fun saveAnalysisAsNewMole() {
        // Para crear nuevo lunar, sí requerimos título
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
                // Crear AnalysisData desde el resultado actual
                val analysisData = createAnalysisDataFromResult(mole.id, title, description)

                // Guardar usando MoleRepository
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
                // Crear datos de análisis estructurados
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
            
            // Añadir valores del usuario si existen
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
        
        // Crear ABCDEScores desde el resultado ABCDE
        val abcdeScores = ABCDEScores(
            asymmetryScore = result.abcdeResult.asymmetryScore,
            borderScore = result.abcdeResult.borderScore,
            colorScore = result.abcdeResult.colorScore,
            diameterScore = result.abcdeResult.diameterScore,
            evolutionScore = result.abcdeResult.evolutionScore,
            totalScore = result.abcdeResult.totalScore
        )

        // Crear metadatos del análisis incluyendo valores del usuario si existen
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
            imageData = photoFile!!, // Por ahora usar la ruta local
            analysisMetadata = metadata
        )
    }

    private fun navigateBack() {
        // Volver a la actividad correspondiente
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
        // TODO: Implementar carga de imagen previa del mismo lunar
        // Por ahora retornar null
        return null
    }

    private fun showHistoryDialog() {
        // TODO: Mostrar diálogo con historial de cambios
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
        // Cancelar procesamiento si está en curso
        if (::asyncImageProcessor.isInitialized) {
            asyncImageProcessor.cancelProcessing()
        }
        melanomaDetector.close()
    }

    override fun onBackPressed() {
        // Si hay procesamiento en curso, preguntar al usuario si quiere cancelar
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

// Adapter para mostrar explicaciones
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
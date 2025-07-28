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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.BodyPartActivity
import es.monsteraltech.skincare_tfm.body.mole.repository.MoleRepository
import es.monsteraltech.skincare_tfm.databinding.ActivityAnalysisResultEnhancedBinding
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
            saveAnalysis()
        }

        binding.historyButton.setOnClickListener {
            showHistoryDialog()
        }

        binding.infoButton.setOnClickListener {
            showABCDEInfo()
        }

        // Configurar spinner de partes del cuerpo
        setupBodyPartSpinner()
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

        binding.bodyPartSpinner.setOnItemClickListener { parent, view, position, id ->
            if (position > 0) {
                selectedBodyPart = bodyPartsList[position]
                bodyPartColorCode = bodyPartToColorMap[selectedBodyPart]
            } else {
                selectedBodyPart = ""
                bodyPartColorCode = null
            }
        }
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
                    if (flippedBitmap != null) {
                        bitmap = flippedBitmap
                    }
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
                val result = asyncImageProcessor.processImage(
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
    }

    private fun updateRiskIndicator(riskLevel: MelanomaAIDetector.RiskLevel) {
        val (color, text) = when (riskLevel) {
            MelanomaAIDetector.RiskLevel.VERY_LOW ->
                Pair(getColor(R.color.risk_very_low), "Muy Bajo")
            MelanomaAIDetector.RiskLevel.LOW ->
                Pair(getColor(R.color.risk_low), "Bajo")
            MelanomaAIDetector.RiskLevel.MODERATE ->
                Pair(getColor(R.color.risk_moderate), "Moderado")
            MelanomaAIDetector.RiskLevel.HIGH ->
                Pair(getColor(R.color.risk_high), "Alto")
            MelanomaAIDetector.RiskLevel.VERY_HIGH ->
                Pair(getColor(R.color.risk_very_high), "Muy Alto")
        }

        binding.riskIndicator.setBackgroundColor(color)
        binding.riskLevelText.text = "Riesgo: $text"
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

    private fun saveAnalysis() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Por favor, inicia sesión para guardar", Toast.LENGTH_SHORT).show()
            return
        }

        val title = binding.titleEditText.text.toString()
        val description = binding.descriptionEditText.text.toString()

        if (title.isEmpty()) {
            Toast.makeText(this, "Por favor, añade un título", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedBodyPart.isEmpty()) {
            Toast.makeText(this, "Por favor, selecciona una parte del cuerpo", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Guardando análisis...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                // Crear resumen del análisis para guardar
                val analysisData = createAnalysisSummary()

                val result = moleRepository.saveMole(
                    context = applicationContext,
                    imageFile = photoFile!!,
                    title = title,
                    description = description,
                    bodyPart = selectedBodyPart,
                    bodyPartColorCode = bodyPartColorCode ?: "",
                    aiResult = analysisData
                )

                progressDialog.dismiss()

                if (result.isSuccess) {
                    Toast.makeText(this@AnalysisResultActivity, "Análisis guardado correctamente", Toast.LENGTH_SHORT).show()

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
            appendLine("ANÁLISIS ABCDE:")
            appendLine("- Asimetría: ${result.abcdeResult.asymmetryScore}/2")
            appendLine("- Bordes: ${result.abcdeResult.borderScore}/8")
            appendLine("- Color: ${result.abcdeResult.colorScore}/6")
            appendLine("- Diámetro: ${result.abcdeResult.diameterScore}/5")
            result.abcdeResult.evolutionScore?.let {
                appendLine("- Evolución: $it/3")
            }
            appendLine("- Score Total ABCDE: ${result.abcdeResult.totalScore}")
            appendLine()
            appendLine("RESULTADO COMBINADO:")
            appendLine("- Score: ${(result.combinedScore * 100).toInt()}%")
            appendLine("- Nivel de Riesgo: ${result.combinedRiskLevel}")
            appendLine("- Urgencia: ${result.urgencyLevel}")
            appendLine()
            appendLine("RECOMENDACIÓN:")
            appendLine(result.recommendation)
        }
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
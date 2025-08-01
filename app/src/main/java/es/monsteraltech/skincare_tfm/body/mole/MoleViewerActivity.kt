package es.monsteraltech.skincare_tfm.body.mole

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.error.ErrorHandler
import es.monsteraltech.skincare_tfm.body.mole.error.RetryManager
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisDataConverter
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import es.monsteraltech.skincare_tfm.body.mole.service.MoleAnalysisService
import es.monsteraltech.skincare_tfm.body.mole.util.ImageLoadingUtil
import es.monsteraltech.skincare_tfm.body.mole.view.EmptyStateView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Actividad mejorada para visualizar lunares con análisis completos guardados
 * Muestra resultados de análisis de IA + ABCDE y permite acceso al histórico
 */
class MoleViewerActivity : AppCompatActivity() {

    private lateinit var moleAnalysisService: MoleAnalysisService
    private lateinit var retryManager: RetryManager
    
    // UI Components
    private lateinit var titleTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var resultImageView: ImageView
    private lateinit var analysisContainer: LinearLayout
    private lateinit var combinedScoreText: TextView
    private lateinit var abcdeScoresContainer: LinearLayout
    private lateinit var aiProbabilityTextView: TextView
    private lateinit var riskLevelTextView: TextView
    private lateinit var recommendationTextView: TextView
    private lateinit var analysisDateTextView: TextView
    private lateinit var viewHistoryButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateView: EmptyStateView
    private lateinit var riskIndicator: View
    private lateinit var abcdeTotalScore: TextView
    
    // ABCDE Progress indicators
    private lateinit var asymmetryProgress: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var borderProgress: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var colorProgress: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var diameterProgress: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var evolutionProgress: com.google.android.material.progressindicator.LinearProgressIndicator
    
    // ABCDE Score TextViews
    private lateinit var asymmetryScore: TextView
    private lateinit var borderScore: TextView
    private lateinit var colorScore: TextView
    private lateinit var diameterScore: TextView
    private lateinit var evolutionScore: TextView
    
    // ABCDE Layouts
    private lateinit var asymmetryLayout: LinearLayout
    private lateinit var borderLayout: LinearLayout
    private lateinit var colorLayout: LinearLayout
    private lateinit var diameterLayout: LinearLayout
    private lateinit var evolutionLayout: LinearLayout

    private var currentMoleData: MoleData? = null
    private var isRetrying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mole_viewer)

        moleAnalysisService = MoleAnalysisService()
        retryManager = RetryManager(this)
        
        initializeViews()
        loadMoleData()
    }

    private fun initializeViews() {
        titleTextView = findViewById(R.id.detailTitle)
        descriptionTextView = findViewById(R.id.detailDescription)
        resultImageView = findViewById(R.id.resultImageView)
        analysisContainer = findViewById(R.id.analysisContainer)
        combinedScoreText = findViewById(R.id.combinedScoreText)
        abcdeScoresContainer = findViewById(R.id.abcdeScoresContainer)
        aiProbabilityTextView = findViewById(R.id.aiProbabilityText)
        riskLevelTextView = findViewById(R.id.riskLevelText)
        recommendationTextView = findViewById(R.id.recommendationText)
        analysisDateTextView = findViewById(R.id.analysisDateText)
        viewHistoryButton = findViewById(R.id.viewHistoryButton)
        progressBar = findViewById(R.id.progressBar)
        emptyStateView = findViewById(R.id.emptyStateView)
        riskIndicator = findViewById(R.id.riskIndicator)
        abcdeTotalScore = findViewById(R.id.abcdeTotalScore)
        
        // ABCDE Progress indicators
        asymmetryProgress = findViewById(R.id.asymmetryProgress)
        borderProgress = findViewById(R.id.borderProgress)
        colorProgress = findViewById(R.id.colorProgress)
        diameterProgress = findViewById(R.id.diameterProgress)
        evolutionProgress = findViewById(R.id.evolutionProgress)
        
        // ABCDE Score TextViews
        asymmetryScore = findViewById(R.id.asymmetryScore)
        borderScore = findViewById(R.id.borderScore)
        colorScore = findViewById(R.id.colorScore)
        diameterScore = findViewById(R.id.diameterScore)
        evolutionScore = findViewById(R.id.evolutionScore)
        
        // ABCDE Layouts
        asymmetryLayout = findViewById(R.id.asymmetryLayout)
        borderLayout = findViewById(R.id.borderLayout)
        colorLayout = findViewById(R.id.colorLayout)
        diameterLayout = findViewById(R.id.diameterLayout)
        evolutionLayout = findViewById(R.id.evolutionLayout)

        viewHistoryButton.setOnClickListener {
            openAnalysisHistory()
        }
    }

    private fun loadMoleData() {
        // Obtener los datos del intent
        val title = intent.getStringExtra("LUNAR_TITLE") ?: ""
        val description = intent.getStringExtra("LUNAR_DESCRIPTION") ?: ""
        val analysisResult = intent.getStringExtra("LUNAR_ANALYSIS_RESULT") ?: ""
        val imageUrl = intent.getStringExtra("LUNAR_IMAGE_URL")
        val moleId = intent.getStringExtra("MOLE_ID") ?: ""
        val analysisCount = intent.getIntExtra("ANALYSIS_COUNT", 0)

        // Debug logs
        Log.d("MoleViewerActivity", "Loading mole data:")
        Log.d("MoleViewerActivity", "Title: '$title'")
        Log.d("MoleViewerActivity", "Description: '$description'")
        Log.d("MoleViewerActivity", "AnalysisResult: '$analysisResult'")
        Log.d("MoleViewerActivity", "ImageUrl: '$imageUrl'")
        Log.d("MoleViewerActivity", "MoleId: '$moleId'")
        Log.d("MoleViewerActivity", "AnalysisCount: $analysisCount")

        // Crear objeto MoleData temporal para trabajar
        currentMoleData = MoleData(
            id = moleId,
            title = title,
            description = description,
            imageUrl = imageUrl ?: "",
            aiResult = analysisResult,
            analysisCount = analysisCount
        )

        // Configurar UI básica
        setupBasicUI(title, description, imageUrl)

        // Cargar y mostrar análisis
        if (analysisResult.isNotEmpty()) {
            Log.d("MoleViewerActivity", "Displaying analysis from AI result")
            displayAnalysisFromAiResult(analysisResult, imageUrl ?: "")
        } else if (moleId.isNotEmpty()) {
            Log.d("MoleViewerActivity", "Loading latest analysis from service")
            loadLatestAnalysisFromService(moleId)
        } else {
            Log.d("MoleViewerActivity", "No analysis data available, showing no analysis state")
            showNoAnalysisState()
        }
    }

    private fun setupBasicUI(title: String, description: String, imageUrl: String?) {
        titleTextView.text = title
        
        // Solo mostrar descripción si no está vacía
        if (description.isNotEmpty()) {
            descriptionTextView.text = description
            descriptionTextView.visibility = View.VISIBLE
        } else {
            descriptionTextView.visibility = View.GONE
        }

        // Configurar ImageView con la imagen usando ImageLoadingUtil
        ImageLoadingUtil.loadImageWithFallback(
            context = this,
            imageView = resultImageView,
            imageUrl = imageUrl,
            config = ImageLoadingUtil.Configs.fullSize(),
            coroutineScope = lifecycleScope
        )
    }

    private fun displayAnalysisFromAiResult(aiResult: String, imageUrl: String) {
        Log.d("MoleViewerActivity", "Displaying analysis from AI result: '$aiResult'")
        try {
            // Convertir aiResult a AnalysisData estructurado
            val analysisData = AnalysisDataConverter.fromAiResultString(
                aiResult = aiResult,
                moleId = currentMoleData?.id ?: "",
                imageUrl = imageUrl,
                createdAt = currentMoleData?.createdAt ?: com.google.firebase.Timestamp.now()
            )

            if (analysisData != null) {
                Log.d("MoleViewerActivity", "Successfully parsed analysis data, displaying structured data")
                displayAnalysisData(analysisData)
            } else {
                Log.d("MoleViewerActivity", "Failed to parse analysis data, displaying raw result")
                displayRawAnalysisResult(aiResult)
            }
        } catch (e: Exception) {
            Log.e("MoleViewerActivity", "Error parsing analysis result", e)
            displayRawAnalysisResult(aiResult)
        }
    }

    private fun loadLatestAnalysisFromService(moleId: String) {
        if (isRetrying) return
        
        progressBar.visibility = View.VISIBLE
        analysisContainer.visibility = View.GONE
        emptyStateView.hide()

        lifecycleScope.launch {
            val retryResult = retryManager.executeWithRetry(
                operation = "Cargar análisis del lunar $moleId",
                config = RetryManager.databaseConfig(),
                onRetryAttempt = { attempt, exception ->
                    isRetrying = true
                    val errorResult = ErrorHandler.handleError(this@MoleViewerActivity, exception, "Cargar análisis")
                    
                    runOnUiThread {
                        emptyStateView.showRetryIndicators(attempt, RetryManager.databaseConfig().maxAttempts)
                        Toast.makeText(
                            this@MoleViewerActivity,
                            getString(R.string.retry_attempting, attempt, RetryManager.databaseConfig().maxAttempts),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                moleAnalysisService.getAnalysisHistory(moleId)
            }
            
            isRetrying = false
            progressBar.visibility = View.GONE
            emptyStateView.hideRetryIndicators()
            
            if (retryResult.result.isSuccess) {
                val analyses = retryResult.result.getOrNull() ?: emptyList()
                if (analyses.isNotEmpty()) {
                    displayAnalysisData(analyses.first()) // Mostrar el más reciente
                    
                    if (retryResult.attemptsMade > 1) {
                        Toast.makeText(
                            this@MoleViewerActivity,
                            getString(R.string.retry_success_after_attempts, retryResult.attemptsMade),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    showEmptyState(EmptyStateView.EmptyStateType.NO_ANALYSIS)
                }
            } else {
                val exception = retryResult.result.exceptionOrNull() ?: Exception("Error desconocido")
                val errorResult = ErrorHandler.handleError(this@MoleViewerActivity, exception, "Cargar análisis")
                
                showErrorState(errorResult) {
                    loadLatestAnalysisFromService(moleId)
                }
                
                if (retryResult.attemptsMade > 1) {
                    Toast.makeText(
                        this@MoleViewerActivity,
                        getString(R.string.retry_failed_all_attempts, retryResult.attemptsMade),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun displayAnalysisData(analysisData: AnalysisData) {
        Log.d("MoleViewerActivity", "Displaying analysis data:")
        Log.d("MoleViewerActivity", "Combined score: ${analysisData.combinedScore}")
        Log.d("MoleViewerActivity", "Risk level: '${analysisData.riskLevel}'")
        Log.d("MoleViewerActivity", "AI probability: ${analysisData.aiProbability}")
        Log.d("MoleViewerActivity", "AI confidence: ${analysisData.aiConfidence}")
        Log.d("MoleViewerActivity", "Recommendation: '${analysisData.recommendation}'")
        
        progressBar.visibility = View.GONE
        analysisContainer.visibility = View.VISIBLE
        emptyStateView.hide()

        // Mostrar score combinado
        val combinedScore = analysisData.combinedScore
        if (combinedScore > 0f) {
            combinedScoreText.text = String.format("%.1f%%", combinedScore * 100)
        } else {
            combinedScoreText.text = "--"
        }

        // Mostrar probabilidad y confianza de IA
        if (analysisData.aiProbability > 0f && analysisData.aiConfidence > 0f) {
            aiProbabilityTextView.text = "IA: ${String.format("%.1f%%", analysisData.aiProbability * 100)} (Confianza: ${String.format("%.1f%%", analysisData.aiConfidence * 100)})"
            aiProbabilityTextView.visibility = View.VISIBLE
        } else {
            aiProbabilityTextView.visibility = View.GONE
        }

        // Mostrar nivel de riesgo y configurar indicador
        displayRiskLevel(analysisData.riskLevel)

        // Mostrar puntuaciones ABCDE detalladas
        displayABCDEScoresDetailed(analysisData)

        // Mostrar recomendación
        if (analysisData.recommendation.isNotEmpty()) {
            recommendationTextView.text = analysisData.recommendation
        } else {
            recommendationTextView.text = "Análisis completado. Consulte los resultados detallados."
        }

        // Mostrar fecha del análisis
        analysisDateTextView.text = "Fecha: ${formatDate(analysisData.createdAt.toDate())}"
        analysisDateTextView.visibility = View.VISIBLE

        // Mostrar botón de histórico si hay múltiples análisis
        val analysisCount = currentMoleData?.analysisCount ?: 0
        if (analysisCount > 1) {
            viewHistoryButton.visibility = View.VISIBLE
            viewHistoryButton.text = "Ver Evolución ($analysisCount análisis)"
        } else {
            viewHistoryButton.visibility = View.GONE
        }
    }

    private fun displayRiskLevel(riskLevel: String) {
        if (riskLevel.isNotEmpty()) {
            riskLevelTextView.text = "Riesgo: $riskLevel"
            riskIndicator.visibility = View.VISIBLE
            
            // Cambiar color según el nivel de riesgo
            val colorRes = when (riskLevel.uppercase()) {
                "LOW", "BAJO" -> android.R.color.holo_green_dark
                "MODERATE", "MODERADO" -> android.R.color.holo_orange_dark
                "HIGH", "ALTO" -> android.R.color.holo_red_dark
                else -> android.R.color.darker_gray
            }
            val color = getColor(colorRes)
            riskLevelTextView.setTextColor(color)
            riskIndicator.setBackgroundColor(color)
        } else {
            riskLevelTextView.text = "Riesgo: --"
            riskIndicator.visibility = View.GONE
        }
    }

    private fun displayABCDEScoresDetailed(analysisData: AnalysisData) {
        val scores = analysisData.abcdeScores
        
        // Solo mostrar si hay puntuaciones válidas
        if (scores.totalScore > 0f || scores.asymmetryScore > 0f || 
            scores.borderScore > 0f || scores.colorScore > 0f || scores.diameterScore > 0f) {
            
            // A - Asimetría
            if (scores.asymmetryScore >= 0f) {
                asymmetryLayout.visibility = View.VISIBLE
                asymmetryScore.text = "${String.format("%.1f", scores.asymmetryScore)}/2"
                asymmetryProgress.progress = ((scores.asymmetryScore / 2f) * 100).toInt()
            } else {
                asymmetryLayout.visibility = View.GONE
            }
            
            // B - Bordes
            if (scores.borderScore >= 0f) {
                borderLayout.visibility = View.VISIBLE
                borderScore.text = "${String.format("%.1f", scores.borderScore)}/8"
                borderProgress.progress = ((scores.borderScore / 8f) * 100).toInt()
            } else {
                borderLayout.visibility = View.GONE
            }
            
            // C - Color
            if (scores.colorScore >= 0f) {
                colorLayout.visibility = View.VISIBLE
                colorScore.text = "${String.format("%.1f", scores.colorScore)}/6"
                colorProgress.progress = ((scores.colorScore / 6f) * 100).toInt()
            } else {
                colorLayout.visibility = View.GONE
            }
            
            // D - Diámetro
            if (scores.diameterScore >= 0f) {
                diameterLayout.visibility = View.VISIBLE
                diameterScore.text = "${String.format("%.1f", scores.diameterScore)}/5"
                diameterProgress.progress = ((scores.diameterScore / 5f) * 100).toInt()
            } else {
                diameterLayout.visibility = View.GONE
            }
            
            // E - Evolución (opcional)
            scores.evolutionScore?.let { evolutionScoreValue ->
                if (evolutionScoreValue > 0f) {
                    evolutionLayout.visibility = View.VISIBLE
                    evolutionScore.text = "${String.format("%.1f", evolutionScoreValue)}/3"
                    evolutionProgress.progress = ((evolutionScoreValue / 3f) * 100).toInt()
                } else {
                    evolutionLayout.visibility = View.GONE
                }
            } ?: run {
                evolutionLayout.visibility = View.GONE
            }
            
            // Score total ABCDE
            if (scores.totalScore > 0f) {
                abcdeTotalScore.text = "Score ABCDE Total: ${String.format("%.1f", scores.totalScore)}"
            } else {
                abcdeTotalScore.text = "Score ABCDE Total: --"
            }
            
        } else {
            // Ocultar todos los layouts si no hay datos
            asymmetryLayout.visibility = View.GONE
            borderLayout.visibility = View.GONE
            colorLayout.visibility = View.GONE
            diameterLayout.visibility = View.GONE
            evolutionLayout.visibility = View.GONE
            abcdeTotalScore.text = "Score ABCDE Total: --"
        }
    }

    private fun displayRawAnalysisResult(aiResult: String) {
        progressBar.visibility = View.GONE
        analysisContainer.visibility = View.VISIBLE
        emptyStateView.hide()

        // Mostrar resultado crudo en la recomendación
        recommendationTextView.text = aiResult
        
        // Configurar valores por defecto
        combinedScoreText.text = "--"
        riskLevelTextView.text = "Riesgo: --"
        riskIndicator.visibility = View.GONE
        
        // Ocultar otros campos específicos
        aiProbabilityTextView.visibility = View.GONE
        
        // Ocultar todos los layouts ABCDE
        asymmetryLayout.visibility = View.GONE
        borderLayout.visibility = View.GONE
        colorLayout.visibility = View.GONE
        diameterLayout.visibility = View.GONE
        evolutionLayout.visibility = View.GONE
        abcdeTotalScore.text = "Score ABCDE Total: --"
        
        analysisDateTextView.visibility = View.GONE

        // Mostrar botón de histórico si hay múltiples análisis
        val analysisCount = currentMoleData?.analysisCount ?: 0
        if (analysisCount > 1) {
            viewHistoryButton.visibility = View.VISIBLE
            viewHistoryButton.text = "Ver Evolución ($analysisCount análisis)"
        } else {
            viewHistoryButton.visibility = View.GONE
        }
    }

    private fun showNoAnalysisState() {
        Log.d("MoleViewerActivity", "Showing no analysis state")
        progressBar.visibility = View.GONE
        
        // Mostrar al menos la información básica del análisis con valores por defecto
        analysisContainer.visibility = View.VISIBLE
        
        // Configurar valores por defecto
        combinedScoreText.text = "--"
        riskLevelTextView.text = "Riesgo: --"
        riskIndicator.visibility = View.GONE
        aiProbabilityTextView.visibility = View.GONE
        
        // Mostrar mensaje en la recomendación
        recommendationTextView.text = "No hay datos de análisis disponibles para este lunar."
        
        // Ocultar todos los layouts ABCDE
        asymmetryLayout.visibility = View.GONE
        borderLayout.visibility = View.GONE
        colorLayout.visibility = View.GONE
        diameterLayout.visibility = View.GONE
        evolutionLayout.visibility = View.GONE
        abcdeTotalScore.text = "Score ABCDE Total: --"
        
        analysisDateTextView.visibility = View.GONE
        viewHistoryButton.visibility = View.GONE
        
        // También mostrar el empty state como información adicional
        showEmptyState(EmptyStateView.EmptyStateType.NO_ANALYSIS)
    }

    /**
     * Muestra estado vacío con tipo específico
     */
    private fun showEmptyState(type: EmptyStateView.EmptyStateType) {
        progressBar.visibility = View.GONE
        analysisContainer.visibility = View.GONE
        viewHistoryButton.visibility = View.GONE
        
        emptyStateView.setEmptyState(
            type = type,
            primaryAction = when (type) {
                EmptyStateView.EmptyStateType.NO_ANALYSIS -> {
                    { finish() } // Volver atrás para crear análisis
                }
                EmptyStateView.EmptyStateType.LOADING_FAILED -> {
                    { 
                        currentMoleData?.id?.let { moleId ->
                            loadLatestAnalysisFromService(moleId)
                        } ?: run {
                            loadMoleData()
                        }
                    }
                }
                else -> null
            }
        )
    }

    /**
     * Muestra estado de error con información contextual
     */
    private fun showErrorState(errorResult: ErrorHandler.ErrorResult, retryAction: () -> Unit) {
        progressBar.visibility = View.GONE
        analysisContainer.visibility = View.GONE
        viewHistoryButton.visibility = View.GONE
        
        val emptyStateType = when (errorResult.type) {
            ErrorHandler.ErrorType.NETWORK_ERROR -> EmptyStateView.EmptyStateType.NETWORK_ERROR
            ErrorHandler.ErrorType.AUTHENTICATION_ERROR -> EmptyStateView.EmptyStateType.AUTHENTICATION_ERROR
            ErrorHandler.ErrorType.DATA_NOT_FOUND -> EmptyStateView.EmptyStateType.NO_ANALYSIS
            else -> EmptyStateView.EmptyStateType.LOADING_FAILED
        }
        
        emptyStateView.setEmptyState(
            type = emptyStateType,
            primaryAction = if (errorResult.isRetryable) {
                { retryAction.invoke() }
            } else null,
            secondaryAction = {
                finish() // Volver atrás
            }
        )
        
        Toast.makeText(this, errorResult.userMessage, Toast.LENGTH_LONG).show()
    }

    private fun openAnalysisHistory() {
        val moleId = currentMoleData?.id
        if (!moleId.isNullOrEmpty()) {
            val intent = Intent(this, MoleAnalysisHistoryActivity::class.java).apply {
                putExtra("MOLE_ID", moleId)
                putExtra("MOLE_TITLE", currentMoleData?.title)
            }
            startActivity(intent)
        } else {
            val errorResult = ErrorHandler.ErrorResult(
                type = ErrorHandler.ErrorType.DATA_NOT_FOUND,
                userMessage = getString(R.string.error_data_not_found),
                technicalMessage = "ID de lunar no disponible",
                isRetryable = false
            )
            showErrorState(errorResult) { loadMoleData() }
        }
    }

    /**
     * Formatea una fecha en formato español DD/MM/YYYY
     */
    private fun formatDate(date: java.util.Date): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))
        return formatter.format(date)
    }
}
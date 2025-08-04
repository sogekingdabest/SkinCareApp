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
import com.google.firebase.auth.FirebaseAuth
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.error.ErrorHandler
import es.monsteraltech.skincare_tfm.body.mole.error.RetryManager
import es.monsteraltech.skincare_tfm.body.mole.model.ABCDEScores
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisDataConverter
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import es.monsteraltech.skincare_tfm.body.mole.repository.MoleRepository
import es.monsteraltech.skincare_tfm.body.mole.util.ImageLoadingUtil
import es.monsteraltech.skincare_tfm.body.mole.util.RiskLevelTranslator
import es.monsteraltech.skincare_tfm.body.mole.view.EmptyStateView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Actividad mejorada para visualizar lunares con análisis completos guardados
 * Muestra resultados de análisis de IA + ABCDE y permite acceso al histórico
 */
class MoleViewerActivity : AppCompatActivity() {

    private lateinit var retryManager: RetryManager
    private val auth = FirebaseAuth.getInstance()
    private val moleRepository = MoleRepository()
    
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

        retryManager = RetryManager()
        
        initializeViews()
        setupToolbar()
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

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadMoleData() {
        // Verificar si es un análisis histórico
        val isHistoricalAnalysis = intent.getBooleanExtra("IS_HISTORICAL_ANALYSIS", false)
        
        if (isHistoricalAnalysis) {
            Log.d("MoleViewerActivity", "Loading historical analysis data")
            loadHistoricalAnalysisData()
            return
        }

        // Obtener los datos del intent (comportamiento normal)
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
        
        Log.d("MoleViewerActivity", "Created currentMoleData with analysisCount: $analysisCount")

        // Configurar UI básica
        setupBasicUI(title, description, imageUrl)

        // Cargar y mostrar análisis
        if (analysisResult.isNotEmpty()) {
            Log.d("MoleViewerActivity", "Displaying analysis from AI result")
            displayAnalysisFromAiResult(analysisResult, imageUrl ?: "")
        } else if (moleId.isNotEmpty()) {
            Log.d("MoleViewerActivity", "Loading complete mole data from Firebase for mole: $moleId")
            loadCompleteMoleData(moleId)
        } else {
            Log.d("MoleViewerActivity", "No analysis data available and no mole ID, showing no analysis state")
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

    /**
     * Carga y muestra datos de un análisis histórico específico
     */
    private fun loadHistoricalAnalysisData() {
        Log.d("MoleViewerActivity", "Loading historical analysis data")
        
        // Obtener datos básicos del lunar
        val title = intent.getStringExtra("LUNAR_TITLE") ?: "Análisis Histórico"
        val description = intent.getStringExtra("LUNAR_DESCRIPTION") ?: ""
        val imageUrl = intent.getStringExtra("LUNAR_IMAGE_URL") ?: ""
        val moleId = intent.getStringExtra("MOLE_ID") ?: ""
        
        // Configurar UI básica
        setupBasicUI(title, description, imageUrl)
        
        // Crear objeto MoleData temporal (sin botón de evolución)
        currentMoleData = MoleData(
            id = moleId,
            title = title,
            description = description,
            imageUrl = imageUrl,
            aiResult = "",
            analysisCount = 1 // Solo 1 para evitar mostrar botón de evolución
        )
        
        // Reconstruir AnalysisData desde los datos históricos del intent
        val historicalAnalysis = reconstructHistoricalAnalysisFromIntent()
        if (historicalAnalysis != null) {
            Log.d("MoleViewerActivity", "Successfully reconstructed historical analysis")
            displayAnalysisData(historicalAnalysis)
        } else {
            Log.e("MoleViewerActivity", "Failed to reconstruct historical analysis")
            showNoAnalysisState()
        }
    }

    /**
     * Reconstruye un objeto AnalysisData desde los datos históricos del intent
     */
    private fun reconstructHistoricalAnalysisFromIntent(): AnalysisData? {
        return try {
            val analysisId = intent.getStringExtra("HISTORICAL_ANALYSIS_ID") ?: return null
            val moleId = intent.getStringExtra("MOLE_ID") ?: ""
            val analysisResult = intent.getStringExtra("HISTORICAL_ANALYSIS_RESULT") ?: ""
            val aiProbability = intent.getFloatExtra("HISTORICAL_AI_PROBABILITY", 0f)
            val aiConfidence = intent.getFloatExtra("HISTORICAL_AI_CONFIDENCE", 0f)
            val combinedScore = intent.getFloatExtra("HISTORICAL_COMBINED_SCORE", 0f)
            val riskLevel = intent.getStringExtra("HISTORICAL_RISK_LEVEL") ?: ""
            val recommendation = intent.getStringExtra("HISTORICAL_RECOMMENDATION") ?: ""
            val imageUrl = intent.getStringExtra("LUNAR_IMAGE_URL") ?: ""
            val createdAtMillis = intent.getLongExtra("HISTORICAL_CREATED_AT", System.currentTimeMillis())
            
            // Reconstruir ABCDE Scores
            val asymmetryScore = intent.getFloatExtra("HISTORICAL_ASYMMETRY_SCORE", 0f)
            val borderScore = intent.getFloatExtra("HISTORICAL_BORDER_SCORE", 0f)
            val colorScore = intent.getFloatExtra("HISTORICAL_COLOR_SCORE", 0f)
            val diameterScore = intent.getFloatExtra("HISTORICAL_DIAMETER_SCORE", 0f)
            val evolutionScore = intent.getFloatExtra("HISTORICAL_EVOLUTION_SCORE", -1f).let { 
                if (it == -1f) null else it 
            }
            val totalScore = intent.getFloatExtra("HISTORICAL_TOTAL_SCORE", 0f)
            
            val abcdeScores = ABCDEScores(
                asymmetryScore = asymmetryScore,
                borderScore = borderScore,
                colorScore = colorScore,
                diameterScore = diameterScore,
                evolutionScore = evolutionScore,
                totalScore = totalScore
            )
            
            // Crear Timestamp desde milisegundos
            val createdAt = com.google.firebase.Timestamp(java.util.Date(createdAtMillis))
            
            // Reconstruir metadatos del análisis
            val metadataBundle = intent.getBundleExtra("HISTORICAL_ANALYSIS_METADATA")
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
                id = analysisId,
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
            Log.e("MoleViewerActivity", "Error reconstructing historical analysis", e)
            null
        }
    }

    private fun displayAnalysisFromAiResult(aiResult: String, imageUrl: String) {
        Log.d("MoleViewerActivity", "Displaying analysis from AI result: '$aiResult'")
        Log.d("MoleViewerActivity", "Current analysisCount: ${currentMoleData?.analysisCount}")
        
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
                    ErrorHandler.handleError(this@MoleViewerActivity, exception, "Cargar análisis")
                    
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
                // Intentar obtener el análisis actual del lunar
                val currentAnalysisResult = moleRepository.getCurrentAnalysis(moleId)
                if (currentAnalysisResult.isSuccess) {
                    val currentAnalysis = currentAnalysisResult.getOrNull()
                    if (currentAnalysis != null) {
                        // Si hay análisis actual, devolverlo
                        Log.d("MoleViewerActivity", "Análisis actual encontrado para lunar $moleId")
                        Result.success(currentAnalysis)
                    } else {
                        // Si no hay análisis actual, no hay datos
                        Log.d("MoleViewerActivity", "No hay análisis actual para lunar $moleId")
                        Result.success(null)
                    }
                } else {
                    // Si falla obtener el análisis actual, propagar el error
                    Log.e("MoleViewerActivity", "Error al obtener análisis actual: ${currentAnalysisResult.exceptionOrNull()}")
                    currentAnalysisResult
                }
            }
            
            isRetrying = false
            progressBar.visibility = View.GONE
            emptyStateView.hideRetryIndicators()
            
            if (retryResult.result.isSuccess) {
                val analysis = retryResult.result.getOrNull()
                if (analysis != null) {
                    Log.d("MoleViewerActivity", "Mostrando análisis cargado desde servicio")
                    displayAnalysisData(analysis)
                    
                    if (retryResult.attemptsMade > 1) {
                        Toast.makeText(
                            this@MoleViewerActivity,
                            getString(R.string.retry_success_after_attempts, retryResult.attemptsMade),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Log.d("MoleViewerActivity", "No hay análisis disponible, mostrando estado vacío")
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
        Log.d("MoleViewerActivity", "=== DISPLAYING ANALYSIS DATA ===")
        Log.d("MoleViewerActivity", "Analysis ID: ${analysisData.id}")
        Log.d("MoleViewerActivity", "Mole ID: ${analysisData.moleId}")
        Log.d("MoleViewerActivity", "Combined score: ${analysisData.combinedScore}")
        Log.d("MoleViewerActivity", "Risk level: '${analysisData.riskLevel}'")
        Log.d("MoleViewerActivity", "AI probability: ${analysisData.aiProbability}")
        Log.d("MoleViewerActivity", "AI confidence: ${analysisData.aiConfidence}")
        Log.d("MoleViewerActivity", "Recommendation: '${analysisData.recommendation}'")
        Log.d("MoleViewerActivity", "Current analysisCount: ${currentMoleData?.analysisCount}")
        
        // ASEGURAR que el contenedor de análisis sea visible
        progressBar.visibility = View.GONE
        analysisContainer.visibility = View.VISIBLE
        emptyStateView.hide()
        
        Log.d("MoleViewerActivity", "Analysis container made visible")

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

        // Mostrar botón de histórico solo si hay múltiples análisis
        val analysisCount = currentMoleData?.analysisCount ?: 0
        Log.d("MoleViewerActivity", "Analysis count: $analysisCount")
        if (analysisCount > 1) {
            viewHistoryButton.visibility = View.VISIBLE
            val numberAnalysisHistory = analysisCount - 1
            viewHistoryButton.text = "Ver Evolución ($numberAnalysisHistory análisis)"
            Log.d("MoleViewerActivity", "Mostrando botón de histórico con $numberAnalysisHistory análisis anteriores")
        } else {
            viewHistoryButton.visibility = View.GONE
            Log.d("MoleViewerActivity", "Ocultando botón de histórico - solo hay $analysisCount análisis")
        }
        
        // Mostrar valores ABCDE del usuario si existen
        displayUserABCDEValues(analysisData)
        
        Log.d("MoleViewerActivity", "=== ANALYSIS DATA DISPLAY COMPLETED ===")
        Log.d("MoleViewerActivity", "Analysis container visibility: ${if (analysisContainer.visibility == View.VISIBLE) "VISIBLE" else "HIDDEN"}")
        Log.d("MoleViewerActivity", "History button visibility: ${if (viewHistoryButton.visibility == View.VISIBLE) "VISIBLE" else "HIDDEN"}")
    }

    private fun displayRiskLevel(riskLevel: String) {
        if (riskLevel.isNotEmpty()) {
            // Traducir el nivel de riesgo al español
            val translatedRiskLevel = RiskLevelTranslator.translateRiskLevel(this, riskLevel)
            riskLevelTextView.text = "Riesgo: $translatedRiskLevel"
            riskIndicator.visibility = View.VISIBLE
            
            // Cambiar color según el nivel de riesgo usando la función de utilidad
            val colorRes = RiskLevelTranslator.getRiskLevelColor(riskLevel)
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

        // Mostrar botón de histórico solo si hay múltiples análisis
        val analysisCount = currentMoleData?.analysisCount ?: 0
        Log.d("MoleViewerActivity", "Raw analysis - Analysis count: $analysisCount")
        if (analysisCount > 1) {
            viewHistoryButton.visibility = View.VISIBLE
            val numberAnalysisHistory = analysisCount - 1
            viewHistoryButton.text = "Ver Evolución ($numberAnalysisHistory análisis)"
            Log.d("MoleViewerActivity", "Mostrando botón de histórico (raw) con $numberAnalysisHistory análisis anteriores")
        } else {
            viewHistoryButton.visibility = View.GONE
            Log.d("MoleViewerActivity", "Ocultando botón de histórico (raw) - solo hay $analysisCount análisis")
        }
    }

    private fun showNoAnalysisState() {
        Log.d("MoleViewerActivity", "Showing no analysis state")
        progressBar.visibility = View.GONE
        analysisContainer.visibility = View.GONE
        viewHistoryButton.visibility = View.GONE
        
        // Mostrar el empty state
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

    private fun loadCompleteMoleData(moleId: String) {
        progressBar.visibility = View.VISIBLE
        analysisContainer.visibility = View.GONE
        emptyStateView.hide()

        lifecycleScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.e("MoleViewerActivity", "User not authenticated")
                    showErrorState(ErrorHandler.ErrorResult(
                        type = ErrorHandler.ErrorType.AUTHENTICATION_ERROR,
                        userMessage = "Usuario no autenticado",
                        technicalMessage = "FirebaseAuth.currentUser is null",
                        isRetryable = false,
                        suggestedAction = "Inicia sesión para ver los datos"
                    )) {}
                    return@launch
                }

                // Cargar el MoleData completo desde Firebase
                val result = moleRepository.getMoleById(currentUser.uid, moleId)
                
                if (result.isSuccess) {
                    val completeMoleData = result.getOrNull()
                    if (completeMoleData != null) {
                        Log.d("MoleViewerActivity", "Complete mole data loaded successfully")
                        Log.d("MoleViewerActivity", "Metadata keys: ${completeMoleData.analysisMetadata.keys}")
                        
                        // Actualizar currentMoleData con los datos completos
                        currentMoleData = completeMoleData
                        
                        // Crear AnalysisData desde MoleData para mostrar
                        val analysisData = createAnalysisDataFromMoleData(completeMoleData)
                        displayAnalysisData(analysisData)
                    } else {
                        Log.e("MoleViewerActivity", "Mole data is null")
                        showErrorState(ErrorHandler.ErrorResult(
                            type = ErrorHandler.ErrorType.DATA_NOT_FOUND,
                            userMessage = "No se pudieron cargar los datos del lunar",
                            technicalMessage = "MoleData is null after successful query",
                            isRetryable = true
                        )) {}
                    }
                } else {
                    Log.e("MoleViewerActivity", "Failed to load mole data: ${result.exceptionOrNull()?.message}")
                    showErrorState(ErrorHandler.ErrorResult(
                        type = ErrorHandler.ErrorType.UNKNOWN_ERROR,
                        userMessage = "Error al cargar los datos del lunar",
                        technicalMessage = result.exceptionOrNull()?.message ?: "Unknown error",
                        isRetryable = true
                    )) {}
                }
            } catch (e: Exception) {
                Log.e("MoleViewerActivity", "Exception loading complete mole data", e)
                showErrorState(ErrorHandler.ErrorResult(
                    type = ErrorHandler.ErrorType.UNKNOWN_ERROR,
                    userMessage = "Error inesperado al cargar los datos",
                    technicalMessage = e.message ?: "Unknown exception",
                    isRetryable = true
                )) {}
            }
        }
    }

    private fun createAnalysisDataFromMoleData(moleData: MoleData): AnalysisData {
        return AnalysisData(
            id = "${moleData.id}_current",
            moleId = moleData.id,
            analysisResult = moleData.aiResult,
            aiProbability = moleData.aiProbability?.toFloat() ?: 0f,
            aiConfidence = moleData.aiConfidence?.toFloat() ?: 0f,
            abcdeScores = ABCDEScores(
                asymmetryScore = moleData.abcdeAsymmetry?.toFloat() ?: 0f,
                borderScore = moleData.abcdeBorder?.toFloat() ?: 0f,
                colorScore = moleData.abcdeColor?.toFloat() ?: 0f,
                diameterScore = moleData.abcdeDiameter?.toFloat() ?: 0f,
                evolutionScore = null, // No se guarda en MoleData
                totalScore = moleData.abcdeTotalScore?.toFloat() ?: 0f
            ),
            combinedScore = moleData.combinedScore?.toFloat() ?: 0f,
            riskLevel = moleData.riskLevel,
            recommendation = moleData.recommendation,
            imageUrl = moleData.imageUrl,
            createdAt = moleData.lastAnalysisDate ?: moleData.createdAt,
            analysisMetadata = moleData.analysisMetadata // ¡Aquí están los metadatos con los valores del usuario!
        )
    }

    private fun displayUserABCDEValues(analysisData: AnalysisData) {
        try {
            Log.d("MoleViewerActivity", "=== DISPLAYING USER ABCDE VALUES ===")
            val metadata = analysisData.analysisMetadata
            Log.d("MoleViewerActivity", "Metadata keys: ${metadata.keys}")
            
            // Debug: mostrar tipos de datos
            metadata.forEach { (key, value) ->
                if (key.startsWith("user")) {
                    Log.d("MoleViewerActivity", "Metadata[$key] = $value (type: ${value.javaClass.simpleName})")
                }
            }
            
            // Verificar si existen valores del usuario en los metadatos
            // Usar casting robusto porque Firebase puede devolver Double en lugar de Float
            val userAsymmetry = (metadata["userAsymmetry"] as? Number)?.toFloat()
            val userBorder = (metadata["userBorder"] as? Number)?.toFloat()
            val userColor = (metadata["userColor"] as? Number)?.toFloat()
            val userDiameter = (metadata["userDiameter"] as? Number)?.toFloat()
            val userEvolution = (metadata["userEvolution"] as? Number)?.toFloat()
            val userTotal = (metadata["userTotal"] as? Number)?.toFloat()
            
            Log.d("MoleViewerActivity", "User values - A:$userAsymmetry, B:$userBorder, C:$userColor, D:$userDiameter, E:$userEvolution, Total:$userTotal")
            
            // Solo mostrar la sección si hay valores del usuario
            if (userAsymmetry != null || userBorder != null || userColor != null || 
                userDiameter != null || userEvolution != null) {
                
                Log.d("MoleViewerActivity", "User values found, showing user ABCDE card")
                
                // Mostrar la tarjeta del usuario
                findViewById<View>(R.id.userAbcdeCard)?.visibility = View.VISIBLE
                
                // Mostrar valores individuales usando findViewById
                userAsymmetry?.let { value ->
                    findViewById<View>(R.id.userAsymmetryLayout)?.visibility = View.VISIBLE
                    findViewById<TextView>(R.id.userAsymmetryScore)?.text = "${String.format("%.1f", value)}/2"
                    findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.userAsymmetryProgress)?.progress = ((value / 2f) * 100).toInt()
                }
                
                userBorder?.let { value ->
                    findViewById<View>(R.id.userBorderLayout)?.visibility = View.VISIBLE
                    findViewById<TextView>(R.id.userBorderScore)?.text = "${String.format("%.1f", value)}/8"
                    findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.userBorderProgress)?.progress = ((value / 8f) * 100).toInt()
                }
                
                userColor?.let { value ->
                    findViewById<View>(R.id.userColorLayout)?.visibility = View.VISIBLE
                    findViewById<TextView>(R.id.userColorScore)?.text = "${String.format("%.1f", value)}/6"
                    findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.userColorProgress)?.progress = ((value / 6f) * 100).toInt()
                }
                
                userDiameter?.let { value ->
                    findViewById<View>(R.id.userDiameterLayout)?.visibility = View.VISIBLE
                    findViewById<TextView>(R.id.userDiameterScore)?.text = "${String.format("%.1f", value)}/5"
                    findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.userDiameterProgress)?.progress = ((value / 5f) * 100).toInt()
                }
                
                userEvolution?.let { value ->
                    if (value > 0f) {
                        findViewById<View>(R.id.userEvolutionLayout)?.visibility = View.VISIBLE
                        findViewById<TextView>(R.id.userEvolutionScore)?.text = "${String.format("%.1f", value)}/3"
                        findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.userEvolutionProgress)?.progress = ((value / 3f) * 100).toInt()
                    }
                }
                
                // Mostrar score total del usuario
                userTotal?.let { total ->
                    findViewById<TextView>(R.id.userAbcdeTotalScore)?.text = "Score ABCDE Total (Usuario): ${String.format("%.1f", total)}"
                    
                    // Calcular y mostrar comparación con la app
                    val appTotal = analysisData.abcdeScores.totalScore
                    val difference = kotlin.math.abs(appTotal - total)
                    
                    val comparisonMessage = when {
                        difference < 1.0f -> "¡Muy similar! Diferencia: ${String.format("%.1f", difference)} puntos"
                        difference < 3.0f -> "Bastante similar. Diferencia: ${String.format("%.1f", difference)} puntos"
                        difference < 5.0f -> "Algunas diferencias. Diferencia: ${String.format("%.1f", difference)} puntos"
                        else -> "Diferencias significativas. Diferencia: ${String.format("%.1f", difference)} puntos"
                    }
                    
                    findViewById<TextView>(R.id.userComparisonText)?.text = comparisonMessage
                    Log.d("MoleViewerActivity", "Comparison: App=$appTotal, User=$total, Diff=$difference")
                }
                
                Log.d("MoleViewerActivity", "User ABCDE values displayed successfully")
                
            } else {
                // Ocultar la sección si no hay valores del usuario
                findViewById<View>(R.id.userAbcdeCard)?.visibility = View.GONE
                Log.d("MoleViewerActivity", "No user values found, hiding user ABCDE card")
            }
        } catch (e: Exception) {
            Log.e("MoleViewerActivity", "Error displaying user ABCDE values", e)
            // En caso de error, simplemente ocultar la sección
            findViewById<View>(R.id.userAbcdeCard)?.visibility = View.GONE
        }
    }

    /**
     * Formatea una fecha en formato español DD/MM/YYYY
     */
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
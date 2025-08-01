package es.monsteraltech.skincare_tfm.body.mole

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.ViewPageImages.UrlImagePagerAdapter
import es.monsteraltech.skincare_tfm.body.mole.ViewPageImages.ZoomOutPageTransformer
import es.monsteraltech.skincare_tfm.body.mole.error.ErrorHandler
import es.monsteraltech.skincare_tfm.body.mole.error.RetryManager
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisDataConverter
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import es.monsteraltech.skincare_tfm.body.mole.service.MoleAnalysisService
import es.monsteraltech.skincare_tfm.body.mole.util.ImageLoadingUtil
import es.monsteraltech.skincare_tfm.body.mole.view.EmptyStateView
import kotlinx.coroutines.launch

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
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var analysisContainer: LinearLayout
    private lateinit var analysisResultTextView: TextView
    private lateinit var abcdeScoresContainer: LinearLayout
    private lateinit var aiProbabilityTextView: TextView
    private lateinit var aiConfidenceTextView: TextView
    private lateinit var riskLevelTextView: TextView
    private lateinit var recommendationTextView: TextView
    private lateinit var analysisDateTextView: TextView
    private lateinit var viewHistoryButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateView: EmptyStateView

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
        viewPager = findViewById(R.id.detailImagePager)
        tabLayout = findViewById(R.id.tabLayout)
        analysisContainer = findViewById(R.id.analysisContainer)
        analysisResultTextView = findViewById(R.id.analysisResultText)
        abcdeScoresContainer = findViewById(R.id.abcdeScoresContainer)
        aiProbabilityTextView = findViewById(R.id.aiProbabilityText)
        aiConfidenceTextView = findViewById(R.id.aiConfidenceText)
        riskLevelTextView = findViewById(R.id.riskLevelText)
        recommendationTextView = findViewById(R.id.recommendationText)
        analysisDateTextView = findViewById(R.id.analysisDateText)
        viewHistoryButton = findViewById(R.id.viewHistoryButton)
        progressBar = findViewById(R.id.progressBar)
        emptyStateView = findViewById(R.id.emptyStateView)

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
            displayAnalysisFromAiResult(analysisResult, imageUrl ?: "")
        } else if (moleId.isNotEmpty()) {
            loadLatestAnalysisFromService(moleId)
        } else {
            showNoAnalysisState()
        }
    }

    private fun setupBasicUI(title: String, description: String, imageUrl: String?) {
        titleTextView.text = title
        descriptionTextView.text = description

        // Configurar ViewPager con las imágenes usando ImageLoadingUtil
        if (!imageUrl.isNullOrEmpty()) {
            val urlList = listOf(imageUrl)
            val urlPagerAdapter = UrlImagePagerAdapter(urlList)
            viewPager.adapter = urlPagerAdapter
            viewPager.setPageTransformer(ZoomOutPageTransformer())
            TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()
            
            // Precargar la imagen para mejor rendimiento
            ImageLoadingUtil.preloadImage(this, imageUrl)
        } else {
            tabLayout.visibility = View.GONE
            showEmptyState(EmptyStateView.EmptyStateType.NO_ANALYSIS)
        }
    }

    private fun displayAnalysisFromAiResult(aiResult: String, imageUrl: String) {
        try {
            // Convertir aiResult a AnalysisData estructurado
            val analysisData = AnalysisDataConverter.fromAiResultString(
                aiResult = aiResult,
                moleId = currentMoleData?.id ?: "",
                imageUrl = imageUrl,
                createdAt = currentMoleData?.createdAt ?: com.google.firebase.Timestamp.now()
            )

            if (analysisData != null) {
                displayAnalysisData(analysisData)
            } else {
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
        progressBar.visibility = View.GONE
        analysisContainer.visibility = View.VISIBLE
        emptyStateView.hide()

        // Mostrar información básica del análisis
        analysisResultTextView.text = if (analysisData.analysisResult.isNotEmpty()) {
            analysisData.analysisResult
        } else {
            "Análisis realizado el ${analysisData.createdAt.toDate()}"
        }

        // Mostrar probabilidad y confianza de IA
        if (analysisData.aiProbability > 0f || analysisData.aiConfidence > 0f) {
            aiProbabilityTextView.text = "Probabilidad IA: ${String.format("%.1f%%", analysisData.aiProbability * 100)}"
            aiConfidenceTextView.text = "Confianza: ${String.format("%.1f%%", analysisData.aiConfidence * 100)}"
            aiProbabilityTextView.visibility = View.VISIBLE
            aiConfidenceTextView.visibility = View.VISIBLE
        } else {
            aiProbabilityTextView.visibility = View.GONE
            aiConfidenceTextView.visibility = View.GONE
        }

        // Mostrar puntuaciones ABCDE
        displayABCDEScores(analysisData)

        // Mostrar nivel de riesgo
        if (analysisData.riskLevel.isNotEmpty()) {
            riskLevelTextView.text = "Nivel de Riesgo: ${analysisData.riskLevel}"
            riskLevelTextView.visibility = View.VISIBLE
            
            // Cambiar color según el nivel de riesgo
            val colorRes = when (analysisData.riskLevel.uppercase()) {
                "LOW" -> android.R.color.holo_green_dark
                "MODERATE" -> android.R.color.holo_orange_dark
                "HIGH", "HIGH" -> android.R.color.holo_red_dark
                else -> android.R.color.black
            }
            riskLevelTextView.setTextColor(getColor(colorRes))
        } else {
            riskLevelTextView.visibility = View.GONE
        }

        // Mostrar recomendación
        if (analysisData.recommendation.isNotEmpty()) {
            recommendationTextView.text = "Recomendación: ${analysisData.recommendation}"
            recommendationTextView.visibility = View.VISIBLE
        } else {
            recommendationTextView.visibility = View.GONE
        }

        // Mostrar fecha del análisis
        analysisDateTextView.text = "Fecha: ${analysisData.createdAt.toDate()}"
        analysisDateTextView.visibility = View.VISIBLE

        // Mostrar botón de histórico si hay múltiples análisis
        val analysisCount = currentMoleData?.analysisCount ?: 0
        if (analysisCount > 1) {
            viewHistoryButton.visibility = View.VISIBLE
            viewHistoryButton.text = "Ver Histórico ($analysisCount análisis)"
        } else {
            viewHistoryButton.visibility = View.GONE
        }
    }

    private fun displayABCDEScores(analysisData: AnalysisData) {
        abcdeScoresContainer.removeAllViews()

        val scores = analysisData.abcdeScores
        
        // Solo mostrar si hay puntuaciones válidas
        if (scores.totalScore > 0f || scores.asymmetryScore > 0f || 
            scores.borderScore > 0f || scores.colorScore > 0f || scores.diameterScore > 0f) {
            
            addScoreView("Asimetría", scores.asymmetryScore, 2f)
            addScoreView("Bordes", scores.borderScore, 8f)
            addScoreView("Color", scores.colorScore, 6f)
            addScoreView("Diámetro", scores.diameterScore, 5f)
            
            scores.evolutionScore?.let { evolutionScore ->
                addScoreView("Evolución", evolutionScore, 3f)
            }
            
            if (scores.totalScore > 0f) {
                addScoreView("Score Total", scores.totalScore, null, isTotal = true)
            }
            
            abcdeScoresContainer.visibility = View.VISIBLE
        } else {
            abcdeScoresContainer.visibility = View.GONE
        }
    }

    private fun addScoreView(label: String, score: Float, maxScore: Float?, isTotal: Boolean = false) {
        val scoreView = TextView(this).apply {
            text = if (maxScore != null) {
                "$label: ${String.format("%.1f", score)}/$maxScore"
            } else {
                "$label: ${String.format("%.1f", score)}"
            }
            
            textSize = if (isTotal) 16f else 14f
            setTextColor(if (isTotal) getColor(android.R.color.black) else getColor(android.R.color.darker_gray))
            
            if (isTotal) {
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            
            setPadding(0, 8, 0, 8)
        }
        
        abcdeScoresContainer.addView(scoreView)
    }

    private fun displayRawAnalysisResult(aiResult: String) {
        progressBar.visibility = View.GONE
        analysisContainer.visibility = View.VISIBLE
        emptyStateView.hide()

        // Mostrar resultado crudo
        analysisResultTextView.text = aiResult
        
        // Ocultar otros campos específicos
        aiProbabilityTextView.visibility = View.GONE
        aiConfidenceTextView.visibility = View.GONE
        abcdeScoresContainer.visibility = View.GONE
        riskLevelTextView.visibility = View.GONE
        recommendationTextView.visibility = View.GONE
        analysisDateTextView.visibility = View.GONE

        // Mostrar botón de histórico si hay múltiples análisis
        val analysisCount = currentMoleData?.analysisCount ?: 0
        if (analysisCount > 1) {
            viewHistoryButton.visibility = View.VISIBLE
            viewHistoryButton.text = "Ver Histórico ($analysisCount análisis)"
        } else {
            viewHistoryButton.visibility = View.GONE
        }
    }

    private fun showNoAnalysisState() {
        progressBar.visibility = View.GONE
        analysisContainer.visibility = View.GONE
        viewHistoryButton.visibility = View.GONE
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
}
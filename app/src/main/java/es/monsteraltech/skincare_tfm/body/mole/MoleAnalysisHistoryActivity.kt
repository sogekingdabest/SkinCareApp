package es.monsteraltech.skincare_tfm.body.mole

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.adapter.AnalysisHistoryAdapter
import es.monsteraltech.skincare_tfm.body.mole.error.ErrorHandler
import es.monsteraltech.skincare_tfm.body.mole.error.RetryManager
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.EvolutionComparison
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import es.monsteraltech.skincare_tfm.body.mole.repository.MoleRepository
import es.monsteraltech.skincare_tfm.body.mole.service.MoleAnalysisService
import es.monsteraltech.skincare_tfm.body.mole.util.ImageLoadingUtil
import es.monsteraltech.skincare_tfm.body.mole.view.EmptyStateView
import es.monsteraltech.skincare_tfm.body.mole.performance.AnalysisPaginationManager
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import es.monsteraltech.skincare_tfm.databinding.ActivityAnalysisHistoryBinding
import kotlinx.coroutines.launch
import java.io.File

class MoleAnalysisHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisHistoryBinding
    private lateinit var adapter: AnalysisHistoryAdapter

    private val moleRepository = MoleRepository()
    private val analysisService = MoleAnalysisService()
    private val firebaseDataManager = FirebaseDataManager()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var retryManager: RetryManager

    private var moleId: String = ""
    private var moleData: MoleData? = null
    private var analysisList: List<AnalysisData> = emptyList()
    private var isRetrying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar la toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Inicializar retry manager
        retryManager = RetryManager(this)

        // Obtener el ID del lunar
        moleId = intent.getStringExtra("MOLE_ID") ?: ""
        if (moleId.isEmpty()) {
            showErrorState(
                ErrorHandler.ErrorResult(
                    type = ErrorHandler.ErrorType.DATA_NOT_FOUND,
                    userMessage = getString(R.string.error_data_not_found),
                    technicalMessage = "ID de lunar no proporcionado",
                    isRetryable = false
                )
            ) { finish() }
            return
        }

        // Configurar RecyclerView
        setupRecyclerView()

        // Cargar datos
        loadMoleData()
        loadAnalysisHistory()
    }

    private fun setupRecyclerView() {
        adapter = AnalysisHistoryAdapter(
            context = this,
            analysisList = emptyList(),
            onItemClick = { analysis ->
                // Abrir vista detallada del análisis pasando datos primitivos
                val intent = Intent(this, AnalysisDetailActivity::class.java)
                intent.putExtra("ANALYSIS_ID", analysis.id)
                intent.putExtra("MOLE_ID", analysis.moleId)
                intent.putExtra("ANALYSIS_RESULT", analysis.analysisResult)
                intent.putExtra("AI_PROBABILITY", analysis.aiProbability)
                intent.putExtra("AI_CONFIDENCE", analysis.aiConfidence)
                intent.putExtra("COMBINED_SCORE", analysis.combinedScore)
                intent.putExtra("RISK_LEVEL", analysis.riskLevel)
                intent.putExtra("RECOMMENDATION", analysis.recommendation)
                intent.putExtra("IMAGE_URL", analysis.imageUrl)
                intent.putExtra("CREATED_AT", analysis.createdAt.toDate().time)
                
                // ABCDE Scores
                intent.putExtra("ASYMMETRY_SCORE", analysis.abcdeScores.asymmetryScore)
                intent.putExtra("BORDER_SCORE", analysis.abcdeScores.borderScore)
                intent.putExtra("COLOR_SCORE", analysis.abcdeScores.colorScore)
                intent.putExtra("DIAMETER_SCORE", analysis.abcdeScores.diameterScore)
                intent.putExtra("EVOLUTION_SCORE", analysis.abcdeScores.evolutionScore ?: -1f)
                intent.putExtra("TOTAL_SCORE", analysis.abcdeScores.totalScore)
                
                startActivity(intent)
            },
            onCompareClick = { currentAnalysis, previousAnalysis ->
                // Abrir vista de comparación pasando datos primitivos
                val intent = Intent(this, AnalysisComparisonActivity::class.java)
                
                // Current Analysis
                intent.putExtra("CURRENT_ID", currentAnalysis.id)
                intent.putExtra("CURRENT_RESULT", currentAnalysis.analysisResult)
                intent.putExtra("CURRENT_AI_PROBABILITY", currentAnalysis.aiProbability)
                intent.putExtra("CURRENT_AI_CONFIDENCE", currentAnalysis.aiConfidence)
                intent.putExtra("CURRENT_COMBINED_SCORE", currentAnalysis.combinedScore)
                intent.putExtra("CURRENT_RISK_LEVEL", currentAnalysis.riskLevel)
                intent.putExtra("CURRENT_IMAGE_URL", currentAnalysis.imageUrl)
                intent.putExtra("CURRENT_CREATED_AT", currentAnalysis.createdAt.toDate().time)
                
                // Previous Analysis
                intent.putExtra("PREVIOUS_ID", previousAnalysis.id)
                intent.putExtra("PREVIOUS_RESULT", previousAnalysis.analysisResult)
                intent.putExtra("PREVIOUS_AI_PROBABILITY", previousAnalysis.aiProbability)
                intent.putExtra("PREVIOUS_AI_CONFIDENCE", previousAnalysis.aiConfidence)
                intent.putExtra("PREVIOUS_COMBINED_SCORE", previousAnalysis.combinedScore)
                intent.putExtra("PREVIOUS_RISK_LEVEL", previousAnalysis.riskLevel)
                intent.putExtra("PREVIOUS_IMAGE_URL", previousAnalysis.imageUrl)
                intent.putExtra("PREVIOUS_CREATED_AT", previousAnalysis.createdAt.toDate().time)
                
                startActivity(intent)
            }
        )

        binding.analysisRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MoleAnalysisHistoryActivity)
            adapter = this@MoleAnalysisHistoryActivity.adapter
        }
    }

    private fun loadMoleData() {
        if (isRetrying) return
        
        binding.progressBar.visibility = View.VISIBLE

        // Verificar que el usuario está autenticado
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showErrorState(
                ErrorHandler.ErrorResult(
                    type = ErrorHandler.ErrorType.AUTHENTICATION_ERROR,
                    userMessage = getString(R.string.error_authentication),
                    technicalMessage = "Usuario no autenticado",
                    isRetryable = false
                )
            ) { finish() }
            return
        }

        lifecycleScope.launch {
            val retryResult = retryManager.executeWithRetry(
                operation = "Cargar datos del lunar $moleId",
                config = RetryManager.databaseConfig(),
                onRetryAttempt = { attempt, exception ->
                    isRetrying = true
                    runOnUiThread {
                        Toast.makeText(
                            this@MoleAnalysisHistoryActivity,
                            getString(R.string.retry_attempting, attempt, RetryManager.databaseConfig().maxAttempts),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                moleRepository.getMoleById(currentUser.uid, moleId)
            }
            
            isRetrying = false
            binding.progressBar.visibility = View.GONE
            
            if (retryResult.result.isSuccess) {
                moleData = retryResult.result.getOrNull()

                moleData?.let { mole ->
                    // Mostrar título
                    binding.moleTitleText.text = mole.title

                    // Cargar imagen con manejo de errores mejorado
                    if (mole.imageUrl.isNotEmpty()) {
                        ImageLoadingUtil.loadImageWithFallback(
                            context = this@MoleAnalysisHistoryActivity,
                            imageView = binding.moleImageView,
                            imageUrl = mole.imageUrl,
                            config = ImageLoadingUtil.Configs.fullSize().copy(
                                onLoadError = { exception ->
                                    // Log del error pero no mostrar al usuario (imagen no crítica)
                                    android.util.Log.w("MoleAnalysisHistory", "Error cargando imagen del lunar", exception)
                                }
                            ),
                            coroutineScope = lifecycleScope
                        )
                    }

                    // Mostrar información adicional del lunar
                    val analysisCount = mole.analysisCount
                    val lastAnalysisDate = mole.lastAnalysisDate?.toDate()
                    if (analysisCount > 0 && lastAnalysisDate != null) {
                        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                        binding.historyTitleText.text = "Historial de Análisis ($analysisCount análisis - Último: ${dateFormat.format(lastAnalysisDate)})"
                    }
                }
                
                if (retryResult.attemptsMade > 1) {
                    Toast.makeText(
                        this@MoleAnalysisHistoryActivity,
                        getString(R.string.retry_success_after_attempts, retryResult.attemptsMade),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                val exception = retryResult.result.exceptionOrNull() ?: Exception("Error desconocido")
                val errorResult = ErrorHandler.handleError(this@MoleAnalysisHistoryActivity, exception, "Cargar datos del lunar")
                
                showErrorState(errorResult) {
                    loadMoleData()
                }
                
                if (retryResult.attemptsMade > 1) {
                    Toast.makeText(
                        this@MoleAnalysisHistoryActivity,
                        getString(R.string.retry_failed_all_attempts, retryResult.attemptsMade),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun loadAnalysisHistory() {
        if (isRetrying) return
        
        // Verificar que el usuario está autenticado
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showErrorState(
                ErrorHandler.ErrorResult(
                    type = ErrorHandler.ErrorType.AUTHENTICATION_ERROR,
                    userMessage = "Error de autenticación",
                    technicalMessage = "Usuario no autenticado",
                    isRetryable = false
                )
            ) { finish() }
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        hideEmptyState()

        lifecycleScope.launch {
            try {
                // Usar paginación para cargar análisis
                val paginationManager = es.monsteraltech.skincare_tfm.body.mole.performance.AnalysisPaginationManager()
                
                // Configurar callbacks de paginación
                paginationManager.setCallbacks(
                    onLoadStart = { 
                        runOnUiThread { binding.progressBar.visibility = View.VISIBLE }
                    },
                    onLoadComplete = { analyses, hasMore ->
                        runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            if (analyses.isNotEmpty()) {
                                handleAnalysisLoaded(analyses, hasMore)
                            } else if (adapter.itemCount == 0) {
                                showEmptyState(EmptyStateView.EmptyStateType.NO_ANALYSIS)
                            }
                        }
                    },
                    onLoadError = { exception ->
                        runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            val errorResult = ErrorHandler.handleError(this@MoleAnalysisHistoryActivity, exception, "Cargar historial")
                            showErrorState(errorResult) { loadAnalysisHistory() }
                        }
                    }
                )
                
                // Configurar scroll listener para paginación automática
                paginationManager.setupScrollListener(binding.analysisRecyclerView) {
                    loadNextPage(paginationManager, moleId)
                }
                
                // Cargar primera página
                val result = paginationManager.loadAnalysisPage(moleId, isFirstPage = true)
                if (result.isFailure) {
                    val exception = result.exceptionOrNull() ?: Exception("Error desconocido")
                    val errorResult = ErrorHandler.handleError(this@MoleAnalysisHistoryActivity, exception, "Cargar historial")
                    showErrorState(errorResult) { loadAnalysisHistory() }
                }
                
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                val errorResult = ErrorHandler.handleError(this@MoleAnalysisHistoryActivity, e, "Cargar historial")
                showErrorState(errorResult) { loadAnalysisHistory() }
            }
        }
    }

    private suspend fun loadNextPage(
        paginationManager: es.monsteraltech.skincare_tfm.body.mole.performance.AnalysisPaginationManager,
        moleId: String
    ) {
        val result = paginationManager.loadAnalysisPage(moleId, isFirstPage = false)
        // Los callbacks del paginationManager manejarán la respuesta
    }

    private fun handleAnalysisLoaded(analyses: List<AnalysisData>, hasMore: Boolean) {
        // Calcular comparaciones de evolución
        val evolutionComparisons = calculateEvolutionComparisons(analyses)
        
        // Si es la primera carga, actualizar datos; si no, añadir datos
        if (adapter.itemCount == 0) {
            analysisList = analyses
            adapter.updateData(analyses, evolutionComparisons)
        } else {
            analysisList = analysisList + analyses
            adapter.addData(analyses, evolutionComparisons)
        }
        
        hideEmptyState()
        binding.analysisRecyclerView.visibility = View.VISIBLE
    }

    /**
     * Calcula las comparaciones de evolución entre análisis consecutivos
     */
    private fun calculateEvolutionComparisons(analyses: List<AnalysisData>): List<EvolutionComparison?> {
        val comparisons = mutableListOf<EvolutionComparison?>()
        
        for (i in analyses.indices) {
            if (i == analyses.size - 1) {
                // El análisis más antiguo no tiene comparación
                comparisons.add(null)
            } else {
                // Comparar con el análisis anterior (más antiguo)
                val current = analyses[i]
                val previous = analyses[i + 1]
                val comparison = EvolutionComparison.create(current, previous)
                comparisons.add(comparison)
            }
        }
        
        return comparisons
    }

    /**
     * Muestra estado de error con información contextual
     */
    private fun showErrorState(errorResult: ErrorHandler.ErrorResult, retryAction: () -> Unit) {
        binding.progressBar.visibility = View.GONE
        binding.analysisRecyclerView.visibility = View.GONE
        
        val emptyStateType = when (errorResult.type) {
            ErrorHandler.ErrorType.NETWORK_ERROR -> EmptyStateView.EmptyStateType.NETWORK_ERROR
            ErrorHandler.ErrorType.AUTHENTICATION_ERROR -> EmptyStateView.EmptyStateType.AUTHENTICATION_ERROR
            ErrorHandler.ErrorType.DATA_NOT_FOUND -> EmptyStateView.EmptyStateType.NO_ANALYSIS
            else -> EmptyStateView.EmptyStateType.LOADING_FAILED
        }
        
        binding.emptyView.setEmptyState(
            type = emptyStateType,
            primaryAction = if (errorResult.isRetryable) {
                { retryAction.invoke() }
            } else null,
            secondaryAction = {
                finish() // Volver atrás
            }
        )
        
        binding.emptyView.visibility = View.VISIBLE
        Toast.makeText(this, errorResult.userMessage, Toast.LENGTH_LONG).show()
    }

    /**
     * Muestra estado vacío con tipo específico
     */
    private fun showEmptyState(type: EmptyStateView.EmptyStateType) {
        binding.progressBar.visibility = View.GONE
        binding.analysisRecyclerView.visibility = View.GONE
        
        binding.emptyView.setEmptyState(
            type = type,
            primaryAction = when (type) {
                EmptyStateView.EmptyStateType.NO_ANALYSIS -> {
                    { finish() } // Volver atrás para crear análisis
                }
                else -> {
                    { loadAnalysisHistory() }
                }
            }
        )
        
        binding.emptyView.visibility = View.VISIBLE
    }

    /**
     * Oculta el estado vacío
     */
    private fun hideEmptyState() {
        binding.emptyView.visibility = View.GONE
        binding.emptyView.hide()
    }

    /**
     * Muestra indicador de reintento
     */
    private fun showRetryIndicator(attempt: Int, maxAttempts: Int) {
        binding.emptyView.showRetryIndicators(attempt, maxAttempts)
        binding.emptyView.visibility = View.VISIBLE
    }

    /**
     * Oculta indicador de reintento
     */
    private fun hideRetryIndicator() {
        binding.emptyView.hideRetryIndicators()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
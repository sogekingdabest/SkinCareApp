package es.monsteraltech.skincare_tfm.body.mole
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.adapter.AnalysisHistoryAdapter
import es.monsteraltech.skincare_tfm.body.mole.error.ErrorHandler
import es.monsteraltech.skincare_tfm.body.mole.error.RetryManager
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import es.monsteraltech.skincare_tfm.body.mole.repository.MoleRepository
import es.monsteraltech.skincare_tfm.body.mole.util.ImageLoadingUtil
import es.monsteraltech.skincare_tfm.body.mole.view.EmptyStateView
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import es.monsteraltech.skincare_tfm.databinding.ActivityAnalysisHistoryBinding
import es.monsteraltech.skincare_tfm.utils.UIUtils
import kotlinx.coroutines.launch

class MoleAnalysisHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnalysisHistoryBinding
    private lateinit var adapter: AnalysisHistoryAdapter
    private val moleRepository = MoleRepository()
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
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        retryManager = RetryManager()
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
        setupRecyclerView()
        loadMoleData()
        loadAnalysisHistory()
    }
    private fun setupRecyclerView() {
        adapter = AnalysisHistoryAdapter(
            context = this,
            analysisList = emptyList(),
            onItemClick = { analysis ->
                val intent = createMoleViewerIntentForHistoricalAnalysis(analysis)
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
                onRetryAttempt = { attempt, _ ->
                    isRetrying = true
                    runOnUiThread {
                        UIUtils.showInfoToast(
                            this@MoleAnalysisHistoryActivity,
                            getString(R.string.retry_attempting, attempt, RetryManager.databaseConfig().maxAttempts)
                        )
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
                    binding.moleTitleText.text = mole.title
                    if (mole.imageUrl.isNotEmpty()) {
                        ImageLoadingUtil.loadImageWithFallback(
                            context = this@MoleAnalysisHistoryActivity,
                            imageView = binding.moleImageView,
                            imageUrl = mole.imageUrl,
                            config = ImageLoadingUtil.Configs.fullSize().copy(
                                onLoadError = { exception ->
                                    android.util.Log.w("MoleAnalysisHistory", "Error cargando imagen del lunar", exception)
                                }
                            ),
                            coroutineScope = lifecycleScope
                        )
                    }
                    val analysisCount = mole.analysisCount
                    val lastAnalysisDate = mole.lastAnalysisDate?.toDate()
                    if (analysisCount > 0 && lastAnalysisDate != null) {
                        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                        binding.historyTitleText.text = "Historial de Análisis\n($analysisCount análisis - Último: ${dateFormat.format(lastAnalysisDate)})"
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
            val retryResult = retryManager.executeWithRetry(
                operation = "Cargar historial de análisis del lunar $moleId",
                config = RetryManager.databaseConfig(),
                onRetryAttempt = { attempt, _ ->
                    isRetrying = true
                    runOnUiThread {
                        showRetryIndicator(attempt, RetryManager.databaseConfig().maxAttempts)
                        Toast.makeText(
                            this@MoleAnalysisHistoryActivity,
                            getString(R.string.retry_attempting, attempt, RetryManager.databaseConfig().maxAttempts),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                moleRepository.getAnalysisHistory(moleId)
            }
            isRetrying = false
            binding.progressBar.visibility = View.GONE
            hideRetryIndicator()
            if (retryResult.result.isSuccess) {
                val analyses = retryResult.result.getOrNull() ?: emptyList()
                if (analyses.isNotEmpty()) {
                    handleAnalysisLoaded(analyses)
                    if (retryResult.attemptsMade > 1) {
                        Toast.makeText(
                            this@MoleAnalysisHistoryActivity,
                            getString(R.string.retry_success_after_attempts, retryResult.attemptsMade),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    showEmptyState(EmptyStateView.EmptyStateType.NO_ANALYSIS)
                }
            } else {
                val exception = retryResult.result.exceptionOrNull() ?: Exception("Error desconocido")
                val errorResult = ErrorHandler.handleError(this@MoleAnalysisHistoryActivity, exception, "Cargar historial")
                showErrorState(errorResult) {
                    loadAnalysisHistory()
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
    private fun handleAnalysisLoaded(analyses: List<AnalysisData>) {
        analysisList = analyses
        adapter.updateData(analyses)
        hideEmptyState()
        binding.analysisRecyclerView.visibility = View.VISIBLE
    }
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
                finish()
            }
        )
        binding.emptyView.visibility = View.VISIBLE
        Toast.makeText(this, errorResult.userMessage, Toast.LENGTH_LONG).show()
    }
    private fun showEmptyState(type: EmptyStateView.EmptyStateType) {
        binding.progressBar.visibility = View.GONE
        binding.analysisRecyclerView.visibility = View.GONE
        binding.emptyView.setEmptyState(
            type = type,
            primaryAction = when (type) {
                EmptyStateView.EmptyStateType.NO_ANALYSIS -> {
                    { finish() }
                }
                else -> {
                    { loadAnalysisHistory() }
                }
            }
        )
        binding.emptyView.visibility = View.VISIBLE
    }
    private fun hideEmptyState() {
        binding.emptyView.visibility = View.GONE
        binding.emptyView.hide()
    }
    private fun showRetryIndicator(attempt: Int, maxAttempts: Int) {
        binding.emptyView.showRetryIndicators(attempt, maxAttempts)
        binding.emptyView.visibility = View.VISIBLE
    }
    private fun hideRetryIndicator() {
        binding.emptyView.hideRetryIndicators()
    }
    private fun createMoleViewerIntentForHistoricalAnalysis(analysis: AnalysisData): Intent {
        return Intent(this, MoleViewerActivity::class.java).apply {
            putExtra("MOLE_ID", analysis.moleId)
            putExtra("LUNAR_TITLE", moleData?.title ?: "Análisis Histórico")
            putExtra("LUNAR_DESCRIPTION", moleData?.description ?: "")
            putExtra("LUNAR_IMAGE_URL", analysis.imageUrl)
            putExtra("IS_HISTORICAL_ANALYSIS", true)
            putExtra("ANALYSIS_COUNT", 1)
            putExtra("HISTORICAL_ANALYSIS_ID", analysis.id)
            putExtra("HISTORICAL_ANALYSIS_RESULT", analysis.analysisResult)
            putExtra("HISTORICAL_AI_PROBABILITY", analysis.aiProbability)
            putExtra("HISTORICAL_AI_CONFIDENCE", analysis.aiConfidence)
            putExtra("HISTORICAL_COMBINED_SCORE", analysis.combinedScore)
            putExtra("HISTORICAL_RISK_LEVEL", analysis.riskLevel)
            putExtra("HISTORICAL_RECOMMENDATION", analysis.recommendation)
            putExtra("HISTORICAL_CREATED_AT", analysis.createdAt.toDate().time)
            putExtra("HISTORICAL_ASYMMETRY_SCORE", analysis.abcdeScores.asymmetryScore)
            putExtra("HISTORICAL_BORDER_SCORE", analysis.abcdeScores.borderScore)
            putExtra("HISTORICAL_COLOR_SCORE", analysis.abcdeScores.colorScore)
            putExtra("HISTORICAL_DIAMETER_SCORE", analysis.abcdeScores.diameterScore)
            putExtra("HISTORICAL_EVOLUTION_SCORE", analysis.abcdeScores.evolutionScore ?: -1f)
            putExtra("HISTORICAL_TOTAL_SCORE", analysis.abcdeScores.totalScore)
            val metadataBundle = Bundle()
            analysis.analysisMetadata.forEach { (key, value) ->
                when (value) {
                    is String -> metadataBundle.putString(key, value)
                    is Float -> metadataBundle.putFloat(key, value)
                    is Int -> metadataBundle.putInt(key, value)
                    is Boolean -> metadataBundle.putBoolean(key, value)
                    is Long -> metadataBundle.putLong(key, value)
                    is Double -> metadataBundle.putDouble(key, value)
                }
            }
            putExtra("HISTORICAL_ANALYSIS_METADATA", metadataBundle)
        }
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
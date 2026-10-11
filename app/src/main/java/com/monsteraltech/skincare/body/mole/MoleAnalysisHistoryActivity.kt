package com.monsteraltech.skincare.body.mole

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.body.mole.adapter.AnalysisHistoryAdapter
import com.monsteraltech.skincare.body.mole.domain.model.MoleListEntry
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.util.ImageLoadingUtil
import com.monsteraltech.skincare.body.mole.view.EmptyStateView
import com.monsteraltech.skincare.databinding.ActivityAnalysisHistoryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class MoleAnalysisHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnalysisHistoryBinding
    private lateinit var adapter: AnalysisHistoryAdapter
    private val viewModel by lazy { ViewModelProvider(this)[AnalysisHistoryViewModel::class.java] }
    private var moleData: MoleListEntry? = null
    private var headerImage: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val moleId = intent.getStringExtra("MOLE_ID").orEmpty()
        adapter = AnalysisHistoryAdapter(this, emptyList(), lifecycleScope) { analysis ->
            startActivity(createMoleViewerIntentForHistoricalAnalysis(analysis))
        }
        binding.analysisRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.analysisRecyclerView.adapter = adapter
        binding.loadMoreButton.setOnClickListener { viewModel.loadMore() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.start(moleId)
                viewModel.uiState.collect { state ->
                    when (state) {
                        AnalysisHistoryViewModel.UiState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.emptyView.visibility = View.GONE
                            binding.loadMoreButton.visibility = View.GONE
                        }
                        is AnalysisHistoryViewModel.UiState.Ready -> {
                            val page = state.page
                            binding.progressBar.visibility = View.GONE
                            moleData = page.mole
                            binding.moleTitleText.text = page.mole.title
                            if (headerImage != page.mole.imageReference) {
                                headerImage = page.mole.imageReference
                                ImageLoadingUtil.loadImageWithFallback(this@MoleAnalysisHistoryActivity, binding.moleImageView,
                                    page.mole.imageReference, ImageLoadingUtil.Configs.fullSize().copy(enableRetry = false), lifecycleScope)
                            }
                            val last = page.mole.lastAnalysisAtEpochMillis?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it)) }.orEmpty()
                            binding.historyTitleText.text = getString(R.string.history_summary, page.totalCount, last)
                            adapter.updateData(page.analyses)
                            binding.analysisRecyclerView.visibility = if (page.analyses.isEmpty()) View.GONE else View.VISIBLE
                            binding.emptyView.visibility = if (page.analyses.isEmpty()) View.VISIBLE else View.GONE
                            if (page.analyses.isEmpty()) binding.emptyView.setEmptyState(EmptyStateView.EmptyStateType.NO_ANALYSIS, primaryAction = { finish() })
                            binding.loadMoreButton.visibility = if (page.hasMore) View.VISIBLE else View.GONE
                            binding.loadMoreButton.text = getString(R.string.list_load_more, page.analyses.size, page.totalCount)
                        }
                        is AnalysisHistoryViewModel.UiState.Failed -> {
                            binding.progressBar.visibility = View.GONE
                            binding.analysisRecyclerView.visibility = View.GONE
                            binding.loadMoreButton.visibility = View.GONE
                            binding.emptyView.visibility = View.VISIBLE
                            val kind = if (state.authentication) EmptyStateView.EmptyStateType.AUTHENTICATION_ERROR
                                else if (state.missing) EmptyStateView.EmptyStateType.NO_ANALYSIS else EmptyStateView.EmptyStateType.LOADING_FAILED
                            binding.emptyView.setEmptyState(kind,
                                primaryAction = if (state.authentication || state.missing) ({ finish() }) else ({ viewModel.retry() }),
                                secondaryAction = { finish() })
                        }
                    }
                }
            }
        }
    }
    override fun onDestroy() {
        binding.analysisRecyclerView.adapter = null
        super.onDestroy()
    }
    private fun createMoleViewerIntentForHistoricalAnalysis(analysis: AnalysisData): Intent {
        return Intent(this, MoleViewerActivity::class.java).apply {
            putExtra("MOLE_ID", analysis.moleId)
            putExtra("LUNAR_TITLE", moleData?.title ?: "Análisis Histórico")
            putExtra("LUNAR_DESCRIPTION", analysis.description)
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

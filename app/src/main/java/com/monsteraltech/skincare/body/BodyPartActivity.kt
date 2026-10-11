package com.monsteraltech.skincare.body
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModelProvider
import com.monsteraltech.skincare.body.mole.MoleListViewModel
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.body.mole.Mole
import com.monsteraltech.skincare.body.mole.MoleAdapter
import com.monsteraltech.skincare.body.mole.MoleDetailActivity
import com.monsteraltech.skincare.camera.CameraActivity
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BodyPartActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var searchEditText: TextInputEditText
    private lateinit var searchInputLayout: TextInputLayout
    private lateinit var lunarRecyclerView: RecyclerView
    private lateinit var moleAdapter: MoleAdapter
    private lateinit var addButton: FloatingActionButton
    private lateinit var emptyStateButton: MaterialButton
    private lateinit var bodyPart: String
    private lateinit var bodyPartTitleTextView: TextView
    private lateinit var moleCountText: TextView
    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var noResultsLayout: LinearLayout
    private val viewModel by lazy { ViewModelProvider(this)[MoleListViewModel::class.java] }
    private lateinit var loadMoreButton: MaterialButton
    private val moleList = ArrayList<Mole>()
    private val imageAnalysisLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri = result.data?.getParcelableExtra<Uri>("selectedImage")
            if (imageUri != null) {
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_body)
        val color = intent.getStringExtra("COLOR_VALUE")
        when (color) {
            "#FF000000" -> {
                bodyPart = "Cabeza"
            }
            "#FFED1C24" -> {
                bodyPart = "Brazo derecho"
            }
            "#FFFFC90E" -> {
                bodyPart = "Torso"
            }
            "#FF22B14C" -> {
                bodyPart = "Brazo izquierdo"
            }
            "#FF3F48CC" -> {
                bodyPart = "Pierna derecha"
            }
            "#FFED00FF" -> {
                bodyPart = "Pierna izquierda"
            }
            else -> {
                bodyPart = "Parte desconocida"
                Toast.makeText(this, "Área Desconocida", Toast.LENGTH_SHORT).show()
            }
        }
        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupSearchBar()
        setupClickListeners(color)
        observeMoles(color)
    }
    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        searchEditText = findViewById(R.id.searchEditText)
        searchInputLayout = findViewById(R.id.searchInputLayout)
        bodyPartTitleTextView = findViewById(R.id.bodyPartTitle)
        moleCountText = findViewById(R.id.moleCountText)
        lunarRecyclerView = findViewById(R.id.lunarRecyclerView)
        addButton = findViewById(R.id.addButton)
        emptyStateButton = findViewById(R.id.emptyStateButton)
        progressBar = findViewById(R.id.progressBar)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        noResultsLayout = findViewById(R.id.noResultsLayout)
        loadMoreButton = findViewById(R.id.loadMoreButton)
        bodyPartTitleTextView.text = bodyPart
    }
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = bodyPart
    }
    private fun setupClickListeners(color: String?) {
        val cameraIntent = Intent(this, CameraActivity::class.java).apply {
            putExtra("BODY_PART_COLOR", color)
        }
        addButton.setOnClickListener {
            imageAnalysisLauncher.launch(cameraIntent)
        }
        emptyStateButton.setOnClickListener {
            imageAnalysisLauncher.launch(cameraIntent)
        }
    }
    private fun setupRecyclerView() {
        lunarRecyclerView.layoutManager = GridLayoutManager(this, 1)
        moleAdapter = MoleAdapter(
            moleList = moleList,
            onClick = { mole ->
                val intent = Intent(this, MoleDetailActivity::class.java)
                intent.putExtra("MOLE_ID", mole.id)
                intent.putExtra("LUNAR_TITLE", mole.title)
                intent.putExtra("LUNAR_DESCRIPTION", mole.description)
                intent.putExtra("LUNAR_ANALYSIS_RESULT", mole.analysisResult)
                intent.putExtra("LUNAR_IMAGE_URL", mole.imageUrl)
                intent.putExtra("ANALYSIS_COUNT", mole.analysisCount)
                intent.putExtra(
                    "BODY_PART_COLOR",
                    this@BodyPartActivity.intent.getStringExtra(
                        "COLOR_VALUE"
                    )
                )
                startActivity(intent)
            }
        )
        lunarRecyclerView.adapter = moleAdapter
        lunarRecyclerView.itemAnimator?.apply {
            addDuration = 250L
            removeDuration = 250L
            changeDuration = 250L
        }
    }
    private fun observeMoles(colorCode: String?) {
        loadMoreButton.setOnClickListener {
            if (viewModel.uiState.value is MoleListViewModel.UiState.Failed) viewModel.retry() else viewModel.loadMore()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.start(bodyPartColor = colorCode.orEmpty())
                viewModel.uiState.collect { state ->
                    when (state) {
                        MoleListViewModel.UiState.Loading -> { showLoading(true); loadMoreButton.visibility = View.GONE }
                        is MoleListViewModel.UiState.Ready -> {
                            val page = state.page
                            showLoading(false)
                            moleAdapter.submitList(page.items.map { Mole(id = it.id, title = it.title,
                                description = it.description, imageUrl = it.imageReference,
                                analysisResult = it.analysisResult, analysisCount = it.analysisCount) })
                            updateMoleCount(page.totalCount)
                            if (page.totalCount == 0) showEmptyState(true)
                            else if (page.matchingCount == 0) showNoResultsState(true)
                            else { showEmptyState(false); showNoResultsState(false) }
                            loadMoreButton.visibility = if (page.hasMore) View.VISIBLE else View.GONE
                            loadMoreButton.text = getString(R.string.list_load_more, page.items.size, page.matchingCount)
                        }
                        is MoleListViewModel.UiState.Failed -> {
                            showLoading(false)
                            lunarRecyclerView.visibility = View.GONE
                            moleCountText.setText(if (state.authentication) R.string.error_authentication else R.string.list_load_error)
                            loadMoreButton.visibility = View.VISIBLE
                            loadMoreButton.setText(R.string.list_retry)
                        }
                    }
                }
            }
        }
    }
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        lunarRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
        emptyStateLayout.visibility = View.GONE
        noResultsLayout.visibility = View.GONE
    }
    private fun showEmptyState(show: Boolean) {
        emptyStateLayout.visibility = if (show) View.VISIBLE else View.GONE
        lunarRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
        noResultsLayout.visibility = View.GONE
    }
    private fun showNoResultsState(show: Boolean) {
        noResultsLayout.visibility = if (show) View.VISIBLE else View.GONE
        lunarRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
        emptyStateLayout.visibility = View.GONE
    }
    private fun updateMoleCount(count: Int) {
        moleCountText.text = when (count) {
            0 -> "No hay lunares registrados"
            1 -> "1 lunar registrado"
            else -> "$count lunares registrados"
        }
    }
    private fun setupSearchBar() {
        searchEditText.setText(viewModel.query)
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.search(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
                true
            } else {
                false
            }
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

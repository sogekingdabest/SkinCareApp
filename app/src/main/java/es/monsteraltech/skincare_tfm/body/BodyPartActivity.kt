package es.monsteraltech.skincare_tfm.body

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.Mole
import es.monsteraltech.skincare_tfm.body.mole.MoleAdapter
import es.monsteraltech.skincare_tfm.body.mole.MoleDetailActivity
import es.monsteraltech.skincare_tfm.camera.CameraActivity
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import kotlinx.coroutines.launch

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
    private val firebaseDataManager = FirebaseDataManager()
    private val moleList = ArrayList<Mole>()

    private val imageAnalysisLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri = result.data?.getParcelableExtra<Uri>("selectedImage")
            if (imageUri != null) {
                // Aquí podríamos procesar la imagen si fuera necesario
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_body)

        // Recibe el color desde el Intent
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
        
        loadMolesFromFirebase(color)
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
                startActivity(intent)
            },
            onFilterChanged = { isEmpty ->
                // Manejar el estado vacío durante la búsqueda
                handleFilteredEmptyState(isEmpty)
            }
        )
        lunarRecyclerView.adapter = moleAdapter

        lunarRecyclerView.itemAnimator?.apply {
            addDuration = 250L
            removeDuration = 250L
            changeDuration = 250L
        }
    }

    private fun loadMolesFromFirebase(colorCode: String?) {
        if (colorCode == null) return

        showLoading(true)

        lifecycleScope.launch {
            try {
                val firebaseMoles = firebaseDataManager.getMolesForBodyPart(colorCode)

                moleList.clear()

                for (fbMole in firebaseMoles) {
                    moleList.add(
                        Mole(
                            id = fbMole.id,
                            title = fbMole.title,
                            description = fbMole.description,
                            imageUrl = fbMole.imageUrl,
                            analysisResult = fbMole.aiResult,
                            analysisCount = fbMole.analysisCount
                        )
                    )
                }

                runOnUiThread {
                    showLoading(false)
                    updateMoleCount()
                    moleAdapter.notifyDataSetChanged()
                    
                    if (moleList.isEmpty()) {
                        showEmptyState(true)
                    } else {
                        showEmptyState(false)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showLoading(false)
                    showEmptyState(true)
                    Toast.makeText(this@BodyPartActivity, "Error al cargar lunares: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun updateMoleCount() {
        val count = moleList.size
        moleCountText.text = when (count) {
            0 -> "No hay lunares registrados"
            1 -> "1 lunar registrado"
            else -> "$count lunares registrados"
        }
    }

    private fun handleFilteredEmptyState(isEmpty: Boolean) {
        if (isEmpty && moleList.isNotEmpty()) {
            // Hay datos pero el filtro no encontró resultados
            showNoResultsState(true)
        } else if (isEmpty && moleList.isEmpty()) {
            // No hay datos en absoluto
            showEmptyState(true)
        } else {
            // Hay resultados filtrados
            showEmptyState(false)
            showNoResultsState(false)
            lunarRecyclerView.visibility = View.VISIBLE
        }
    }
    private fun setupSearchBar() {
        // Configurar el listener para el texto de búsqueda
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Filtrar la lista cuando el texto cambie
                moleAdapter.filter.filter(s)
            }
            
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Configurar el listener para cuando se presione enter o se envíe la búsqueda
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // Ocultar el teclado cuando se presione buscar
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
                true
            } else {
                false
            }
        }

        // El ícono de limpiar ya está configurado automáticamente por endIconMode="clear_text"
    }

    override fun onResume() {
        super.onResume()
        // Recargar datos cuando volvamos a esta actividad
        val color = intent.getStringExtra("COLOR_VALUE")
        if (color != null) {
            loadMolesFromFirebase(color)
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
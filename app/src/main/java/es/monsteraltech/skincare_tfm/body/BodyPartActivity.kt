package es.monsteraltech.skincare_tfm.body

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.search.SearchBar
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.Mole
import es.monsteraltech.skincare_tfm.body.mole.MoleAdapter
import es.monsteraltech.skincare_tfm.body.mole.MoleDetailActivity
import es.monsteraltech.skincare_tfm.camera.CameraActivity
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import kotlinx.coroutines.launch

class BodyPartActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var searchBar: SearchBar
    private lateinit var lunarRecyclerView: RecyclerView
    private lateinit var moleAdapter: MoleAdapter
    private lateinit var addButton: FloatingActionButton
    private lateinit var emptyStateButton: MaterialButton
    private lateinit var bodyPart: String
    private lateinit var bodyPartTitleTextView: TextView
    private lateinit var moleCountText: TextView
    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var emptyStateLayout: LinearLayout
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
        searchBar = findViewById(R.id.searchBar)
        bodyPartTitleTextView = findViewById(R.id.bodyPartTitle)
        moleCountText = findViewById(R.id.moleCountText)
        lunarRecyclerView = findViewById(R.id.lunarRecyclerView)
        addButton = findViewById(R.id.addButton)
        emptyStateButton = findViewById(R.id.emptyStateButton)
        progressBar = findViewById(R.id.progressBar)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        
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
        moleAdapter = MoleAdapter(moleList) { mole ->
            val intent = Intent(this, MoleDetailActivity::class.java)
            intent.putExtra("MOLE_ID", mole.id)
            intent.putExtra("LUNAR_TITLE", mole.title)
            intent.putExtra("LUNAR_DESCRIPTION", mole.description)
            intent.putExtra("LUNAR_ANALYSIS_RESULT", mole.analysisResult)
            intent.putExtra("LUNAR_IMAGE_URL", mole.imageUrl)
            intent.putExtra("ANALYSIS_COUNT", mole.analysisCount)
            startActivity(intent)
        }
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
    }

    private fun showEmptyState(show: Boolean) {
        emptyStateLayout.visibility = if (show) View.VISIBLE else View.GONE
        lunarRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateMoleCount() {
        val count = moleList.size
        moleCountText.text = when (count) {
            0 -> "No hay lunares registrados"
            1 -> "1 lunar registrado"
            else -> "$count lunares registrados"
        }
    }

    private fun setupSearchBar() {
        searchBar.setOnMenuItemClickListener { menuItem ->
            // Manejar elementos del menú si es necesario
            false
        }
        
        // Configurar búsqueda (si el SearchBar soporta texto de búsqueda)
        // Nota: SearchBar de Material 3 funciona diferente al SearchView tradicional
        // Aquí puedes implementar la lógica de búsqueda según tus necesidades
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
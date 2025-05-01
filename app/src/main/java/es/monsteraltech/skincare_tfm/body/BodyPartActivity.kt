package es.monsteraltech.skincare_tfm.body

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.Mole
import es.monsteraltech.skincare_tfm.body.mole.MoleAdapter
import es.monsteraltech.skincare_tfm.body.mole.MoleDetailActivity
import es.monsteraltech.skincare_tfm.camera.CameraActivity
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import kotlinx.coroutines.launch

class BodyPartActivity : ComponentActivity() {

    private lateinit var lunarRecyclerView: RecyclerView
    private lateinit var moleAdapter: MoleAdapter
    private lateinit var addButton: FloatingActionButton
    private lateinit var bodyPart: String
    private lateinit var bodyPartTitleTextView: TextView
    private lateinit var searchView: SearchView
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

        bodyPartTitleTextView = findViewById(R.id.bodyPartTitle)
        bodyPartTitleTextView.text = bodyPart

        lunarRecyclerView = findViewById(R.id.lunarRecyclerView)
        addButton = findViewById(R.id.addButton)
        searchView = findViewById(R.id.moleSearch)

        setupRecyclerView()
        loadMolesFromFirebase(color)

        addButton.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            // Pasamos el código de color para que se pueda usar luego en el flujo
            intent.putExtra("BODY_PART_COLOR", color)
            imageAnalysisLauncher.launch(intent)
        }

        setupSearchView()
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
                            imageUrl = fbMole.imageUrl,  // Ahora es una ruta local
                            analysisResult = fbMole.analysisResult
                        )
                    )
                }

                runOnUiThread {
                    if (moleList.isEmpty()) {
                        Toast.makeText(this@BodyPartActivity, "No hay lunares registrados en esta parte del cuerpo", Toast.LENGTH_SHORT).show()
                    }
                    moleAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@BodyPartActivity, "Error al cargar lunares: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSearchView() {
        searchView.isIconified = false
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                moleAdapter.filter.filter(newText)
                return false
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Recargar datos cuando volvamos a esta actividad
        val color = intent.getStringExtra("COLOR_VALUE")
        if (color != null) {
            loadMolesFromFirebase(color)
        }
    }
}
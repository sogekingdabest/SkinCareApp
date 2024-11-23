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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.Mole
import es.monsteraltech.skincare_tfm.body.mole.MoleAdapter
import es.monsteraltech.skincare_tfm.body.mole.MoleDetailActivity
import es.monsteraltech.skincare_tfm.camera.CameraActivity


class BodyPartActivity : ComponentActivity() {

    private lateinit var lunarRecyclerView: RecyclerView
    private lateinit var moleAdapter: MoleAdapter
    private lateinit var addButton: FloatingActionButton
    private lateinit var bodyPart: String
    private lateinit var bodyPartTitleTextView: TextView

    private val imageAnalysisLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri = result.data?.getParcelableExtra<Uri>("selectedImage")
            if (imageUri != null) {
                // analyzeImage(imageUri)
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
                Toast.makeText(this, "Área Negra", Toast.LENGTH_SHORT).show()
                bodyPart = "Cabeza"

            }
            "#FFED1C24" -> {
                Toast.makeText(this, "Área Roja", Toast.LENGTH_SHORT).show()
                bodyPart = "Brazo derecho"
            }
            "#FFFFC90E" -> {
                Toast.makeText(this, "Área Amarilla", Toast.LENGTH_SHORT).show()
                bodyPart = "Torso"
            }
            "#FF22B14C" -> {
                Toast.makeText(this, "Área Verde", Toast.LENGTH_SHORT).show()
                bodyPart = "Brazo izquierdo"
            }
            "#FF3F48CC" -> {
                Toast.makeText(this, "Área Azul", Toast.LENGTH_SHORT).show()
                bodyPart = "Pierna derecha"
            }
            "#FFED00FF" -> {
                Toast.makeText(this, "Área Morada", Toast.LENGTH_SHORT).show()
                bodyPart = "Pierna izquierda"
            }
            else -> {
                Toast.makeText(this, "Área Desconocida", Toast.LENGTH_SHORT).show()
            }
        }

        bodyPartTitleTextView = findViewById(R.id.bodyPartTitle)
        bodyPartTitleTextView.text = bodyPart

        // Mostrar el valor del color en un TextView (o personalizar la UI según tus necesidades)
        //val colorTextView: TextView = findViewById(R.id.colorTextView)
        // colorTextView.text = String.format("Color seleccionado: $color ")

        lunarRecyclerView = findViewById(R.id.lunarRecyclerView)
        addButton = findViewById(R.id.addButton)

        // Lista de ejemplo
        val moleLists = listOf(
            Mole("Lunar 1", "Descripción 1", listOf(R.drawable.cat, R.drawable.cat)),
            Mole("Lunar 2", "Descripción 2",listOf(R.drawable.cat)),
            Mole("Lunaar 3", "Descripción 3", listOf(R.drawable.cat))
        )

        lunarRecyclerView.layoutManager = GridLayoutManager(this, 1)
        moleAdapter = MoleAdapter(moleLists) { lunar ->
            // Aquí puedes abrir una nueva actividad con más detalles del lunar
            val intent = Intent(this, MoleDetailActivity::class.java)
            intent.putExtra("LUNAR_TITLE", lunar.title)
            intent.putExtra("LUNAR_DESCRIPTION", lunar.description)
            intent.putIntegerArrayListExtra("LUNAR_IMAGE_LIST", ArrayList(lunar.imageList))
            startActivity(intent)
        }
        lunarRecyclerView.adapter = moleAdapter

        lunarRecyclerView.itemAnimator?.apply {
            addDuration = 250L   // Duración de la animación de adición
            removeDuration = 250L // Duración de la animación de eliminación
            changeDuration = 250L // Duración de la animación de cambio
        }

        addButton.setOnClickListener {
            // Lógica para agregar un nuevo lunar
            // Puedes abrir una nueva actividad para capturar una imagen y añadir un nuevo lunar
            /*val intent = Intent(this, AddLunarActivity::class.java)
            startActivity(intent)*/
        }

        addButton.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            imageAnalysisLauncher.launch(intent)
        }

        val searchView: SearchView = findViewById(R.id.moleSearch)
        searchView.isIconified = false
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                moleAdapter.filter.filter(newText) // Aplicar el filtro
                return false
            }
        })

    }

}
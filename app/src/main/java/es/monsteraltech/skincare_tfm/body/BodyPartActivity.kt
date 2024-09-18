package es.monsteraltech.skincare_tfm.body

import android.os.Bundle
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import es.monsteraltech.skincare_tfm.R


class BodyPartActivity : ComponentActivity() {

    private lateinit var lunarRecyclerView: RecyclerView
    private lateinit var moleAdapter: MoleAdapter
    private lateinit var addButton: FloatingActionButton
    private lateinit var bodyPart: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_body)

        // Recibe el color desde el Intent
        val color = intent.getStringExtra("COLOR_VALUE")

        when (color) {
            "#FF000000" -> {
                Toast.makeText(this, "Área Negra", Toast.LENGTH_SHORT).show()
                bodyPart = "head"

            }
            "#FFED1C24" -> {
                Toast.makeText(this, "Área Roja", Toast.LENGTH_SHORT).show()
                bodyPart = "right_arm"
            }
            "#FFFFC90E" -> {
                Toast.makeText(this, "Área Amarilla", Toast.LENGTH_SHORT).show()
                bodyPart = "torso"
            }
            "#FF22B14C" -> {
                Toast.makeText(this, "Área Verde", Toast.LENGTH_SHORT).show()
                bodyPart = "left_arm"
            }
            "#FF3F48CC" -> {
                Toast.makeText(this, "Área Azul", Toast.LENGTH_SHORT).show()
                bodyPart = "right_leg"
            }
            "#FFED00FF" -> {
                Toast.makeText(this, "Área Morada", Toast.LENGTH_SHORT).show()
                bodyPart = "left_leg"
            }
            else -> {
                Toast.makeText(this, "Área Desconocida", Toast.LENGTH_SHORT).show()
            }
        }

        // Mostrar el valor del color en un TextView (o personalizar la UI según tus necesidades)
        //val colorTextView: TextView = findViewById(R.id.colorTextView)
        // colorTextView.text = String.format("Color seleccionado: $color ")

        lunarRecyclerView = findViewById(R.id.lunarRecyclerView)
        addButton = findViewById(R.id.addButton)

        // Lista de ejemplo
        val moleLists = listOf(
            Mole("Lunar 1", "Descripción 1", R.drawable.cat),
            Mole("Lunar 2", "Descripción 2", R.drawable.cat),
            Mole("Lunar 3", "Descripción 3", R.drawable.cat)
        )

        lunarRecyclerView.layoutManager = GridLayoutManager(this, 1)
        moleAdapter = MoleAdapter(moleLists) { lunar ->
            // Aquí puedes abrir una nueva actividad con más detalles del lunar
            /*val intent = Intent(this, LunarDetailActivity::class.java)
            intent.putExtra("lunar", lunar)
            startActivity(intent)*/
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
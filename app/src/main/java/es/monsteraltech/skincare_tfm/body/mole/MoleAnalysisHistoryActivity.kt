package es.monsteraltech.skincare_tfm.body.mole

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
import es.monsteraltech.skincare_tfm.body.mole.adapter.AnalysisAdapter
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import es.monsteraltech.skincare_tfm.body.mole.repository.MoleRepository
import es.monsteraltech.skincare_tfm.body.mole.service.MoleAnalysisService
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import es.monsteraltech.skincare_tfm.databinding.ActivityAnalysisHistoryBinding
import kotlinx.coroutines.launch
import java.io.File

class MoleAnalysisHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisHistoryBinding
    private lateinit var adapter: AnalysisAdapter

    private val moleRepository = MoleRepository()
    private val analysisService = MoleAnalysisService()
    private val firebaseDataManager = FirebaseDataManager()
    private val auth = FirebaseAuth.getInstance()

    private var moleId: String = ""
    private var moleData: MoleData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar la toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Obtener el ID del lunar
        moleId = intent.getStringExtra("MOLE_ID") ?: ""
        if (moleId.isEmpty()) {
            Toast.makeText(this, "Error: No se encontró el ID del lunar", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Configurar RecyclerView
        setupRecyclerView()

        // Cargar datos
        loadMoleData()
        loadAnalysisHistory()
    }

    private fun setupRecyclerView() {
        adapter = AnalysisAdapter(emptyList()) { analysis ->
            // Aquí podrías mostrar un diálogo con detalles adicionales si lo deseas
            Toast.makeText(this, "Análisis del ${analysis.analysisDate.toDate()}", Toast.LENGTH_SHORT).show()
        }

        binding.analysisRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MoleAnalysisHistoryActivity)
            adapter = this@MoleAnalysisHistoryActivity.adapter
        }
    }

    private fun loadMoleData() {
        binding.progressBar.visibility = View.VISIBLE

        // Verificar que el usuario está autenticado
        val currentUser = auth.currentUser
        if (currentUser == null) {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                // Obtener datos del lunar usando la nueva estructura
                val mole = moleRepository.getMoleById(currentUser.uid, moleId)

                if (mole.isSuccess) {
                    moleData = mole.getOrNull()

                    moleData?.let { mole ->
                        // Mostrar título
                        binding.moleTitleText.text = mole.title

                        // Cargar imagen - ahora considerando que puede ser una ruta local
                        if (mole.imageUrl.isNotEmpty()) {
                            if (mole.imageUrl.startsWith("http")) {
                                // Es una URL remota (caso legacy)
                                Glide.with(this@MoleAnalysisHistoryActivity)
                                    .load(mole.imageUrl)
                                    .placeholder(R.drawable.ic_launcher_background)
                                    .into(binding.moleImageView)
                            } else {
                                // Es una ruta local
                                val fullPath = firebaseDataManager.getFullImagePath(
                                    this@MoleAnalysisHistoryActivity,
                                    mole.imageUrl
                                )
                                Glide.with(this@MoleAnalysisHistoryActivity)
                                    .load(File(fullPath))
                                    .placeholder(R.drawable.ic_launcher_background)
                                    .into(binding.moleImageView)
                            }
                        }
                    }
                } else {
                    Toast.makeText(
                        this@MoleAnalysisHistoryActivity,
                        "Error al cargar datos del lunar: ${mole.exceptionOrNull()?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@MoleAnalysisHistoryActivity,
                    "Error al cargar datos del lunar: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadAnalysisHistory() {
        // Verificar que el usuario está autenticado
        val currentUser = auth.currentUser
        if (currentUser == null) {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                // Obtener historial de análisis usando la nueva estructura anidada
                val result = analysisService.getMoleAnalysisHistory(moleId)

                binding.progressBar.visibility = View.GONE

                if (result.isSuccess) {
                    val analysisList = result.getOrNull() ?: emptyList()

                    if (analysisList.isEmpty()) {
                        binding.emptyView.visibility = View.VISIBLE
                        binding.analysisRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyView.visibility = View.GONE
                        binding.analysisRecyclerView.visibility = View.VISIBLE
                        adapter.updateData(analysisList)
                    }
                } else {
                    Toast.makeText(
                        this@MoleAnalysisHistoryActivity,
                        "Error al cargar historial: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    binding.emptyView.visibility = View.VISIBLE
                    binding.analysisRecyclerView.visibility = View.GONE
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this@MoleAnalysisHistoryActivity,
                    "Error al cargar historial: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()

                binding.emptyView.visibility = View.VISIBLE
                binding.analysisRecyclerView.visibility = View.GONE
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
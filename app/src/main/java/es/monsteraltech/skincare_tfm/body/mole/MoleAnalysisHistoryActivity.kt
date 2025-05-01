package es.monsteraltech.skincare_tfm.body.mole

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.adapter.AnalysisAdapter
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import es.monsteraltech.skincare_tfm.body.mole.repository.MoleRepository
import es.monsteraltech.skincare_tfm.body.mole.service.MoleAnalysisService
import es.monsteraltech.skincare_tfm.databinding.ActivityAnalysisHistoryBinding
import kotlinx.coroutines.launch

class MoleAnalysisHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisHistoryBinding
    private lateinit var adapter: AnalysisAdapter

    private val moleRepository = MoleRepository()
    private val analysisService = MoleAnalysisService()

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

        lifecycleScope.launch {
            try {
                // Obtener datos del lunar
                val mole = moleRepository.getMoleById(moleId)

                if (mole.isSuccess) {
                    moleData = mole.getOrNull()

                    moleData?.let { mole ->
                        // Mostrar título
                        binding.moleTitleText.text = mole.title

                        // Cargar imagen
                        if (mole.imageUrl.isNotEmpty()) {
                            Glide.with(this@MoleAnalysisHistoryActivity)
                                .load(mole.imageUrl)
                                .placeholder(R.drawable.ic_launcher_background)
                                .into(binding.moleImageView)
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
        lifecycleScope.launch {
            try {
                // Obtener historial de análisis
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
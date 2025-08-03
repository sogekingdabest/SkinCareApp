package es.monsteraltech.skincare_tfm.body.mole.dialog

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.error.RetryManager
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import es.monsteraltech.skincare_tfm.body.mole.repository.MoleRepository
import es.monsteraltech.skincare_tfm.databinding.DialogMoleSelectorBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Diálogo modal para seleccionar lunares existentes durante el proceso de guardado de análisis.
 * Permite buscar lunares por nombre y ofrece la opción de crear un nuevo lunar.
 */
class MoleSelectorDialog : DialogFragment() {

    private var _binding: DialogMoleSelectorBinding? = null
    private val binding get() = _binding!!

    private lateinit var moleAdapter: MoleSelectorAdapter
    private val moleRepository = MoleRepository()
    private val auth = FirebaseAuth.getInstance()

    private var allMoles: List<MoleData> = emptyList()
    private var filteredMoles: List<MoleData> = emptyList()
    private var searchJob: Job? = null
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // Callback para notificar la selección
    private var onMoleSelectedListener: ((MoleData?) -> Unit)? = null

    companion object {
        const val TAG = "MoleSelectorDialog"

        fun newInstance(): MoleSelectorDialog {
            return MoleSelectorDialog()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMoleSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        loadMoles()
    }

    private fun setupUI() {
        // Configurar RecyclerView
        moleAdapter = MoleSelectorAdapter { selectedMole ->
            onMoleSelectedListener?.invoke(selectedMole)
            dismiss()
        }

        binding.molesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = moleAdapter
        }

        // Configurar búsqueda en tiempo real con debounce para optimizar rendimiento
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performDebouncedSearch(s?.toString() ?: "")
            }
        })

        // Configurar botones
        binding.createNewMoleButton.setOnClickListener {
            onMoleSelectedListener?.invoke(null) // null indica crear nuevo lunar
            dismiss()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        // Configurar botón de limpiar búsqueda
        binding.clearSearchButton.setOnClickListener {
            binding.searchEditText.text?.clear()
            binding.searchEditText.clearFocus()
        }

        // Configurar título y descripción
        binding.titleText.text = getString(R.string.mole_selector_title)
        binding.descriptionText.text = getString(R.string.mole_selector_description)
        
        // Configurar sugerencias de búsqueda
        setupSearchSuggestions()
    }

    private fun loadMoles() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showError(getString(R.string.mole_selector_user_not_authenticated))
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.molesRecyclerView.visibility = View.GONE

        lifecycleScope.launch {
            val retryManager = RetryManager()
            
            val retryResult = retryManager.executeWithRetry(
                operation = "Cargar lunares del usuario",
                config = RetryManager.databaseConfig(),
                onRetryAttempt = { attempt, _ ->
                    requireActivity().runOnUiThread {
                        binding.progressBar.visibility = View.VISIBLE
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.retry_attempting, attempt, es.monsteraltech.skincare_tfm.body.mole.error.RetryManager.databaseConfig().maxAttempts),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                moleRepository.getAllMolesForUser(currentUser.uid)
            }
            
            binding.progressBar.visibility = View.GONE
            
            if (retryResult.result.isSuccess) {
                allMoles = retryResult.result.getOrNull() ?: emptyList()
                filteredMoles = allMoles
                updateUI()
                
                if (retryResult.attemptsMade > 1) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.retry_success_after_attempts, retryResult.attemptsMade),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                val exception = retryResult.result.exceptionOrNull() ?: Exception("Error desconocido")
                val errorResult = es.monsteraltech.skincare_tfm.body.mole.error.ErrorHandler.handleError(
                    requireContext(), 
                    exception, 
                    "Cargar lunares"
                )
                
                showError(errorResult.userMessage)
                
                if (retryResult.attemptsMade > 1) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.retry_failed_all_attempts, retryResult.attemptsMade),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            
            binding.molesRecyclerView.visibility = View.VISIBLE
        }
    }

    /**
     * Realiza búsqueda con debounce para optimizar rendimiento en listas grandes
     */
    private fun performDebouncedSearch(query: String) {
        // Cancelar búsqueda anterior si existe
        searchJob?.cancel()
        
        searchJob = lifecycleScope.launch {
            // Debounce de 300ms para evitar búsquedas excesivas
            delay(300)
            filterMoles(query)
        }
    }

    /**
     * Filtra lunares por nombre, parte del cuerpo, descripción y fechas
     * Optimizado para listas grandes con búsqueda eficiente
     */
    private fun filterMoles(query: String) {
        filteredMoles = if (query.isEmpty()) {
            allMoles
        } else {
            val searchTerms = query.lowercase().split(" ").filter { it.isNotBlank() }
            
            allMoles.filter { mole ->
                searchTerms.all { term ->
                    // Búsqueda por nombre
                    mole.title.lowercase().contains(term) ||
                    // Búsqueda por parte del cuerpo
                    mole.bodyPart.lowercase().contains(term) ||
                    // Búsqueda por descripción
                    mole.description.lowercase().contains(term) ||
                    // Búsqueda por fecha de creación
                    dateFormat.format(mole.createdAt.toDate()).contains(term) ||
                    // Búsqueda por fecha de último análisis
                    (mole.lastAnalysisDate?.let { 
                        dateFormat.format(it.toDate()).contains(term) 
                    } ?: false) ||
                    // Búsqueda por número de análisis
                    mole.analysisCount.toString().contains(term)
                }
            }
        }
        updateUI()
    }

    private fun updateUI() {
        val searchQuery = binding.searchEditText.text?.toString() ?: ""
        
        // Actualizar query de búsqueda en el adapter para resaltado
        moleAdapter.updateSearchQuery(searchQuery)
        
        // Actualizar lista con animación suave
        moleAdapter.submitList(filteredMoles.toList()) // Crear nueva lista para trigger DiffUtil
        
        // Mostrar indicadores visuales apropiados
        updateEmptyState()
        updateResultCount()
        updateSearchIndicators()
    }

    /**
     * Actualiza el estado vacío con mensajes contextuales
     */
    private fun updateEmptyState() {
        val searchQuery = binding.searchEditText.text?.toString() ?: ""
        
        if (filteredMoles.isEmpty()) {
            if (allMoles.isEmpty()) {
                // No hay lunares en absoluto
                binding.emptyStateText.text = getString(R.string.mole_selector_no_moles)
                binding.emptyStateIcon.setImageResource(R.drawable.ic_add_circle_outline)
                binding.emptyStateIcon.visibility = View.VISIBLE
            } else {
                // Hay lunares pero no coinciden con la búsqueda
                binding.emptyStateText.text = if (searchQuery.isNotEmpty()) {
                    getString(R.string.mole_selector_no_search_results, searchQuery)
                } else {
                    getString(R.string.mole_selector_no_results)
                }
                binding.emptyStateIcon.setImageResource(R.drawable.ic_search_off)
                binding.emptyStateIcon.visibility = View.VISIBLE
            }
            binding.emptyStateContainer.visibility = View.VISIBLE
            binding.searchSuggestions.visibility = if (searchQuery.isNotEmpty() && allMoles.isNotEmpty()) View.VISIBLE else View.GONE
        } else {
            binding.emptyStateContainer.visibility = View.GONE
        }
    }

    /**
     * Actualiza el contador de resultados con información contextual
     */
    private fun updateResultCount() {
        val searchQuery = binding.searchEditText.text?.toString() ?: ""
        
        binding.resultCountText.text = if (searchQuery.isNotEmpty()) {
            getString(R.string.mole_selector_search_results_count, filteredMoles.size, allMoles.size)
        } else {
            getString(R.string.mole_selector_results_count, filteredMoles.size)
        }
        
        // Cambiar color del contador según el contexto
        binding.resultCountText.setTextColor(
            if (filteredMoles.isEmpty() && searchQuery.isNotEmpty()) {
                requireContext().getColor(android.R.color.holo_orange_dark)
            } else {
                requireContext().getColor(android.R.color.darker_gray)
            }
        )
    }

    /**
     * Actualiza indicadores visuales de búsqueda
     */
    private fun updateSearchIndicators() {
        val searchQuery = binding.searchEditText.text?.toString() ?: ""
        
        // Mostrar indicador de búsqueda activa
        if (searchQuery.isNotEmpty()) {
            binding.searchActiveIndicator.visibility = View.VISIBLE
            binding.clearSearchButton.visibility = View.VISIBLE
        } else {
            binding.searchActiveIndicator.visibility = View.GONE
            binding.clearSearchButton.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    fun setOnMoleSelectedListener(listener: (MoleData?) -> Unit) {
        onMoleSelectedListener = listener
    }

    /**
     * Configura sugerencias de búsqueda basadas en los datos disponibles
     */
    private fun setupSearchSuggestions() {
        binding.searchSuggestions.text = getString(R.string.mole_selector_search_suggestions)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancelar búsqueda pendiente
        searchJob?.cancel()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        // Configurar tamaño del diálogo
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.8).toInt()
        )
    }
}
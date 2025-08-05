package es.monsteraltech.skincare_tfm.body.mole.dialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
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
        moleAdapter = MoleSelectorAdapter { selectedMole ->
            onMoleSelectedListener?.invoke(selectedMole)
            dismiss()
        }
        binding.molesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = moleAdapter
        }
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performDebouncedSearch(s?.toString() ?: "")
            }
        })
        binding.createNewMoleButton.setOnClickListener {
            onMoleSelectedListener?.invoke(null)
            dismiss()
        }
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
        binding.clearSearchButton.setOnClickListener {
            clearSearch()
        }
        binding.titleText.text = getString(R.string.mole_selector_title)
        binding.descriptionText.text = getString(R.string.mole_selector_description)
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
    private fun performDebouncedSearch(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            try {
                delay(300)
                if (isAdded && !isDetached) {
                    filterMoles(query)
                }
            } catch (e: Exception) {
            }
        }
    }
    private fun filterMoles(query: String) {
        try {
            filteredMoles = if (query.isEmpty()) {
                allMoles
            } else {
                val searchTerms = query.lowercase().trim().split(" ").filter { it.isNotBlank() }
                allMoles.filter { mole ->
                    searchTerms.any { term ->
                        mole.title.lowercase().contains(term) ||
                        mole.bodyPart.lowercase().contains(term) ||
                        mole.description.lowercase().contains(term) ||
                        try {
                            dateFormat.format(mole.createdAt.toDate()).contains(term)
                        } catch (e: Exception) {
                            false
                        } ||
                        try {
                            mole.lastAnalysisDate?.let {
                                dateFormat.format(it.toDate()).contains(term)
                            } ?: false
                        } catch (e: Exception) {
                            false
                        } ||
                        mole.analysisCount.toString().contains(term)
                    }
                }
            }
            requireActivity().runOnUiThread {
                updateUI()
            }
        } catch (e: Exception) {
            filteredMoles = allMoles
            requireActivity().runOnUiThread {
                updateUI()
            }
        }
    }
    private fun updateUI() {
        val searchQuery = binding.searchEditText.text?.toString() ?: ""
        moleAdapter.updateSearchQuery(searchQuery)
        moleAdapter.submitList(filteredMoles.toList())
        if (filteredMoles.isEmpty()) {
            binding.molesRecyclerView.visibility = View.GONE
            binding.emptyStateContainer.visibility = View.VISIBLE
        } else {
            binding.molesRecyclerView.visibility = View.VISIBLE
            binding.emptyStateContainer.visibility = View.GONE
        }
        updateEmptyState()
        updateResultCount()
        updateSearchIndicators()
    }
    private fun updateEmptyState() {
        val searchQuery = binding.searchEditText.text?.toString()?.trim() ?: ""
        if (filteredMoles.isEmpty()) {
            binding.emptyStateContainer.visibility = View.VISIBLE
            if (allMoles.isEmpty()) {
                binding.emptyStateText.text = getString(R.string.mole_selector_no_moles)
                try {
                    binding.emptyStateIcon.setImageResource(R.drawable.ic_add_circle_outline)
                    binding.emptyStateIcon.visibility = View.VISIBLE
                } catch (e: Exception) {
                    binding.emptyStateIcon.visibility = View.GONE
                }
                binding.searchSuggestions.visibility = View.GONE
            } else {
                binding.emptyStateText.text = if (searchQuery.isNotEmpty()) {
                    getString(R.string.mole_selector_no_search_results, searchQuery)
                } else {
                    getString(R.string.mole_selector_no_results)
                }
                try {
                    binding.emptyStateIcon.setImageResource(R.drawable.ic_search_off)
                    binding.emptyStateIcon.visibility = View.VISIBLE
                } catch (e: Exception) {
                    binding.emptyStateIcon.visibility = View.GONE
                }
                binding.searchSuggestions.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
            }
        } else {
            binding.emptyStateContainer.visibility = View.GONE
        }
    }
    private fun updateResultCount() {
        val searchQuery = binding.searchEditText.text?.toString() ?: ""
        binding.resultCountText.text = if (searchQuery.isNotEmpty()) {
            getString(R.string.mole_selector_search_results_count, filteredMoles.size, allMoles.size)
        } else {
            getString(R.string.mole_selector_results_count, filteredMoles.size)
        }
        binding.resultCountText.setTextColor(
            if (filteredMoles.isEmpty() && searchQuery.isNotEmpty()) {
                requireContext().getColor(android.R.color.holo_orange_dark)
            } else {
                requireContext().getColor(android.R.color.darker_gray)
            }
        )
    }
    private fun updateSearchIndicators() {
        val searchQuery = binding.searchEditText.text?.toString()?.trim() ?: ""
        if (searchQuery.isNotEmpty()) {
            binding.searchActiveIndicator.visibility = View.VISIBLE
        } else {
            binding.searchActiveIndicator.visibility = View.GONE
        }
    }
    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
    fun setOnMoleSelectedListener(listener: (MoleData?) -> Unit) {
        onMoleSelectedListener = listener
    }
    private fun setupSearchSuggestions() {
        binding.searchSuggestions.text = getString(R.string.mole_selector_search_suggestions)
    }
    private fun clearSearch() {
        binding.searchEditText.text?.clear()
        binding.searchEditText.clearFocus()
        try {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
        } catch (e: Exception) {
        }
        filteredMoles = allMoles
        updateUI()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.8).toInt()
        )
    }
}
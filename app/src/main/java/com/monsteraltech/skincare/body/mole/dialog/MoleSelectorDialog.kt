package com.monsteraltech.skincare.body.mole.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.body.mole.MoleListViewModel
import com.monsteraltech.skincare.body.mole.domain.model.MoleListEntry
import com.monsteraltech.skincare.databinding.DialogMoleSelectorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MoleSelectorDialog : DialogFragment() {
    private var _binding: DialogMoleSelectorBinding? = null
    private val binding get() = _binding!!
    private val viewModel by lazy { ViewModelProvider(this)[MoleListViewModel::class.java] }
    private lateinit var moleAdapter: MoleSelectorAdapter
    private var onMoleSelectedListener: ((MoleListEntry?) -> Unit)? = null
    companion object {
        const val TAG = "MoleSelectorDialog"
        fun newInstance() = MoleSelectorDialog()
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = super.onCreateDialog(savedInstanceState).also {
        it.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogMoleSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        moleAdapter = MoleSelectorAdapter { selected -> onMoleSelectedListener?.invoke(selected); dismiss() }
        binding.molesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.molesRecyclerView.adapter = moleAdapter
        binding.searchEditText.setText(viewModel.query)
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { viewModel.search(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        binding.createNewMoleButton.setOnClickListener { onMoleSelectedListener?.invoke(null); dismiss() }
        binding.cancelButton.setOnClickListener { dismiss() }
        binding.clearSearchButton.setOnClickListener { binding.searchEditText.text?.clear() }
        binding.loadMoreButton.setOnClickListener {
            if (viewModel.uiState.value is MoleListViewModel.UiState.Failed) viewModel.retry() else viewModel.loadMore()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.start(selector = true)
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state is MoleListViewModel.UiState.Loading) View.VISIBLE else View.GONE
                    when (state) {
                        MoleListViewModel.UiState.Loading -> {
                            binding.molesRecyclerView.visibility = View.GONE
                            binding.emptyStateContainer.visibility = View.GONE
                            binding.loadMoreButton.visibility = View.GONE
                        }
                        is MoleListViewModel.UiState.Ready -> {
                            val page = state.page
                            moleAdapter.updateSearchQuery(state.query)
                            moleAdapter.submitList(page.items)
                            binding.molesRecyclerView.visibility = if (page.items.isEmpty()) View.GONE else View.VISIBLE
                            binding.emptyStateContainer.visibility = if (page.items.isEmpty()) View.VISIBLE else View.GONE
                            binding.emptyStateText.text = if (page.totalCount == 0) getString(R.string.mole_selector_no_moles)
                                else getString(R.string.mole_selector_no_search_results, state.query)
                            binding.resultCountText.text = if (state.query.isBlank()) getString(R.string.mole_selector_results_count, page.matchingCount)
                                else getString(R.string.mole_selector_search_results_count, page.matchingCount, page.totalCount)
                            binding.searchActiveIndicator.visibility = if (state.query.isBlank()) View.GONE else View.VISIBLE
                            binding.clearSearchButton.visibility = if (state.query.isBlank()) View.GONE else View.VISIBLE
                            binding.loadMoreButton.visibility = if (page.hasMore) View.VISIBLE else View.GONE
                            binding.loadMoreButton.text = getString(R.string.list_load_more, page.items.size, page.matchingCount)
                        }
                        is MoleListViewModel.UiState.Failed -> {
                            binding.molesRecyclerView.visibility = View.GONE
                            binding.emptyStateContainer.visibility = View.VISIBLE
                            binding.emptyStateText.setText(if (state.authentication) R.string.error_authentication else R.string.list_load_error)
                            binding.loadMoreButton.visibility = View.VISIBLE
                            binding.loadMoreButton.setText(R.string.list_retry)
                        }
                    }
                }
            }
        }
    }
    fun setOnMoleSelectedListener(listener: (MoleListEntry?) -> Unit) { onMoleSelectedListener = listener }
    override fun onDestroyView() {
        binding.molesRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), (resources.displayMetrics.heightPixels * 0.8).toInt())
    }
}

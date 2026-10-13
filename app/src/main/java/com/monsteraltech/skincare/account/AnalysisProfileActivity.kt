package com.monsteraltech.skincare.account

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.analysis.model.AnalysisSex
import com.monsteraltech.skincare.databinding.ActivityAnalysisProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AnalysisProfileActivity : AppCompatActivity() {
    private val viewModel: AnalysisProfileViewModel by viewModels()
    private lateinit var binding: ActivityAnalysisProfileBinding
    private val choices = listOf(null, AnalysisSex.MALE, AnalysisSex.FEMALE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = getString(R.string.analysis_profile_title)
        val names = resources.getStringArray(R.array.analysis_sex_choices)
        binding.sexInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, names))
        binding.sexInput.setOnItemClickListener { _, _, position, _ -> viewModel.setSex(choices[position]) }
        binding.ageInput.doAfterTextChanged { viewModel.setAge(it?.toString().orEmpty()) }
        binding.saveButton.setOnClickListener { viewModel.save() }
        binding.clearButton.setOnClickListener { viewModel.clear() }
        binding.cancelButton.setOnClickListener { finish() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (state.saved) { setResult(RESULT_OK); finish(); return@collect }
                    if (binding.ageInput.text.toString() != state.ageText) binding.ageInput.setText(state.ageText)
                    binding.sexInput.setText(names[choices.indexOf(state.sex)], false)
                    val enabled = state.canEdit && !state.saving
                    binding.ageLayout.isEnabled = enabled
                    binding.sexLayout.isEnabled = enabled
                    binding.saveButton.isEnabled = enabled
                    binding.clearButton.isEnabled = enabled
                    binding.progress.isVisible = state.loading || state.saving
                    binding.reviewAgeText.isVisible = state.ageNeedsReview
                    binding.errorText.isVisible = state.error != null
                    state.error?.let { binding.errorText.setText(it) }
                }
            }
        }
    }
}

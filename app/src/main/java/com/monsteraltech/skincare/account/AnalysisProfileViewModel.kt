package com.monsteraltech.skincare.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.analysis.model.AnalysisSex
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.Year
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AnalysisProfileViewModel @Inject constructor(
    private val repository: AccountProfileRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    data class UiState(
        val ageText: String = "",
        val sex: AnalysisSex? = null,
        val loading: Boolean = true,
        val saving: Boolean = false,
        val saved: Boolean = false,
        val canEdit: Boolean = false,
        val ageNeedsReview: Boolean = false,
        val error: Int? = null
    )
    private val owner = savedState.get<String>("owner") ?: repository.getCurrentUserId().also { savedState["owner"] = it }
    private val _state = MutableStateFlow(UiState(saved = savedState["saved"] ?: false))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                if (owner == null || repository.getCurrentUserId() != owner) {
                    _state.value = UiState(loading = false, error = R.string.error_authentication)
                    return@launch
                }
                val settings = repository.getUserSettings().getDataOrNull()
                if (settings == null || repository.getCurrentUserId() != owner) {
                    _state.value = UiState(loading = false, error = R.string.settings_error)
                    return@launch
                }
                val profile = settings.analysisProfile ?: AnalysisProfile()
                val current = profile.forCapture(Year.now().value)
                val sex = if (savedState.contains("sex")) savedState.get<String>("sex")?.let { name -> AnalysisSex.entries.firstOrNull { it.name == name } } else current.sex
                _state.update { it.copy(ageText = savedState["age"] ?: current.ageYears?.toString().orEmpty(),
                    sex = sex, loading = false, canEdit = true,
                    ageNeedsReview = profile.ageYears != null && current.ageYears == null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(loading = false, error = R.string.settings_error) } }
        }
    }

    fun setAge(text: String) {
        savedState["age"] = text
        _state.update { it.copy(ageText = text, error = null) }
    }
    fun setSex(sex: AnalysisSex?) {
        savedState["sex"] = sex?.name
        _state.update { it.copy(sex = sex, error = null) }
    }
    fun clear() { setAge(""); setSex(null) }

    fun save(year: Int = Year.now().value) {
        val draft = _state.value
        if (!draft.canEdit || draft.saving || draft.saved) return
        val text = draft.ageText.trim()
        val age = text.toIntOrNull()
        if ((text.isNotEmpty() && (age == null || age !in 0..120)) || year !in 1900..9999) {
            _state.update { it.copy(error = R.string.analysis_profile_invalid_age) }
            return
        }
        if (owner == null || repository.getCurrentUserId() != owner) {
            _state.update { it.copy(error = R.string.error_authentication) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val profile = AnalysisProfile(age, year.takeIf { age != null }, draft.sex)
                val result = repository.applySettingsPatch(AccountSettingsPatch(analysisProfile = profile), owner)
                if (result.getDataOrNull() != null && repository.getCurrentUserId() == owner) {
                    savedState["saved"] = true
                    _state.update { it.copy(saved = true) }
                } else _state.update { it.copy(error = R.string.settings_error) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(error = R.string.settings_error) } }
            finally { _state.update { it.copy(saving = false) } }
        }
    }
}

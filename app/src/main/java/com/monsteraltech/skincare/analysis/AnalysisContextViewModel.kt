package com.monsteraltech.skincare.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.skincare.account.AccountSettingsRepository
import com.monsteraltech.skincare.account.AnalysisProfile
import com.monsteraltech.skincare.analysis.model.AnalysisSex
import com.monsteraltech.skincare.analysis.model.AnatomicalSite
import com.monsteraltech.skincare.analysis.model.LesionMetadata
import com.monsteraltech.skincare.data.identity.ActiveProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Year
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Optional capture annotations; current image-only inference does not consume this draft. */
@HiltViewModel
class AnalysisContextViewModel @Inject constructor(
    private val settings: AccountSettingsRepository,
    private val activeProfile: ActiveProfile,
    private val saved: SavedStateHandle
) : ViewModel() {
    data class Draft(val ageText: String = "", val sex: AnalysisSex? = null, val site: AnatomicalSite? = null)
    private val owner = saved.get<String>("contextOwner") ?: activeProfile.ownerId.also { saved["contextOwner"] = it }
    private fun restore() = Draft(saved["contextAge"] ?: "",
        saved.get<String>("contextSex")?.let { n -> AnalysisSex.entries.firstOrNull { it.name == n } },
        saved.get<String>("contextSite")?.let { n -> AnatomicalSite.entries.firstOrNull { it.name == n } })
    private val _draft = MutableStateFlow(if (owner == activeProfile.ownerId) restore() else Draft())
    val draft = _draft.asStateFlow()
    val hasValidOwner: Boolean get() = owner != null && owner == activeProfile.ownerId

    init {
        if (owner != null && owner == activeProfile.ownerId) viewModelScope.launch {
            try {
                val profile = settings.getUserSettings(owner).analysisProfile ?: AnalysisProfile()
                if (owner != activeProfile.ownerId) return@launch
                val defaults = profile.forCapture(Year.now().value)
                if (!saved.contains("contextAge")) saved["contextAge"] = defaults.ageYears?.toString().orEmpty()
                if (!saved.contains("contextSex")) saved["contextSex"] = defaults.sex?.name
                _draft.value = restore()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { /* Optional defaults unavailable; the user may enter or omit them. */ }
        }
    }
    fun setAge(text: String) { saved["contextAge"] = text; _draft.update { it.copy(ageText = text) } }
    fun setSex(sex: AnalysisSex?) { saved["contextSex"] = sex?.name; _draft.update { it.copy(sex = sex) } }
    fun setSite(site: AnatomicalSite?) { saved["contextSite"] = site?.name; _draft.update { it.copy(site = site) } }
    fun context(): LesionMetadata? {
        if (owner == null || owner != activeProfile.ownerId) return null
        val current = _draft.value
        val text = current.ageText.trim()
        val age = text.toIntOrNull()
        if (text.isNotEmpty() && (age == null || age !in 0..120)) return null
        return LesionMetadata(age, current.sex, current.site)
    }
}

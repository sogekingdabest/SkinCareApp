package com.monsteraltech.skincare.body.mole

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.*
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.body.BodyPartActivity
import com.monsteraltech.skincare.body.mole.domain.model.*
import com.monsteraltech.skincare.body.mole.domain.repository.*
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.data.identity.ActiveProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReactiveListsUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val owner = object : ActiveProfile {
        override val ownerId = "ui-fixture"
        override val isLocal = true
        override fun activateLocalProfile() = ownerId
        override fun activateAccountProfile() = Unit
        override fun clear() = Unit
    }
    private fun entry(id: String, title: String = id) = MoleListEntry(id, title, "", "Brazo derecho", "#FFED1C24", "", "", 60, 1000, 1000)
    private inner class Reads : MoleReadRepository {
        val entries = MutableStateFlow((1..70).map { entry("m$it") })
        val history = MutableStateFlow(AnalysisHistoryPage(entry("m"), (1..60).map { AnalysisData(id = "a$it", moleId = "m") }, 60))
        override fun observeMoles(ownerId: String, query: MoleListQuery, limit: Int) = entries.map {
            val matched = it.filter { row -> MoleListSearch.matches(row, query) }
            MoleListPage(matched.take(limit), matched.size, it.size)
        }
        override fun observeHistory(ownerId: String, moleId: String, limit: Int) = history.map { it.copy(analyses = it.analyses.take(limit)) }
    }
    private fun install(model: ViewModel, type: Class<out ViewModel>, screen: Class<out Activity>): Application.ActivityLifecycleCallbacks {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = model as T
        }
        return object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, state: Bundle?) {
                if (screen.isInstance(activity)) ViewModelProvider(activity as ViewModelStoreOwner, factory)[type]
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }.also { (context.applicationContext as Application).registerActivityLifecycleCallbacks(it) }
    }
    private fun unregister(callbacks: Application.ActivityLifecycleCallbacks) =
        (context.applicationContext as Application).unregisterActivityLifecycleCallbacks(callbacks)
    private fun snapshot(name: String) {
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        try { File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun bodyScreenRendersPagingAndLiveChangesWithoutResumingTheActivity() {
        val reads = Reads()
        val model = MoleListViewModel(reads, owner, SavedStateHandle())
        val hooks = install(model, MoleListViewModel::class.java, BodyPartActivity::class.java)
        try {
            ActivityScenario.launch<BodyPartActivity>(Intent(context, BodyPartActivity::class.java).putExtra("COLOR_VALUE", "#FFED1C24")).use { scenario ->
                runBlocking { withTimeout(5000) { model.uiState.first { it is MoleListViewModel.UiState.Ready } } }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertEquals(50, it.findViewById<RecyclerView>(R.id.lunarRecyclerView).adapter!!.itemCount)
                    assertTrue(it.findViewById<MaterialButton>(R.id.loadMoreButton).isShown)
                }
                snapshot("reactive_body_paging.png")
                scenario.onActivity { it.findViewById<MaterialButton>(R.id.loadMoreButton).performClick() }
                runBlocking { withTimeout(5000) { model.uiState.first { it is MoleListViewModel.UiState.Ready && it.page.items.size == 70 } } }
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertEquals(70, it.findViewById<RecyclerView>(R.id.lunarRecyclerView).adapter!!.itemCount) }
                reads.entries.value = listOf(entry("live", "Live title"))
                runBlocking { withTimeout(5000) { model.uiState.first { it is MoleListViewModel.UiState.Ready && it.page.items.singleOrNull()?.id == "live" } } }
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertEquals(1, it.findViewById<RecyclerView>(R.id.lunarRecyclerView).adapter!!.itemCount) }
                snapshot("reactive_body.png")
            }
        } finally { unregister(hooks) }
    }

    @Test fun historyScreenShowsTheTotalAndLoadsTheRemainingObservations() {
        val reads = Reads()
        val model = AnalysisHistoryViewModel(reads, owner, SavedStateHandle())
        val hooks = install(model, AnalysisHistoryViewModel::class.java, MoleAnalysisHistoryActivity::class.java)
        try {
            ActivityScenario.launch<MoleAnalysisHistoryActivity>(Intent(context, MoleAnalysisHistoryActivity::class.java).putExtra("MOLE_ID", "m")).use { scenario ->
                runBlocking { withTimeout(5000) { model.uiState.first { it is AnalysisHistoryViewModel.UiState.Ready } } }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertTrue(it.findViewById<TextView>(R.id.historyTitleText).text.contains("60"))
                    assertEquals(50, it.findViewById<RecyclerView>(R.id.analysisRecyclerView).adapter!!.itemCount)
                }
                snapshot("reactive_history_paging.png")
                scenario.onActivity { it.findViewById<MaterialButton>(R.id.loadMoreButton).performClick() }
                runBlocking { withTimeout(5000) { model.uiState.first { it is AnalysisHistoryViewModel.UiState.Ready && it.page.analyses.size == 60 } } }
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertEquals(60, it.findViewById<RecyclerView>(R.id.analysisRecyclerView).adapter!!.itemCount) }
                snapshot("reactive_history.png")
            }
        } finally { unregister(hooks) }
    }
}

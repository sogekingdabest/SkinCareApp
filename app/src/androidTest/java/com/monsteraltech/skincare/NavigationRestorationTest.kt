package com.monsteraltech.skincare

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.monsteraltech.skincare.body.mole.MoleViewerArgs
import com.monsteraltech.skincare.data.auth.AuthRepository
import com.monsteraltech.skincare.login.LoginViewModel
import com.monsteraltech.skincare.utils.MedicalDisclaimerHelper
import com.monsteraltech.skincare.testing.TestingDependencies
import dagger.hilt.android.EntryPointAccessors
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationRestorationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun embeddedSnapshotsCannotOverrideAnIdentityRoute() {
        val intent = MoleViewerArgs("m", "a").toIntent(context).apply {
            putExtra("LUNAR_TITLE", "stale title")
            putExtra("HISTORICAL_AI_PROBABILITY", 0.99f)
            putExtra("HISTORICAL_ANALYSIS_RESULT", "stale result")
        }
        assertEquals(MoleViewerArgs("m", "a"), MoleViewerArgs.fromIntent(intent))
        val clean = MoleViewerArgs.fromIntent(intent).toIntent(context)
        assertEquals(setOf("MOLE_ID", "ANALYSIS_ID"), clean.extras!!.keySet())
        assertEquals(MoleViewerArgs("m", "legacy-id"), MoleViewerArgs.fromIntent(Intent()
            .putExtra("MOLE_ID", "m").putExtra("HISTORICAL_ANALYSIS_ID", "legacy-id")))
    }

    @Test fun rootTabsReuseFragmentsAndRestoreTheSelectedTabWithoutBackStackEntries() {
        val dependencies = EntryPointAccessors.fromApplication(context, TestingDependencies::class.java)
        dependencies.profile().activateLocalProfile()
        MedicalDisclaimerHelper(context).markDisclaimerAsAccepted()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            var body: Any? = null
            var account: Any? = null
            scenario.onActivity { activity ->
                val tabs = activity.findViewById<BottomNavigationView>(R.id.navigation)
                body = activity.supportFragmentManager.findFragmentByTag("my_body")
                repeat(6) {
                    tabs.selectedItemId = R.id.navigation_account
                    if (account == null) account = activity.supportFragmentManager.findFragmentByTag("account")
                    assertSame(account, activity.supportFragmentManager.findFragmentByTag("account"))
                    tabs.selectedItemId = R.id.navigation_my_body
                    assertSame(body, activity.supportFragmentManager.findFragmentByTag("my_body"))
                }
                tabs.selectedItemId = R.id.navigation_account
                assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
                assertEquals(2, activity.supportFragmentManager.fragments.size)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(R.id.navigation_account, activity.findViewById<BottomNavigationView>(R.id.navigation).selectedItemId)
                assertEquals("account", activity.supportFragmentManager.primaryNavigationFragment!!.tag)
                assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
                activity.onBackPressedDispatcher.onBackPressed()
                assertEquals(R.id.navigation_my_body, activity.findViewById<BottomNavigationView>(R.id.navigation).selectedItemId)
            }
        }
    }

    @Test fun savedStateRegistryRestoresACompletionIntoANewViewModelStoreAfterParcelRoundTrip() {
        val auth = EntryPointAccessors.fromApplication(context, TestingDependencies::class.java).auth()
        instrumentation.runOnMainSync {
            val first = RestoredOwner(null)
            var originalHandle: SavedStateHandle? = null
            val original = first.model(auth) { originalHandle = it }
            originalHandle!!["authCompleted"] = true
            val out = Bundle()
            first.controller.performSave(out)
            first.viewModelStore.clear()
            val parcel = Parcel.obtain()
            val restoredBundle = try {
                parcel.writeBundle(out)
                parcel.setDataPosition(0)
                parcel.readBundle(javaClass.classLoader)!!
            } finally { parcel.recycle() }
            val second = RestoredOwner(restoredBundle)
            try {
                val restored = second.model(auth)
                assertNotSame(original, restored)
                assertTrue(restored.requiresMain.value)
            } finally { second.viewModelStore.clear() }
        }
    }

    private class RestoredOwner(state: Bundle?) : SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry get() = controller.savedStateRegistry
        override val viewModelStore = ViewModelStore()
        init {
            controller.performAttach()
            enableSavedStateHandles()
            controller.performRestore(state)
            registry.currentState = Lifecycle.State.CREATED
        }
        fun model(auth: AuthRepository, handle: (SavedStateHandle) -> Unit = {}): LoginViewModel {
            val extras = MutableCreationExtras().apply {
                set(SAVED_STATE_REGISTRY_OWNER_KEY, this@RestoredOwner)
                set(VIEW_MODEL_STORE_OWNER_KEY, this@RestoredOwner)
            }
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
                    val saved = extras.createSavedStateHandle()
                    handle(saved)
                    return LoginViewModel(auth, saved) as T
                }
            }
            return ViewModelProvider(viewModelStore, factory, extras)[LoginViewModel::class.java]
        }
    }
}

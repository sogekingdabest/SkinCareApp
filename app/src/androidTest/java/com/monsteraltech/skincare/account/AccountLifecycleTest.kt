package com.monsteraltech.skincare.account

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.switchmaterial.SwitchMaterial
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.fragments.AccountFragment
import com.monsteraltech.skincare.testing.LifecycleTestActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountLifecycleTest {
    private class DelayedProfile : AccountProfileRepository {
        val info = CompletableDeferred<UserInfo>()
        val initialSettings = CompletableDeferred<AccountSettings>()
        val write = CompletableDeferred<Boolean>()
        val writeStarted = CompletableDeferred<Unit>()
        @Volatile var settings = AccountSettings(userId = "lifecycle-fixture")
        @Volatile var infoCancelled = false
        @Volatile var settingsCancelled = false
        override suspend fun getCurrentUserInfo(): AccountResult<UserInfo> {
            return try { AccountResult.success(info.await()) }
            catch (cancelled: CancellationException) { infoCancelled = true; throw cancelled }
        }
        override suspend fun getUserSettings(): AccountResult<AccountSettings> {
            try { initialSettings.await() } catch (cancelled: CancellationException) { settingsCancelled = true; throw cancelled }
            return AccountResult.success(settings)
        }
        override suspend fun updateUserSettings(settings: AccountSettings): AccountResult<Boolean> {
            writeStarted.complete(Unit)
            val committed = write.await()
            if (committed) this.settings = settings
            return AccountResult.success(committed)
        }
        override suspend fun applySettingsPatch(patch: AccountSettingsPatch, ownerId: String): AccountResult<AccountSettings> {
            val candidate = patch.applyTo(settings)
            val result = updateUserSettings(candidate)
            return if (result.getDataOrNull() == true) AccountResult.success(candidate)
                else AccountResult.error(IllegalStateException("write failed"))
        }
        override fun isUserAuthenticated() = true
        override fun getCurrentUserId() = "lifecycle-fixture"
        fun releaseLoads() {
            info.complete(UserInfo(uid = "lifecycle-fixture", displayName = "Loaded after navigation", isLocalProfile = true))
            initialSettings.complete(settings)
        }
    }
    private class Fixture(val scenario: ActivityScenario<LifecycleTestActivity>, val fragment: AccountFragment,
        val model: AccountViewModel) : AutoCloseable {
        fun hideView() = scenario.onActivity { host ->
            host.supportFragmentManager.beginTransaction().replace(R.id.fragment_container, Fragment()).addToBackStack("away").commit()
            host.supportFragmentManager.executePendingTransactions()
        }
        fun showView() = scenario.onActivity { it.supportFragmentManager.popBackStackImmediate() }
        override fun close() = scenario.close()
    }
    private fun open(repository: AccountProfileRepository): Fixture {
        val scenario = ActivityScenario.launch(LifecycleTestActivity::class.java)
        val fragment = AccountFragment()
        lateinit var model: AccountViewModel
        scenario.onActivity { host ->
            host.supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentPreCreated(fm: FragmentManager, f: Fragment, savedInstanceState: Bundle?) {
                    if (f !== fragment) return
                    val factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = AccountViewModel(repository) as T
                    }
                    model = ViewModelProvider(f, factory)[AccountViewModel::class.java]
                }
            }, false)
            host.supportFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment).commitNow()
        }
        return Fixture(scenario, fragment, model)
    }
    private fun idle() = InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    private fun awaitLoaded(model: AccountViewModel) = runBlocking {
        withTimeout(5000) { model.uiState.first { it.userInfo is AccountResult.Success && it.settings != null } }
        idle()
    }

    @Test fun delayedLoadsNeverRenderIntoTheDestroyedViewAndRenderAfterReturning() {
        val repository = DelayedProfile()
        open(repository).use { fixture ->
            lateinit var oldView: View
            lateinit var oldName: TextView
            lateinit var previousText: String
            fixture.scenario.onActivity {
                oldView = fixture.fragment.requireView()
                oldName = oldView.findViewById(R.id.tv_user_name)
                previousText = oldName.text.toString()
            }
            fixture.hideView()
            assertNull(fixture.fragment.view)
            repository.releaseLoads()
            awaitLoaded(fixture.model)
            fixture.scenario.onActivity { assertEquals(previousText, oldName.text.toString()) }
            fixture.showView(); idle()
            fixture.scenario.onActivity {
                assertNotSame(oldView, fixture.fragment.requireView())
                assertEquals("Loaded after navigation", fixture.fragment.requireView().findViewById<TextView>(R.id.tv_user_name).text.toString())
                assertEquals(previousText, oldName.text.toString())
            }
        }
    }

    @Test fun removingTheFragmentCancelsBothPendingLoads() {
        val repository = DelayedProfile()
        open(repository).use { fixture ->
            fixture.scenario.onActivity { host ->
                host.supportFragmentManager.beginTransaction().remove(fixture.fragment).commitNow()
            }
            idle()
            assertTrue(repository.infoCancelled)
            assertTrue(repository.settingsCancelled)
            repository.releaseLoads(); idle()
            assertTrue(fixture.model.uiState.value.userInfo is AccountResult.Loading)
            assertNull(fixture.model.uiState.value.settings)
        }
    }

    @Test fun aWriteKeepsItsStateAcrossViewDestructionAndUpdatesTheReplacementView() {
        val repository = DelayedProfile().apply { releaseLoads() }
        open(repository).use { fixture ->
            awaitLoaded(fixture.model)
            fixture.scenario.onActivity { fixture.model.changeSetting(AccountViewModel.Setting.NOTIFICATIONS, false) }
            runBlocking { withTimeout(5000) { repository.writeStarted.await() } }
            fixture.hideView()
            repository.write.complete(true)
            runBlocking { withTimeout(5000) { fixture.model.uiState.first { !it.isSaving && it.settings?.notificationsEnabled == false } } }
            assertNull(fixture.fragment.view)
            fixture.showView(); idle()
            fixture.scenario.onActivity {
                assertFalse(fixture.fragment.requireView().findViewById<SwitchMaterial>(R.id.switch_notifications).isChecked)
            }
        }
    }
}

package com.monsteraltech.skincare.account

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textfield.TextInputEditText
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.data.identity.ActiveProfile
import com.monsteraltech.skincare.login.ForgotPasswordActivity
import com.monsteraltech.skincare.login.ForgotPasswordViewModel
import com.monsteraltech.skincare.login.PasswordResetRepository
import com.monsteraltech.skincare.notifications.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountBoundariesUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val owner = object : ActiveProfile {
        override val ownerId = "a5-ui-fixture"
        override val isLocal = true
        override fun activateLocalProfile() = ownerId
        override fun activateAccountProfile() = Unit
        override fun clear() = Unit
    }
    private fun install(model: ViewModel, type: Class<out ViewModel>, screen: Class<out Activity>): Application.ActivityLifecycleCallbacks {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = model as T
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
    private fun uninstall(callbacks: Application.ActivityLifecycleCallbacks) =
        (context.applicationContext as Application).unregisterActivityLifecycleCallbacks(callbacks)

    @Test fun notificationsRenderCommittedEditsAndKeepThemAfterRecreation() {
        var stored = AccountSettings(userId = owner.ownerId, notificationsEnabled = true, darkModeEnabled = true)
        val repository = object : AccountProfileRepository {
            override suspend fun getCurrentUserInfo() = AccountResult.success(UserInfo(uid = owner.ownerId))
            override suspend fun getUserSettings() = AccountResult.success(stored)
            override suspend fun updateUserSettings(settings: AccountSettings): AccountResult<Boolean> {
                stored = settings
                return AccountResult.success(true)
            }
            override suspend fun applySettingsPatch(patch: AccountSettingsPatch, ownerId: String): AccountResult<AccountSettings> {
                stored = patch.applyTo(stored)
                return AccountResult.success(stored)
            }
            override fun isUserAuthenticated() = true
            override fun getCurrentUserId() = owner.ownerId
        }
        val scheduled = mutableListOf<AccountSettings>()
        val scheduler = object : NotificationScheduleService {
            override fun apply(settings: AccountSettings) { scheduled += settings }
        }
        val model = NotificationSettingsViewModel(repository, owner, UpdateNotificationSettings(repository, owner, scheduler))
        val hooks = install(model, NotificationSettingsViewModel::class.java, NotificationSettingsActivity::class.java)
        try {
            ActivityScenario.launch<NotificationSettingsActivity>(Intent(context, NotificationSettingsActivity::class.java)).use { scenario ->
                runBlocking { withTimeout(5000) { model.state.first { it is NotificationSettingsViewModel.State.Ready } } }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val sound = it.findViewById<CompoundButton>(R.id.switch_sound)
                    assertTrue(sound.isChecked)
                    assertTrue(sound.isEnabled)
                    sound.performClick()
                }
                runBlocking { withTimeout(5000) { model.state.first { it is NotificationSettingsViewModel.State.Ready && !it.settings.notificationSound } } }
                scenario.recreate()
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertFalse(it.findViewById<CompoundButton>(R.id.switch_sound).isChecked) }
                assertTrue(stored.darkModeEnabled)
                assertFalse(stored.notificationSound)
                assertEquals(1, scheduled.size)
            }
        } finally { uninstall(hooks) }
    }

    @Test fun aPendingPasswordResetSurvivesRecreationWithoutStartingASecondRequest() {
        val response = CompletableDeferred<Result<Unit>>()
        var calls = 0
        val repository = object : PasswordResetRepository {
            override suspend fun sendPasswordReset(email: String): Result<Unit> { calls++; return response.await() }
        }
        val model = ForgotPasswordViewModel(repository)
        val hooks = install(model, ForgotPasswordViewModel::class.java, ForgotPasswordActivity::class.java)
        try {
            ActivityScenario.launch<ForgotPasswordActivity>(Intent(context, ForgotPasswordActivity::class.java).putExtra("password", "fixture@example.test")).use { scenario ->
                scenario.onActivity { it.findViewById<Button>(R.id.recuperarButton).performClick() }
                runBlocking { withTimeout(5000) { model.isSending.first { it } } }
                scenario.recreate()
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertFalse(it.findViewById<Button>(R.id.recuperarButton).isEnabled) }
                assertEquals(1, calls)
                response.complete(Result.failure(IllegalStateException("fixture")))
                runBlocking { withTimeout(5000) { model.isSending.first { !it } } }
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertTrue(it.findViewById<Button>(R.id.recuperarButton).isEnabled) }
            }
        } finally { response.cancel(); uninstall(hooks) }
    }

    @Test fun aPasswordChangeRetainsItsBusyStateAndRendersErrorsOnTheRecreatedScreen() {
        val response = CompletableDeferred<AccountResult<Unit>>()
        var calls = 0
        val repository = object : PasswordChangeRepository {
            override fun isAuthenticated() = true
            override suspend fun changePassword(currentPassword: String, newPassword: String): AccountResult<Unit> {
                calls++
                return response.await()
            }
        }
        val model = PasswordChangeViewModel(PasswordChangeManager(repository, PasswordValidator()))
        val hooks = install(model, PasswordChangeViewModel::class.java, PasswordChangeActivity::class.java)
        try {
            ActivityScenario.launch<PasswordChangeActivity>(Intent(context, PasswordChangeActivity::class.java)).use { scenario ->
                scenario.onActivity {
                    it.findViewById<TextInputEditText>(R.id.currentPasswordEditText).setText("Oldpass1")
                    it.findViewById<TextInputEditText>(R.id.newPasswordEditText).setText("Newpass2")
                    it.findViewById<TextInputEditText>(R.id.confirmPasswordEditText).setText("Newpass2")
                    it.findViewById<View>(R.id.changePasswordButton).performClick()
                }
                runBlocking { withTimeout(5000) { model.isSaving.first { it } } }
                scenario.recreate()
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertEquals(View.VISIBLE, it.findViewById<View>(R.id.loadingOverlay).visibility)
                    assertFalse(it.findViewById<View>(R.id.changePasswordButton).isEnabled)
                }
                assertEquals(1, calls)
                response.complete(AccountResult.Error(IllegalArgumentException("fixture"), "La contraseña actual es incorrecta", AccountResult.ErrorType.VALIDATION_ERROR))
                runBlocking { withTimeout(5000) { model.isSaving.first { !it } } }
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertEquals(View.GONE, it.findViewById<View>(R.id.loadingOverlay).visibility) }
            }
        } finally { response.cancel(); uninstall(hooks) }
    }
}

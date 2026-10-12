package com.monsteraltech.skincare.notifications
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.databinding.ActivityNotificationSettingsBinding
import com.monsteraltech.skincare.utils.UIUtils
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
@AndroidEntryPoint
class NotificationSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotificationSettingsBinding
    private val viewModel: NotificationSettingsViewModel by viewModels()
    private lateinit var permissionManager: NotificationPermissionManager
    private var currentSettings = NotificationSettings()
    private var generalNotificationsEnabled = false
    private var isRendering = false
    private var isReady = false
    private var isSaving = false
    companion object {
        private const val TAG = "NotificationSettings"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()
        initializeComponents()
        setupListeners()
        observeViewModel()
    }
    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        isReady = state is NotificationSettingsViewModel.State.Ready
                        isSaving = (state as? NotificationSettingsViewModel.State.Ready)?.isSaving == true
                        if (state is NotificationSettingsViewModel.State.Ready) {
                            currentSettings = state.settings.toNotificationSettings()
                            generalNotificationsEnabled = state.settings.notificationsEnabled
                        } else {
                            currentSettings = NotificationSettings()
                            generalNotificationsEnabled = false
                        }
                        applySettingsToUI()
                        if (state is NotificationSettingsViewModel.State.Failed) {
                            UIUtils.showErrorToast(this@NotificationSettingsActivity, getString(R.string.error_unknown))
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        if (event == NotificationSettingsViewModel.Event.Saved) {
                            UIUtils.showSuccessToast(this@NotificationSettingsActivity, getString(R.string.settings_saved))
                        } else UIUtils.showErrorToast(this@NotificationSettingsActivity, getString(R.string.settings_error))
                    }
                }
            }
        }
    }
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Configuración de Notificaciones"
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }
    private fun initializeComponents() {
        permissionManager = NotificationPermissionManager(this)
    }
    private fun applySettingsToUI() {
        isRendering = true
        binding.apply {
            switchMoleCheckups.isChecked = currentSettings.moleCheckups
            tvMoleTime.text = currentSettings.moleCheckupTime
            tvMoleFrequency.text = "${currentSettings.moleCheckupFrequency} días"
            switchVibration.isChecked = currentSettings.vibration
            switchSound.isChecked = currentSettings.sound
            updateUIState()
        }
        isRendering = false
    }
    private fun updateUIState() {
        binding.apply {
            switchMoleCheckups.isEnabled = isReady && !isSaving && generalNotificationsEnabled
            switchVibration.isEnabled = isReady && !isSaving
            switchSound.isEnabled = isReady && !isSaving
            val moleOptionsEnabled = isReady && !isSaving && generalNotificationsEnabled && switchMoleCheckups.isChecked
            layoutMoleOptions.alpha = if (moleOptionsEnabled) 1.0f else 0.5f
            layoutMoleTime.isEnabled = moleOptionsEnabled
            layoutMoleFrequency.isEnabled = moleOptionsEnabled
            if (!generalNotificationsEnabled) {
                cardNotificationDisabledMessage.visibility = View.VISIBLE
                Log.d(TAG, "Mostrando mensaje de notificaciones desactivadas")
            } else {
                cardNotificationDisabledMessage.visibility = View.GONE
                Log.d(TAG, "Ocultando mensaje de notificaciones desactivadas")
            }
            Log.d(
                    TAG,
                    "UI State updated - generalNotifications: $generalNotificationsEnabled, moleCheckups: ${switchMoleCheckups.isChecked}, optionsEnabled: $moleOptionsEnabled"
            )
        }
    }
    private fun setupListeners() {
        binding.apply {
            switchMoleCheckups.setOnCheckedChangeListener { _, isChecked ->
                if (!isRendering) {
                    Log.d(TAG, "Switch mole checkups cambiado por usuario: $isChecked")
                    if (isChecked && !permissionManager.hasNotificationPermission()) {
                        showPermissionDialog()
                        isRendering = true
                        switchMoleCheckups.isChecked = false
                        isRendering = false
                        return@setOnCheckedChangeListener
                    }
                    viewModel.setCheckups(isChecked)
                } else {
                    Log.d(TAG, "Switch mole checkups aplicado desde carga inicial: $isChecked")
                }
            }
            switchVibration.setOnCheckedChangeListener { _, isChecked ->
                if (!isRendering) {
                    Log.d(TAG, "Switch vibration cambiado por usuario: $isChecked")
                    viewModel.setVibration(isChecked)
                } else {
                    Log.d(TAG, "Switch vibration aplicado desde carga inicial: $isChecked")
                }
            }
            switchSound.setOnCheckedChangeListener { _, isChecked ->
                if (!isRendering) {
                    Log.d(TAG, "Switch sound cambiado por usuario: $isChecked")
                    viewModel.setSound(isChecked)
                } else {
                    Log.d(TAG, "Switch sound aplicado desde carga inicial: $isChecked")
                }
            }
            layoutMoleTime.setOnClickListener {
                if (generalNotificationsEnabled && switchMoleCheckups.isChecked) {
                    showTimePicker(currentSettings.moleCheckupTime) { time ->
                        viewModel.setTime(time)
                    }
                }
            }
            layoutMoleFrequency.setOnClickListener {
                if (generalNotificationsEnabled && switchMoleCheckups.isChecked) {
                    showFrequencyDialog(
                            "Frecuencia de Revisión de Lunares",
                            currentSettings.moleCheckupFrequency
                    ) { frequency ->
                        viewModel.setFrequency(frequency)
                    }
                }
            }
        }
    }
    private fun showTimePicker(currentTime: String, onTimeSelected: (String) -> Unit) {
        val parts = currentTime.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        TimePickerDialog(
                        this,
                        { _, selectedHour, selectedMinute ->
                            val formattedTime =
                                    String.format("%02d:%02d", selectedHour, selectedMinute)
                            onTimeSelected(formattedTime)
                        },
                        hour,
                        minute,
                        true
                )
                .show()
    }
    private fun showFrequencyDialog(
            title: String,
            currentFrequency: Int,
            onFrequencySelected: (Int) -> Unit
    ) {
        val frequencies =
                arrayOf("1 día", "3 días", "7 días", "14 días", "30 días", "60 días", "90 días")
        val values = arrayOf(1, 3, 7, 14, 30, 60, 90)
        val currentIndex = values.indexOf(currentFrequency).takeIf { it >= 0 } ?: 2
        AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(frequencies, currentIndex) { dialog, which ->
                    onFrequencySelected(values[which])
                    dialog.dismiss()
                }
                .setNegativeButton("Cancelar", null)
                .show()
    }
    private fun showPermissionDialog() {
        if (permissionManager.shouldShowRequestPermissionRationale(this)) {
            AlertDialog.Builder(this)
                    .setTitle(getString(R.string.notification_permission_title))
                    .setMessage(getString(R.string.notification_permission_message))
                    .setPositiveButton("Activar") { _, _ ->
                        permissionManager.requestNotificationPermission(this)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
        } else {
            permissionManager.requestNotificationPermission(this)
        }
    }
    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NotificationPermissionManager.NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() &&
                                grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d(TAG, "Permiso de notificaciones concedido")
                    viewModel.setCheckups(true)
                } else {
                    Log.d(TAG, "Permiso de notificaciones denegado")
                    UIUtils.showErrorToast(
                                    this,
                                    getString(R.string.notification_permission_denied)
                            )
                }
            }
        }
    }
}

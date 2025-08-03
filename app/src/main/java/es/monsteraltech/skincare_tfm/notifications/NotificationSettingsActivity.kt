package es.monsteraltech.skincare_tfm.notifications

import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.account.AccountResult
import es.monsteraltech.skincare_tfm.account.AccountSettings
import es.monsteraltech.skincare_tfm.account.UserProfileManager
import es.monsteraltech.skincare_tfm.databinding.ActivityNotificationSettingsBinding
import es.monsteraltech.skincare_tfm.utils.UIUtils
import kotlinx.coroutines.launch

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationSettingsBinding
    private lateinit var notificationManager: NotificationManager
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var permissionManager: NotificationPermissionManager
    private var currentSettings = NotificationSettings()
    private var currentAccountSettings = AccountSettings()
    private var generalNotificationsEnabled = false
    private var isLoadingSettings = false

    companion object {
        private const val TAG = "NotificationSettings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        initializeComponents()
        loadSettings()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - Verificando estado actual")

        // Recargar el estado de notificaciones generales por si cambió desde otra pantalla
        lifecycleScope.launch {
            val result = userProfileManager.getUserSettings()
            if (result is AccountResult.Success<*>) {
                val accountSettings = result.data as AccountSettings
                val newGeneralNotificationsEnabled = accountSettings.notificationsEnabled

                if (newGeneralNotificationsEnabled != generalNotificationsEnabled) {
                    Log.d(
                            TAG,
                            "Estado de notificaciones generales cambió: $generalNotificationsEnabled -> $newGeneralNotificationsEnabled"
                    )
                    generalNotificationsEnabled = newGeneralNotificationsEnabled
                    updateUIState()
                }
            }
        }

        debugCurrentState()
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
        notificationManager = NotificationManager(this)
        userProfileManager = UserProfileManager()
        permissionManager = NotificationPermissionManager(this)
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            when (val result = userProfileManager.getUserSettings()) {
                is AccountResult.Success<*> -> {
                    Log.d(TAG, "Configuraciones cargadas desde Firebase")
                    currentAccountSettings = result.data as AccountSettings
                    currentSettings = currentAccountSettings.toNotificationSettings()
                    generalNotificationsEnabled = currentAccountSettings.notificationsEnabled

                    Log.d(TAG, "Configuraciones cargadas:")
                    Log.d(
                            TAG,
                            "  - RAW notificationsEnabled: ${currentAccountSettings.notificationsEnabled}"
                    )
                    Log.d(TAG, "  - generalNotificationsEnabled: $generalNotificationsEnabled")
                    Log.d(
                            TAG,
                            "  - moleCheckupsEnabled: ${currentAccountSettings.moleCheckupsEnabled}"
                    )
                    Log.d(TAG, "  - moleCheckupTime: ${currentAccountSettings.moleCheckupTime}")
                    Log.d(
                            TAG,
                            "  - moleCheckupFrequency: ${currentAccountSettings.moleCheckupFrequency}"
                    )

                    applySettingsToUI()
                }
                is AccountResult.Error<*> -> {
                    Log.e(TAG, "Error al cargar configuraciones: ${result.message}")
                    UIUtils.showErrorSnackbar(
                            binding.root,
                            "Error al cargar configuraciones: ${result.message}"
                    )

                    // Usar configuraciones por defecto
                    currentAccountSettings = AccountSettings()
                    currentSettings = NotificationSettings()
                    generalNotificationsEnabled = false
                    applySettingsToUI()
                }
                is AccountResult.Loading<*> -> {
                    Log.d(TAG, "Cargando configuraciones...")
                }
            }
        }
    }

    private fun applySettingsToUI() {
        // Desactivar listeners temporalmente para evitar guardado automático
        isLoadingSettings = true

        binding.apply {
            // Mole checkups
            switchMoleCheckups.isChecked = currentSettings.moleCheckups
            tvMoleTime.text = currentSettings.moleCheckupTime
            tvMoleFrequency.text = "${currentSettings.moleCheckupFrequency} días"

            // General settings
            switchVibration.isChecked = currentSettings.vibration
            switchSound.isChecked = currentSettings.sound

            updateUIState()
        }

        // Reactivar listeners después de aplicar configuraciones
        isLoadingSettings = false
    }

    private fun updateUIState() {
        binding.apply {
            // Disable the switch if general notifications are disabled
            switchMoleCheckups.isEnabled = generalNotificationsEnabled

            // Enable/disable mole checkup options
            val moleOptionsEnabled = generalNotificationsEnabled && switchMoleCheckups.isChecked

            layoutMoleOptions.alpha = if (moleOptionsEnabled) 1.0f else 0.5f
            layoutMoleTime.isEnabled = moleOptionsEnabled
            layoutMoleFrequency.isEnabled = moleOptionsEnabled

            // Show a message if general notifications are disabled
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

        debugCurrentState()
    }

    private fun setupListeners() {
        binding.apply {
            switchMoleCheckups.setOnCheckedChangeListener { _, isChecked ->
                if (!isLoadingSettings) {
                    Log.d(TAG, "Switch mole checkups cambiado por usuario: $isChecked")

                    if (isChecked && !permissionManager.hasNotificationPermission()) {
                        showPermissionDialog()
                        switchMoleCheckups.isChecked = false
                        return@setOnCheckedChangeListener
                    }

                    currentSettings = currentSettings.copy(moleCheckups = isChecked)
                    updateUIState()
                    saveSettings()
                } else {
                    Log.d(TAG, "Switch mole checkups aplicado desde carga inicial: $isChecked")
                }
            }

            switchVibration.setOnCheckedChangeListener { _, isChecked ->
                if (!isLoadingSettings) {
                    Log.d(TAG, "Switch vibration cambiado por usuario: $isChecked")
                    currentSettings = currentSettings.copy(vibration = isChecked)
                    saveSettings()
                } else {
                    Log.d(TAG, "Switch vibration aplicado desde carga inicial: $isChecked")
                }
            }

            switchSound.setOnCheckedChangeListener { _, isChecked ->
                if (!isLoadingSettings) {
                    Log.d(TAG, "Switch sound cambiado por usuario: $isChecked")
                    currentSettings = currentSettings.copy(sound = isChecked)
                    saveSettings()
                } else {
                    Log.d(TAG, "Switch sound aplicado desde carga inicial: $isChecked")
                }
            }

            layoutMoleTime.setOnClickListener {
                if (generalNotificationsEnabled && switchMoleCheckups.isChecked) {
                    showTimePicker(currentSettings.moleCheckupTime) { time ->
                        currentSettings = currentSettings.copy(moleCheckupTime = time)
                        tvMoleTime.text = time
                        saveSettings()
                    }
                }
            }

            layoutMoleFrequency.setOnClickListener {
                if (generalNotificationsEnabled && switchMoleCheckups.isChecked) {
                    showFrequencyDialog(
                            "Frecuencia de Revisión de Lunares",
                            currentSettings.moleCheckupFrequency
                    ) { frequency ->
                        currentSettings = currentSettings.copy(moleCheckupFrequency = frequency)
                        tvMoleFrequency.text = "$frequency días"
                        saveSettings()
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

    private fun saveSettings() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Guardando configuraciones en Firebase")

                val updatedAccountSettings =
                        currentSettings.toAccountSettings(currentAccountSettings)
                when (val result = userProfileManager.updateUserSettings(updatedAccountSettings)) {
                    is AccountResult.Success<*> -> {
                        Log.d(TAG, "Configuraciones guardadas exitosamente")
                        currentAccountSettings = updatedAccountSettings

                        if (generalNotificationsEnabled &&
                                        permissionManager.hasNotificationPermission()
                        ) {
                            notificationManager.scheduleNotifications(currentSettings)
                            Log.d(TAG, "Notificaciones programadas")
                        } else {
                            notificationManager.cancelAllNotifications()
                            Log.d(TAG, "Notificaciones canceladas")
                        }

                        Toast.makeText(
                                        this@NotificationSettingsActivity,
                                        "Configuración guardada",
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                    }
                    is AccountResult.Error<*> -> {
                        Log.e(TAG, "Error al guardar configuraciones: ${result.message}")
                        UIUtils.showErrorSnackbar(
                                binding.root,
                                "Error al guardar configuraciones: ${result.message}"
                        )
                    }
                    is AccountResult.Loading<*> -> {
                        Log.d(TAG, "Guardando configuraciones...")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error inesperado al guardar configuraciones: ${e.message}", e)
                UIUtils.showErrorSnackbar(binding.root, "Error al guardar configuraciones")
            }
        }
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
                    binding.switchMoleCheckups.isChecked = true
                    currentSettings = currentSettings.copy(moleCheckups = true)
                    updateUIState()
                    saveSettings()
                } else {
                    Log.d(TAG, "Permiso de notificaciones denegado")
                    Toast.makeText(
                                    this,
                                    getString(R.string.notification_permission_denied),
                                    Toast.LENGTH_LONG
                            )
                            .show()
                }
            }
        }
    }

    private fun debugCurrentState() {
        Log.d(TAG, "=== DEBUG CURRENT STATE ===")
        Log.d(TAG, "generalNotificationsEnabled: $generalNotificationsEnabled")
        Log.d(
                TAG,
                "currentAccountSettings.notificationsEnabled: ${currentAccountSettings.notificationsEnabled}"
        )
        Log.d(TAG, "currentSettings.moleCheckups: ${currentSettings.moleCheckups}")
        Log.d(TAG, "switchMoleCheckups.isEnabled: ${binding.switchMoleCheckups.isEnabled}")
        Log.d(TAG, "switchMoleCheckups.isChecked: ${binding.switchMoleCheckups.isChecked}")
        Log.d(TAG, "layoutMoleOptions.isEnabled: ${binding.layoutMoleOptions.isEnabled}")
        Log.d(TAG, "layoutMoleOptions.alpha: ${binding.layoutMoleOptions.alpha}")
        Log.d(
                TAG,
                "cardNotificationDisabledMessage.visibility: ${binding.cardNotificationDisabledMessage.visibility}"
        )
        Log.d(
                TAG,
                "cardNotificationDisabledMessage.visibility == View.VISIBLE: ${binding.cardNotificationDisabledMessage.visibility == View.VISIBLE}"
        )
        Log.d(
                TAG,
                "cardNotificationDisabledMessage.visibility == View.GONE: ${binding.cardNotificationDisabledMessage.visibility == View.GONE}"
        )
        Log.d(TAG, "hasNotificationPermission: ${permissionManager.hasNotificationPermission()}")
        Log.d(TAG, "========================")
    }
}

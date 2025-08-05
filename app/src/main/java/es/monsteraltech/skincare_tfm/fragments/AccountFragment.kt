package es.monsteraltech.skincare_tfm.fragments
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import es.monsteraltech.skincare_tfm.MainActivity
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.account.AccountResult
import es.monsteraltech.skincare_tfm.account.AccountSettings
import es.monsteraltech.skincare_tfm.account.PasswordChangeActivity
import es.monsteraltech.skincare_tfm.account.UserInfo
import es.monsteraltech.skincare_tfm.account.UserProfileManager
import es.monsteraltech.skincare_tfm.databinding.FragmentAccountBinding
import es.monsteraltech.skincare_tfm.login.LoginActivity
import es.monsteraltech.skincare_tfm.notifications.NotificationPermissionManager
import es.monsteraltech.skincare_tfm.utils.UIUtils
import kotlinx.coroutines.launch
class AccountFragment : Fragment() {
    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var permissionManager: NotificationPermissionManager
    private val TAG = "AccountFragment"
    private var isLoadingSettings = false
    private val passwordChangeResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handlePasswordChangeResult(result.resultCode)
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userProfileManager = UserProfileManager()
        permissionManager = NotificationPermissionManager(requireContext())
        animateUIElements()
        loadUserInformation()
        loadUserSettings()
        setupAccountOptions()
        setupSettingsListeners()
    }
    private fun animateUIElements() {
        binding.apply {
            UIUtils.animateListItem(cardUserInfo, 0)
            UIUtils.animateListItem(cardSettings, 1)
            UIUtils.animateListItem(cardLogout, 2)
        }
    }
    private fun loadUserInformation() {
        lifecycleScope.launch {
            Log.d(TAG, "Iniciando carga de información de usuario")
            showLoadingState()
            val result = userProfileManager.getCurrentUserInfo()
            result.onSuccess { userInfo ->
                Log.d(TAG, "Información de usuario cargada exitosamente: ${userInfo.email}")
                showUserInformation(userInfo)
            }.onError { error ->
                Log.e(TAG, "Error al cargar información del usuario: ${error.message}", error.exception)
                showErrorState(error.message)
                if (error.errorType == AccountResult.ErrorType.AUTHENTICATION_ERROR) {
                }
            }.onLoading { loading ->
                Log.d(TAG, "Cargando información de usuario: ${loading.message}")
                showLoadingState()
            }
        }
    }
    private fun showLoadingState() {
        binding.apply {
            layoutUserInfoContent.visibility = View.GONE
            layoutLoading.visibility = View.VISIBLE
            tvError.visibility = View.GONE
        }
        Log.d(TAG, "Estado de carga mostrado")
    }
    private fun showUserInformation(userInfo: UserInfo) {
        Log.d(TAG, "showUserInformation llamado con: displayName='${userInfo.displayName}', email='${userInfo.email}', isGoogleUser=${userInfo.isGoogleUser}")
        binding.apply {
            layoutLoading.visibility = View.GONE
            tvError.visibility = View.GONE
            layoutUserInfoContent.visibility = View.VISIBLE
            val displayName = userInfo.displayName
            if (!displayName.isNullOrBlank()) {
                tvUserName.text = displayName
                Log.d(TAG, "Nombre configurado: $displayName")
            } else {
                tvUserName.text = "Sin nombre configurado"
                Log.d(TAG, "Nombre no disponible, usando texto por defecto")
            }
            val email = userInfo.email
            if (!email.isNullOrBlank()) {
                tvUserEmail.text = email
                Log.d(TAG, "Email configurado: $email")
            } else {
                tvUserEmail.text = "Sin email configurado"
                Log.d(TAG, "Email no disponible, usando texto por defecto")
            }
            configurePasswordChangeVisibility(userInfo.isGoogleUser)
            Log.d(TAG, "layoutUserInfoContent visibility después: ${layoutUserInfoContent.visibility}")
            Log.d(TAG, "tvUserName text: '${tvUserName.text}'")
            Log.d(TAG, "tvUserEmail text: '${tvUserEmail.text}'")
            UIUtils.fadeIn(tvUserName, 300)
            UIUtils.fadeIn(tvUserEmail, 400)
        }
        Log.d(TAG, "Información de usuario mostrada en UI")
    }
    private fun showErrorState(errorMessage: String) {
        binding.apply {
            layoutLoading.visibility = View.GONE
            layoutUserInfoContent.visibility = View.GONE
            tvError.text = errorMessage
            tvError.visibility = View.VISIBLE
            UIUtils.shakeView(tvError)
        }
        Log.d(TAG, "Estado de error mostrado: $errorMessage")
    }
    private fun loadUserSettings() {
        lifecycleScope.launch {
            Log.d(TAG, "Cargando configuraciones de usuario")
            val result = userProfileManager.getUserSettings()
            result.onSuccess { settings ->
                Log.d(TAG, "Configuraciones de usuario cargadas exitosamente")
                applySettingsToUI(settings)
            }.onError { error ->
                Log.e(TAG, "Error al cargar configuraciones de usuario: ${error.message}", error.exception)
                if (error.message.contains("PERMISSION_DENIED") == true) {
                    Log.d(TAG, "Intentando crear configuraciones iniciales para el usuario")
                    createInitialUserSettings()
                } else {
                    applySettingsToUI(AccountSettings())
                }
            }
        }
    }
    private fun createInitialUserSettings() {
        lifecycleScope.launch {
            try {
                val userId = userProfileManager.getCurrentUserId()
                if (userId != null) {
                    val defaultSettings = AccountSettings(userId = userId)
                    val updateResult = userProfileManager.updateUserSettings(defaultSettings)
                    updateResult.onSuccess {
                        Log.d(TAG, "Configuraciones iniciales creadas exitosamente")
                        applySettingsToUI(defaultSettings)
                    }.onError { error ->
                        Log.e(TAG, "Error al crear configuraciones iniciales: ${error.message}")
                        applySettingsToUI(AccountSettings())
                    }
                } else {
                    Log.w(TAG, "No se pudo obtener el ID del usuario para crear configuraciones")
                    applySettingsToUI(AccountSettings())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción al crear configuraciones iniciales: ${e.message}", e)
                applySettingsToUI(AccountSettings())
            }
        }
    }
    private fun applySettingsToUI(settings: AccountSettings) {
        isLoadingSettings = true
        binding.apply {
            switchNotifications.isChecked = settings.notificationsEnabled
            switchDarkMode.isChecked = settings.darkModeEnabled
        }
        binding.root.post {
            isLoadingSettings = false
        }
        Log.d(TAG, "Configuraciones aplicadas a la UI: notifications=${settings.notificationsEnabled}, darkMode=${settings.darkModeEnabled}")
    }
    private fun setupSettingsListeners() {
        binding.apply {
            switchNotifications.setOnCheckedChangeListener { _, isChecked ->
                if (!isLoadingSettings) {
                    Log.d(TAG, "Configuración de notificaciones cambiada por el usuario: $isChecked")
                    if (isChecked) {
                        if (!permissionManager.hasNotificationPermission()) {
                            showPermissionDialog { granted ->
                                if (granted) {
                                    saveSettingChange("notifications", true)
                                } else {
                                    isLoadingSettings = true
                                    switchNotifications.isChecked = false
                                    isLoadingSettings = false
                                }
                            }
                        } else {
                            saveSettingChange("notifications", isChecked)
                        }
                    } else {
                        saveSettingChange("notifications", isChecked)
                        try {
                            val notificationManager = es.monsteraltech.skincare_tfm.notifications.NotificationManager(requireContext())
                            notificationManager.cancelAllNotifications()
                            Log.d(TAG, "Notificaciones programadas canceladas")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error al cancelar notificaciones: ${e.message}", e)
                        }
                    }
                } else {
                    Log.d(TAG, "Configuración de notificaciones aplicada desde carga inicial: $isChecked")
                }
            }
            switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
                if (!isLoadingSettings) {
                    Log.d(TAG, "Configuración de modo oscuro cambiada por el usuario: $isChecked")
                    saveSettingChange("darkMode", isChecked)
                } else {
                    Log.d(TAG, "Configuración de modo oscuro aplicada desde carga inicial: $isChecked")
                }
            }
        }
    }
    private fun saveSettingChange(settingName: String, value: Boolean) {
        lifecycleScope.launch {
            Log.d(TAG, "Guardando configuración '$settingName': $value")
            if (!userProfileManager.isUserAuthenticated()) {
                Log.w(TAG, "Usuario no autenticado, no se puede guardar configuración")
                UIUtils.showErrorSnackbar(
                    binding.root,
                    "Debes estar autenticado para guardar configuraciones"
                )
                return@launch
            }
            val currentSettingsResult = userProfileManager.getUserSettings()
            currentSettingsResult.onSuccess { currentSettings ->
                val newSettings = when (settingName) {
                    "notifications" -> currentSettings.copy(notificationsEnabled = value)
                    "darkMode" -> currentSettings.copy(darkModeEnabled = value)
                    else -> currentSettings
                }
                val updateResult = userProfileManager.updateUserSettings(newSettings)
                updateResult.onSuccess {
                    Log.d(TAG, "Configuración '$settingName' guardada exitosamente: $value")
                    if (settingName == "darkMode") {
                        updateAppTheme(value)
                    }
                    _binding?.let { binding ->
                        UIUtils.showSuccessSnackbar(
                            binding.root,
                            getString(R.string.settings_saved)
                        )
                    } ?: run {
                        Log.w(TAG, "Binding es null, no se puede mostrar snackbar de éxito")
                    }
                }.onError { error ->
                    Log.e(TAG, "Error al guardar configuración '$settingName': ${error.message}", error.exception)
                    _binding?.let { binding ->
                        UIUtils.showErrorSnackbar(
                            binding.root,
                            error.message
                        )
                        revertSettingInUI(settingName, !value)
                    } ?: run {
                        Log.w(TAG, "Binding es null, no se puede mostrar snackbar de error ni revertir UI")
                    }
                }
            }.onError { error ->
                Log.e(TAG, "Error al obtener configuraciones actuales: ${error.message}", error.exception)
                _binding?.let { binding ->
                    UIUtils.showErrorSnackbar(
                        binding.root,
                        "Error al cargar configuraciones actuales"
                    )
                    revertSettingInUI(settingName, !value)
                } ?: run {
                    Log.w(TAG, "Binding es null, no se puede mostrar snackbar de error ni revertir UI")
                }
            }
        }
    }
    private fun revertSettingInUI(settingName: String, originalValue: Boolean) {
        _binding?.let { binding ->
            isLoadingSettings = true
            binding.apply {
                when (settingName) {
                    "notifications" -> switchNotifications.isChecked = originalValue
                    "darkMode" -> switchDarkMode.isChecked = originalValue
                }
            }
            binding.root.post {
                isLoadingSettings = false
            }
            Log.d(TAG, "Configuración '$settingName' revertida en UI a: $originalValue")
        } ?: run {
            Log.w(TAG, "Binding es null, no se puede revertir configuración '$settingName'")
        }
    }
    private fun configurePasswordChangeVisibility(isGoogleUser: Boolean) {
        try {
            binding.apply {
                if (isGoogleUser) {
                    layoutChangePassword.visibility = View.GONE
                    view?.findViewById<View>(R.id.separator_change_password)?.visibility = View.GONE
                    Log.d(TAG, "Opción de cambiar contraseña ocultada para usuario de Google")
                } else {
                    layoutChangePassword.visibility = View.VISIBLE
                    view?.findViewById<View>(R.id.separator_change_password)?.visibility = View.VISIBLE
                    Log.d(TAG, "Opción de cambiar contraseña mostrada para usuario con email/password")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al configurar visibilidad de cambiar contraseña: ${e.message}", e)
        }
    }
    private fun setupAccountOptions() {
        binding.apply {
            layoutChangePassword.setOnClickListener {
                UIUtils.bounceView(it)
                Log.d(TAG, "Navegando a cambio de contraseña")
                navigateToPasswordChange()
            }
            layoutNotificationSettings?.setOnClickListener {
                UIUtils.bounceView(it)
                Log.d(TAG, "Navegando a configuración de notificaciones")
                navigateToNotificationSettings()
            }
            layoutLogout.setOnClickListener {
                UIUtils.bounceView(it)
                Log.d(TAG, "Opción cerrar sesión seleccionada")
                showLogoutConfirmationDialog()
            }
        }
    }
    private fun showPermissionDialog(onResult: (Boolean) -> Unit) {
        if (permissionManager.shouldShowRequestPermissionRationale(requireActivity())) {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.notification_permission_title))
                .setMessage(getString(R.string.notification_permission_message))
                .setPositiveButton("Activar") { _, _ ->
                    requestNotificationPermission(onResult)
                }
                .setNegativeButton("Cancelar") { _, _ ->
                    onResult(false)
                }
                .show()
        } else {
            requestNotificationPermission(onResult)
        }
    }
    private fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        permissionCallback = onResult
        permissionManager.requestNotificationPermission(requireActivity())
    }
    private var permissionCallback: ((Boolean) -> Unit)? = null
    private fun navigateToNotificationSettings() {
        try {
            Log.d(TAG, "Iniciando navegación a NotificationSettingsActivity")
            val intent = Intent().apply {
                setClassName(requireContext(), "es.monsteraltech.skincare_tfm.notifications.NotificationSettingsActivity")
            }
            startActivity(intent)
            Log.d(TAG, "NotificationSettingsActivity lanzada exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al navegar a configuración de notificaciones: ${e.message}", e)
            UIUtils.showErrorSnackbar(
                binding.root,
                "Error al abrir configuración de notificaciones"
            )
        }
    }
    private fun navigateToPasswordChange() {
        try {
            Log.d(TAG, "Iniciando navegación a PasswordChangeActivity")
            if (!userProfileManager.isUserAuthenticated()) {
                Log.w(TAG, "Usuario no autenticado, no se puede cambiar contraseña")
                UIUtils.showErrorSnackbar(
                    binding.root,
                    "Debes estar autenticado para cambiar la contraseña"
                )
                return
            }
            lifecycleScope.launch {
                val userInfoResult = userProfileManager.getCurrentUserInfo()
                userInfoResult.onSuccess { userInfo ->
                    if (userInfo.isGoogleUser) {
                        Log.w(TAG, "Usuario de Google intentando cambiar contraseña")
                        UIUtils.showErrorSnackbar(
                            binding.root,
                            "Los usuarios de Google no pueden cambiar la contraseña desde la aplicación"
                        )
                        return@onSuccess
                    }
                    val intent = Intent(requireContext(), PasswordChangeActivity::class.java)
                    passwordChangeResultLauncher.launch(intent)
                    Log.d(TAG, "PasswordChangeActivity lanzada exitosamente")
                }.onError { error ->
                    Log.e(TAG, "Error al verificar tipo de usuario: ${error.message}")
                    UIUtils.showErrorSnackbar(
                        binding.root,
                        "Error al verificar información del usuario"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al navegar a cambio de contraseña: ${e.message}", e)
            UIUtils.showErrorSnackbar(
                binding.root,
                "Error al abrir cambio de contraseña"
            )
        }
    }
    private fun handlePasswordChangeResult(resultCode: Int) {
        Log.d(TAG, "Resultado de cambio de contraseña recibido: $resultCode")
        when (resultCode) {
            Activity.RESULT_OK -> {
                Log.d(TAG, "Cambio de contraseña exitoso")
                UIUtils.showSuccessSnackbar(
                    binding.root,
                    getString(R.string.password_change_success_message)
                )
            }
            Activity.RESULT_CANCELED -> {
                Log.d(TAG, "Cambio de contraseña cancelado por el usuario")
            }
            else -> {
                Log.w(TAG, "Resultado inesperado del cambio de contraseña: $resultCode")
                UIUtils.showErrorSnackbar(
                    binding.root,
                    "Error inesperado en el cambio de contraseña"
                )
            }
        }
    }
    private fun showLogoutConfirmationDialog() {
        Log.d(TAG, "Mostrando diálogo de confirmación de logout")
        try {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.account_logout))
                .setMessage(getString(R.string.account_logout_confirm))
                .setPositiveButton(getString(R.string.account_logout_confirm_button)) { _, _ ->
                    Log.d(TAG, "Usuario confirmó logout")
                    performLogout()
                }
                .setNegativeButton(getString(R.string.account_logout_cancel)) { dialog, _ ->
                    Log.d(TAG, "Usuario canceló logout")
                    dialog.dismiss()
                }
                .setCancelable(true)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error al mostrar diálogo de confirmación: ${e.message}", e)
            UIUtils.showErrorSnackbar(
                binding.root,
                "Error al mostrar confirmación"
            )
        }
    }
    private fun performLogout() {
        lifecycleScope.launch {
            Log.d(TAG, "Iniciando proceso de logout")
            val result = userProfileManager.signOut()
            result.onSuccess {
                Log.d(TAG, "Logout ejecutado exitosamente")
                clearApplicationState()
                navigateToLogin()
            }.onError { error ->
                Log.e(TAG, "Error durante el logout: ${error.message}", error.exception)
                UIUtils.showErrorSnackbar(
                    binding.root,
                    error.message
                )
                if (error.errorType == AccountResult.ErrorType.AUTHENTICATION_ERROR) {
                    Log.d(TAG, "Error de autenticación durante logout, navegando al login de todas formas")
                    clearApplicationState()
                    navigateToLogin()
                }
            }
        }
    }
    private fun clearApplicationState() {
        Log.d(TAG, "Limpiando estado de la aplicación")
        try {
            _binding?.let { binding ->
                binding.apply {
                    layoutUserInfoContent.visibility = View.GONE
                    tvUserName.text = ""
                    tvUserEmail.text = ""
                    layoutLoading.visibility = View.GONE
                    tvError.visibility = View.GONE
                }
            }
            Log.d(TAG, "Estado de la aplicación limpiado exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al limpiar estado de la aplicación: ${e.message}", e)
        }
    }
    private fun navigateToLogin() {
        Log.d(TAG, "Navegando a LoginActivity")
        try {
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
            Log.d(TAG, "Navegación a LoginActivity completada")
        } catch (e: Exception) {
            Log.e(TAG, "Error al navegar a LoginActivity: ${e.message}", e)
            UIUtils.showErrorSnackbar(
                binding.root,
                "Error al navegar al login"
            )
        }
    }
    private fun updateAppTheme(darkModeEnabled: Boolean) {
        try {
            val mainActivity = activity as? MainActivity
            mainActivity?.let { activity ->
                val themeManager = activity.getThemeManager()
                themeManager.updateThemeFromSettings(activity, darkModeEnabled)
                Log.d(TAG, "Tema de la aplicación actualizado: ${if (darkModeEnabled) "OSCURO" else "CLARO"}")
            } ?: run {
                Log.w(TAG, "No se pudo obtener MainActivity para actualizar el tema")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar el tema de la aplicación: ${e.message}", e)
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
                val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "Resultado de permisos de notificaciones: $granted")
                if (!granted) {
                    Toast.makeText(requireContext(),
                        getString(R.string.notification_permission_denied),
                        Toast.LENGTH_LONG).show()
                }
                permissionCallback?.invoke(granted)
                permissionCallback = null
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
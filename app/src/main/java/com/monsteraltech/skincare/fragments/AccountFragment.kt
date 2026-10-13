package com.monsteraltech.skincare.fragments
import android.app.Activity
import android.content.Intent
import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModelProvider
import com.monsteraltech.skincare.account.AccountViewModel
import com.monsteraltech.skincare.BuildConfig
import com.monsteraltech.skincare.MainActivity
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.account.AccountResult
import com.monsteraltech.skincare.account.PasswordChangeActivity
import com.monsteraltech.skincare.account.UserInfo
import com.monsteraltech.skincare.backup.BackupActivity
import com.monsteraltech.skincare.camera.guidance.ExperimentalCapturePreferences
import dagger.hilt.android.AndroidEntryPoint
import com.monsteraltech.skincare.databinding.FragmentAccountBinding
import com.monsteraltech.skincare.notifications.NotificationPermissionManager
import com.monsteraltech.skincare.utils.UIUtils
import kotlinx.coroutines.launch
@AndroidEntryPoint
class AccountFragment : Fragment() {
    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!
    private val viewModel by lazy { ViewModelProvider(this)[AccountViewModel::class.java] }
    private var lastRenderedInfo: AccountResult<UserInfo>? = null
    private var permissionDialog: AlertDialog? = null
    private lateinit var permissionManager: NotificationPermissionManager
    private lateinit var experimentalCapturePreferences: ExperimentalCapturePreferences
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
        permissionManager = NotificationPermissionManager(requireContext())
        experimentalCapturePreferences = ExperimentalCapturePreferences(requireContext())
        animateUIElements()
        setupAccountOptions()
        binding.analysisProfileButton.setOnClickListener {
            startActivity(Intent(requireContext(), com.monsteraltech.skincare.account.AnalysisProfileActivity::class.java))
        }
        setupSettingsListeners()
        setupExperimentalFeatures()
        observeAccount()
        viewModel.refresh()
    }

    private fun setupExperimentalFeatures() {
        val isAvailable = BuildConfig.CAPTURE_VALIDITY_MODEL_ENABLED
        binding.separatorCaptureValidity.visibility = if (isAvailable) View.VISIBLE else View.GONE
        binding.layoutCaptureValiditySetting.visibility = if (isAvailable) View.VISIBLE else View.GONE

        if (!isAvailable) return

        binding.switchCaptureValidity.isChecked =
            experimentalCapturePreferences.captureValidityEnabled
        binding.switchCaptureValidity.setOnCheckedChangeListener { _, isChecked ->
            experimentalCapturePreferences.captureValidityEnabled = isChecked
        }
        binding.layoutCaptureValiditySetting.setOnClickListener {
            binding.switchCaptureValidity.toggle()
        }
    }
    private fun animateUIElements() {
        binding.apply {
            UIUtils.animateListItem(cardUserInfo, 0)
            UIUtils.animateListItem(cardSettings, 1)
            UIUtils.animateListItem(cardBackup, 2)
            UIUtils.animateListItem(cardLogout, 3)
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
            if (userInfo.isLocalProfile) {
                tvUserEmail.text = getString(R.string.local_profile_description)
            } else if (!email.isNullOrBlank()) {
                tvUserEmail.text = email
                Log.d(TAG, "Email configurado: $email")
            } else {
                tvUserEmail.text = "Sin email configurado"
                Log.d(TAG, "Email no disponible, usando texto por defecto")
            }
            configurePasswordChangeVisibility(userInfo.isGoogleUser || userInfo.isLocalProfile)
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
    private fun observeAccount() {
        renderAccount(viewModel.uiState.value)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { renderAccount(it) } }
                launch { viewModel.events.collect { event ->
                    when (event) {
                        AccountViewModel.Event.SettingsSaved -> UIUtils.showSuccessToast(requireContext(), getString(R.string.settings_saved))
                        is AccountViewModel.Event.Error -> UIUtils.showErrorToast(requireContext(), getString(event.message))
                    }
                } }
            }
        }
    }
    private fun renderAccount(state: AccountViewModel.UiState) {
        if (state.userInfo != lastRenderedInfo) {
            lastRenderedInfo = state.userInfo
            when (val info = state.userInfo) {
                is AccountResult.Loading -> showLoadingState()
                is AccountResult.Success -> showUserInformation(info.data)
                is AccountResult.Error -> showErrorState(info.message)
            }
        }
        binding.layoutChangePassword.isEnabled = state.userInfo is AccountResult.Success
        isLoadingSettings = true
        try {
            state.settings?.let { settings ->
                binding.switchNotifications.isChecked = settings.notificationsEnabled
                binding.switchDarkMode.isChecked = settings.darkModeEnabled
            }
        } finally { isLoadingSettings = false }
        val editable = state.settings != null && !state.isSaving
        binding.switchNotifications.isEnabled = editable
        binding.switchDarkMode.isEnabled = editable
    }
    private fun setupSettingsListeners() {
        binding.switchNotifications.setOnCheckedChangeListener { _, enabled ->
            if (isLoadingSettings) return@setOnCheckedChangeListener
            if (enabled && !permissionManager.hasNotificationPermission()) {
                showPermissionDialog { granted ->
                    if (granted) viewModel.changeSetting(AccountViewModel.Setting.NOTIFICATIONS, true)
                    else {
                        isLoadingSettings = true
                        try { _binding?.switchNotifications?.isChecked = false }
                        finally { isLoadingSettings = false }
                    }
                }
            } else {
                viewModel.changeSetting(AccountViewModel.Setting.NOTIFICATIONS, enabled)
                if (!enabled) com.monsteraltech.skincare.notifications.NotificationManager(requireContext()).cancelAllNotifications()
            }
        }
        binding.switchDarkMode.setOnCheckedChangeListener { _, enabled ->
            if (!isLoadingSettings) viewModel.changeSetting(AccountViewModel.Setting.DARK_MODE, enabled)
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
            layoutBackup.setOnClickListener {
                UIUtils.bounceView(it)
                startActivity(Intent(requireContext(), BackupActivity::class.java))
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
            permissionDialog = AlertDialog.Builder(requireContext())
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
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    private var permissionCallback: ((Boolean) -> Unit)? = null
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val callback = permissionCallback
        permissionCallback = null
        if (_binding != null) {
            if (!granted) UIUtils.showErrorToast(requireContext(), getString(R.string.notification_permission_denied))
            callback?.invoke(granted)
        }
    }
    private fun navigateToNotificationSettings() {
        try {
            Log.d(TAG, "Iniciando navegación a NotificationSettingsActivity")
            val intent = Intent().apply {
                setClassName(requireContext(), "com.monsteraltech.skincare.notifications.NotificationSettingsActivity")
            }
            startActivity(intent)
            Log.d(TAG, "NotificationSettingsActivity lanzada exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al navegar a configuración de notificaciones: ${e.message}", e)
            UIUtils.showErrorToast(
                requireContext(),
                getString(R.string.error_unknown)
            )
        }
    }
    private fun navigateToPasswordChange() {
        val info = viewModel.uiState.value.userInfo.getDataOrNull() ?: return
        if (info.isGoogleUser || info.isLocalProfile) {
            UIUtils.showInfoToast(requireContext(), getString(R.string.account_google_user_info))
            return
        }
        passwordChangeResultLauncher.launch(Intent(requireContext(), PasswordChangeActivity::class.java))
    }
    private fun handlePasswordChangeResult(resultCode: Int) {
        Log.d(TAG, "Resultado de cambio de contraseña recibido: $resultCode")
        when (resultCode) {
            Activity.RESULT_OK -> {
                Log.d(TAG, "Cambio de contraseña exitoso")
                UIUtils.showSuccessToast(
                    requireContext(),
                    getString(R.string.password_change_success_message)
                )
            }
            Activity.RESULT_CANCELED -> {
                Log.d(TAG, "Cambio de contraseña cancelado por el usuario")
            }
            else -> {
                Log.w(TAG, "Resultado inesperado del cambio de contraseña: $resultCode")
                UIUtils.showErrorToast(
                    requireContext(),
                    getString(R.string.error_unknown)
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
            UIUtils.showErrorToast(
                requireContext(),
                getString(R.string.error_unknown)
            )
        }
    }
    private fun performLogout() {
        Log.d(TAG, "Iniciando proceso de logout")
        val mainActivity = activity as? MainActivity
        if (mainActivity == null) {
            UIUtils.showErrorToast(
                requireContext(),
                getString(R.string.error_unknown)
            )
            return
        }
        mainActivity.logoutWithProgress()
    }
    override fun onDestroyView() {
        permissionDialog?.dismiss()
        permissionDialog = null
        permissionCallback = null
        lastRenderedInfo = null
        isLoadingSettings = false
        super.onDestroyView()
        _binding = null
    }
}

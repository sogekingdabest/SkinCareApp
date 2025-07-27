package es.monsteraltech.skincare_tfm.fragments

import android.app.Activity
import android.content.Intent
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
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.account.AccountResult
import es.monsteraltech.skincare_tfm.account.AccountSettings
import es.monsteraltech.skincare_tfm.account.PasswordChangeActivity
import es.monsteraltech.skincare_tfm.account.UserInfo
import es.monsteraltech.skincare_tfm.account.UserProfileManager
import es.monsteraltech.skincare_tfm.databinding.FragmentAccountBinding
import es.monsteraltech.skincare_tfm.login.LoginActivity
import es.monsteraltech.skincare_tfm.utils.UIUtils
import kotlinx.coroutines.launch

/**
 * Fragment para gestión de cuenta de usuario
 * Muestra información del usuario y proporciona acceso a opciones de cuenta
 */
class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var userProfileManager: UserProfileManager
    
    private val TAG = "AccountFragment"

    // Activity result launcher para manejar el resultado del cambio de contraseña
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
        
        // Inicializar UserProfileManager
        userProfileManager = UserProfileManager()
        
        // Apply entrance animations to UI elements
        animateUIElements()
        
        // Cargar información del usuario
        loadUserInformation()
        
        // Cargar configuraciones de usuario
        loadUserSettings()
        
        // Configurar listeners para opciones de cuenta
        setupAccountOptions()
        
        // Configurar listeners para configuraciones
        setupSettingsListeners()
    }

    /**
     * Aplica animaciones de entrada a los elementos de la UI
     */
    private fun animateUIElements() {
        binding.apply {
            // Animate cards with staggered entrance
            UIUtils.animateListItem(cardUserInfo, 0)
            UIUtils.animateListItem(cardAccountOptions, 1)
            UIUtils.animateListItem(cardLogout, 2)
        }
    }

    /**
     * Carga y muestra la información del usuario actual con manejo de estados
     */
    private fun loadUserInformation() {
        lifecycleScope.launch {
            Log.d(TAG, "Iniciando carga de información de usuario")
            
            // Mostrar estado de carga
            showLoadingState()
            
            // Obtener información del usuario usando AccountResult
            val result = userProfileManager.getCurrentUserInfo()
            
            result.onSuccess { userInfo ->
                Log.d(TAG, "Información de usuario cargada exitosamente: ${userInfo.email}")
                showUserInformation(userInfo)
            }.onError { error ->
                Log.e(TAG, "Error al cargar información del usuario: ${error.message}", error.exception)
                showErrorState(error.message)
                
                // Si es error de autenticación, podríamos redirigir al login
                if (error.errorType == AccountResult.ErrorType.AUTHENTICATION_ERROR) {
                    // Opcional: redirigir al login automáticamente
                    // navigateToLogin()
                }
            }.onLoading { loading ->
                Log.d(TAG, "Cargando información de usuario: ${loading.message}")
                showLoadingState()
            }
        }
    }

    /**
     * Muestra el estado de carga en la UI
     */
    private fun showLoadingState() {
        binding.apply {
            UIUtils.crossFadeViews(layoutUserInfoContent, layoutLoading)
            UIUtils.fadeOut(tvError)
        }
    }

    /**
     * Muestra la información del usuario en la UI
     */
    private fun showUserInformation(userInfo: UserInfo) {
        binding.apply {
            // Cross fade from loading to content
            UIUtils.crossFadeViews(layoutLoading, layoutUserInfoContent)
            UIUtils.fadeOut(tvError)
            
            // Configurar nombre del usuario
            val displayName = userInfo.displayName
            if (!displayName.isNullOrBlank()) {
                tvUserName.text = displayName
            } else {
                tvUserName.text = "Sin nombre configurado"
            }
            
            // Configurar email del usuario
            val email = userInfo.email
            if (!email.isNullOrBlank()) {
                tvUserEmail.text = email
            } else {
                tvUserEmail.text = "Sin email configurado"
            }
            
            // Animate user info elements
            UIUtils.fadeIn(tvUserName, 300)
            UIUtils.fadeIn(tvUserEmail, 400)
        }
        
        Log.d(TAG, "Información de usuario mostrada en UI")
    }

    /**
     * Muestra el estado de error en la UI
     */
    private fun showErrorState(errorMessage: String) {
        binding.apply {
            UIUtils.fadeOut(layoutLoading)
            UIUtils.fadeOut(layoutUserInfoContent)
            
            tvError.text = errorMessage
            UIUtils.fadeIn(tvError)
            UIUtils.shakeView(tvError)
        }
        
        Log.d(TAG, "Estado de error mostrado: $errorMessage")
    }

    /**
     * Carga las configuraciones del usuario desde Firestore con manejo de estados
     */
    private fun loadUserSettings() {
        lifecycleScope.launch {
            Log.d(TAG, "Cargando configuraciones de usuario")
            
            // Obtener configuraciones del usuario usando AccountResult
            val result = userProfileManager.getUserSettings()
            
            result.onSuccess { settings ->
                Log.d(TAG, "Configuraciones de usuario cargadas exitosamente")
                applySettingsToUI(settings)
            }.onError { error ->
                Log.e(TAG, "Error al cargar configuraciones de usuario: ${error.message}", error.exception)
                // En caso de error, usar configuraciones por defecto
                applySettingsToUI(AccountSettings())
            }
        }
    }

    /**
     * Aplica las configuraciones cargadas a los elementos de la UI
     */
    private fun applySettingsToUI(settings: AccountSettings) {
        binding.apply {
            switchNotifications.isChecked = settings.notificationsEnabled
            switchDarkMode.isChecked = settings.darkModeEnabled
            switchAutoBackup.isChecked = settings.autoBackup
        }
        
        Log.d(TAG, "Configuraciones aplicadas a la UI: notifications=${settings.notificationsEnabled}, darkMode=${settings.darkModeEnabled}, autoBackup=${settings.autoBackup}")
    }

    /**
     * Configura los listeners para los switches de configuraciones
     */
    private fun setupSettingsListeners() {
        binding.apply {
            // Listener para notificaciones
            switchNotifications.setOnCheckedChangeListener { _, isChecked ->
                Log.d(TAG, "Configuración de notificaciones cambiada: $isChecked")
                saveSettingChange("notifications", isChecked)
            }
            
            // Listener para modo oscuro
            switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
                Log.d(TAG, "Configuración de modo oscuro cambiada: $isChecked")
                saveSettingChange("darkMode", isChecked)
            }
            
            // Listener para respaldo automático
            switchAutoBackup.setOnCheckedChangeListener { _, isChecked ->
                Log.d(TAG, "Configuración de respaldo automático cambiada: $isChecked")
                saveSettingChange("autoBackup", isChecked)
            }
        }
    }

    /**
     * Guarda un cambio de configuración específico con manejo de estados
     */
    private fun saveSettingChange(settingName: String, value: Boolean) {
        lifecycleScope.launch {
            Log.d(TAG, "Guardando configuración '$settingName': $value")
            
            // Verificar si el usuario está autenticado
            if (!userProfileManager.isUserAuthenticated()) {
                Log.w(TAG, "Usuario no autenticado, no se puede guardar configuración")
                UIUtils.showErrorSnackbar(
                    binding.root,
                    "Debes estar autenticado para guardar configuraciones"
                )
                return@launch
            }
            
            // Obtener configuraciones actuales
            val currentSettingsResult = userProfileManager.getUserSettings()
            
            currentSettingsResult.onSuccess { currentSettings ->
                // Crear nuevas configuraciones con el cambio aplicado
                val newSettings = when (settingName) {
                    "notifications" -> currentSettings.copy(notificationsEnabled = value)
                    "darkMode" -> currentSettings.copy(darkModeEnabled = value)
                    "autoBackup" -> currentSettings.copy(autoBackup = value)
                    else -> currentSettings
                }
                
                // Guardar las nuevas configuraciones
                val updateResult = userProfileManager.updateUserSettings(newSettings)
                
                updateResult.onSuccess {
                    Log.d(TAG, "Configuración '$settingName' guardada exitosamente: $value")
                    UIUtils.showSuccessSnackbar(
                        binding.root,
                        getString(R.string.settings_saved)
                    )
                }.onError { error ->
                    Log.e(TAG, "Error al guardar configuración '$settingName': ${error.message}", error.exception)
                    UIUtils.showErrorSnackbar(
                        binding.root,
                        error.message
                    )
                    
                    // Revertir el cambio en la UI si falló el guardado
                    revertSettingInUI(settingName, !value)
                }
                
            }.onError { error ->
                Log.e(TAG, "Error al obtener configuraciones actuales: ${error.message}", error.exception)
                UIUtils.showErrorSnackbar(
                    binding.root,
                    "Error al cargar configuraciones actuales"
                )
                
                // Revertir el cambio en la UI
                revertSettingInUI(settingName, !value)
            }
        }
    }
    
    /**
     * Revierte un cambio de configuración en la UI
     */
    private fun revertSettingInUI(settingName: String, originalValue: Boolean) {
        binding.apply {
            when (settingName) {
                "notifications" -> switchNotifications.isChecked = originalValue
                "darkMode" -> switchDarkMode.isChecked = originalValue
                "autoBackup" -> switchAutoBackup.isChecked = originalValue
            }
        }
        Log.d(TAG, "Configuración '$settingName' revertida en UI a: $originalValue")
    }

    /**
     * Configura los listeners para las opciones de cuenta
     */
    private fun setupAccountOptions() {
        binding.apply {
            // Navegación a cambio de contraseña
            layoutChangePassword.setOnClickListener {
                UIUtils.bounceView(it)
                Log.d(TAG, "Navegando a cambio de contraseña")
                navigateToPasswordChange()
            }
            
            layoutLogout.setOnClickListener {
                UIUtils.bounceView(it)
                Log.d(TAG, "Opción cerrar sesión seleccionada")
                showLogoutConfirmationDialog()
            }
        }
    }

    /**
     * Navega a la actividad de cambio de contraseña
     */
    private fun navigateToPasswordChange() {
        try {
            Log.d(TAG, "Iniciando navegación a PasswordChangeActivity")
            
            // Verificar que el usuario esté autenticado antes de navegar
            if (!userProfileManager.isUserAuthenticated()) {
                Log.w(TAG, "Usuario no autenticado, no se puede cambiar contraseña")
                UIUtils.showErrorSnackbar(
                    binding.root,
                    "Debes estar autenticado para cambiar la contraseña"
                )
                return
            }
            
            // Crear intent para PasswordChangeActivity
            val intent = Intent(requireContext(), PasswordChangeActivity::class.java)
            
            // Lanzar la actividad y esperar resultado
            passwordChangeResultLauncher.launch(intent)
            
            Log.d(TAG, "PasswordChangeActivity lanzada exitosamente")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al navegar a cambio de contraseña: ${e.message}", e)
            UIUtils.showErrorSnackbar(
                binding.root,
                "Error al abrir cambio de contraseña"
            )
        }
    }

    /**
     * Maneja el resultado de la actividad de cambio de contraseña
     */
    private fun handlePasswordChangeResult(resultCode: Int) {
        Log.d(TAG, "Resultado de cambio de contraseña recibido: $resultCode")
        
        when (resultCode) {
            Activity.RESULT_OK -> {
                // Cambio de contraseña exitoso
                Log.d(TAG, "Cambio de contraseña exitoso")
                UIUtils.showSuccessSnackbar(
                    binding.root,
                    getString(R.string.password_change_success_message)
                )
            }
            Activity.RESULT_CANCELED -> {
                // Usuario canceló el cambio de contraseña
                Log.d(TAG, "Cambio de contraseña cancelado por el usuario")
                // No mostrar mensaje, es una acción normal del usuario
            }
            else -> {
                // Error inesperado
                Log.w(TAG, "Resultado inesperado del cambio de contraseña: $resultCode")
                UIUtils.showErrorSnackbar(
                    binding.root,
                    "Error inesperado en el cambio de contraseña"
                )
            }
        }
    }

    /**
     * Muestra el diálogo de confirmación para cerrar sesión
     */
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

    /**
     * Ejecuta el proceso de logout y navega a LoginActivity con manejo de estados
     */
    private fun performLogout() {
        lifecycleScope.launch {
            Log.d(TAG, "Iniciando proceso de logout")
            
            // Ejecutar logout usando UserProfileManager con AccountResult
            val result = userProfileManager.signOut()
            
            result.onSuccess {
                Log.d(TAG, "Logout ejecutado exitosamente")
                
                // Limpiar datos de sesión y estado de la aplicación
                clearApplicationState()
                
                // Navegar a LoginActivity
                navigateToLogin()
                
            }.onError { error ->
                Log.e(TAG, "Error durante el logout: ${error.message}", error.exception)
                
                // Mostrar error al usuario
                UIUtils.showErrorSnackbar(
                    binding.root,
                    error.message
                )
                
                // En caso de error de logout, aún podemos intentar navegar al login
                // ya que es mejor estar seguro y cerrar la sesión localmente
                if (error.errorType == AccountResult.ErrorType.AUTHENTICATION_ERROR) {
                    Log.d(TAG, "Error de autenticación durante logout, navegando al login de todas formas")
                    clearApplicationState()
                    navigateToLogin()
                }
            }
        }
    }

    /**
     * Limpia el estado de la aplicación después del logout
     */
    private fun clearApplicationState() {
        Log.d(TAG, "Limpiando estado de la aplicación")
        
        try {
            // Limpiar binding y datos del fragment
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

    /**
     * Navega a LoginActivity y finaliza la actividad actual
     */
    private fun navigateToLogin() {
        Log.d(TAG, "Navegando a LoginActivity")
        
        try {
            // Crear intent para LoginActivity
            val intent = Intent(requireContext(), LoginActivity::class.java)
            
            // Limpiar el stack de actividades para que el usuario no pueda volver atrás
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            
            // Iniciar LoginActivity
            startActivity(intent)
            
            // Finalizar la actividad actual
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
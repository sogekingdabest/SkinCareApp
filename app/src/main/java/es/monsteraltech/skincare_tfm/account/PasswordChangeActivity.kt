package es.monsteraltech.skincare_tfm.account

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.utils.RealTimeValidator
import es.monsteraltech.skincare_tfm.utils.UIUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PasswordChangeActivity : AppCompatActivity() {

    // UI Components
    private lateinit var currentPasswordLayout: TextInputLayout
    private lateinit var newPasswordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout
    private lateinit var currentPasswordEditText: TextInputEditText
    private lateinit var newPasswordEditText: TextInputEditText
    private lateinit var confirmPasswordEditText: TextInputEditText
    private lateinit var changePasswordButton: MaterialButton
    private lateinit var cancelButton: MaterialButton
    private lateinit var loadingOverlay: View
    private lateinit var loadingProgressBar: View

    // Password Change Manager
    private lateinit var passwordChangeManager: PasswordChangeManager

    // Real-time validator
    private lateinit var validator: RealTimeValidator

    // Firebase
    private lateinit var auth: FirebaseAuth
    private var currentUser: FirebaseUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_change)

        // Apply enter animation
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)

        initializeFirebase()
        initializeViews()
        setupRealTimeValidation()
        setupClickListeners()
    }

    private fun initializeFirebase() {
        auth = FirebaseAuth.getInstance()
        currentUser = auth.currentUser
        passwordChangeManager = PasswordChangeManager()
        
        // If user is not authenticated, finish activity
        if (currentUser == null) {
            Toast.makeText(this, "Error de autenticación", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    private fun initializeViews() {
        currentPasswordLayout = findViewById(R.id.currentPasswordLayout)
        newPasswordLayout = findViewById(R.id.newPasswordLayout)
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout)
        currentPasswordEditText = findViewById(R.id.currentPasswordEditText)
        newPasswordEditText = findViewById(R.id.newPasswordEditText)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText)
        changePasswordButton = findViewById(R.id.changePasswordButton)
        cancelButton = findViewById(R.id.cancelButton)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)

        // Apply fade in animation to form elements
        UIUtils.fadeIn(currentPasswordLayout)
        UIUtils.fadeIn(newPasswordLayout, 100)
        UIUtils.fadeIn(confirmPasswordLayout, 200)
    }

    private fun setupRealTimeValidation() {
        validator = RealTimeValidator()

        // Setup password validation with real-time feedback
        validator.setupPasswordValidation(
            newPasswordEditText,
            newPasswordLayout,
            confirmPasswordEditText,
            confirmPasswordLayout
        )

        // Add validation for current password
        validator.addField(
            currentPasswordEditText,
            currentPasswordLayout,
            RealTimeValidator.Validators.required("Ingresa tu contraseña actual")
        )

        // Enable/disable button based on validation status
        setupButtonStateListener()
    }

    private fun setupButtonStateListener() {
        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateButtonState()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }

        currentPasswordEditText.addTextChangedListener(textWatcher)
        newPasswordEditText.addTextChangedListener(textWatcher)
        confirmPasswordEditText.addTextChangedListener(textWatcher)
    }

    private fun updateButtonState() {
        val hasCurrentPassword = !currentPasswordEditText.text.isNullOrBlank()
        val hasNewPassword = !newPasswordEditText.text.isNullOrBlank()
        val hasConfirmPassword = !confirmPasswordEditText.text.isNullOrBlank()

        val shouldEnable = hasCurrentPassword && hasNewPassword && hasConfirmPassword
        
        if (changePasswordButton.isEnabled != shouldEnable) {
            changePasswordButton.isEnabled = shouldEnable
            
            // Add visual feedback when button becomes enabled
            if (shouldEnable) {
                UIUtils.pulseView(changePasswordButton)
            }
        }
    }

    private fun setupClickListeners() {
        changePasswordButton.setOnClickListener {
            // Add bounce animation for button feedback
            UIUtils.bounceView(it)
            
            if (validator.validateAll()) {
                changePassword()
            } else {
                // Show error feedback
                UIUtils.shakeView(changePasswordButton)
            }
        }

        cancelButton.setOnClickListener {
            UIUtils.bounceView(it)
            finishWithAnimation()
        }

        // Clear errors when user starts typing - handled by RealTimeValidator
        // But we can add focus animations
        currentPasswordEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                UIUtils.pulseView(currentPasswordLayout, false)
            }
        }

        newPasswordEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                UIUtils.pulseView(newPasswordLayout, false)
            }
        }

        confirmPasswordEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                UIUtils.pulseView(confirmPasswordLayout, false)
            }
        }
    }



    private fun changePassword() {
        val currentPassword = currentPasswordEditText.text.toString().trim()
        val newPassword = newPasswordEditText.text.toString().trim()

        showLoading(true)

        CoroutineScope(Dispatchers.Main).launch {
            val result = passwordChangeManager.changePassword(currentPassword, newPassword)
            
            showLoading(false)
            
            when (result) {
                is AccountResult.Success -> {
                    // Show success feedback with snackbar
                    UIUtils.showSuccessSnackbar(
                        findViewById(android.R.id.content),
                        getString(R.string.password_change_success)
                    )
                    
                    // Establecer resultado exitoso para comunicar a AccountFragment
                    setResult(RESULT_OK)
                    
                    // Delay finish to show success message
                    findViewById<View>(android.R.id.content).postDelayed({
                        finishWithAnimation()
                    }, 1500)
                }
                is AccountResult.Error -> {
                    handlePasswordChangeError(result)
                }
                is AccountResult.Loading -> {
                    // This shouldn't happen here since we handle loading separately
                    // but we include it for completeness
                }
            }
        }
    }

    private fun handlePasswordChangeError(error: AccountResult.Error<Unit>) {
        when (error.errorType) {
            AccountResult.ErrorType.VALIDATION_ERROR -> {
                // For validation errors, show the specific message in the appropriate field
                if (error.message.contains("actual", ignoreCase = true)) {
                    currentPasswordLayout.error = error.message
                    UIUtils.shakeView(currentPasswordLayout)
                } else {
                    newPasswordLayout.error = error.message
                    UIUtils.shakeView(newPasswordLayout)
                }
            }
            AccountResult.ErrorType.NETWORK_ERROR -> {
                UIUtils.showErrorSnackbar(
                    findViewById(android.R.id.content),
                    error.message
                )
            }
            AccountResult.ErrorType.AUTHENTICATION_ERROR -> {
                UIUtils.showErrorSnackbar(
                    findViewById(android.R.id.content),
                    error.message
                )
                // Delay finish to show error message
                findViewById<View>(android.R.id.content).postDelayed({
                    finishWithAnimation()
                }, 2000)
            }
            AccountResult.ErrorType.FIREBASE_ERROR -> {
                UIUtils.showErrorSnackbar(
                    findViewById(android.R.id.content),
                    error.message
                )
            }
            else -> {
                UIUtils.showErrorSnackbar(
                    findViewById(android.R.id.content),
                    error.message
                )
            }
        }
    }

    private fun finishWithAnimation() {
        // Apply exit animation
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up validator
        if (::validator.isInitialized) {
            validator.removeAllValidations()
        }
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            loadingOverlay.visibility = View.VISIBLE
            loadingProgressBar.visibility = View.VISIBLE
            changePasswordButton.isEnabled = false
            cancelButton.isEnabled = false
            
            // Disable input fields during loading
            currentPasswordEditText.isEnabled = false
            newPasswordEditText.isEnabled = false
            confirmPasswordEditText.isEnabled = false
            
            // Clear any existing errors
            currentPasswordLayout.error = null
            newPasswordLayout.error = null
            confirmPasswordLayout.error = null
        } else {
            loadingOverlay.visibility = View.GONE
            loadingProgressBar.visibility = View.GONE
            changePasswordButton.isEnabled = true
            cancelButton.isEnabled = true
            
            // Re-enable input fields
            currentPasswordEditText.isEnabled = true
            newPasswordEditText.isEnabled = true
            confirmPasswordEditText.isEnabled = true
        }
    }
}
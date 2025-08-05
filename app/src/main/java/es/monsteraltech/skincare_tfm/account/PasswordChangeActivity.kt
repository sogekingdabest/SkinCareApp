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
    private lateinit var passwordChangeManager: PasswordChangeManager
    private lateinit var validator: RealTimeValidator
    private lateinit var auth: FirebaseAuth
    private var currentUser: FirebaseUser? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_change)
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
        UIUtils.fadeIn(currentPasswordLayout)
        UIUtils.fadeIn(newPasswordLayout, 100)
        UIUtils.fadeIn(confirmPasswordLayout, 200)
    }
    private fun setupRealTimeValidation() {
        validator = RealTimeValidator()
        validator.setupPasswordValidation(
            newPasswordEditText,
            newPasswordLayout,
            confirmPasswordEditText,
            confirmPasswordLayout
        )
        validator.addField(
            currentPasswordEditText,
            currentPasswordLayout,
            RealTimeValidator.Validators.required("Ingresa tu contraseña actual")
        )
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
            if (shouldEnable) {
                UIUtils.pulseView(changePasswordButton)
            }
        }
    }
    private fun setupClickListeners() {
        changePasswordButton.setOnClickListener {
            UIUtils.bounceView(it)
            if (validator.validateAll()) {
                changePassword()
            } else {
                UIUtils.shakeView(changePasswordButton)
            }
        }
        cancelButton.setOnClickListener {
            UIUtils.bounceView(it)
            finishWithAnimation()
        }
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
                    UIUtils.showSuccessSnackbar(
                        findViewById(android.R.id.content),
                        getString(R.string.password_change_success)
                    )
                    setResult(RESULT_OK)
                    findViewById<View>(android.R.id.content).postDelayed({
                        finishWithAnimation()
                    }, 1500)
                }
                is AccountResult.Error -> {
                    handlePasswordChangeError(result)
                }
                is AccountResult.Loading -> {
                }
            }
        }
    }
    private fun handlePasswordChangeError(error: AccountResult.Error<Unit>) {
        when (error.errorType) {
            AccountResult.ErrorType.VALIDATION_ERROR -> {
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
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right)
        finish()
    }
    override fun onDestroy() {
        super.onDestroy()
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
            currentPasswordEditText.isEnabled = false
            newPasswordEditText.isEnabled = false
            confirmPasswordEditText.isEnabled = false
            currentPasswordLayout.error = null
            newPasswordLayout.error = null
            confirmPasswordLayout.error = null
        } else {
            loadingOverlay.visibility = View.GONE
            loadingProgressBar.visibility = View.GONE
            changePasswordButton.isEnabled = true
            cancelButton.isEnabled = true
            currentPasswordEditText.isEnabled = true
            newPasswordEditText.isEnabled = true
            confirmPasswordEditText.isEnabled = true
        }
    }
}
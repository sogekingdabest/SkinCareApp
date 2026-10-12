package com.monsteraltech.skincare.login
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.util.Pair
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.google.android.material.textfield.TextInputLayout
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.utils.UIUtils
@AndroidEntryPoint
class ForgotPasswordActivity : AppCompatActivity() {
    private val viewModel: ForgotPasswordViewModel by viewModels()
    private lateinit var logoImageView: ImageView
    private lateinit var welcomeTextView: TextView
    private lateinit var instructionsTextView: TextView
    private lateinit var emailEditText: TextInputLayout
    private lateinit var recuperarButton: Button
    private lateinit var loginTextView: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        val initialEmail = intent.getStringExtra("password")
        logoImageView = findViewById(R.id.logoImageView)
        welcomeTextView = findViewById(R.id.welcomeTextView)
        instructionsTextView = findViewById(R.id.instructionsTextView)
        emailEditText = findViewById(R.id.emailLabel)
        recuperarButton = findViewById(R.id.recuperarButton)
        loginTextView = findViewById(R.id.loginTextView)
        emailEditText.editText?.setText(initialEmail)
        recuperarButton.setOnClickListener {
            if (validateEmail()) {
                val email = emailEditText.editText?.text.toString().trim()
                viewModel.send(email)
            } else {
                val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(emailEditText.editText?.windowToken, 0)
                emailEditText.editText?.clearFocus()
                UIUtils.showErrorToast(this@ForgotPasswordActivity, getString(R.string.error_invalid_email))
            }
        }
        loginTextView.setOnClickListener { navigateToLogin() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.isSending.collect { recuperarButton.isEnabled = !it } }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            ForgotPasswordViewModel.Event.Sent -> {
                                UIUtils.showSuccessToast(this@ForgotPasswordActivity, getString(R.string.password_reset_email_sent))
                                navigateToLogin()
                            }
                            ForgotPasswordViewModel.Event.Failed ->
                                UIUtils.showErrorToast(this@ForgotPasswordActivity, getString(R.string.error_password_reset_failed))
                        }
                    }
                }
            }
        }
    }
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        val pairs = arrayOf(
            Pair<View, String>(logoImageView, "logoImageView"),
            Pair<View, String>(welcomeTextView, "textTrans"),
            Pair<View, String>(instructionsTextView, "instructionsTextView"),
            Pair<View, String>(emailEditText, "emailEditText"),
            Pair<View, String>(recuperarButton, "registerOrLoginButton"),
            Pair<View, String>(loginTextView, "registerOrLoginEditText")
        )
        val options = ActivityOptions.makeSceneTransitionAnimation(this, *pairs)
        startActivity(intent, options.toBundle())
    }
    private fun validateEmail(): Boolean {
        val email = emailEditText.editText?.text.toString().trim()
        return !(email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
    }
}
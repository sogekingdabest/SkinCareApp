package com.monsteraltech.skincare.login
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import com.monsteraltech.skincare.MainActivity
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.utils.UIUtils
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Pair as UtilPair

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    @Inject
    lateinit var googleCredentialHelper: GoogleCredentialHelper

    private lateinit var welcomeTextView: TextView
    private lateinit var instructionsTextView: TextView
    private lateinit var emailEditText: TextInputLayout
    private lateinit var passwordEditText: TextInputLayout
    private lateinit var loginButton: Button
    private lateinit var registerButton: TextView
    private lateinit var forgotPasswordTextView: TextView
    private lateinit var googleSignInButton: MaterialButton
    private lateinit var localProfileButton: MaterialButton
    private lateinit var logoImageView: ImageView
    private lateinit var loadingOverlay: View
    private var credentialFlowLoading = false
    private var viewModelLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        bindViews()
        emailEditText.editText?.setText(intent.getStringExtra("email"))
        passwordEditText.editText?.setText(intent.getStringExtra("password"))

        loginButton.setOnClickListener {
            viewModel.signInWithEmail(
                emailEditText.editText?.text.toString(),
                passwordEditText.editText?.text.toString()
            )
        }
        googleSignInButton.setOnClickListener { launchGoogleSignIn() }
        localProfileButton.setOnClickListener { viewModel.continueWithoutAccount() }
        registerButton.setOnClickListener { navigateToRegister() }
        forgotPasswordTextView.setOnClickListener { navigateToForgotPassword() }

        observeViewModel()
    }

    private fun bindViews() {
        logoImageView = findViewById(R.id.logoImageView)
        welcomeTextView = findViewById(R.id.welcomeTextView)
        instructionsTextView = findViewById(R.id.instructionsTextView)
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
        forgotPasswordTextView = findViewById(R.id.forgotPasswordTextView)
        googleSignInButton = findViewById(R.id.googleSignInButton)
        localProfileButton = findViewById(R.id.localProfileButton)
        loadingOverlay = findViewById(R.id.loginLoadingOverlay)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isLoading.collect { loading ->
                        viewModelLoading = loading
                        renderLoading()
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is LoginViewModel.Event.NavigateToMain -> navigateToMain()
                            is LoginViewModel.Event.ShowMessage ->
                                UIUtils.showErrorToast(this@LoginActivity, getString(event.messageRes))
                        }
                    }
                }
            }
        }
    }

    /**
     * Lanza la hoja de selección de cuenta de Google (Credential Manager) y,
     * si el usuario elige una, delega la autenticación Firebase al ViewModel.
     */
    private fun launchGoogleSignIn() {
        lifecycleScope.launch {
            credentialFlowLoading = true
            renderLoading()
            val result = try {
                googleCredentialHelper.signIn(
                    this@LoginActivity,
                    getString(R.string.default_web_client_id)
                )
            } finally {
                credentialFlowLoading = false
                renderLoading()
            }
            when (result) {
                is GoogleCredentialHelper.SignInResult.Success ->
                    viewModel.signInWithGoogleToken(result.idToken)
                is GoogleCredentialHelper.SignInResult.Cancelled -> Unit
                is GoogleCredentialHelper.SignInResult.NoCredentialAvailable ->
                    UIUtils.showInfoToast(this@LoginActivity, getString(R.string.login_no_google_accounts))
                is GoogleCredentialHelper.SignInResult.Error ->
                    UIUtils.showErrorToast(this@LoginActivity, getString(R.string.error_authentication))
            }
        }
    }

    private fun renderLoading() {
        val loading = viewModelLoading || credentialFlowLoading
        loadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !loading
        googleSignInButton.isEnabled = !loading
        localProfileButton.isEnabled = !loading
        registerButton.isEnabled = !loading
        forgotPasswordTextView.isEnabled = !loading
        emailEditText.isEnabled = !loading
        passwordEditText.isEnabled = !loading
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun navigateToRegister() {
        val intent = Intent(this, RegisterActivity::class.java)
        intent.putExtra("email", emailEditText.editText?.text.toString())
        intent.putExtra("password", passwordEditText.editText?.text.toString())
        val pairs = arrayOf(
            UtilPair<View, String>(logoImageView, "logoImageView"),
            UtilPair<View, String>(welcomeTextView, "textTrans"),
            UtilPair<View, String>(instructionsTextView, "instructionsTextView"),
            UtilPair<View, String>(emailEditText, "emailEditText"),
            UtilPair<View, String>(passwordEditText, "passwordEditText"),
            UtilPair<View, String>(loginButton, "registerOrLoginButton"),
            UtilPair<View, String>(registerButton, "registerOrLoginEditText")
        )
        val options = ActivityOptions.makeSceneTransitionAnimation(this, *pairs)
        startActivity(intent, options.toBundle())
    }

    private fun navigateToForgotPassword() {
        val intent = Intent(this, ForgotPasswordActivity::class.java)
        intent.putExtra("email", emailEditText.editText?.text.toString())
        val pairs = arrayOf(
            UtilPair<View, String>(logoImageView, "logoImageView"),
            UtilPair<View, String>(welcomeTextView, "textTrans"),
            UtilPair<View, String>(instructionsTextView, "instructionsTextView"),
            UtilPair<View, String>(emailEditText, "emailEditText"),
            UtilPair<View, String>(loginButton, "registerOrLoginButton"),
            UtilPair<View, String>(registerButton, "registerOrLoginEditText")
        )
        val options = ActivityOptions.makeSceneTransitionAnimation(this, *pairs)
        startActivity(intent, options.toBundle())
    }
}

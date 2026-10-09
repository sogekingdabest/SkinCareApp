package com.monsteraltech.skincare.login
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import com.monsteraltech.skincare.MainActivity
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.utils.UIUtils
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    private val viewModel: RegisterViewModel by viewModels()

    private lateinit var logoImageView: ImageView
    private lateinit var welcomeTextView: TextView
    private lateinit var instructionsTextView: TextView
    private lateinit var emailEditText: TextInputLayout
    private lateinit var confirmEmailEditText: TextInputLayout
    private lateinit var passwordEditText: TextInputLayout
    private lateinit var confirmPasswordEditText: TextInputLayout
    private lateinit var loginButton: TextView
    private lateinit var registerButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        bindViews()
        emailEditText.editText?.setText(intent.getStringExtra("email"))
        passwordEditText.editText?.setText(intent.getStringExtra("password"))

        registerButton.setOnClickListener {
            viewModel.register(
                email = emailEditText.editText?.text.toString(),
                confirmEmail = confirmEmailEditText.editText?.text.toString(),
                password = passwordEditText.editText?.text.toString(),
                confirmPassword = confirmPasswordEditText.editText?.text.toString()
            )
        }
        loginButton.setOnClickListener { transitionBack() }

        // Registrado sobre ESTA activity (antes se registraba sobre una instancia
        // suelta `RegisterActivity()`, por lo que la transición nunca se ejecutaba).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                transitionBack()
            }
        })

        observeViewModel()
    }

    private fun bindViews() {
        logoImageView = findViewById(R.id.logoImageView)
        welcomeTextView = findViewById(R.id.welcomeTextView)
        instructionsTextView = findViewById(R.id.instructionsTextView)
        emailEditText = findViewById(R.id.emailLabel)
        confirmEmailEditText = findViewById(R.id.confirmEmailLabel)
        passwordEditText = findViewById(R.id.passwordLabel)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordLabel)
        registerButton = findViewById(R.id.registerButton)
        loginButton = findViewById(R.id.loginButton)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isLoading.collect { loading ->
                        registerButton.isEnabled = !loading
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is RegisterViewModel.Event.NavigateToMain -> navigateToMain()
                            is RegisterViewModel.Event.ShowMessage ->
                                UIUtils.showErrorToast(this@RegisterActivity, getString(event.messageRes))
                            is RegisterViewModel.Event.ShowMessageText ->
                                UIUtils.showErrorToast(this@RegisterActivity, event.message)
                        }
                    }
                }
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun transitionBack() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.putExtra("email", emailEditText.editText?.text.toString())
        intent.putExtra("password", passwordEditText.editText?.text.toString())
        val pairs = arrayOf(
            android.util.Pair<View, String>(logoImageView, "logoImageView"),
            android.util.Pair<View, String>(welcomeTextView, "textTrans"),
            android.util.Pair<View, String>(instructionsTextView, "instructionsTextView"),
            android.util.Pair<View, String>(emailEditText, "emailEditText"),
            android.util.Pair<View, String>(passwordEditText, "passwordEditText"),
            android.util.Pair<View, String>(loginButton, "registerOrLoginEditText"),
            android.util.Pair<View, String>(registerButton, "registerOrLoginButton")
        )
        val options = ActivityOptions.makeSceneTransitionAnimation(this, *pairs)
        startActivity(intent, options.toBundle())
    }
}

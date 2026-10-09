package com.monsteraltech.skincare.login
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import com.monsteraltech.skincare.MainActivity
import com.monsteraltech.skincare.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SessionCheckActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "SessionCheckActivity"
        private const val SPLASH_ANIMATION_DURATION = 4000L
    }

    private val viewModel: SessionCheckViewModel by viewModels()

    private lateinit var logoImageView: ImageView
    private lateinit var skinCareTextView: TextView
    private lateinit var deTextView: TextView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var statusTextView: TextView
    private lateinit var errorMessageTextView: TextView
    private lateinit var retryButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_check)
        initializeViews()
        retryButton.setOnClickListener { viewModel.checkSession() }

        // La pantalla de splash no debe poder cerrarse con "atrás".
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { }
        })

        observeViewModel()
        startSplashAnimation()
    }

    private fun initializeViews() {
        logoImageView = findViewById(R.id.logoImageView)
        skinCareTextView = findViewById(R.id.skinCareTextView)
        deTextView = findViewById(R.id.deTextView)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        statusTextView = findViewById(R.id.statusTextView)
        errorMessageTextView = findViewById(R.id.errorMessageTextView)
        retryButton = findViewById(R.id.retryButton)
    }

    private fun startSplashAnimation() {
        val animacion1 = AnimationUtils.loadAnimation(this, R.anim.desplazamiento_arriba)
        val animacion2 = AnimationUtils.loadAnimation(this, R.anim.desplazamiento_abajo)
        deTextView.animation = animacion2
        skinCareTextView.animation = animacion2
        logoImageView.animation = animacion1
        lifecycleScope.launch {
            delay(SPLASH_ANIMATION_DURATION)
            viewModel.checkSession()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> render(state) }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is SessionCheckViewModel.Event.NavigateToMain -> navigateToMain()
                            is SessionCheckViewModel.Event.NavigateToLogin -> navigateToLogin()
                        }
                    }
                }
            }
        }
    }

    private fun render(state: SessionCheckViewModel.UiState) {
        when (state) {
            is SessionCheckViewModel.UiState.Splash -> {
                loadingProgressBar.visibility = View.GONE
                statusTextView.visibility = View.GONE
                errorMessageTextView.visibility = View.GONE
                retryButton.visibility = View.GONE
            }
            is SessionCheckViewModel.UiState.Loading -> {
                loadingProgressBar.visibility = View.VISIBLE
                statusTextView.visibility = View.VISIBLE
                statusTextView.text = getString(R.string.session_check_verifying)
                errorMessageTextView.visibility = View.GONE
                retryButton.visibility = View.GONE
            }
            is SessionCheckViewModel.UiState.Success -> {
                loadingProgressBar.visibility = View.VISIBLE
                statusTextView.visibility = View.VISIBLE
                statusTextView.text = getString(R.string.session_check_success)
                errorMessageTextView.visibility = View.GONE
                retryButton.visibility = View.GONE
            }
            is SessionCheckViewModel.UiState.Error -> {
                loadingProgressBar.visibility = View.GONE
                statusTextView.visibility = View.GONE
                errorMessageTextView.visibility = View.VISIBLE
                errorMessageTextView.text = getString(state.messageRes)
                retryButton.visibility = View.VISIBLE
            }
        }
    }

    private fun navigateToMain() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            val pairs = arrayOf(
                android.util.Pair<View, String>(logoImageView, "logoImageView"),
                android.util.Pair<View, String>(skinCareTextView, "textTrans")
            )
            val options = ActivityOptions.makeSceneTransitionAnimation(this, *pairs)
            startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            Log.e(TAG, "Error al navegar a MainActivity: ${e.message}", e)
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun navigateToLogin() {
        try {
            val intent = Intent(this, LoginActivity::class.java)
            val pairs = arrayOf(
                android.util.Pair<View, String>(logoImageView, "logoImageView"),
                android.util.Pair<View, String>(skinCareTextView, "textTrans")
            )
            val options = ActivityOptions.makeSceneTransitionAnimation(this, *pairs)
            startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            Log.e(TAG, "Error al navegar a LoginActivity: ${e.message}", e)
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}

package es.monsteraltech.skincare_tfm.login
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import es.monsteraltech.skincare_tfm.MainActivity
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.data.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
class SessionCheckActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "SessionCheckActivity"
        private const val MIN_LOADING_TIME_MS = 1500L
        private const val SPLASH_ANIMATION_DURATION = 4000L
    }
    private lateinit var sessionManager: SessionManager
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
        Log.d(TAG, "SessionCheckActivity iniciada")
        initializeViews()
        initializeSessionManager()
        setupRetryButton()
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
        showSplashState()
    }
    private fun initializeSessionManager() {
        sessionManager = SessionManager.getInstance(this)
    }
    private fun setupRetryButton() {
        retryButton.setOnClickListener {
            Log.d(TAG, "Usuario solicitó reintentar verificación de sesión")
            checkSession()
        }
    }
    private fun startSplashAnimation() {
        Log.d(TAG, "Iniciando animación de splash")
        val animacion1: Animation = AnimationUtils.loadAnimation(this, R.anim.desplazamiento_arriba)
        val animacion2: Animation = AnimationUtils.loadAnimation(this, R.anim.desplazamiento_abajo)
        deTextView.animation = animacion2
        skinCareTextView.animation = animacion2
        logoImageView.animation = animacion1
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "Animación de splash completada, iniciando verificación de sesión")
            checkSession()
        }, SPLASH_ANIMATION_DURATION)
    }
    private fun showSplashState() {
        Log.d(TAG, "Mostrando estado de splash")
        loadingProgressBar.visibility = View.GONE
        statusTextView.visibility = View.GONE
        errorMessageTextView.visibility = View.GONE
        retryButton.visibility = View.GONE
    }
    private fun checkSession() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Iniciando verificación de sesión optimizada")
                showLoadingState()
                val startTime = System.currentTimeMillis()
                updateProgressStatus(getString(R.string.session_check_verifying))
                val isSessionValid = sessionManager.isSessionValid(fastMode = true)
                val elapsedTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Verificación completada en ${elapsedTime}ms")
                val cacheStats = sessionManager.getCacheStats()
                Log.d(TAG, "Cache stats: $cacheStats")
                if (elapsedTime < MIN_LOADING_TIME_MS) {
                    updateProgressStatus(getString(R.string.session_check_loading))
                    delay(MIN_LOADING_TIME_MS - elapsedTime)
                }
                if (isSessionValid) {
                    Log.d(TAG, "Sesión válida encontrada, navegando a MainActivity")
                    showSuccessState()
                    delay(300)
                    navigateToMain()
                } else {
                    Log.d(TAG, "No se encontró sesión válida, navegando a LoginActivity")
                    navigateToLogin()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error durante verificación de sesión: ${e.message}", e)
                handleSessionCheckError(e)
            }
        }
    }
    private fun handleSessionCheckError(exception: Exception) {
        Log.w(TAG, "Manejando error de verificación: ${exception.message}")
        when {
            isNetworkError(exception) -> {
                Log.i(TAG, "Error de red detectado")
                showErrorState(getString(R.string.session_check_error_network))
                lifecycleScope.launch {
                    delay(2000)
                    try {
                        val sessionData = sessionManager.getStoredSession()
                        if (sessionData != null && !sessionData.isExpired()) {
                            Log.i(TAG, "Permitiendo acceso offline con sesión local válida")
                            showSuccessState()
                            delay(500)
                            navigateToMain()
                        } else {
                            Log.i(TAG, "No hay sesión local válida para acceso offline")
                            navigateToLogin()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error en verificación offline: ${e.message}")
                        navigateToLogin()
                    }
                }
            }
            else -> {
                Log.w(TAG, "Error no relacionado con red")
                showErrorState(getString(R.string.session_check_error_generic))
            }
        }
    }
    private fun isNetworkError(exception: Exception): Boolean {
        return when (exception) {
            is java.net.UnknownHostException,
            is java.net.SocketTimeoutException,
            is java.io.IOException -> true
            else -> {
                val message = exception.message?.lowercase() ?: ""
                message.contains("network") ||
                message.contains("timeout") ||
                message.contains("connection") ||
                message.contains("unreachable")
            }
        }
    }
    private fun showLoadingState() {
        Log.d(TAG, "Mostrando estado de carga")
        loadingProgressBar.visibility = View.VISIBLE
        statusTextView.visibility = View.VISIBLE
        statusTextView.text = getString(R.string.session_check_verifying)
        errorMessageTextView.visibility = View.GONE
        retryButton.visibility = View.GONE
    }
    private fun updateProgressStatus(message: String) {
        runOnUiThread {
            if (statusTextView.visibility == View.VISIBLE) {
                statusTextView.text = message
                Log.d(TAG, "Estado actualizado: $message")
            }
        }
    }
    private fun showSuccessState() {
        Log.d(TAG, "Mostrando estado de éxito")
        loadingProgressBar.visibility = View.VISIBLE
        statusTextView.visibility = View.VISIBLE
        statusTextView.text = getString(R.string.session_check_success)
        errorMessageTextView.visibility = View.GONE
        retryButton.visibility = View.GONE
    }
    private fun showErrorState(errorMessage: String) {
        Log.d(TAG, "Mostrando estado de error: $errorMessage")
        loadingProgressBar.visibility = View.GONE
        statusTextView.visibility = View.GONE
        errorMessageTextView.visibility = View.VISIBLE
        errorMessageTextView.text = errorMessage
        retryButton.visibility = View.VISIBLE
    }
    private fun navigateToMain() {
        Log.d(TAG, "Navegando a MainActivity")
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
        Log.d(TAG, "Navegando a LoginActivity")
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
    override fun onBackPressed() {
        Log.d(TAG, "Botón de retroceso presionado - ignorando")
    }
}
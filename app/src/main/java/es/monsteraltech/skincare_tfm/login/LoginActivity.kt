package es.monsteraltech.skincare_tfm.login
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import es.monsteraltech.skincare_tfm.MainActivity
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import es.monsteraltech.skincare_tfm.data.SessionManager
import es.monsteraltech.skincare_tfm.utils.UIUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Pair as UtilPair
class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseDataManager: FirebaseDataManager
    private lateinit var sessionManager: SessionManager
    private lateinit var welcomeTextView: TextView
    private lateinit var instructionsTextView: TextView
    private lateinit var emailEditText: TextInputLayout
    private lateinit var passwordEditText: TextInputLayout
    private lateinit var loginButton: Button
    private lateinit var registerButton: TextView
    private lateinit var forgotPasswordTextView: TextView
    private lateinit var googleSignInButton: com.google.android.gms.common.SignInButton
    private lateinit var logoImageView: ImageView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        val email = intent.getStringExtra("email")
        val password = intent.getStringExtra("password")
        auth = FirebaseAuth.getInstance()
        firebaseDataManager = FirebaseDataManager()
        sessionManager = SessionManager.getInstance(this)
        logoImageView = findViewById(R.id.logoImageView)
        welcomeTextView = findViewById(R.id.welcomeTextView)
        instructionsTextView = findViewById(R.id.instructionsTextView)
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
        forgotPasswordTextView = findViewById(R.id.forgotPasswordTextView)
        googleSignInButton = findViewById(R.id.googleSignInButton)
        emailEditText.editText?.setText(email)
        passwordEditText.editText?.setText(password)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        loginButton.setOnClickListener {
            val email = emailEditText.editText?.text.toString()
            val password = passwordEditText.editText?.text.toString()
            loginUser(email, password)
        }
        registerButton.setOnClickListener {
            val intent = Intent(this@LoginActivity, RegisterActivity::class.java)
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
            val options = ActivityOptions.makeSceneTransitionAnimation(this@LoginActivity, *pairs)
            startActivity(intent, options.toBundle())
        }
        forgotPasswordTextView.setOnClickListener {
            val intent = Intent(this@LoginActivity, ForgotPasswordActivity::class.java)
            intent.putExtra("email", emailEditText.editText?.text.toString())
            val pairs = arrayOf(
                UtilPair<View, String>(logoImageView, "logoImageView"),
                UtilPair<View, String>(welcomeTextView, "textTrans"),
                UtilPair<View, String>(instructionsTextView, "instructionsTextView"),
                UtilPair<View, String>(emailEditText, "emailEditText"),
                UtilPair<View, String>(loginButton, "registerOrLoginButton"),
                UtilPair<View, String>(registerButton, "registerOrLoginEditText")
            )
            val options = ActivityOptions.makeSceneTransitionAnimation(this@LoginActivity, *pairs)
            startActivity(intent, options.toBundle())
        }
        googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }
    }
    private fun loginUser(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            UIUtils.showErrorToast(this, getString(R.string.error_empty_fields))
            return
        }
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, OnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    initializeUserSettingsAndNavigate(user)
                } else {
                    UIUtils.showErrorToast(this@LoginActivity, getString(R.string.error_authentication))
                    updateUI(null)
                }
            })
    }
    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                UIUtils.showErrorToast(this, getString(R.string.error_authentication))
            }
        }
    }
    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    initializeUserSettingsAndNavigate(user)
                } else {
                    UIUtils.showErrorToast(this, getString(R.string.error_authentication))
                    updateUI(null)
                }
            }
    }
    private fun initializeUserSettingsAndNavigate(user: FirebaseUser?) {
        if (user != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val sessionSaved = sessionManager.saveSession(user)
                    if (sessionSaved) {
                        android.util.Log.d("LoginActivity", "Sesión guardada exitosamente para usuario: ${user.uid}")
                    } else {
                        android.util.Log.w("LoginActivity", "No se pudo guardar la sesión, continuando sin persistencia")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LoginActivity", "Error al guardar sesión: ${e.message}", e)
                }
                try {
                    firebaseDataManager.initializeUserSettings(user.uid)
                    runOnUiThread {
                        updateUI(user)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("LoginActivity", "Error al inicializar configuraciones de usuario: ${e.message}", e)
                    runOnUiThread {
                        updateUI(user)
                    }
                }
            }
        }
    }
    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
    companion object {
        private const val RC_SIGN_IN = 9001
    }
}
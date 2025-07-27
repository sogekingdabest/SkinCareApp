package es.monsteraltech.skincare_tfm.login

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Pair as UtilPair


class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseDataManager: FirebaseDataManager

    private lateinit var welcomeTextView: TextView
    private lateinit var instructionsTextView: TextView
    private lateinit var emailEditText: TextInputLayout
    private lateinit var passwordEditText: TextInputLayout
    private lateinit var loginButton: Button
    private lateinit var registerButton: TextView
    private lateinit var googleSignInButton: com.google.android.gms.common.SignInButton
    private lateinit var logoImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val email = intent.getStringExtra("email")
        val password = intent.getStringExtra("password")

        auth = FirebaseAuth.getInstance()
        firebaseDataManager = FirebaseDataManager()

        logoImageView = findViewById(R.id.logoImageView)
        welcomeTextView = findViewById(R.id.welcomeTextView)
        instructionsTextView = findViewById(R.id.instructionsTextView)
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
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

        googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, OnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    initializeUserSettingsAndNavigate(user)
                } else {
                    Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Google sign in failed", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show()
                    updateUI(null)
                }
            }
    }

    /**
     * Inicializa las configuraciones del usuario si no existen y navega a MainActivity
     */
    private fun initializeUserSettingsAndNavigate(user: FirebaseUser?) {
        if (user != null) {
            // Inicializar configuraciones del usuario en background
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Intentar inicializar las configuraciones del usuario
                    firebaseDataManager.initializeUserSettings(user.uid)
                    
                    // Navegar a MainActivity en el hilo principal
                    runOnUiThread {
                        updateUI(user)
                    }
                } catch (e: Exception) {
                    // Si falla la inicialización, aún así navegar a MainActivity
                    // Las configuraciones se crearán cuando sea necesario
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

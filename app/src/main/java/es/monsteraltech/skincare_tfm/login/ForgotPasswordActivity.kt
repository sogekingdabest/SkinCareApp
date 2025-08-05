package es.monsteraltech.skincare_tfm.login
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.util.Pair
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import es.monsteraltech.skincare_tfm.R
class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var logoImageView: ImageView
    private lateinit var welcomeTextView: TextView
    private lateinit var instructionsTextView: TextView
    private lateinit var emailEditText: TextInputLayout
    private lateinit var recuperarButton: Button
    private lateinit var loginTextView: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        val email = intent.getStringExtra("password")
        auth = FirebaseAuth.getInstance()
        logoImageView = findViewById(R.id.logoImageView)
        welcomeTextView = findViewById(R.id.welcomeTextView)
        instructionsTextView = findViewById(R.id.instructionsTextView)
        emailEditText = findViewById(R.id.emailLabel)
        recuperarButton = findViewById(R.id.recuperarButton)
        loginTextView = findViewById(R.id.loginTextView)
        emailEditText.editText?.setText(email)
        recuperarButton.setOnClickListener {
            if (validateEmail()) {
                val email = emailEditText.editText?.text.toString().trim()
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Snackbar.make(it, R.string.password_reset_email_sent, Snackbar.LENGTH_LONG).show()
                            val intent = Intent(this@ForgotPasswordActivity, LoginActivity::class.java)
                            val pairs = arrayOf(
                                Pair<View, String>(logoImageView, "logoImageView"),
                                Pair<View, String>(welcomeTextView, "textTrans"),
                                Pair<View, String>(instructionsTextView, "instructionsTextView"),
                                Pair<View, String>(emailEditText, "emailEditText"),
                                Pair<View, String>(recuperarButton, "registerOrLoginButton"),
                                Pair<View, String>(loginTextView, "registerOrLoginEditText")
                            )
                            val options = ActivityOptions.makeSceneTransitionAnimation(this@ForgotPasswordActivity, *pairs)
                            startActivity(intent, options.toBundle())
                        } else {
                            Snackbar.make(it, R.string.error_password_reset_failed, Snackbar.LENGTH_LONG).show()
                        }
                    }
            } else {
                val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(emailEditText.editText?.windowToken, 0)
                emailEditText.editText?.clearFocus()
                Snackbar.make(it, R.string.error_invalid_email, Snackbar.LENGTH_LONG).show()
            }
        }
        loginTextView.setOnClickListener {
            val intent = Intent(this@ForgotPasswordActivity, LoginActivity::class.java)
            val pairs = arrayOf(
                Pair<View, String>(logoImageView, "logoImageView"),
                Pair<View, String>(welcomeTextView, "textTrans"),
                Pair<View, String>(instructionsTextView, "instructionsTextView"),
                Pair<View, String>(emailEditText, "emailEditText"),
                Pair<View, String>(recuperarButton, "registerOrLoginButton"),
                Pair<View, String>(loginTextView, "registerOrLoginEditText")
            )
            val options = ActivityOptions.makeSceneTransitionAnimation(this@ForgotPasswordActivity, *pairs)
            startActivity(intent, options.toBundle())
        }
    }
    private fun validateEmail(): Boolean {
        val email = emailEditText.editText?.text.toString().trim()
        return !(email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
    }
}
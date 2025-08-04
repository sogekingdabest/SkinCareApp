package es.monsteraltech.skincare_tfm.login

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import es.monsteraltech.skincare_tfm.MainActivity
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.account.PasswordChangeManager


class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var passwordChangeManager: PasswordChangeManager
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

        val email = intent.getStringExtra("email")
        val password = intent.getStringExtra("password")

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        passwordChangeManager = PasswordChangeManager()

        logoImageView = findViewById(R.id.logoImageView)
        welcomeTextView = findViewById(R.id.welcomeTextView)
        instructionsTextView = findViewById(R.id.instructionsTextView)
        emailEditText = findViewById(R.id.emailLabel)
        confirmEmailEditText = findViewById(R.id.confirmEmailLabel)
        passwordEditText = findViewById(R.id.passwordLabel)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordLabel)
        registerButton = findViewById(R.id.registerButton)
        loginButton = findViewById(R.id.loginButton)

        emailEditText.editText?.setText(email)
        passwordEditText.editText?.setText(password)

        registerButton.setOnClickListener {
            val email = emailEditText.editText?.text.toString()
            val confirmEmail = confirmEmailEditText.editText?.text.toString()
            val password = passwordEditText.editText?.text.toString()
            val confirmPassword = confirmPasswordEditText.editText?.text.toString()

            if (email.isNotEmpty() &&
                confirmEmail.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty()) {

                if (validateEmail()) Snackbar.make(it, R.string.error_invalid_email, Snackbar.LENGTH_LONG).show()
                val validPassword = passwordChangeManager.validatePassword(password)
                if (!validPassword.isValid) {
                    validPassword.errorMessage?.let { it1 -> Snackbar.make(it, it1, Snackbar.LENGTH_LONG).show() }
                }

                if (email == confirmEmail && password == confirmPassword) {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful) {
                                // Sign in success, update UI with the signed-in user's information
                                val user = auth.currentUser
                                updateUI(user)
                            } else {
                                // If sign in fails, display a message to the user.
                                try {
                                    throw task.exception!!
                                } catch (e: FirebaseAuthUserCollisionException) {
                                    Toast.makeText(baseContext, "This email is already in use.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT).show()
                                }
                                updateUI(null)
                            }
                        }
                } else {
                    Toast.makeText(this, "Email or password do not match.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please fill out all fields.", Toast.LENGTH_SHORT).show()
            }
        }

        val callback = object : OnBackPressedCallback(
            true // default to enabled
        ) {
            override fun handleOnBackPressed() {
                transitionBack()
            }
        }

        RegisterActivity().onBackPressedDispatcher.addCallback(this, callback)

        loginButton.setOnClickListener {
            transitionBack()
        }
    }


    fun transitionBack() {

        val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
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



        val options = ActivityOptions.makeSceneTransitionAnimation(this@RegisterActivity, *pairs)
        startActivity(intent, options.toBundle())
    }



    private fun validateEmail(): Boolean {
        val email = emailEditText.editText?.text.toString().trim()
        return email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}

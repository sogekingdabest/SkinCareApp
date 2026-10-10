package com.monsteraltech.skincare
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import com.monsteraltech.skincare.account.UserProfileManager
import com.monsteraltech.skincare.camera.CameraActivity
import com.monsteraltech.skincare.data.SessionManager
import com.monsteraltech.skincare.data.auth.AuthRepository
import com.monsteraltech.skincare.databinding.ActivityMainBinding
import com.monsteraltech.skincare.fragments.AccountFragment
import com.monsteraltech.skincare.fragments.MyBodyFragment
import com.monsteraltech.skincare.login.LoginActivity
import com.monsteraltech.skincare.utils.MedicalDisclaimerHelper
import com.monsteraltech.skincare.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import javax.inject.Inject
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    @Inject
    lateinit var authRepository: AuthRepository
    @Inject
    lateinit var userProfileManager: UserProfileManager
    private lateinit var themeManager: ThemeManager
    private lateinit var sessionManager: SessionManager
    private lateinit var disclaimerHelper: MedicalDisclaimerHelper
    private var isLoggingOut = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startTime = System.currentTimeMillis()
        Log.d("MainActivity", "Iniciando MainActivity con optimizaciones")
        initializeManagers()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lifecycleScope.launch {
            try {
                themeManager.applyThemeFromUserSettings()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w("MainActivity", "Error al aplicar tema: ${e.message}")
            }
        }
        if (!OpenCVLoader.initLocal()) {
            Log.d("OpenCV", "Unable to load OpenCV!")
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully")
        }
        binding.navigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_my_body -> {
                    openFragment(MyBodyFragment())
                    true
                }
                R.id.camera_capture_button -> {
                    val intent = Intent(this, CameraActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.navigation_account -> {
                    openFragment(AccountFragment())
                    true
                }
                else -> false
            }
        }
        if (savedInstanceState == null) {
            binding.navigation.selectedItemId = R.id.navigation_my_body
        }
        val elapsedTime = System.currentTimeMillis() - startTime
        Log.d("MainActivity", "MainActivity inicializada en ${elapsedTime}ms")

        disclaimerHelper.showInitialDisclaimerIfNeeded(this) {
            checkPreloadedData()
        }
    }
    private fun openFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }
    private fun initializeManagers() {
        try {
            themeManager = ThemeManager.getInstance()
            themeManager.initialize(userProfileManager)
            sessionManager = SessionManager.getInstance(this)
            disclaimerHelper = MedicalDisclaimerHelper(this)
            Log.d("MainActivity", "Managers inicializados correctamente")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al inicializar managers: ${e.message}", e)
        }
    }
    private fun checkPreloadedData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cacheStats = sessionManager.getCacheStats()
                Log.d("MainActivity", "Cache stats al iniciar: $cacheStats")
                if (cacheStats["cached_data_exists"] == true) {
                    Log.d("MainActivity", "Datos de sesiÃ³n precargados disponibles")
                    preloadAdditionalUserData()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w("MainActivity", "Error al verificar datos precargados: ${e.message}")
            }
        }
    }
    private suspend fun preloadAdditionalUserData() {
        try {
            Log.d("MainActivity", "Precargando datos adicionales de usuario")
            withContext(Dispatchers.Main) {
                Log.d("MainActivity", "Datos adicionales precargados exitosamente")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w("MainActivity", "Error al precargar datos adicionales: ${e.message}")
        }
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_logout)?.isEnabled = !isLoggingOut
        return super.onPrepareOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                if (!isLoggingOut) showLogoutConfirmationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.account_logout))
            .setMessage(getString(R.string.account_logout_confirm))
            .setPositiveButton(getString(R.string.account_logout_confirm_button)) { _, _ ->
                logoutWithProgress()
            }
            .setNegativeButton(getString(R.string.account_logout_cancel), null)
            .show()
    }
    fun logoutWithProgress() {
        if (isLoggingOut) return
        isLoggingOut = true
        binding.logoutLoadingOverlay.visibility = View.VISIBLE
        binding.navigation.isEnabled = false
        invalidateOptionsMenu()
        lifecycleScope.launch {
            // Cierre de sesiÃ³n completo (sesiÃ³n local + Firebase + Credential Manager).
            // La limpieza termina antes de propagar la cancelación de esta pantalla.
            authRepository.signOut()
            navigateToLogin()
        }
    }
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

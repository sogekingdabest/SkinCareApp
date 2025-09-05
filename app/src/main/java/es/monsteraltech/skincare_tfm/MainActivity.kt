package es.monsteraltech.skincare_tfm
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import es.monsteraltech.skincare_tfm.account.UserProfileManager
import es.monsteraltech.skincare_tfm.camera.CameraActivity
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import es.monsteraltech.skincare_tfm.data.SessionManager
import es.monsteraltech.skincare_tfm.databinding.ActivityMainBinding
import es.monsteraltech.skincare_tfm.fragments.AccountFragment
import es.monsteraltech.skincare_tfm.fragments.MyBodyFragment
import es.monsteraltech.skincare_tfm.login.LoginActivity
import es.monsteraltech.skincare_tfm.utils.MedicalDisclaimerHelper
import es.monsteraltech.skincare_tfm.utils.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var themeManager: ThemeManager
    private lateinit var sessionManager: SessionManager
    private lateinit var firebaseDataManager: FirebaseDataManager
    private lateinit var disclaimerHelper: MedicalDisclaimerHelper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startTime = System.currentTimeMillis()
        Log.d("MainActivity", "Iniciando MainActivity con optimizaciones")
        initializeManagers()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                themeManager.applyThemeFromUserSettings(this@MainActivity)
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
            userProfileManager = UserProfileManager()
            themeManager = ThemeManager.getInstance()
            themeManager.initialize(userProfileManager)
            sessionManager = SessionManager.getInstance(this)
            firebaseDataManager = FirebaseDataManager()
            disclaimerHelper = MedicalDisclaimerHelper(this)
            Log.d("MainActivity", "Managers inicializados correctamente")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al inicializar managers: ${e.message}", e)
        }
    }
    fun getThemeManager(): ThemeManager = themeManager
    private fun checkPreloadedData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cacheStats = sessionManager.getCacheStats()
                Log.d("MainActivity", "Cache stats al iniciar: $cacheStats")
                if (cacheStats["cached_data_exists"] == true) {
                    Log.d("MainActivity", "Datos de sesión precargados disponibles")
                    preloadAdditionalUserData()
                }
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
        } catch (e: Exception) {
            Log.w("MainActivity", "Error al precargar datos adicionales: ${e.message}")
        }
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                showLogoutConfirmationDialog()
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
                logout()
            }
            .setNegativeButton(getString(R.string.account_logout_cancel), null)
            .show()
    }
    private fun logout() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sessionCleared = sessionManager.clearSession()
                if (sessionCleared) {
                    Log.d("MainActivity", "Sesión limpiada exitosamente")
                } else {
                    Log.w("MainActivity", "No se pudo limpiar la sesión completamente")
                }
                firebaseDataManager.signOut()
                Log.d("MainActivity", "Sesión de Firebase cerrada")
                runOnUiThread {
                    navigateToLogin()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error durante el logout: ${e.message}", e)
                runOnUiThread {
                    try {
                        firebaseDataManager.signOut()
                        navigateToLogin()
                    } catch (ex: Exception) {
                        Log.e("MainActivity", "Error crítico durante logout: ${ex.message}", ex)
                        navigateToLogin()
                    }
                }
            }
        }
    }
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
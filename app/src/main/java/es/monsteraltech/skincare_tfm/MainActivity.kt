package es.monsteraltech.skincare_tfm

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import es.monsteraltech.skincare_tfm.account.UserProfileManager
import es.monsteraltech.skincare_tfm.camera.CameraActivity
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import es.monsteraltech.skincare_tfm.data.SessionManager
import es.monsteraltech.skincare_tfm.databinding.ActivityMainBinding
import es.monsteraltech.skincare_tfm.fragments.AccountFragment
import es.monsteraltech.skincare_tfm.fragments.MyBodyFragment
import es.monsteraltech.skincare_tfm.login.LoginActivity
import es.monsteraltech.skincare_tfm.utils.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import org.opencv.android.OpenCVLoader

/*
class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAnchorView(R.id.fab)
                .setAction("Action", null).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}

*/

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var themeManager: ThemeManager
    private lateinit var sessionManager: SessionManager
    private lateinit var firebaseDataManager: FirebaseDataManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val startTime = System.currentTimeMillis()
        Log.d("MainActivity", "Iniciando MainActivity con optimizaciones")
        
        // Inicializar managers antes de establecer el contenido
        initializeManagers()
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Aplicar tema basado en la configuración del usuario de forma asíncrona
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                themeManager.applyThemeFromUserSettings(this@MainActivity)
            } catch (e: Exception) {
                Log.w("MainActivity", "Error al aplicar tema: ${e.message}")
            }
        }

        val bottomNavigationView: BottomNavigationView = binding.navigation

        if (!OpenCVLoader.initLocal()) {
            Log.d("OpenCV", "Unable to load OpenCV!")
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully")
        }

        // Configurar la barra de navegación inferior
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

        // Abre el fragmento predeterminado
        if (savedInstanceState == null) {
            binding.navigation.selectedItemId = R.id.navigation_my_body
        }
        
        val elapsedTime = System.currentTimeMillis() - startTime
        Log.d("MainActivity", "MainActivity inicializada en ${elapsedTime}ms")
        
        // Verificar si hay datos precargados disponibles
        checkPreloadedData()
    }

    private fun openFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }
    
    /**
     * Inicializa los managers necesarios para la aplicación
     */
    private fun initializeManagers() {
        try {
            // Inicializar UserProfileManager
            userProfileManager = UserProfileManager()
            
            // Inicializar ThemeManager
            themeManager = ThemeManager.getInstance()
            themeManager.initialize(userProfileManager)
            
            // Inicializar SessionManager y FirebaseDataManager
            sessionManager = SessionManager.getInstance(this)
            firebaseDataManager = FirebaseDataManager()
            
            Log.d("MainActivity", "Managers inicializados correctamente")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al inicializar managers: ${e.message}", e)
        }
    }
    
    /**
     * Obtiene el ThemeManager para uso en otros componentes
     */
    fun getThemeManager(): ThemeManager = themeManager
    
    /**
     * Verifica si hay datos precargados disponibles y los utiliza para optimizar la carga
     */
    private fun checkPreloadedData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cacheStats = sessionManager.getCacheStats()
                Log.d("MainActivity", "Cache stats al iniciar: $cacheStats")
                
                // Si hay datos precargados, podemos optimizar la carga inicial
                if (cacheStats["cached_data_exists"] == true) {
                    Log.d("MainActivity", "Datos de sesión precargados disponibles")
                    
                    // Precargar datos adicionales de usuario si es necesario
                    preloadAdditionalUserData()
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Error al verificar datos precargados: ${e.message}")
            }
        }
    }
    
    /**
     * Precarga datos adicionales de usuario para mejorar la experiencia
     */
    private suspend fun preloadAdditionalUserData() {
        try {
            Log.d("MainActivity", "Precargando datos adicionales de usuario")
            
            // Aquí se pueden precargar datos específicos de la aplicación
            // como configuraciones de usuario, historial reciente, etc.
            
            withContext(Dispatchers.Main) {
                // Actualizar UI si es necesario con datos precargados
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
    
    /**
     * Muestra un diálogo de confirmación antes de cerrar sesión
     */
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
    
    /**
     * Realiza el logout del usuario limpiando la sesión y navegando al login
     */
    private fun logout() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Limpiar sesión persistente
                val sessionCleared = sessionManager.clearSession()
                if (sessionCleared) {
                    Log.d("MainActivity", "Sesión limpiada exitosamente")
                } else {
                    Log.w("MainActivity", "No se pudo limpiar la sesión completamente")
                }
                
                // Cerrar sesión en Firebase
                firebaseDataManager.signOut()
                Log.d("MainActivity", "Sesión de Firebase cerrada")
                
                // Navegar al LoginActivity en el hilo principal
                runOnUiThread {
                    navigateToLogin()
                }
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Error durante el logout: ${e.message}", e)
                
                // Aún así intentar cerrar sesión de Firebase y navegar
                runOnUiThread {
                    try {
                        firebaseDataManager.signOut()
                        navigateToLogin()
                    } catch (ex: Exception) {
                        Log.e("MainActivity", "Error crítico durante logout: ${ex.message}", ex)
                        // Como último recurso, navegar al login
                        navigateToLogin()
                    }
                }
            }
        }
    }
    
    /**
     * Navega a LoginActivity y finaliza MainActivity
     */
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}



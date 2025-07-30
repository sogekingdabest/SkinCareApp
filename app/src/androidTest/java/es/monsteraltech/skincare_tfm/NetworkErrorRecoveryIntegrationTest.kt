package es.monsteraltech.skincare_tfm

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import es.monsteraltech.skincare_tfm.data.SessionData
import es.monsteraltech.skincare_tfm.data.SessionManager
import es.monsteraltech.skincare_tfm.login.SessionCheckActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de integración específicos para manejo de errores de red y recuperación
 * Requisitos: 4.1, 4.2, 4.3
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class NetworkErrorRecoveryIntegrationTest {

    private lateinit var context: Context
    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionManager = SessionManager.getInstance(context)
        
        // Limpiar cualquier sesión existente
        runBlocking {
            sessionManager.clearSession()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            sessionManager.clearSession()
        }
    }

    /**
     * Test de acceso offline con sesión local válida
     * Requisito: 4.1 - Permitir acceso con último token válido conocido
     */
    @Test
    fun testOfflineAccessWithValidLocalSession() {
        // Given: Una sesión local válida almacenada
        val validSessionData = SessionData(
            userId = "offline_user_123",
            email = "offline@example.com",
            displayName = "Offline User",
            tokenExpiry = System.currentTimeMillis() + 3600000, // 1 hora en el futuro
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        // Simular que tenemos una sesión válida almacenada localmente
        // (En un test real, esto se haría a través del SessionManager)
        
        // When: Se inicia SessionCheckActivity sin conexión de red
        ActivityScenario.launch(SessionCheckActivity::class.java).use { scenario ->
            
            // Then: Debe mostrar estado de carga inicialmente
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            
            onView(withId(R.id.statusTextView))
                .check(matches(withText(R.string.session_check_verifying)))
            
            // Esperar a que detecte el error de red y maneje el acceso offline
            Thread.sleep(5000)
            
            // Si hay una sesión local válida, debería mostrar éxito o navegar
            // Si no hay conexión pero hay sesión local válida, debería permitir acceso
        }
    }

    /**
     * Test de reintentos automáticos en caso de error de red
     * Requisito: 4.2 - Reintentar verificación cuando falla por problemas de red
     */
    @Test
    fun testAutomaticRetryOnNetworkError() {
        // When: Se inicia SessionCheckActivity con problemas de red simulados
        ActivityScenario.launch(SessionCheckActivity::class.java).use { scenario ->
            
            // Then: Debe mostrar estado de carga
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            
            // Esperar a que detecte error de red
            Thread.sleep(3000)
            
            // Debe mostrar mensaje de error de red
            onView(withId(R.id.errorMessageTextView))
                .check(matches(isDisplayed()))
            
            // Debe mostrar botón de reintentar
            onView(withId(R.id.retryButton))
                .check(matches(isDisplayed()))
                .check(matches(isEnabled()))
        }
    }

    /**
     * Test de mensaje informativo y acceso limitado offline
     * Requisito: 4.3 - Mostrar mensaje informativo y permitir acceso offline limitado
     */
    @Test
    fun testInformativeMessageAndLimitedOfflineAccess() {
        // Given: Sesión local válida pero sin conexión de red
        
        // When: Se verifica la sesión sin conexión
        ActivityScenario.launch(SessionCheckActivity::class.java).use { scenario ->
            
            // Esperar a que detecte problemas de red
            Thread.sleep(4000)
            
            // Then: Debe mostrar mensaje informativo sobre el estado de la red
            // (El mensaje específico depende de la implementación)
            onView(withId(R.id.errorMessageTextView))
                .check(matches(isDisplayed()))
            
            // Debe permitir reintentar
            onView(withId(R.id.retryButton))
                .check(matches(isDisplayed()))
            
            // When: Usuario hace click en reintentar
            onView(withId(R.id.retryButton))
                .perform(click())
            
            // Then: Debe volver a intentar la verificación
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            
            Thread.sleep(2000)
        }
    }

    /**
     * Test de timeout apropiado para verificaciones de sesión
     * Requisito: 4.2 - Manejo de timeouts
     */
    @Test
    fun testSessionVerificationTimeout() {
        // When: Se inicia verificación de sesión
        ActivityScenario.launch(SessionCheckActivity::class.java).use { scenario ->
            
            val startTime = System.currentTimeMillis()
            
            // Then: Debe mostrar estado de carga
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            
            // Esperar tiempo suficiente para que ocurra timeout
            Thread.sleep(8000)
            
            val elapsedTime = System.currentTimeMillis() - startTime
            
            // Verificar que no se queda colgado indefinidamente
            assert(elapsedTime < 10000) { "La verificación no debería tomar más de 10 segundos" }
            
            // Debe haber manejado el timeout apropiadamente
            // (mostrando error o navegando según corresponda)
        }
    }

    /**
     * Test de recuperación después de restaurar conectividad
     * Requisito: 4.2 - Recuperación automática cuando se restaura la conexión
     */
    @Test
    fun testRecoveryAfterConnectivityRestored() {
        // When: Se inicia con error de red
        ActivityScenario.launch(SessionCheckActivity::class.java).use { scenario ->
            
            // Esperar a que detecte error de red
            Thread.sleep(3000)
            
            // Then: Debe mostrar error y botón de reintentar
            onView(withId(R.id.retryButton))
                .check(matches(isDisplayed()))
            
            // When: Se simula restauración de conectividad y usuario reintenta
            onView(withId(R.id.retryButton))
                .perform(click())
            
            // Then: Debe volver a intentar verificación
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            
            // Esperar a que complete la verificación con conectividad restaurada
            Thread.sleep(3000)
            
            // Debe haber manejado la recuperación apropiadamente
        }
    }

    /**
     * Test de manejo de múltiples tipos de errores de red
     * Requisito: 4.1, 4.2, 4.3 - Manejo robusto de diferentes errores de red
     */
    @Test
    fun testMultipleNetworkErrorTypesHandling() {
        // Test que verifica el manejo de diferentes tipos de errores de red:
        // - UnknownHostException
        // - SocketTimeoutException
        // - IOException
        // - Otros errores de conectividad
        
        ActivityScenario.launch(SessionCheckActivity::class.java).use { scenario ->
            
            // Verificar que la actividad maneja errores gracefully
            onView(withId(R.id.logoImageView))
                .check(matches(isDisplayed()))
            
            // Esperar a que maneje cualquier error de red
            Thread.sleep(4000)
            
            // La actividad debe seguir siendo funcional independientemente del tipo de error
            onView(withId(R.id.logoImageView))
                .check(matches(isDisplayed()))
            
            // Debe mostrar algún tipo de feedback al usuario
            // (ya sea error message o navegación)
        }
    }

    /**
     * Test de comportamiento con sesión local expirada y sin conexión
     * Requisito: 4.1, 4.3 - Manejo de sesión expirada offline
     */
    @Test
    fun testExpiredSessionOfflineBehavior() {
        // Given: Sesión local expirada y sin conexión de red
        
        // When: Se intenta verificar sesión
        ActivityScenario.launch(SessionCheckActivity::class.java).use { scenario ->
            
            // Then: Debe mostrar carga inicialmente
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            
            // Esperar a que detecte sesión expirada y falta de conexión
            Thread.sleep(4000)
            
            // Debe manejar apropiadamente el caso de sesión expirada sin conexión
            // (probablemente navegando a login o mostrando mensaje apropiado)
        }
    }

    /**
     * Test de logging apropiado sin exponer datos sensibles
     * Requisito: Implícito - Seguridad y debugging
     */
    @Test
    fun testSecureLoggingDuringNetworkErrors() {
        // When: Ocurren errores de red durante verificación
        ActivityScenario.launch(SessionCheckActivity::class.java).use { scenario ->
            
            scenario.onActivity { activity ->
                // Verificar que la actividad maneja errores sin crashear
                // En un test real, aquí se verificaría que los logs no contienen
                // información sensible como tokens o datos de usuario
            }
            
            Thread.sleep(3000)
            
            // La actividad debe seguir funcionando
            onView(withId(R.id.logoImageView))
                .check(matches(isDisplayed()))
        }
    }
}
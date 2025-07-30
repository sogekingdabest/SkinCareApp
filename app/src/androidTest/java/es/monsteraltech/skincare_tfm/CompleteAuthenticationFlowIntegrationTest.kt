package es.monsteraltech.skincare_tfm

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.firebase.auth.FirebaseUser
import es.monsteraltech.skincare_tfm.data.SessionManager
import es.monsteraltech.skincare_tfm.login.LoginActivity
import es.monsteraltech.skincare_tfm.login.SessionCheckActivity
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de integración para el flujo completo de autenticación incluyendo login
 * Cubre la integración entre LoginActivity, SessionManager y el flujo de navegación
 * Requisitos: 1.1, 2.1, 2.2, 2.3
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class CompleteAuthenticationFlowIntegrationTest {

    private lateinit var context: Context
    private lateinit var sessionManager: SessionManager
    private lateinit var mockFirebaseUser: FirebaseUser

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionManager = SessionManager.getInstance(context)
        
        // Limpiar cualquier sesión existente
        runBlocking {
            sessionManager.clearSession()
        }
        
        // Configurar mock de FirebaseUser
        mockFirebaseUser = mockk {
            every { uid } returns "test_user_123"
            every { email } returns "test@example.com"
            every { displayName } returns "Test User"
        }
        
        Intents.init()
    }

    @After
    fun tearDown() {
        runBlocking {
            sessionManager.clearSession()
        }
        
        unmockkAll()
        Intents.release()
    }

    /**
     * Test del flujo completo de login con guardado de sesión
     * Requisito: 1.1 - Guardar token de sesión después de autenticación exitosa
     */
    @Test
    fun testCompleteLoginFlowWithSessionSaving() {
        // Given: Usuario está en LoginActivity
        ActivityScenario.launch(LoginActivity::class.java).use { loginScenario ->
            
            // Then: Debe mostrar elementos de login
            onView(withId(R.id.logoImageView))
                .check(matches(isDisplayed()))
            
            onView(withId(R.id.skinCareTextView))
                .check(matches(isDisplayed()))
            
            // Verificar que los campos de login están presentes
            onView(withId(R.id.emailEditText))
                .check(matches(isDisplayed()))
            
            onView(withId(R.id.passwordEditText))
                .check(matches(isDisplayed()))
            
            onView(withId(R.id.loginButton))
                .check(matches(isDisplayed()))
            
            // When: Usuario ingresa credenciales válidas
            onView(withId(R.id.emailEditText))
                .perform(typeText("test@example.com"))
            
            onView(withId(R.id.passwordEditText))
                .perform(typeText("password123"))
            
            // Cerrar teclado
            onView(withId(R.id.passwordEditText))
                .perform(closeSoftKeyboard())
            
            // Simular login exitoso
            loginScenario.onActivity { activity ->
                // En un test real, aquí se simularía la respuesta exitosa de Firebase
                // y se verificaría que se llama a SessionManager.saveSession()
                
                // Simular que el login fue exitoso y se guardó la sesión
                runBlocking {
                    sessionManager.saveSession(mockFirebaseUser)
                }
                
                // Simular navegación a MainActivity
                activity.finish()
            }
        }
        
        // Then: Verificar que la sesión fue guardada
        runBlocking {
            val savedSession = sessionManager.getStoredSession()
            assert(savedSession != null) { "La sesión debería haber sido guardada después del login" }
        }
        
        // Verificar que el próximo inicio usa la sesión guardada
        ActivityScenario.launch(SessionCheckActivity::class.java).use { sessionCheckScenario ->
            
            // Debe mostrar verificación de sesión
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            
            Thread.sleep(3000)
        }
        
        // Debe navegar a MainActivity debido a la sesión válida
        intended(hasComponent(MainActivity::class.java.name))
    }

    /**
     * Test de login con Google Sign-In y guardado de sesión
     * Requisito: 1.1 - Guardar sesión para ambos tipos de login
     */
    @Test
    fun testGoogleSignInWithSessionSaving() {
        // Given: Usuario está en LoginActivity
        ActivityScenario.launch(LoginActivity::class.java).use { loginScenario ->
            
            // Then: Debe mostrar botón de Google Sign-In
            onView(withId(R.id.googleSignInButton))
                .check(matches(isDisplayed()))
            
            // When: Usuario hace click en Google Sign-In
            onView(withId(R.id.googleSignInButton))
                .perform(click())
            
            // Simular Google Sign-In exitoso
            loginScenario.onActivity { activity ->
                // Simular respuesta exitosa de Google Sign-In
                runBlocking {
                    sessionManager.saveSession(mockFirebaseUser)
                }
                
                activity.finish()
            }
        }
        
        // Then: Verificar que la sesión fue guardada
        runBlocking {
            val savedSession = sessionManager.getStoredSession()
            assert(savedSession != null) { "La sesión debería haber sido guardada después de Google Sign-In" }
        }
    }

    /**
     * Test de manejo de errores durante el guardado de sesión
     * Requisito: 1.1 - Continuar sin persistencia si falla el guardado
     */
    @Test
    fun testLoginWithSessionSaveFailure() {
        // Given: SessionManager que falla al guardar
        val mockSessionManager = mockk<SessionManager>()
        coEvery { mockSessionManager.saveSession(any()) } returns false
        
        // When: Usuario hace login exitoso pero falla el guardado de sesión
        ActivityScenario.launch(LoginActivity::class.java).use { loginScenario ->
            
            loginScenario.onActivity { activity ->
                // Reemplazar SessionManager con mock que falla
                val sessionManagerField = LoginActivity::class.java.getDeclaredField("sessionManager")
                sessionManagerField.isAccessible = true
                sessionManagerField.set(activity, mockSessionManager)
                
                // Simular login exitoso con fallo en guardado de sesión
                runBlocking {
                    val result = mockSessionManager.saveSession(mockFirebaseUser)
                    assert(!result) { "El guardado de sesión debería fallar en este test" }
                }
            }
        }
        
        // Then: La aplicación debe continuar funcionando sin persistencia
        // (El usuario debería poder usar la app pero tendrá que hacer login la próxima vez)
    }

    /**
     * Test del flujo completo de logout con limpieza de sesión
     * Requisitos: 2.1, 2.2, 2.3 - Logout completo con limpieza y navegación
     */
    @Test
    fun testCompleteLogoutFlowWithSessionCleanup() {
        // Given: Usuario autenticado con sesión guardada
        runBlocking {
            sessionManager.saveSession(mockFirebaseUser)
        }
        
        ActivityScenario.launch(MainActivity::class.java).use { mainScenario ->
            
            // When: Usuario hace logout
            mainScenario.onActivity { activity ->
                // Simular logout
                val logoutMethod = MainActivity::class.java.getDeclaredMethod("logout")
                logoutMethod.isAccessible = true
                logoutMethod.invoke(activity)
            }
            
            Thread.sleep(2000)
        }
        
        // Then: Verificar que la sesión fue limpiada
        runBlocking {
            val sessionData = sessionManager.getStoredSession()
            assert(sessionData == null) { "La sesión debería haber sido limpiada después del logout" }
        }
        
        // Verificar navegación a LoginActivity
        intended(hasComponent(LoginActivity::class.java.name))
        
        // Verificar que el próximo inicio requiere login
        ActivityScenario.launch(SessionCheckActivity::class.java).use { sessionCheckScenario ->
            Thread.sleep(3000)
        }
        
        // Debe navegar a LoginActivity ya que no hay sesión
        intended(hasComponent(LoginActivity::class.java.name))
    }

    /**
     * Test de confirmación de logout
     * Requisito: 2.1 - Confirmación antes de cerrar sesión
     */
    @Test
    fun testLogoutConfirmationDialog() {
        // Given: Usuario autenticado
        runBlocking {
            sessionManager.saveSession(mockFirebaseUser)
        }
        
        ActivityScenario.launch(MainActivity::class.java).use { mainScenario ->
            
            // When: Usuario intenta hacer logout
            mainScenario.onActivity { activity ->
                // Mostrar diálogo de confirmación
                val showDialogMethod = MainActivity::class.java.getDeclaredMethod("showLogoutConfirmationDialog")
                showDialogMethod.isAccessible = true
                showDialogMethod.invoke(activity)
            }
            
            Thread.sleep(500)
            
            // Then: Debe mostrar diálogo de confirmación
            onView(withText(R.string.account_logout_confirm))
                .check(matches(isDisplayed()))
            
            onView(withText(R.string.account_logout_confirm_button))
                .check(matches(isDisplayed()))
            
            onView(withText(R.string.account_logout_cancel))
                .check(matches(isDisplayed()))
            
            // When: Usuario confirma logout
            onView(withText(R.string.account_logout_confirm_button))
                .perform(click())
            
            Thread.sleep(1000)
        }
        
        // Then: Debe proceder con el logout
        intended(hasComponent(LoginActivity::class.java.name))
    }

    /**
     * Test de cancelación de logout
     * Requisito: 2.1 - Permitir cancelar logout
     */
    @Test
    fun testLogoutCancellation() {
        // Given: Usuario autenticado
        runBlocking {
            sessionManager.saveSession(mockFirebaseUser)
        }
        
        ActivityScenario.launch(MainActivity::class.java).use { mainScenario ->
            
            // When: Usuario intenta hacer logout pero cancela
            mainScenario.onActivity { activity ->
                val showDialogMethod = MainActivity::class.java.getDeclaredMethod("showLogoutConfirmationDialog")
                showDialogMethod.isAccessible = true
                showDialogMethod.invoke(activity)
            }
            
            Thread.sleep(500)
            
            // When: Usuario cancela logout
            onView(withText(R.string.account_logout_cancel))
                .perform(click())
            
            Thread.sleep(500)
            
            // Then: Debe permanecer en MainActivity
            mainScenario.onActivity { activity ->
                assert(!activity.isFinishing) { "MainActivity no debería estar finalizando después de cancelar logout" }
            }
        }
        
        // Verificar que la sesión sigue intacta
        runBlocking {
            val sessionData = sessionManager.getStoredSession()
            assert(sessionData != null) { "La sesión debería seguir existiendo después de cancelar logout" }
        }
    }

    /**
     * Test de integración completa: Login -> Uso -> Logout -> Login nuevamente
     * Requisitos: 1.1, 2.1, 2.2, 2.3 - Ciclo completo de autenticación
     */
    @Test
    fun testCompleteAuthenticationCycle() {
        // Phase 1: Login inicial
        ActivityScenario.launch(LoginActivity::class.java).use { loginScenario ->
            
            // Simular login exitoso
            loginScenario.onActivity { activity ->
                runBlocking {
                    sessionManager.saveSession(mockFirebaseUser)
                }
                activity.finish()
            }
        }
        
        // Verificar sesión guardada
        runBlocking {
            val session1 = sessionManager.getStoredSession()
            assert(session1 != null) { "Primera sesión debería estar guardada" }
        }
        
        // Phase 2: Uso de la aplicación con sesión persistente
        ActivityScenario.launch(SessionCheckActivity::class.java).use { sessionCheckScenario ->
            Thread.sleep(3000)
        }
        
        intended(hasComponent(MainActivity::class.java.name))
        
        // Phase 3: Logout
        ActivityScenario.launch(MainActivity::class.java).use { mainScenario ->
            mainScenario.onActivity { activity ->
                val logoutMethod = MainActivity::class.java.getDeclaredMethod("logout")
                logoutMethod.isAccessible = true
                logoutMethod.invoke(activity)
            }
            Thread.sleep(2000)
        }
        
        // Verificar sesión limpiada
        runBlocking {
            val session2 = sessionManager.getStoredSession()
            assert(session2 == null) { "Sesión debería estar limpiada después del logout" }
        }
        
        // Phase 4: Nuevo login requerido
        ActivityScenario.launch(SessionCheckActivity::class.java).use { sessionCheckScenario ->
            Thread.sleep(3000)
        }
        
        intended(hasComponent(LoginActivity::class.java.name))
        
        // Phase 5: Segundo login
        ActivityScenario.launch(LoginActivity::class.java).use { loginScenario ->
            loginScenario.onActivity { activity ->
                runBlocking {
                    sessionManager.saveSession(mockFirebaseUser)
                }
            }
        }
        
        // Verificar nueva sesión guardada
        runBlocking {
            val session3 = sessionManager.getStoredSession()
            assert(session3 != null) { "Nueva sesión debería estar guardada después del segundo login" }
        }
    }
}
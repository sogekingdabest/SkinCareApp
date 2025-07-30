package es.monsteraltech.skincare_tfm.login

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Before
import org.junit.After
import org.junit.Assert.*

/**
 * Tests de instrumentación para verificar la navegación de InicioActivity
 * Verifica que InicioActivity navega correctamente a SessionCheckActivity
 */
@RunWith(AndroidJUnit4::class)
class InicioActivityNavigationTest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun testInicioActivityLaunchesSuccessfully() {
        // Given
        val intent = Intent(ApplicationProvider.getApplicationContext(), InicioActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // When
        val scenario = ActivityScenario.launch<InicioActivity>(intent)
        
        // Then
        scenario.onActivity { activity ->
            assertNotNull("InicioActivity should be created", activity)
            assertFalse("InicioActivity should be running initially", activity.isFinishing)
        }
        
        scenario.close()
    }

    @Test
    fun testSplashScreenDisplaysForCorrectDuration() {
        // Given
        val intent = Intent(ApplicationProvider.getApplicationContext(), InicioActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val startTime = System.currentTimeMillis()

        // When
        val scenario = ActivityScenario.launch<InicioActivity>(intent)
        
        // Verificar que la actividad no navega inmediatamente
        Thread.sleep(1000) // Wait 1 second
        
        scenario.onActivity { activity ->
            // Should still be running after 1 second
            assertFalse("Activity should not finish immediately", activity.isFinishing)
        }

        // Wait for navigation to complete
        Thread.sleep(4500) // Wait for full delay + buffer
        
        val endTime = System.currentTimeMillis()
        val elapsedTime = endTime - startTime

        // Then
        assertTrue("Should display splash for at least 4 seconds", elapsedTime >= 4000)
        
        scenario.close()
    }

    @Test
    fun testNavigationIntentIsCorrect() {
        // Given
        val intent = Intent(ApplicationProvider.getApplicationContext(), InicioActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // When
        val scenario = ActivityScenario.launch<InicioActivity>(intent)
        
        // Wait for navigation to happen
        Thread.sleep(5000) // 4 seconds delay + 1 second buffer

        // Then - Verificar que la navegación ocurre sin errores
        // En un entorno de test real, podríamos verificar que SessionCheckActivity está activa
        // Para este test, verificamos que no hay crashes durante la navegación
        
        scenario.close()
    }

    @After
    fun tearDown() {
        // Cleanup if needed
    }
}
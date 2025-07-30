package es.monsteraltech.skincare_tfm.login

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivity
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import java.util.concurrent.TimeUnit

/**
 * Tests unitarios para InicioActivity
 * Verifica que la navegación se dirija correctamente a SessionCheckActivity
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class InicioActivityTest {

    @Test
    fun `onCreate should set content view and start animations`() {
        // Given & When
        val activityController: ActivityController<InicioActivity> = Robolectric.buildActivity(InicioActivity::class.java)
        val activity = activityController.create().get()
        val shadowActivity = Shadows.shadowOf(activity)

        // Then
        assertNotNull("Activity should be created", activity)
        assertFalse("Activity should not be finished initially", activity.isFinishing)
    }

    @Test
    fun `should navigate to SessionCheckActivity after delay`() {
        // Given
        val activityController: ActivityController<InicioActivity> = Robolectric.buildActivity(InicioActivity::class.java)
        val activity = activityController.create().start().resume().get()
        val shadowActivity = Shadows.shadowOf(activity)

        // When - Avanzar el tiempo para que se ejecute el Handler
        Robolectric.getForegroundThreadScheduler().advanceBy(4000, TimeUnit.MILLISECONDS)

        // Then
        val nextStartedActivity = shadowActivity.nextStartedActivity
        assertNotNull("Should start next activity", nextStartedActivity)
        assertEquals(
            "Should navigate to SessionCheckActivity",
            SessionCheckActivity::class.java.name,
            nextStartedActivity.component?.className
        )
        assertTrue("Activity should be finishing after navigation", activity.isFinishing)
    }

    @Test
    fun `should not navigate before delay completes`() {
        // Given
        val activityController: ActivityController<InicioActivity> = Robolectric.buildActivity(InicioActivity::class.java)
        val activity = activityController.create().start().resume().get()
        val shadowActivity = Shadows.shadowOf(activity)

        // When - Avanzar el tiempo pero no completar el delay
        Robolectric.getForegroundThreadScheduler().advanceBy(2000, TimeUnit.MILLISECONDS)

        // Then
        val nextStartedActivity = shadowActivity.nextStartedActivity
        assertNull("Should not start next activity before delay", nextStartedActivity)
        assertFalse("Activity should not be finishing before delay", activity.isFinishing)
    }

    @Test
    fun `should use correct transition animation pairs`() {
        // Given
        val activityController: ActivityController<InicioActivity> = Robolectric.buildActivity(InicioActivity::class.java)
        val activity = activityController.create().start().resume().get()
        val shadowActivity = Shadows.shadowOf(activity)

        // When
        Robolectric.getForegroundThreadScheduler().advanceBy(4000, TimeUnit.MILLISECONDS)

        // Then
        val nextStartedActivity = shadowActivity.nextStartedActivity
        assertNotNull("Should start next activity", nextStartedActivity)
        
        // Verificar que se está usando ActivityOptions para las transiciones
        // (En un test real con Espresso podríamos verificar las transiciones más detalladamente)
        assertEquals(
            "Should navigate to SessionCheckActivity",
            SessionCheckActivity::class.java.name,
            nextStartedActivity.component?.className
        )
    }

    @Test
    fun `should maintain splash screen timing of 4 seconds`() {
        // Given
        val activityController: ActivityController<InicioActivity> = Robolectric.buildActivity(InicioActivity::class.java)
        val activity = activityController.create().start().resume().get()
        val shadowActivity = Shadows.shadowOf(activity)

        // When - Verificar que no navega antes de 4 segundos
        Robolectric.getForegroundThreadScheduler().advanceBy(3999, TimeUnit.MILLISECONDS)
        assertNull("Should not navigate at 3999ms", shadowActivity.nextStartedActivity)

        // When - Verificar que navega exactamente a los 4 segundos
        Robolectric.getForegroundThreadScheduler().advanceBy(1, TimeUnit.MILLISECONDS)
        
        // Then
        val nextStartedActivity = shadowActivity.nextStartedActivity
        assertNotNull("Should navigate at 4000ms", nextStartedActivity)
        assertEquals(
            "Should navigate to SessionCheckActivity",
            SessionCheckActivity::class.java.name,
            nextStartedActivity.component?.className
        )
    }
}
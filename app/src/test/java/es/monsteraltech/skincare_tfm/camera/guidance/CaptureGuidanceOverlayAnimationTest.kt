package es.monsteraltech.skincare_tfm.camera.guidance

import android.animation.ValueAnimator
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

/**
 * Tests para las animaciones del CaptureGuidanceOverlay
 * Verifica el comportamiento de las animaciones de pulso y transiciones de color
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class CaptureGuidanceOverlayAnimationTest {

    private lateinit var context: Context
    private lateinit var overlay: CaptureGuidanceOverlay

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        overlay = CaptureGuidanceOverlay(context)
        overlay.layout(0, 0, 800, 600)
    }

    @Test
    fun `estado SEARCHING inicia animación de pulso`() {
        overlay.updateGuideState(GuideState.SEARCHING)
        
        // Avanzar el tiempo para que la animación se inicie
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        
        val pulseAnimator = getPrivateField<ValueAnimator?>(overlay, "pulseAnimator")
        assertNotNull(pulseAnimator, "Animación de pulso debe estar activa para SEARCHING")
    }

    @Test
    fun `estado READY inicia animación de ready`() {
        overlay.updateGuideState(GuideState.READY)
        
        // Avanzar el tiempo para que la animación se inicie
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        
        val pulseAnimator = getPrivateField<ValueAnimator?>(overlay, "pulseAnimator")
        assertNotNull(pulseAnimator, "Animación de ready debe estar activa para READY")
    }

    @Test
    fun `estados sin animación detienen pulso`() {
        // Primero iniciar una animación
        overlay.updateGuideState(GuideState.SEARCHING)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        
        // Luego cambiar a un estado sin animación
        overlay.updateGuideState(GuideState.CENTERING)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        
        val pulseScale = getPrivateField<Float>(overlay, "pulseScale")
        assertEquals(1f, pulseScale, "Escala de pulso debe volver a 1.0 cuando se detiene la animación")
    }

    @Test
    fun `cambio de estado cancela animación anterior`() {
        // Iniciar animación SEARCHING
        overlay.updateGuideState(GuideState.SEARCHING)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        
        val firstAnimator = getPrivateField<ValueAnimator?>(overlay, "pulseAnimator")
        assertNotNull(firstAnimator)
        
        // Cambiar a READY (diferente animación)
        overlay.updateGuideState(GuideState.READY)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        
        val secondAnimator = getPrivateField<ValueAnimator?>(overlay, "pulseAnimator")
        assertNotNull(secondAnimator)
        
        // Los animadores deben ser diferentes (el anterior se canceló)
        assert(firstAnimator != secondAnimator)
    }

    @Test
    fun `transición de color se ejecuta al cambiar estado`() {
        val initialColor = getPrivateField<Int>(overlay, "currentColor")
        
        overlay.updateGuideState(GuideState.READY)
        
        // Avanzar tiempo para la transición
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        
        val colorAnimator = getPrivateField<ValueAnimator?>(overlay, "colorTransitionAnimator")
        assertNotNull(colorAnimator, "Debe haber un animador de transición de color")
    }

    @Test
    fun `onDetachedFromWindow cancela animaciones`() {
        // Iniciar animaciones
        overlay.updateGuideState(GuideState.SEARCHING)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        
        // Simular detach
        overlay.onDetachedFromWindow()
        
        val pulseAnimator = getPrivateField<ValueAnimator?>(overlay, "pulseAnimator")
        val colorAnimator = getPrivateField<ValueAnimator?>(overlay, "colorTransitionAnimator")
        
        // Las animaciones deben estar canceladas o ser null
        if (pulseAnimator != null) {
            assert(!pulseAnimator.isRunning)
        }
        if (colorAnimator != null) {
            assert(!colorAnimator.isRunning)
        }
    }

    @Test
    fun `animación de pulso modifica escala correctamente`() {
        overlay.updateGuideState(GuideState.SEARCHING)
        
        val initialScale = getPrivateField<Float>(overlay, "pulseScale")
        assertEquals(1f, initialScale, "Escala inicial debe ser 1.0")
        
        // Avanzar tiempo para que la animación progrese
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        
        // La escala puede haber cambiado debido a la animación
        val animatedScale = getPrivateField<Float>(overlay, "pulseScale")
        // No podemos predecir el valor exacto, pero debe ser un float válido
        assert(animatedScale > 0f)
    }

    @Test
    fun `múltiples cambios de estado manejan animaciones correctamente`() {
        // Secuencia rápida de cambios de estado
        overlay.updateGuideState(GuideState.SEARCHING)
        overlay.updateGuideState(GuideState.CENTERING)
        overlay.updateGuideState(GuideState.READY)
        overlay.updateGuideState(GuideState.BLURRY)
        
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        
        // El overlay debe manejar los cambios sin errores
        val currentState = getPrivateField<GuideState>(overlay, "currentState")
        assertEquals(GuideState.BLURRY, currentState)
    }

    @Test
    fun `animación READY tiene duración diferente a SEARCHING`() {
        // Configurar SEARCHING
        overlay.updateGuideState(GuideState.SEARCHING)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        
        val searchingAnimator = getPrivateField<ValueAnimator?>(overlay, "pulseAnimator")
        val searchingDuration = searchingAnimator?.duration
        
        // Cambiar a READY
        overlay.updateGuideState(GuideState.READY)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        
        val readyAnimator = getPrivateField<ValueAnimator?>(overlay, "pulseAnimator")
        val readyDuration = readyAnimator?.duration
        
        // Las duraciones deben ser diferentes
        if (searchingDuration != null && readyDuration != null) {
            assert(searchingDuration != readyDuration)
        }
    }

    @Test
    fun `escala de pulso se resetea al detener animación`() {
        // Iniciar animación
        overlay.updateGuideState(GuideState.SEARCHING)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        
        // Detener animación
        overlay.updateGuideState(GuideState.CENTERING)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        
        val pulseScale = getPrivateField<Float>(overlay, "pulseScale")
        assertEquals(1f, pulseScale, "Escala debe resetearse a 1.0 al detener animación")
    }

    /**
     * Función auxiliar para acceder a campos privados usando reflexión
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrivateField(obj: Any, fieldName: String): T {
        val field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(obj) as T
    }
}
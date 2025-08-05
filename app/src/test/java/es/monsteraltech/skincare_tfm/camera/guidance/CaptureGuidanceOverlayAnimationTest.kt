package es.monsteraltech.skincare_tfm.camera.guidance
import android.animation.ValueAnimator
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

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
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val pulseAnimator = getPrivateField<ValueAnimator?>(overlay, "pulseAnimator")
        assertNotNull(pulseAnimator, "Animación de pulso debe estar activa para SEARCHING")
    }
    @Test
    fun `estado READY inicia animación de ready`() {
        overlay.updateGuideState(GuideState.READY)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val pulseAnimator = getPrivateField<ValueAnimator?>(overlay, "pulseAnimator")
        assertNotNull(pulseAnimator, "Animación de ready debe estar activa para READY")
    }
    @Test
    fun `estados sin animación detienen pulso`() {
        overlay.updateGuideState(GuideState.SEARCHING)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        overlay.updateGuideState(GuideState.CENTERING)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val pulseScale = getPrivateField<Float>(overlay, "pulseScale")
        assertEquals(1f, pulseScale, "Escala de pulso debe volver a 1.0 cuando se detiene la animación")
    }
    @Test
    fun `cambio de estado cancela animación anterior`() {
        overlay.updateGuideState(GuideState.SEARCHING)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val firstAnimator = getPrivateField<ValueAnimator?>(overlay, "pulseAnimator")
        assertNotNull(firstAnimator)
        overlay.updateGuideState(GuideState.READY)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val secondAnimator = getPrivateField<ValueAnimator?>(overlay, "pulseAnimator")
        assertNotNull(secondAnimator)
        assert(firstAnimator != secondAnimator)
    }
    @Test
    fun `transición de color se ejecuta al cambiar estado`() {
        val initialColor = getPrivateField<Int>(overlay, "currentColor")
        overlay.updateGuideState(GuideState.READY)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val colorAnimator = getPrivateField<ValueAnimator?>(overlay, "colorTransitionAnimator")
        assertNotNull(colorAnimator, "Debe haber un animador de transición de color")
    }
    @Test
    fun `onDetachedFromWindow cancela animaciones`() {
        overlay.updateGuideState(GuideState.SEARCHING)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        overlay.onDetachedFromWindow()
        val pulseAnimator = getPrivateField<ValueAnimator?>(overlay, "pulseAnimator")
        val colorAnimator = getPrivateField<ValueAnimator?>(overlay, "colorTransitionAnimator")
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
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val animatedScale = getPrivateField<Float>(overlay, "pulseScale")
        assert(animatedScale > 0f)
    }
    @Test
    fun `múltiples cambios de estado manejan animaciones correctamente`() {
        overlay.updateGuideState(GuideState.SEARCHING)
        overlay.updateGuideState(GuideState.CENTERING)
        overlay.updateGuideState(GuideState.READY)
        overlay.updateGuideState(GuideState.BLURRY)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val currentState = getPrivateField<GuideState>(overlay, "currentState")
        assertEquals(GuideState.BLURRY, currentState)
    }
    @Test
    fun `animación READY tiene duración diferente a SEARCHING`() {
        overlay.updateGuideState(GuideState.SEARCHING)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val searchingAnimator = getPrivateField<ValueAnimator?>(overlay, "pulseAnimator")
        val searchingDuration = searchingAnimator?.duration
        overlay.updateGuideState(GuideState.READY)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val readyAnimator = getPrivateField<ValueAnimator?>(overlay, "pulseAnimator")
        val readyDuration = readyAnimator?.duration
        if (searchingDuration != null && readyDuration != null) {
            assert(searchingDuration != readyDuration)
        }
    }
    @Test
    fun `escala de pulso se resetea al detener animación`() {
        overlay.updateGuideState(GuideState.SEARCHING)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        overlay.updateGuideState(GuideState.CENTERING)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val pulseScale = getPrivateField<Float>(overlay, "pulseScale")
        assertEquals(1f, pulseScale, "Escala debe resetearse a 1.0 al detener animación")
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrivateField(obj: Any, fieldName: String): T {
        val field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(obj) as T
    }
}
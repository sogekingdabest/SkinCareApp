package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * Tests de integración para CaptureGuidanceOverlay
 * Verifica la integración con otros componentes del sistema de guías
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class CaptureGuidanceOverlayIntegrationTest {

    private lateinit var context: Context
    private lateinit var overlay: CaptureGuidanceOverlay
    private lateinit var config: CaptureGuidanceConfig

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        config = CaptureGuidanceConfig()
        overlay = CaptureGuidanceOverlay(context)
        overlay.updateConfig(config)
        overlay.layout(0, 0, 800, 600)
    }

    @Test
    fun `overlay integra correctamente con CaptureGuidanceConfig`() {
        val customConfig = CaptureGuidanceConfig(
            guideCircleRadius = 180f,
            centeringTolerance = 40f,
            enableHapticFeedback = true
        )

        overlay.updateConfig(customConfig)

        // Verificar que la configuración se aplicó
        val storedConfig = getPrivateField<CaptureGuidanceConfig>(overlay, "config")
        assertEquals(customConfig.guideCircleRadius, storedConfig.guideCircleRadius)
        assertEquals(customConfig.centeringTolerance, storedConfig.centeringTolerance)
        assertEquals(customConfig.enableHapticFeedback, storedConfig.enableHapticFeedback)
    }

    @Test
    fun `overlay responde a todos los estados de GuideState`() {
        val allStates = GuideState.values()

        allStates.forEach { state ->
            overlay.updateGuideState(state)
            
            val currentState = getPrivateField<GuideState>(overlay, "currentState")
            assertEquals("Estado no se actualizó correctamente", state, currentState)
            
            val message = getPrivateField<String>(overlay, "guidanceMessage")
            assertNotNull("Mensaje debe existir para estado $state", message)
            assert(message.isNotEmpty()) { "Mensaje no debe estar vacío para estado $state" }
        }
    }

    @Test
    fun `overlay maneja secuencia típica de detección`() {
        // Simular secuencia típica de uso
        
        // 1. Inicialmente buscando
        overlay.updateGuideState(GuideState.SEARCHING)
        assertEquals(GuideState.SEARCHING, getPrivateField<GuideState>(overlay, "currentState"))
        
        // 2. Lunar detectado pero no centrado
        overlay.updateGuideState(GuideState.CENTERING)
        overlay.updateMolePosition(PointF(350f, 250f), 0.7f)
        assertEquals(GuideState.CENTERING, getPrivateField<GuideState>(overlay, "currentState"))
        assertNotNull(getPrivateField<PointF?>(overlay, "molePosition"))
        
        // 3. Problemas de calidad
        overlay.updateGuideState(GuideState.BLURRY)
        assertEquals(GuideState.BLURRY, getPrivateField<GuideState>(overlay, "currentState"))
        
        // 4. Finalmente listo
        overlay.updateGuideState(GuideState.READY)
        assertEquals(GuideState.READY, getPrivateField<GuideState>(overlay, "currentState"))
    }

    @Test
    fun `overlay maneja configuración de accesibilidad`() {
        val accessibilityConfig = CaptureGuidanceConfig(
            enableHapticFeedback = true,
            enableAutoCapture = true,
            autoCaptureDelay = 2000L
        )

        overlay.updateConfig(accessibilityConfig)

        val storedConfig = getPrivateField<CaptureGuidanceConfig>(overlay, "config")
        assertEquals(true, storedConfig.enableHapticFeedback)
        assertEquals(true, storedConfig.enableAutoCapture)
        assertEquals(2000L, storedConfig.autoCaptureDelay)
    }

    @Test
    fun `overlay maneja diferentes niveles de confianza del lunar`() {
        val position = PointF(400f, 300f)
        
        // Confianza baja
        overlay.updateMolePosition(position, 0.2f)
        assertEquals(0.2f, getPrivateField<Float>(overlay, "moleConfidence"), 0.01f)
        
        // Confianza media
        overlay.updateMolePosition(position, 0.6f)
        assertEquals(0.6f, getPrivateField<Float>(overlay, "moleConfidence"), 0.01f)
        
        // Confianza alta
        overlay.updateMolePosition(position, 0.9f)
        assertEquals(0.9f, getPrivateField<Float>(overlay, "moleConfidence"), 0.01f)
    }

    @Test
    fun `overlay maneja cambios de configuración en tiempo real`() {
        // Configuración inicial
        val config1 = CaptureGuidanceConfig(guideCircleRadius = 150f)
        overlay.updateConfig(config1)
        
        // Simular detección
        overlay.updateGuideState(GuideState.CENTERING)
        overlay.updateMolePosition(PointF(400f, 300f), 0.8f)
        
        // Cambiar configuración
        val config2 = CaptureGuidanceConfig(guideCircleRadius = 200f)
        overlay.updateConfig(config2)
        
        // Verificar que la nueva configuración se aplicó
        val storedConfig = getPrivateField<CaptureGuidanceConfig>(overlay, "config")
        assertEquals(200f, storedConfig.guideCircleRadius, 0.01f)
        
        // El estado y posición del lunar deben mantenerse
        assertEquals(GuideState.CENTERING, getPrivateField<GuideState>(overlay, "currentState"))
        assertNotNull(getPrivateField<PointF?>(overlay, "molePosition"))
    }

    @Test
    fun `overlay maneja mensajes personalizados`() {
        val customMessage = "Mensaje personalizado de prueba"
        
        overlay.setGuidanceMessage(customMessage)
        
        val storedMessage = getPrivateField<String>(overlay, "guidanceMessage")
        assertEquals(customMessage, storedMessage)
    }

    @Test
    fun `overlay resetea correctamente al cambiar de estado`() {
        // Establecer estado con lunar
        overlay.updateGuideState(GuideState.CENTERING)
        overlay.updateMolePosition(PointF(400f, 300f), 0.8f)
        
        // Cambiar a estado de búsqueda
        overlay.updateGuideState(GuideState.SEARCHING)
        
        // El estado debe cambiar pero la posición del lunar se mantiene
        // (es responsabilidad del controlador limpiarla si es necesario)
        assertEquals(GuideState.SEARCHING, getPrivateField<GuideState>(overlay, "currentState"))
    }

    @Test
    fun `overlay maneja múltiples actualizaciones rápidas`() {
        // Simular actualizaciones rápidas como las que vendrían del procesamiento en tiempo real
        for (i in 1..10) {
            val x = 400f + (i * 5f)
            val y = 300f + (i * 3f)
            val confidence = 0.5f + (i * 0.04f)
            
            overlay.updateMolePosition(PointF(x, y), confidence)
            
            if (i % 3 == 0) {
                overlay.updateGuideState(GuideState.CENTERING)
            } else if (i % 5 == 0) {
                overlay.updateGuideState(GuideState.READY)
            }
        }
        
        // Verificar que el overlay maneja las actualizaciones sin errores
        val finalPosition = getPrivateField<PointF?>(overlay, "molePosition")
        assertNotNull(finalPosition)
        assertEquals(445f, finalPosition!!.x, 0.01f)
        assertEquals(327f, finalPosition.y, 0.01f)
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
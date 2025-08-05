package es.monsteraltech.skincare_tfm.camera.guidance
import android.content.Context
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class CaptureGuidanceOverlayTest {
    private lateinit var context: Context
    private lateinit var overlay: CaptureGuidanceOverlay
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        overlay = CaptureGuidanceOverlay(context)
    }
    @Test
    fun `overlay se inicializa correctamente`() {
        assertNotNull(overlay)
        assertEquals(GuideState.SEARCHING, getPrivateField(overlay, "currentState"))
    }
    @Test
    fun `updateMolePosition actualiza posición correctamente`() {
        val testPosition = PointF(100f, 200f)
        val testConfidence = 0.8f
        overlay.updateMolePosition(testPosition, testConfidence)
        val storedPosition = getPrivateField<PointF?>(overlay, "molePosition")
        val storedConfidence = getPrivateField<Float>(overlay, "moleConfidence")
        assertEquals(testPosition.x, storedPosition?.x)
        assertEquals(testPosition.y, storedPosition?.y)
        assertEquals(testConfidence, storedConfidence)
    }
    @Test
    fun `updateMolePosition con null limpia posición`() {
        overlay.updateMolePosition(PointF(100f, 200f), 0.5f)
        overlay.updateMolePosition(null, 0f)
        val storedPosition = getPrivateField<PointF?>(overlay, "molePosition")
        val storedConfidence = getPrivateField<Float>(overlay, "moleConfidence")
        assertEquals(null, storedPosition)
        assertEquals(0f, storedConfidence)
    }
    @Test
    fun `updateGuideState cambia estado correctamente`() {
        val newState = GuideState.READY
        overlay.updateGuideState(newState)
        val currentState = getPrivateField<GuideState>(overlay, "currentState")
        assertEquals(newState, currentState)
    }
    @Test
    fun `setGuidanceMessage actualiza mensaje`() {
        val testMessage = "Mensaje de prueba"
        overlay.setGuidanceMessage(testMessage)
        val storedMessage = getPrivateField<String>(overlay, "guidanceMessage")
        assertEquals(testMessage, storedMessage)
    }
    @Test
    fun `updateConfig actualiza configuración`() {
        val newConfig = CaptureGuidanceConfig(
            guideCircleRadius = 200f,
            centeringTolerance = 30f
        )
        overlay.updateConfig(newConfig)
        val storedConfig = getPrivateField<CaptureGuidanceConfig>(overlay, "config")
        assertEquals(newConfig.guideCircleRadius, storedConfig.guideCircleRadius)
        assertEquals(newConfig.centeringTolerance, storedConfig.centeringTolerance)
    }
    @Test
    fun `estados diferentes generan mensajes apropiados`() {
        val stateMessages = mapOf(
            GuideState.SEARCHING to "Busca un lunar en el área circular",
            GuideState.CENTERING to "Centra el lunar en el círculo",
            GuideState.TOO_FAR to "Acércate más al lunar",
            GuideState.TOO_CLOSE to "Aléjate un poco del lunar",
            GuideState.POOR_LIGHTING to "Mejora la iluminación",
            GuideState.BLURRY to "Mantén firme la cámara",
            GuideState.READY to "¡Listo para capturar!"
        )
        stateMessages.forEach { (state, expectedMessage) ->
            overlay.updateGuideState(state)
            val actualMessage = getPrivateField<String>(overlay, "guidanceMessage")
            assertEquals(expectedMessage, actualMessage, "Mensaje incorrecto para estado $state")
        }
    }
    @Test
    fun `configuración por defecto es válida`() {
        val config = getPrivateField<CaptureGuidanceConfig>(overlay, "config")
        assert(config.guideCircleRadius > 0f)
        assert(config.centeringTolerance > 0f)
        assert(config.minMoleAreaRatio > 0f && config.minMoleAreaRatio < 1f)
        assert(config.maxMoleAreaRatio > config.minMoleAreaRatio && config.maxMoleAreaRatio < 1f)
    }
    @Test
    fun `onSizeChanged actualiza geometría`() {
        val width = 800
        val height = 600
        overlay.layout(0, 0, width, height)
        overlay.onSizeChanged(width, height, 0, 0)
        val centerPoint = getPrivateField<PointF>(overlay, "centerPoint")
        assertEquals(width / 2f, centerPoint.x)
        assertEquals(height / 2f, centerPoint.y)
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrivateField(obj: Any, fieldName: String): T {
        val field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(obj) as T
    }
}
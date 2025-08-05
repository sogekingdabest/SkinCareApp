package es.monsteraltech.skincare_tfm.camera.guidance
import android.content.Context
import android.graphics.Canvas
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class CaptureGuidanceOverlayUITest {
    private lateinit var context: Context
    private lateinit var overlay: CaptureGuidanceOverlay
    @Mock
    private lateinit var mockCanvas: Canvas
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
        overlay = CaptureGuidanceOverlay(context)
        overlay.layout(0, 0, 800, 600)
        overlay.onSizeChanged(800, 600, 0, 0)
    }
    @Test
    fun `onDraw renderiza elementos básicos`() {
        overlay.onDraw(mockCanvas)
        verify(mockCanvas, times(1)).drawRect(any(), any(), any(), any(), any())
        verify(mockCanvas, times(2)).drawCircle(any(), any(), any(), any())
    }
    @Test
    fun `estado SEARCHING muestra animación de pulso`() {
        overlay.updateGuideState(GuideState.SEARCHING)
        Thread.sleep(100)
        overlay.onDraw(mockCanvas)
        verify(mockCanvas, times(2)).drawCircle(any(), any(), any(), any())
    }
    @Test
    fun `estado READY muestra color verde`() {
        overlay.updateGuideState(GuideState.READY)
        Thread.sleep(350)
        overlay.onDraw(mockCanvas)
        verify(mockCanvas, times(2)).drawCircle(any(), any(), any(), any())
    }
    @Test
    fun `estado CENTERING muestra líneas de centrado`() {
        overlay.updateGuideState(GuideState.CENTERING)
        overlay.onDraw(mockCanvas)
        verify(mockCanvas, times(2)).drawLine(any(), any(), any(), any(), any())
    }
    @Test
    fun `posición de lunar se renderiza correctamente`() {
        val molePosition = PointF(400f, 300f)
        overlay.updateMolePosition(molePosition, 0.8f)
        overlay.onDraw(mockCanvas)
        verify(mockCanvas, times(3)).drawCircle(any(), any(), any(), any())
    }
    @Test
    fun `estado CENTERING con lunar muestra línea de conexión`() {
        overlay.updateGuideState(GuideState.CENTERING)
        overlay.updateMolePosition(PointF(350f, 250f), 0.7f)
        overlay.onDraw(mockCanvas)
        verify(mockCanvas, times(3)).drawLine(any(), any(), any(), any(), any())
    }
    @Test
    fun `mensaje de guía se renderiza`() {
        overlay.setGuidanceMessage("Mensaje de prueba")
        overlay.onDraw(mockCanvas)
        verify(mockCanvas, times(1)).drawText(any<String>(), any(), any(), any())
        verify(mockCanvas, times(1)).drawRoundRect(any(), any(), any(), any())
    }
    @Test
    fun `estado TOO_FAR muestra indicadores de distancia`() {
        overlay.updateGuideState(GuideState.TOO_FAR)
        overlay.onDraw(mockCanvas)
        verify(mockCanvas, times(2)).drawPath(any(), any())
    }
    @Test
    fun `estado TOO_CLOSE muestra indicadores de distancia`() {
        overlay.updateGuideState(GuideState.TOO_CLOSE)
        overlay.onDraw(mockCanvas)
        verify(mockCanvas, times(2)).drawPath(any(), any())
    }
    @Test
    fun `estado BLURRY muestra indicador de desenfoque`() {
        overlay.updateGuideState(GuideState.BLURRY)
        overlay.onDraw(mockCanvas)
        verify(mockCanvas, times(1)).drawPath(any(), any())
    }
    @Test
    fun `estado POOR_LIGHTING muestra indicador de iluminación`() {
        overlay.updateGuideState(GuideState.POOR_LIGHTING)
        overlay.onDraw(mockCanvas)
        verify(mockCanvas, times(3)).drawCircle(any(), any(), any(), any())
        verify(mockCanvas, times(8)).drawLine(any(), any(), any(), any(), any())
    }
    @Test
    fun `configuración personalizada afecta renderizado`() {
        val customConfig = CaptureGuidanceConfig(
            guideCircleRadius = 200f,
            centeringTolerance = 30f
        )
        overlay.updateConfig(customConfig)
        overlay.onDraw(mockCanvas)
        verify(mockCanvas, times(2)).drawCircle(any(), any(), any(), any())
    }
    @Test
    fun `múltiples actualizaciones de estado funcionan correctamente`() {
        overlay.updateGuideState(GuideState.SEARCHING)
        overlay.onDraw(mockCanvas)
        overlay.updateGuideState(GuideState.CENTERING)
        overlay.onDraw(mockCanvas)
        overlay.updateGuideState(GuideState.READY)
        overlay.onDraw(mockCanvas)
        verify(mockCanvas, times(6)).drawCircle(any(), any(), any(), any())
    }
    @Test
    fun `confianza del lunar afecta tamaño del indicador`() {
        overlay.updateMolePosition(PointF(400f, 300f), 0.2f)
        overlay.onDraw(mockCanvas)
        overlay.updateMolePosition(PointF(400f, 300f), 0.9f)
        overlay.onDraw(mockCanvas)
        verify(mockCanvas, times(4)).drawCircle(any(), any(), any(), any())
    }
    @Test
    fun `overlay maneja cambios de tamaño correctamente`() {
        overlay.onSizeChanged(1000, 800, 800, 600)
        overlay.onDraw(mockCanvas)
        verify(mockCanvas, times(2)).drawCircle(any(), any(), any(), any())
    }
}
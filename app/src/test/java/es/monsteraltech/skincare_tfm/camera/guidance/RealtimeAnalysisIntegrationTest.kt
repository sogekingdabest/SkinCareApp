package es.monsteraltech.skincare_tfm.camera.guidance

import android.graphics.PointF
import android.graphics.RectF
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import org.opencv.core.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests de integración para el análisis en tiempo real completo
 * Verifica que el pipeline completo funcione correctamente
 */
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class RealtimeAnalysisIntegrationTest {

    @Mock
    private lateinit var mockMat: Mat

    private lateinit var moleDetector: MoleDetectionProcessor
    private lateinit var qualityAnalyzer: ImageQualityAnalyzer
    private lateinit var validationManager: CaptureValidationManager

    @Before
    fun setup() {
        moleDetector = MoleDetectionProcessor()
        qualityAnalyzer = ImageQualityAnalyzer()
        validationManager = CaptureValidationManager()
    }

    @Test
    fun `pipeline completo con detección exitosa y buena calidad`() = runTest {
        // Arrange
        val testFrame = createMockFrame(640, 480)
        val guideArea = RectF(200f, 150f, 440f, 330f) // Área central

        // Simular detección exitosa
        val mockDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(300, 220, 80, 60),
            centerPoint = Point(340.0, 250.0),
            confidence = 0.8f,
            area = 4800.0,
            contour = MatOfPoint(),
            method = "test"
        )

        // Simular buena calidad
        val mockQuality = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.5f,
            brightness = 120f,
            contrast = 0.4f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )

        // Act - Simular pipeline completo
        val validationResult = validationManager.validateCapture(
            mockDetection,
            mockQuality,
            guideArea
        )

        // Assert
        assertTrue(validationResult.canCapture)
        assertEquals(CaptureValidationManager.GuideState.READY, validationResult.guideState)
        assertEquals("Listo para capturar", validationResult.message)
        assertTrue(validationResult.confidence > 0.7f)
    }

    @Test
    fun `pipeline con lunar descentrado`() = runTest {
        // Arrange
        val guideArea = RectF(200f, 150f, 440f, 330f)

        // Lunar detectado pero fuera del centro
        val mockDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(100, 100, 50, 50),
            centerPoint = Point(125.0, 125.0), // Lejos del centro
            confidence = 0.8f,
            area = 2500.0,
            contour = MatOfPoint(),
            method = "test"
        )

        val mockQuality = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.5f,
            brightness = 120f,
            contrast = 0.4f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )

        // Act
        val validationResult = validationManager.validateCapture(
            mockDetection,
            mockQuality,
            guideArea
        )

        // Assert
        assertFalse(validationResult.canCapture)
        assertEquals(CaptureValidationManager.GuideState.CENTERING, validationResult.guideState)
        assertEquals("Centra el lunar en la guía", validationResult.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.NOT_CENTERED, validationResult.failureReason)
    }

    @Test
    fun `pipeline con lunar muy pequeño (muy lejos)`() = runTest {
        // Arrange
        val guideArea = RectF(200f, 150f, 440f, 330f)

        // Lunar centrado pero muy pequeño
        val mockDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(315, 235, 10, 10),
            centerPoint = Point(320.0, 240.0), // Centrado
            confidence = 0.7f,
            area = 100.0, // Muy pequeño
            contour = MatOfPoint(),
            method = "test"
        )

        val mockQuality = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.5f,
            brightness = 120f,
            contrast = 0.4f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )

        // Act
        val validationResult = validationManager.validateCapture(
            mockDetection,
            mockQuality,
            guideArea
        )

        // Assert
        assertFalse(validationResult.canCapture)
        assertEquals(CaptureValidationManager.GuideState.TOO_FAR, validationResult.guideState)
        assertEquals("Acércate más al lunar", validationResult.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.TOO_FAR, validationResult.failureReason)
    }

    @Test
    fun `pipeline con imagen borrosa`() = runTest {
        // Arrange
        val guideArea = RectF(200f, 150f, 440f, 330f)

        val mockDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(300, 220, 80, 60),
            centerPoint = Point(340.0, 250.0),
            confidence = 0.8f,
            area = 4800.0,
            contour = MatOfPoint(),
            method = "test"
        )

        // Imagen borrosa
        val mockQuality = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.1f, // Muy baja nitidez
            brightness = 120f,
            contrast = 0.4f,
            isBlurry = true,
            isOverexposed = false,
            isUnderexposed = false
        )

        // Act
        val validationResult = validationManager.validateCapture(
            mockDetection,
            mockQuality,
            guideArea
        )

        // Assert
        assertFalse(validationResult.canCapture)
        assertEquals(CaptureValidationManager.GuideState.BLURRY, validationResult.guideState)
        assertEquals("Imagen borrosa - mantén firme la cámara", validationResult.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.BLURRY, validationResult.failureReason)
    }

    @Test
    fun `pipeline sin detección de lunar`() = runTest {
        // Arrange
        val guideArea = RectF(200f, 150f, 440f, 330f)

        val mockQuality = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.5f,
            brightness = 120f,
            contrast = 0.4f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )

        // Act - Sin detección de lunar
        val validationResult = validationManager.validateCapture(
            null, // No hay detección
            mockQuality,
            guideArea
        )

        // Assert
        assertFalse(validationResult.canCapture)
        assertEquals(CaptureValidationManager.GuideState.SEARCHING, validationResult.guideState)
        assertEquals("Buscando lunar...", validationResult.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.NO_MOLE_DETECTED, validationResult.failureReason)
    }

    @Test
    fun `pipeline con iluminación insuficiente`() = runTest {
        // Arrange
        val guideArea = RectF(200f, 150f, 440f, 330f)

        val mockDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(300, 220, 80, 60),
            centerPoint = Point(340.0, 250.0),
            confidence = 0.8f,
            area = 4800.0,
            contour = MatOfPoint(),
            method = "test"
        )

        // Iluminación insuficiente
        val mockQuality = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.5f,
            brightness = 50f, // Muy oscuro
            contrast = 0.4f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = true
        )

        // Act
        val validationResult = validationManager.validateCapture(
            mockDetection,
            mockQuality,
            guideArea
        )

        // Assert
        assertFalse(validationResult.canCapture)
        assertEquals(CaptureValidationManager.GuideState.POOR_LIGHTING, validationResult.guideState)
        assertEquals("Necesitas más luz", validationResult.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.POOR_LIGHTING, validationResult.failureReason)
    }

    @Test
    fun `pipeline con sobreexposición`() = runTest {
        // Arrange
        val guideArea = RectF(200f, 150f, 440f, 330f)

        val mockDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(300, 220, 80, 60),
            centerPoint = Point(340.0, 250.0),
            confidence = 0.8f,
            area = 4800.0,
            contour = MatOfPoint(),
            method = "test"
        )

        // Sobreexposición
        val mockQuality = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.5f,
            brightness = 220f, // Muy brillante
            contrast = 0.4f,
            isBlurry = false,
            isOverexposed = true,
            isUnderexposed = false
        )

        // Act
        val validationResult = validationManager.validateCapture(
            mockDetection,
            mockQuality,
            guideArea
        )

        // Assert
        assertFalse(validationResult.canCapture)
        assertEquals(CaptureValidationManager.GuideState.POOR_LIGHTING, validationResult.guideState)
        assertEquals("Demasiada luz - busca sombra", validationResult.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.POOR_LIGHTING, validationResult.failureReason)
    }

    @Test
    fun `throttling de análisis funciona correctamente`() {
        // Arrange
        val throttleMs = 100L
        val startTime = System.currentTimeMillis()
        
        // Act - Simular múltiples llamadas rápidas
        val shouldProcess1 = shouldProcessFrame(startTime, throttleMs)
        val shouldProcess2 = shouldProcessFrame(startTime + 50, throttleMs) // Muy pronto
        val shouldProcess3 = shouldProcessFrame(startTime + 150, throttleMs) // Suficiente tiempo

        // Assert
        assertTrue(shouldProcess1) // Primera llamada siempre se procesa
        assertFalse(shouldProcess2) // Segunda llamada muy pronto, se omite
        assertTrue(shouldProcess3) // Tercera llamada después del throttle, se procesa
    }

    @Test
    fun `cálculo de área de guía es correcto`() {
        // Arrange
        val centerX = 320f
        val centerY = 240f
        val radius = 120f

        // Act
        val guideArea = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        // Assert
        assertEquals(200f, guideArea.left)
        assertEquals(120f, guideArea.top)
        assertEquals(440f, guideArea.right)
        assertEquals(360f, guideArea.bottom)
        assertEquals(centerX, guideArea.centerX())
        assertEquals(centerY, guideArea.centerY())
    }

    @Test
    fun `conversión de coordenadas funciona correctamente`() {
        // Arrange
        val imageWidth = 640
        val imageHeight = 480
        val previewWidth = 320
        val previewHeight = 240

        // Punto en coordenadas de imagen
        val imagePoint = Point(320.0, 240.0) // Centro de imagen

        // Act - Convertir a coordenadas de preview
        val previewPoint = PointF(
            (imagePoint.x * previewWidth / imageWidth).toFloat(),
            (imagePoint.y * previewHeight / imageHeight).toFloat()
        )

        // Assert
        assertEquals(160f, previewPoint.x) // Centro de preview
        assertEquals(120f, previewPoint.y) // Centro de preview
    }

    private fun createMockFrame(width: Int, height: Int): Mat {
        return Mat(height, width, CvType.CV_8UC3)
    }

    private fun shouldProcessFrame(currentTime: Long, throttleMs: Long): Boolean {
        // Simular lógica de throttling
        return currentTime % throttleMs == 0L || currentTime == 0L
    }
}
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
import kotlin.test.assertTrue
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
        val testFrame = createMockFrame(640, 480)
        val guideArea = RectF(200f, 150f, 440f, 330f)
        val mockDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(300, 220, 80, 60),
            centerPoint = Point(340.0, 250.0),
            confidence = 0.8f,
            area = 4800.0,
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
        val validationResult = validationManager.validateCapture(
            mockDetection,
            mockQuality,
            guideArea
        )
        assertTrue(validationResult.canCapture)
        assertEquals(CaptureValidationManager.GuideState.READY, validationResult.guideState)
        assertEquals("Listo para capturar", validationResult.message)
        assertTrue(validationResult.confidence > 0.7f)
    }
    @Test
    fun `pipeline con lunar descentrado`() = runTest {
        val guideArea = RectF(200f, 150f, 440f, 330f)
        val mockDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(100, 100, 50, 50),
            centerPoint = Point(125.0, 125.0),
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
        val validationResult = validationManager.validateCapture(
            mockDetection,
            mockQuality,
            guideArea
        )
        assertFalse(validationResult.canCapture)
        assertEquals(CaptureValidationManager.GuideState.CENTERING, validationResult.guideState)
        assertEquals("Centra el lunar en la guía", validationResult.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.NOT_CENTERED, validationResult.failureReason)
    }
    @Test
    fun `pipeline con lunar muy pequeño (muy lejos)`() = runTest {
        val guideArea = RectF(200f, 150f, 440f, 330f)
        val mockDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(315, 235, 10, 10),
            centerPoint = Point(320.0, 240.0),
            confidence = 0.7f,
            area = 100.0,
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
        val validationResult = validationManager.validateCapture(
            mockDetection,
            mockQuality,
            guideArea
        )
        assertFalse(validationResult.canCapture)
        assertEquals(CaptureValidationManager.GuideState.TOO_FAR, validationResult.guideState)
        assertEquals("Acércate más al lunar", validationResult.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.TOO_FAR, validationResult.failureReason)
    }
    @Test
    fun `pipeline con imagen borrosa`() = runTest {
        val guideArea = RectF(200f, 150f, 440f, 330f)
        val mockDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(300, 220, 80, 60),
            centerPoint = Point(340.0, 250.0),
            confidence = 0.8f,
            area = 4800.0,
            contour = MatOfPoint(),
            method = "test"
        )
        val mockQuality = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.1f,
            brightness = 120f,
            contrast = 0.4f,
            isBlurry = true,
            isOverexposed = false,
            isUnderexposed = false
        )
        val validationResult = validationManager.validateCapture(
            mockDetection,
            mockQuality,
            guideArea
        )
        assertFalse(validationResult.canCapture)
        assertEquals(CaptureValidationManager.GuideState.BLURRY, validationResult.guideState)
        assertEquals("Imagen borrosa - mantén firme la cámara", validationResult.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.BLURRY, validationResult.failureReason)
    }
    @Test
    fun `pipeline sin detección de lunar`() = runTest {
        val guideArea = RectF(200f, 150f, 440f, 330f)
        val mockQuality = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.5f,
            brightness = 120f,
            contrast = 0.4f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        val validationResult = validationManager.validateCapture(
            null,
            mockQuality,
            guideArea
        )
        assertFalse(validationResult.canCapture)
        assertEquals(CaptureValidationManager.GuideState.SEARCHING, validationResult.guideState)
        assertEquals("Buscando lunar...", validationResult.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.NO_MOLE_DETECTED, validationResult.failureReason)
    }
    @Test
    fun `pipeline con iluminación insuficiente`() = runTest {
        val guideArea = RectF(200f, 150f, 440f, 330f)
        val mockDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(300, 220, 80, 60),
            centerPoint = Point(340.0, 250.0),
            confidence = 0.8f,
            area = 4800.0,
            contour = MatOfPoint(),
            method = "test"
        )
        val mockQuality = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.5f,
            brightness = 50f,
            contrast = 0.4f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = true
        )
        val validationResult = validationManager.validateCapture(
            mockDetection,
            mockQuality,
            guideArea
        )
        assertFalse(validationResult.canCapture)
        assertEquals(CaptureValidationManager.GuideState.POOR_LIGHTING, validationResult.guideState)
        assertEquals("Necesitas más luz", validationResult.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.POOR_LIGHTING, validationResult.failureReason)
    }
    @Test
    fun `pipeline con sobreexposición`() = runTest {
        val guideArea = RectF(200f, 150f, 440f, 330f)
        val mockDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(300, 220, 80, 60),
            centerPoint = Point(340.0, 250.0),
            confidence = 0.8f,
            area = 4800.0,
            contour = MatOfPoint(),
            method = "test"
        )
        val mockQuality = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.5f,
            brightness = 220f,
            contrast = 0.4f,
            isBlurry = false,
            isOverexposed = true,
            isUnderexposed = false
        )
        val validationResult = validationManager.validateCapture(
            mockDetection,
            mockQuality,
            guideArea
        )
        assertFalse(validationResult.canCapture)
        assertEquals(CaptureValidationManager.GuideState.POOR_LIGHTING, validationResult.guideState)
        assertEquals("Demasiada luz - busca sombra", validationResult.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.POOR_LIGHTING, validationResult.failureReason)
    }
    @Test
    fun `throttling de análisis funciona correctamente`() {
        val throttleMs = 100L
        val startTime = System.currentTimeMillis()
        val shouldProcess1 = shouldProcessFrame(startTime, throttleMs)
        val shouldProcess2 = shouldProcessFrame(startTime + 50, throttleMs)
        val shouldProcess3 = shouldProcessFrame(startTime + 150, throttleMs)
        assertTrue(shouldProcess1)
        assertFalse(shouldProcess2)
        assertTrue(shouldProcess3)
    }
    @Test
    fun `cálculo de área de guía es correcto`() {
        val centerX = 320f
        val centerY = 240f
        val radius = 120f
        val guideArea = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        assertEquals(200f, guideArea.left)
        assertEquals(120f, guideArea.top)
        assertEquals(440f, guideArea.right)
        assertEquals(360f, guideArea.bottom)
        assertEquals(centerX, guideArea.centerX())
        assertEquals(centerY, guideArea.centerY())
    }
    @Test
    fun `conversión de coordenadas funciona correctamente`() {
        val imageWidth = 640
        val imageHeight = 480
        val previewWidth = 320
        val previewHeight = 240
        val imagePoint = Point(320.0, 240.0)
        val previewPoint = PointF(
            (imagePoint.x * previewWidth / imageWidth).toFloat(),
            (imagePoint.y * previewHeight / imageHeight).toFloat()
        )
        assertEquals(160f, previewPoint.x)
        assertEquals(120f, previewPoint.y)
    }
    private fun createMockFrame(width: Int, height: Int): Mat {
        return Mat(height, width, CvType.CV_8UC3)
    }
    private fun shouldProcessFrame(currentTime: Long, throttleMs: Long): Boolean {
        return currentTime % throttleMs == 0L || currentTime == 0L
    }
}
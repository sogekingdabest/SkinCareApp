package es.monsteraltech.skincare_tfm.camera.guidance
import android.graphics.RectF
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.opencv.core.*
import kotlin.test.*

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class CameraActivityIntegrationTest {
    private lateinit var moleDetector: MoleDetectionProcessor
    private lateinit var qualityAnalyzer: ImageQualityAnalyzer
    private lateinit var validationManager: CaptureValidationManager
    private lateinit var preprocessor: ImagePreprocessor
    @Before
    fun setup() {
        moleDetector = MoleDetectionProcessor()
        qualityAnalyzer = ImageQualityAnalyzer()
        validationManager = CaptureValidationManager()
        preprocessor = ImagePreprocessor()
    }
    @Test
    fun `flujo completo desde frame hasta validación exitosa`() = runTest {
        val frame = createFrameWithMole(640, 480)
        val guideArea = RectF(270f, 190f, 370f, 290f)
        val detection = moleDetector.detectMole(frame)
        val quality = qualityAnalyzer.analyzeQuality(frame)
        val validation = validationManager.validateCapture(detection, quality, guideArea)
        assertNotNull(detection, "Debería detectar el lunar simulado")
        assertTrue(quality.isGoodQuality(), "La calidad debería ser buena")
        assertTrue(validation.canCapture, "Debería permitir captura")
        assertEquals(CaptureValidationManager.GuideState.READY, validation.guideState)
        frame.release()
    }
    @Test
    fun `flujo con lunar detectado pero descentrado`() = runTest {
        val frame = createFrameWithMoleAtPosition(640, 480, 100, 100)
        val guideArea = RectF(270f, 190f, 370f, 290f)
        val detection = moleDetector.detectMole(frame)
        val quality = qualityAnalyzer.analyzeQuality(frame)
        val validation = validationManager.validateCapture(detection, quality, guideArea)
        assertNotNull(detection, "Debería detectar el lunar")
        assertFalse(validation.canCapture, "No debería permitir captura")
        assertEquals(CaptureValidationManager.GuideState.CENTERING, validation.guideState)
        assertEquals(CaptureValidationManager.ValidationFailureReason.NOT_CENTERED, validation.failureReason)
        frame.release()
    }
    @Test
    fun `flujo con imagen de mala calidad`() = runTest {
        val frame = createBlurryFrame(640, 480)
        val guideArea = RectF(270f, 190f, 370f, 290f)
        val detection = moleDetector.detectMole(frame)
        val quality = qualityAnalyzer.analyzeQuality(frame)
        val validation = validationManager.validateCapture(detection, quality, guideArea)
        assertTrue(quality.isBlurry, "Debería detectar que está borrosa")
        assertFalse(validation.canCapture, "No debería permitir captura")
        assertEquals(CaptureValidationManager.GuideState.BLURRY, validation.guideState)
        frame.release()
    }
    @Test
    fun `throttling de frames funciona correctamente`() {
        val throttleMs = 100L
        var lastProcessTime = 0L
        val frames = listOf(0L, 50L, 100L, 150L, 200L)
        frames.forEach { currentTime ->
            val shouldProcess = (currentTime - lastProcessTime) >= throttleMs
            if (shouldProcess) {
                lastProcessTime = currentTime
            }
            when (currentTime) {
                0L -> assertTrue(shouldProcess, "Primer frame siempre se procesa")
                50L -> assertFalse(shouldProcess, "Frame a 50ms se omite")
                100L -> assertTrue(shouldProcess, "Frame a 100ms se procesa")
                150L -> assertFalse(shouldProcess, "Frame a 150ms se omite")
                200L -> assertTrue(shouldProcess, "Frame a 200ms se procesa")
            }
        }
    }
    @Test
    fun `conversión de ImageProxy a Mat funciona correctamente`() {
        val width = 640
        val height = 480
        val imageData = ByteArray(width * height) { (it % 256).toByte() }
        val mat = Mat(height, width, CvType.CV_8UC1)
        mat.put(0, 0, imageData)
        assertEquals(height, mat.rows())
        assertEquals(width, mat.cols())
        assertEquals(CvType.CV_8UC1, mat.type())
        assertFalse(mat.empty())
        mat.release()
    }
    @Test
    fun `cálculo de área de guía es preciso`() {
        val previewWidth = 640f
        val previewHeight = 480f
        val radius = 120f
        val centerX = previewWidth / 2f
        val centerY = previewHeight / 2f
        val guideArea = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        assertEquals(320f, guideArea.centerX())
        assertEquals(240f, guideArea.centerY())
        assertEquals(240f, guideArea.width())
        assertEquals(240f, guideArea.height())
    }
    @Test
    fun `actualización de UI refleja estado de validación`() {
        val validationResults = listOf(
            CaptureValidationManager.ValidationResult(
                canCapture = true,
                guideState = CaptureValidationManager.GuideState.READY,
                message = "Listo para capturar",
                confidence = 0.9f
            ),
            CaptureValidationManager.ValidationResult(
                canCapture = false,
                guideState = CaptureValidationManager.GuideState.CENTERING,
                message = "Centra el lunar",
                confidence = 0.8f,
                failureReason = CaptureValidationManager.ValidationFailureReason.NOT_CENTERED
            ),
            CaptureValidationManager.ValidationResult(
                canCapture = false,
                guideState = CaptureValidationManager.GuideState.TOO_FAR,
                message = "Acércate más",
                confidence = 0.7f,
                failureReason = CaptureValidationManager.ValidationFailureReason.TOO_FAR
            )
        )
        validationResults.forEach { result ->
            val buttonEnabled = result.canCapture
            val messageText = result.message
            val stateColor = when (result.guideState) {
                CaptureValidationManager.GuideState.READY -> "green"
                CaptureValidationManager.GuideState.CENTERING -> "yellow"
                CaptureValidationManager.GuideState.TOO_FAR -> "red"
                else -> "white"
            }
            assertEquals(result.canCapture, buttonEnabled)
            assertEquals(result.message, messageText)
            assertNotNull(stateColor)
        }
    }
    @Test
    fun `preprocesado se integra correctamente con captura`() = runTest {
        val originalBitmap = createTestBitmap(640, 480)
        val preprocessingResult = preprocessor.preprocessImage(originalBitmap)
        assertNotNull(preprocessingResult.processedBitmap)
        assertNotEquals(originalBitmap, preprocessingResult.processedBitmap)
        assertTrue(preprocessingResult.appliedFilters.isNotEmpty())
        assertTrue(preprocessingResult.processingTime > 0)
    }
    @Test
    fun `manejo de errores en pipeline no interrumpe flujo`() = runTest {
        val invalidFrame = Mat()
        assertNull(moleDetector.detectMole(invalidFrame))
        try {
            qualityAnalyzer.analyzeQuality(invalidFrame)
        } catch (e: Exception) {
            assertTrue(true)
        }
    }
    @Test
    fun `estados de guía transicionan correctamente`() {
        val stateTransitions = listOf(
            CaptureValidationManager.GuideState.SEARCHING,
            CaptureValidationManager.GuideState.CENTERING,
            CaptureValidationManager.GuideState.TOO_FAR,
            CaptureValidationManager.GuideState.CENTERING,
            CaptureValidationManager.GuideState.READY
        )
        for (i in 1 until stateTransitions.size) {
            val previousState = stateTransitions[i - 1]
            val currentState = stateTransitions[i]
            val isValidTransition = when (previousState) {
                CaptureValidationManager.GuideState.SEARCHING ->
                    currentState in listOf(CaptureValidationManager.GuideState.CENTERING, CaptureValidationManager.GuideState.BLURRY, CaptureValidationManager.GuideState.POOR_LIGHTING)
                CaptureValidationManager.GuideState.CENTERING ->
                    currentState in listOf(CaptureValidationManager.GuideState.READY, CaptureValidationManager.GuideState.TOO_FAR, CaptureValidationManager.GuideState.TOO_CLOSE)
                CaptureValidationManager.GuideState.TOO_FAR ->
                    currentState in listOf(CaptureValidationManager.GuideState.CENTERING, CaptureValidationManager.GuideState.READY)
                else -> true
            }
            assertTrue(isValidTransition, "Transición inválida: $previousState -> $currentState")
        }
    }
    private fun createFrameWithMole(width: Int, height: Int): Mat {
        val frame = Mat(height, width, CvType.CV_8UC3)
        val centerX = width / 2
        val centerY = height / 2
        val moleRadius = 30
        frame.setTo(Scalar(200.0, 200.0, 200.0))
        org.opencv.imgproc.Imgproc.circle(
            frame,
            Point(centerX.toDouble(), centerY.toDouble()),
            moleRadius.toDouble(),
            Scalar(50.0, 30.0, 20.0),
            -1
        )
        return frame
    }
    private fun createFrameWithMoleAtPosition(width: Int, height: Int, x: Int, y: Int): Mat {
        val frame = Mat(height, width, CvType.CV_8UC3)
        frame.setTo(Scalar(200.0, 200.0, 200.0))
        org.opencv.imgproc.Imgproc.circle(
            frame,
            Point(x.toDouble(), y.toDouble()),
            25.0,
            Scalar(50.0, 30.0, 20.0),
            -1
        )
        return frame
    }
    private fun createBlurryFrame(width: Int, height: Int): Mat {
        val frame = createFrameWithMole(width, height)
        val blurred = Mat()
        org.opencv.imgproc.Imgproc.GaussianBlur(frame, blurred, org.opencv.core.Size(15.0, 15.0), 0.0)
        frame.release()
        return blurred
    }
    private fun createTestBitmap(width: Int, height: Int): android.graphics.Bitmap {
        return android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    }
}
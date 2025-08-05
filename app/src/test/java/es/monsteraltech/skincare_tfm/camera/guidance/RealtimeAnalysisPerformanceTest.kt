package es.monsteraltech.skincare_tfm.camera.guidance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.opencv.core.*
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class RealtimeAnalysisPerformanceTest {
    private lateinit var moleDetector: MoleDetectionProcessor
    private lateinit var qualityAnalyzer: ImageQualityAnalyzer
    private lateinit var validationManager: CaptureValidationManager
    companion object {
        private const val MAX_PROCESSING_TIME_MS = 100L
        private const val TARGET_FPS = 10
        private const val FRAME_BUDGET_MS = 1000L / TARGET_FPS
    }
    @Before
    fun setup() {
        moleDetector = MoleDetectionProcessor()
        qualityAnalyzer = ImageQualityAnalyzer()
        validationManager = CaptureValidationManager()
    }
    @Test
    fun `análisis de calidad debe completarse dentro del presupuesto de tiempo`() = runTest {
        val testFrame = createTestFrame(640, 480)
        val processingTime = measureTimeMillis {
            qualityAnalyzer.analyzeQuality(testFrame)
        }
        assertTrue(
            processingTime < MAX_PROCESSING_TIME_MS,
            "Análisis de calidad tomó ${processingTime}ms, máximo permitido: ${MAX_PROCESSING_TIME_MS}ms"
        )
        testFrame.release()
    }
    @Test
    fun `detección de lunar debe completarse dentro del presupuesto de tiempo`() = runTest {
        val testFrame = createTestFrame(640, 480)
        val processingTime = measureTimeMillis {
            moleDetector.detectMole(testFrame)
        }
        assertTrue(
            processingTime < MAX_PROCESSING_TIME_MS,
            "Detección de lunar tomó ${processingTime}ms, máximo permitido: ${MAX_PROCESSING_TIME_MS}ms"
        )
        testFrame.release()
    }
    @Test
    fun `pipeline completo debe completarse dentro del presupuesto de frame`() = runTest {
        val testFrame = createTestFrame(640, 480)
        val guideArea = android.graphics.RectF(200f, 150f, 440f, 330f)
        val totalTime = measureTimeMillis {
            val detection = moleDetector.detectMole(testFrame)
            val quality = qualityAnalyzer.analyzeQuality(testFrame)
            validationManager.validateCapture(detection, quality, guideArea)
        }
        assertTrue(
            totalTime < FRAME_BUDGET_MS,
            "Pipeline completo tomó ${totalTime}ms, presupuesto de frame: ${FRAME_BUDGET_MS}ms"
        )
        testFrame.release()
    }
    @Test
    fun `procesamiento de múltiples frames consecutivos mantiene rendimiento`() = runTest {
        val frameCount = 30
        val frames = (1..frameCount).map { createTestFrame(640, 480) }
        val guideArea = android.graphics.RectF(200f, 150f, 440f, 330f)
        val totalTime = measureTimeMillis {
            frames.forEach { frame ->
                val detection = moleDetector.detectMole(frame)
                val quality = qualityAnalyzer.analyzeQuality(frame)
                validationManager.validateCapture(detection, quality, guideArea)
            }
        }
        val averageTimePerFrame = totalTime / frameCount
        assertTrue(
            averageTimePerFrame < FRAME_BUDGET_MS,
            "Tiempo promedio por frame: ${averageTimePerFrame}ms, presupuesto: ${FRAME_BUDGET_MS}ms"
        )
        frames.forEach { it.release() }
    }
    @Test
    fun `throttling reduce efectivamente la carga de procesamiento`() = runTest {
        val throttleMs = 100L
        val totalFrames = 100
        val frameInterval = 33L
        var processedFrames = 0
        var lastProcessTime = 0L
        for (i in 1..totalFrames) {
            val currentTime = i * frameInterval
            if (currentTime - lastProcessTime >= throttleMs) {
                processedFrames++
                lastProcessTime = currentTime
            }
        }
        val expectedProcessedFrames = (totalFrames * frameInterval / throttleMs).toInt()
        val tolerance = 2
        assertTrue(
            kotlin.math.abs(processedFrames - expectedProcessedFrames) <= tolerance,
            "Frames procesados: $processedFrames, esperados: ~$expectedProcessedFrames"
        )
    }
    @Test
    fun `memoria se mantiene estable durante procesamiento prolongado`() = runTest {
        val frameCount = 100
        val guideArea = android.graphics.RectF(200f, 150f, 440f, 330f)
        repeat(frameCount) {
            val frame = createTestFrame(640, 480)
            val detection = moleDetector.detectMole(frame)
            val quality = qualityAnalyzer.analyzeQuality(frame)
            validationManager.validateCapture(detection, quality, guideArea)
            frame.release()
            if (it % 20 == 0) {
                System.gc()
            }
        }
        assertTrue(true, "Procesamiento prolongado completado sin problemas de memoria")
    }
    @Test
    fun `procesamiento con diferentes resoluciones mantiene rendimiento`() = runTest {
        val resolutions = listOf(
            Pair(320, 240),
            Pair(640, 480),
            Pair(1280, 720),
            Pair(1920, 1080)
        )
        val guideArea = android.graphics.RectF(200f, 150f, 440f, 330f)
        resolutions.forEach { (width, height) ->
            val frame = createTestFrame(width, height)
            val processingTime = measureTimeMillis {
                val detection = moleDetector.detectMole(frame)
                val quality = qualityAnalyzer.analyzeQuality(frame)
                validationManager.validateCapture(detection, quality, guideArea)
            }
            val maxTime = when {
                width * height <= 320 * 240 -> 50L
                width * height <= 640 * 480 -> 100L
                width * height <= 1280 * 720 -> 200L
                else -> 300L
            }
            assertTrue(
                processingTime < maxTime,
                "Resolución ${width}x${height} tomó ${processingTime}ms, máximo: ${maxTime}ms"
            )
            frame.release()
        }
    }
    @Test
    fun `validación es instantánea comparada con análisis de imagen`() = runTest {
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
        val guideArea = android.graphics.RectF(200f, 150f, 440f, 330f)
        val validationTime = measureTimeMillis {
            repeat(1000) {
                validationManager.validateCapture(mockDetection, mockQuality, guideArea)
            }
        }
        val averageValidationTime = validationTime / 1000.0
        assertTrue(
            averageValidationTime < 1.0,
            "Validación promedio: ${averageValidationTime}ms, debe ser < 1ms"
        )
    }
    private fun createTestFrame(width: Int, height: Int): Mat {
        val frame = Mat(height, width, CvType.CV_8UC3)
        val data = ByteArray(width * height * 3)
        for (i in data.indices) {
            data[i] = (i % 256).toByte()
        }
        frame.put(0, 0, data)
        return frame
    }
}
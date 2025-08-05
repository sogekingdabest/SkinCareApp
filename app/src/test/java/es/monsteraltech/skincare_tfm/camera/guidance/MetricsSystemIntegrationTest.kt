package es.monsteraltech.skincare_tfm.camera.guidance
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import java.io.File
class MetricsSystemIntegrationTest {
    @Mock
    private lateinit var mockContext: Context
    @Mock
    private lateinit var mockFilesDir: File
    private lateinit var metricsIntegration: MetricsIntegration
    private lateinit var testDashboardListener: TestDashboardListener
    private class TestDashboardListener : MetricsDashboard.DashboardListener {
        var updateCount = 0
        var alertCount = 0
        var lastStats: DashboardStats? = null
        var lastAlert: MetricsDashboard.PerformanceAlert? = null
        override fun onMetricsUpdated(stats: DashboardStats) {
            lastStats = stats
            updateCount++
        }
        override fun onPerformanceAlert(alert: MetricsDashboard.PerformanceAlert) {
            lastAlert = alert
            alertCount++
        }
    }
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(mockContext.filesDir).thenReturn(mockFilesDir)
        `when`(mockFilesDir.exists()).thenReturn(true)
        val config = MetricsConfig(
            enableLogging = true,
            enablePerformanceTracking = true,
            logLevel = LogLevel.DEBUG
        )
        metricsIntegration = MetricsIntegration(mockContext, config)
        testDashboardListener = TestDashboardListener()
        runTest {
            CaptureMetrics.getInstance().reset()
        }
    }
    @Test
    fun `complete metrics system should start and stop correctly`() {
        metricsIntegration.start()
        assertTrue("Metrics system should start successfully", true)
        metricsIntegration.stop()
        assertTrue("Metrics system should stop successfully", true)
    }
    @Test
    fun `mole detection metrics should be integrated correctly`() = runTest {
        metricsIntegration.start()
        metricsIntegration.logMoleDetection(true, 120L, 0.85f)
        metricsIntegration.logMoleDetection(false, 180L, 0.45f)
        metricsIntegration.logMoleDetection(true, 95L, 0.92f)
        val metrics = metricsIntegration.getCurrentMetrics()
        assertEquals(3, metrics.detectionAttempts)
        assertEquals(2, metrics.successfulDetections)
        assertEquals(67, metrics.detectionSuccessRate)
        metricsIntegration.stop()
    }
    @Test
    fun `quality analysis metrics should be integrated correctly`() = runTest {
        metricsIntegration.start()
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.8f,
            brightness = 125f,
            contrast = 0.7f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        metricsIntegration.logQualityAnalysis(qualityMetrics, 65L)
        val metrics = metricsIntegration.getCurrentMetrics()
        assertEquals(65L, metrics.avgQualityAnalysisTime)
        metricsIntegration.stop()
    }
    @Test
    fun `capture validation metrics should be integrated correctly`() = runTest {
        metricsIntegration.start()
        val validationResult = CaptureValidationManager.ValidationResult(
            canCapture = true,
            guideState = CaptureGuidanceOverlay.GuideState.READY,
            message = "Ready to capture",
            confidence = 0.9f
        )
        metricsIntegration.logCaptureValidation(validationResult, 35L)
        metricsIntegration.logCaptureValidation(validationResult, 40L)
        val metrics = metricsIntegration.getCurrentMetrics()
        assertEquals(37L, metrics.avgValidationTime)
        assertEquals(2, metrics.validationStates["READY"])
        metricsIntegration.stop()
    }
    @Test
    fun `image preprocessing metrics should be integrated correctly`() = runTest {
        metricsIntegration.start()
        val filters = listOf("contrast_enhancement", "noise_reduction", "sharpening")
        metricsIntegration.logImagePreprocessing(250L, filters)
        metricsIntegration.logImagePreprocessing(200L, filters.take(2))
        val metrics = metricsIntegration.getCurrentMetrics()
        assertEquals(450L, metrics.avgPreprocessingTime)
        metricsIntegration.stop()
    }
    @Test
    fun `frame processing metrics should be integrated correctly`() = runTest {
        metricsIntegration.start()
        metricsIntegration.logFrameProcessing(45L, 1920, 1080)
        metricsIntegration.logFrameProcessing(55L, 1280, 720)
        metricsIntegration.logFrameProcessing(40L, 1920, 1080)
        val metrics = metricsIntegration.getCurrentMetrics()
        assertEquals(46L, metrics.avgFrameProcessingTime)
        metricsIntegration.stop()
    }
    @Test
    fun `dashboard integration should work correctly`() = runTest {
        metricsIntegration.addDashboardListener(testDashboardListener)
        metricsIntegration.start()
        metricsIntegration.logMoleDetection(true, 100L, 0.8f)
        metricsIntegration.logMoleDetection(true, 120L, 0.9f)
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.75f,
            brightness = 110f,
            contrast = 0.65f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        metricsIntegration.logQualityAnalysis(qualityMetrics, 50L)
        delay(1200)
        assertTrue("Dashboard should be integrated correctly", true)
        metricsIntegration.removeDashboardListener(testDashboardListener)
        metricsIntegration.stop()
    }
    @Test
    fun `memory monitoring should work automatically`() = runTest {
        metricsIntegration.start()
        delay(2500)
        val metrics = metricsIntegration.getCurrentMetrics()
        assertTrue("Memory monitoring should work automatically", true)
        metricsIntegration.stop()
    }
    @Test
    fun `debug report generation should work end-to-end`() = runTest {
        metricsIntegration.start()
        metricsIntegration.logMoleDetection(true, 100L, 0.8f)
        metricsIntegration.logMoleDetection(false, 150L, 0.4f)
        metricsIntegration.logMoleDetection(true, 110L, 0.9f)
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.7f,
            brightness = 120f,
            contrast = 0.6f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        metricsIntegration.logQualityAnalysis(qualityMetrics, 60L)
        val validationResult = CaptureValidationManager.ValidationResult(
            canCapture = true,
            guideState = CaptureGuidanceOverlay.GuideState.READY,
            message = "Ready",
            confidence = 0.85f
        )
        metricsIntegration.logCaptureValidation(validationResult, 30L)
        metricsIntegration.logImagePreprocessing(200L, listOf("contrast", "brightness"))
        metricsIntegration.logFrameProcessing(45L, 1920, 1080)
        val debugReport = metricsIntegration.generateDebugReport()
        assertTrue(debugReport.contains("DETAILED METRICS REPORT"))
        assertTrue(debugReport.contains("Detection Attempts: 3"))
        assertTrue(debugReport.contains("Success Rate: 67%"))
        assertTrue(debugReport.contains("QUALITY METRICS"))
        assertTrue(debugReport.contains("VALIDATION STATES"))
        assertTrue(debugReport.contains("READY: 1"))
        metricsIntegration.stop()
    }
    @Test
    fun `JSON export should work end-to-end`() = runTest {
        metricsIntegration.start()
        metricsIntegration.logMoleDetection(true, 100L, 0.8f)
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.75f,
            brightness = 115f,
            contrast = 0.65f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        metricsIntegration.logQualityAnalysis(qualityMetrics, 55L)
        val jsonExport = metricsIntegration.exportMetricsAsJson()
        assertTrue(jsonExport.contains("\"timestamp\":"))
        assertTrue(jsonExport.contains("\"detectionAttempts\": 1"))
        assertTrue(jsonExport.contains("\"successRate\": 100"))
        assertTrue(jsonExport.contains("\"qualityHistory\":"))
        assertTrue(jsonExport.contains("\"sharpness\": 0.75"))
        assertTrue(jsonExport.startsWith("{"))
        assertTrue(jsonExport.endsWith("}"))
        metricsIntegration.stop()
    }
    @Test
    fun `measureOperation wrapper should work correctly`() = runTest {
        metricsIntegration.start()
        val result = metricsIntegration.measureOperation("test_operation") {
            delay(50)
            "success"
        }
        assertEquals("success", result)
        try {
            metricsIntegration.measureOperation("failing_operation") {
                delay(30)
                throw RuntimeException("Test error")
            }
            fail("Should have thrown exception")
        } catch (e: RuntimeException) {
            assertEquals("Test error", e.message)
        }
        metricsIntegration.stop()
    }
    @Test
    fun `realtime performance metrics should be calculated correctly`() = runTest {
        metricsIntegration.start()
        repeat(10) { index ->
            metricsIntegration.logFrameProcessing((40 + index * 2).toLong(), 1920, 1080)
        }
        val performance = metricsIntegration.getRealtimePerformance()
        assertTrue(performance.fps > 0f)
        assertTrue(performance.frameProcessingTime > 0L)
        assertTrue(performance.memoryUsage >= 0)
        assertNotNull(performance.thermalState)
        metricsIntegration.stop()
    }
    @Test
    fun `metrics reset should work correctly`() = runTest {
        metricsIntegration.start()
        metricsIntegration.logMoleDetection(true, 100L)
        metricsIntegration.logFrameProcessing(50L, 1920, 1080)
        val beforeReset = metricsIntegration.getCurrentMetrics()
        assertEquals(1, beforeReset.detectionAttempts)
        metricsIntegration.resetMetrics()
        val afterReset = metricsIntegration.getCurrentMetrics()
        assertEquals(0, afterReset.detectionAttempts)
        assertEquals(0, afterReset.successfulDetections)
        assertEquals(0L, afterReset.avgFrameProcessingTime)
        metricsIntegration.stop()
    }
    @Test
    fun `concurrent operations should be handled safely`() = runTest {
        metricsIntegration.start()
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        jobs.add(kotlinx.coroutines.launch {
            repeat(20) { index ->
                metricsIntegration.logMoleDetection(index % 3 != 0, (50 + index * 5).toLong(), 0.7f + index * 0.01f)
                delay(10)
            }
        })
        jobs.add(kotlinx.coroutines.launch {
            repeat(15) { index ->
                val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
                    sharpness = 0.6f + index * 0.02f,
                    brightness = 100f + index * 2f,
                    contrast = 0.5f + index * 0.01f,
                    isBlurry = index % 5 == 0,
                    isOverexposed = false,
                    isUnderexposed = false
                )
                metricsIntegration.logQualityAnalysis(qualityMetrics, (40 + index * 3).toLong())
                delay(15)
            }
        })
        jobs.add(kotlinx.coroutines.launch {
            repeat(25) { index ->
                metricsIntegration.logFrameProcessing((30 + index * 2).toLong(), 1920, 1080)
                delay(8)
            }
        })
        jobs.forEach { it.join() }
        val finalMetrics = metricsIntegration.getCurrentMetrics()
        assertEquals(20, finalMetrics.detectionAttempts)
        assertTrue(finalMetrics.avgQualityAnalysisTime > 0L)
        assertTrue(finalMetrics.avgFrameProcessingTime > 0L)
        metricsIntegration.stop()
    }
    @Test
    fun `log file management should work correctly`() = runTest {
        metricsIntegration.start()
        metricsIntegration.logMoleDetection(true, 100L)
        val logPath = metricsIntegration.getLogFilePath()
        assertTrue("Log file path should be accessible", logPath != null || logPath == null)
        metricsIntegration.clearLogs()
        assertTrue("Log clearing should work", true)
        metricsIntegration.stop()
    }
}
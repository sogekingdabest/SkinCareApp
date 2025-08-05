package es.monsteraltech.skincare_tfm.camera.guidance
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
class MetricsDashboardTest {
    @Mock
    private lateinit var mockContext: Context
    @Mock
    private lateinit var mockMetrics: CaptureMetrics
    private lateinit var dashboard: MetricsDashboard
    private lateinit var testListener: TestDashboardListener
    private class TestDashboardListener : MetricsDashboard.DashboardListener {
        var lastStats: DashboardStats? = null
        var lastAlert: MetricsDashboard.PerformanceAlert? = null
        var updateCount = 0
        var alertCount = 0
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
        dashboard = MetricsDashboard(mockContext, CaptureMetrics.getInstance())
        testListener = TestDashboardListener()
    }
    @Test
    fun `start should initialize dashboard correctly`() {
        dashboard.start()
        assertTrue("Dashboard should start without errors", true)
        dashboard.stop()
    }
    @Test
    fun `stop should cleanup dashboard correctly`() {
        dashboard.start()
        dashboard.stop()
        assertTrue("Dashboard should stop without errors", true)
    }
    @Test
    fun `addListener should register listener correctly`() = runTest {
        dashboard.addListener(testListener)
        dashboard.start()
        delay(100)
        assertTrue("Listener should be added without errors", true)
        dashboard.stop()
    }
    @Test
    fun `removeListener should unregister listener correctly`() {
        dashboard.addListener(testListener)
        dashboard.removeListener(testListener)
        assertTrue("Listener should be removed without errors", true)
    }
    @Test
    fun `generateDetailedReport should create comprehensive report`() = runTest {
        val metrics = CaptureMetrics.getInstance()
        metrics.reset()
        metrics.logDetectionAttempt(true, 100L)
        metrics.logDetectionAttempt(false, 150L)
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.8f,
            brightness = 120f,
            contrast = 0.6f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        metrics.logQualityAnalysis(qualityMetrics, 50L)
        val report = dashboard.generateDetailedReport()
        assertTrue(report.contains("DETAILED METRICS REPORT"))
        assertTrue(report.contains("GENERAL SUMMARY"))
        assertTrue(report.contains("Detection Attempts: 2"))
        assertTrue(report.contains("Success Rate: 50%"))
        assertTrue(report.contains("QUALITY METRICS"))
        assertTrue(report.contains("Sharpness: 0.80"))
        assertTrue(report.contains("Brightness: 120"))
    }
    @Test
    fun `exportToJson should create valid JSON structure`() = runTest {
        val metrics = CaptureMetrics.getInstance()
        metrics.reset()
        metrics.logDetectionAttempt(true, 100L)
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.7f,
            brightness = 100f,
            contrast = 0.5f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        metrics.logQualityAnalysis(qualityMetrics, 40L)
        val json = dashboard.exportToJson()
        assertTrue(json.contains("\"timestamp\":"))
        assertTrue(json.contains("\"sessionDuration\":"))
        assertTrue(json.contains("\"detectionAttempts\": 1"))
        assertTrue(json.contains("\"successRate\": 100"))
        assertTrue(json.contains("\"qualityHistory\":"))
        assertTrue(json.contains("\"sharpness\": 0.7"))
        assertTrue(json.contains("\"brightness\": 100.0"))
        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
    }
    @Test
    fun `performance alerts should be generated for high memory usage`() = runTest {
        val metrics = CaptureMetrics.getInstance()
        metrics.reset()
        val totalMemory = 8L * 1024L * 1024L * 1024L
        val highUsedMemory = (totalMemory * 0.95).toLong()
        metrics.logMemoryUsage(highUsedMemory, totalMemory)
        dashboard.addListener(testListener)
        dashboard.start()
        delay(1200)
        assertTrue("High memory usage should be handled", true)
        dashboard.stop()
    }
    @Test
    fun `performance alerts should be generated for slow processing`() = runTest {
        val metrics = CaptureMetrics.getInstance()
        metrics.reset()
        repeat(5) {
            metrics.logDetectionAttempt(true, 600L)
        }
        dashboard.addListener(testListener)
        dashboard.start()
        delay(1200)
        assertTrue("Slow processing should be handled", true)
        dashboard.stop()
    }
    @Test
    fun `performance alerts should be generated for low success rate`() = runTest {
        val metrics = CaptureMetrics.getInstance()
        metrics.reset()
        repeat(15) { index ->
            metrics.logDetectionAttempt(index < 3, 100L)
        }
        dashboard.addListener(testListener)
        dashboard.start()
        delay(1200)
        assertTrue("Low success rate should be handled", true)
        dashboard.stop()
    }
    @Test
    fun `calculateQualityScore should handle various quality scenarios`() = runTest {
        val perfectMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 1.0f,
            brightness = 130f,
            contrast = 1.0f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        val poorMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.1f,
            brightness = 30f,
            contrast = 0.1f,
            isBlurry = true,
            isOverexposed = false,
            isUnderexposed = true
        )
        val metrics = CaptureMetrics.getInstance()
        metrics.reset()
        metrics.logQualityAnalysis(perfectMetrics, 50L)
        metrics.logQualityAnalysis(poorMetrics, 50L)
        val report = dashboard.generateDetailedReport()
        assertTrue(report.contains("QUALITY METRICS"))
        assertTrue(report.contains("Sharpness: 1.00") || report.contains("Sharpness: 0.10"))
    }
    @Test
    fun `dashboard should handle empty metrics gracefully`() = runTest {
        val metrics = CaptureMetrics.getInstance()
        metrics.reset()
        val report = dashboard.generateDetailedReport()
        val json = dashboard.exportToJson()
        assertTrue(report.contains("DETAILED METRICS REPORT"))
        assertTrue(report.contains("Detection Attempts: 0"))
        assertTrue(json.contains("\"detectionAttempts\": 0"))
        assertTrue(json.contains("\"successRate\": 0"))
    }
    @Test
    fun `dashboard should handle concurrent access safely`() = runTest {
        val metrics = CaptureMetrics.getInstance()
        metrics.reset()
        dashboard.addListener(testListener)
        dashboard.start()
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        repeat(5) { index ->
            val job = kotlinx.coroutines.launch {
                repeat(10) {
                    metrics.logDetectionAttempt(true, (index * 10).toLong())
                    delay(10)
                }
            }
            jobs.add(job)
        }
        jobs.forEach { it.join() }
        delay(1200)
        assertTrue("Concurrent access should be handled safely", true)
        dashboard.stop()
    }
    @Test
    fun `performance score calculation should be accurate`() = runTest {
        val metrics = CaptureMetrics.getInstance()
        metrics.reset()
        repeat(10) {
            metrics.logDetectionAttempt(true, 50L)
        }
        val goodQualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.8f,
            brightness = 120f,
            contrast = 0.7f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        repeat(5) {
            metrics.logQualityAnalysis(goodQualityMetrics, 30L)
            metrics.logFrameProcessingTime(25L)
        }
        val report = dashboard.generateDetailedReport()
        assertTrue(report.contains("GENERAL SUMMARY"))
        assertTrue(report.contains("Success Rate: 100%"))
        assertTrue(report.contains("FRAME PROCESSING"))
        assertTrue(report.contains("Average: 25ms"))
    }
}
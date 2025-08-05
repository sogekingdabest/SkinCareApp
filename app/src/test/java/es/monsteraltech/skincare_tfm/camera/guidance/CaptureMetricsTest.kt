package es.monsteraltech.skincare_tfm.camera.guidance
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CaptureMetricsTest {
    private lateinit var metrics: CaptureMetrics
    @Before
    fun setup() {
        metrics = CaptureMetrics.getInstance()
        runTest {
            metrics.reset()
        }
    }
    @Test
    fun `logDetectionAttempt should increment counters correctly`() {
        metrics.logDetectionAttempt(true, 100L)
        metrics.logDetectionAttempt(false, 150L)
        metrics.logDetectionAttempt(true, 120L)
        runTest {
            val summary = metrics.getMetricsSummary()
            assertEquals(3, summary.detectionAttempts)
            assertEquals(2, summary.successfulDetections)
            assertEquals(67, summary.detectionSuccessRate)
            assertEquals(123L, summary.avgDetectionTime)
        }
    }
    @Test
    fun `logQualityAnalysis should store metrics correctly`() = runTest {
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.8f,
            brightness = 120f,
            contrast = 0.6f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        metrics.logQualityAnalysis(qualityMetrics, 50L)
        val history = metrics.getQualityMetricsHistory()
        assertEquals(1, history.size)
        val snapshot = history.first()
        assertEquals(0.8f, snapshot.sharpness, 0.01f)
        assertEquals(120f, snapshot.brightness, 0.01f)
        assertEquals(0.6f, snapshot.contrast, 0.01f)
        assertEquals(50L, snapshot.processingTime)
        assertFalse(snapshot.isBlurry)
    }
    @Test
    fun `logCaptureValidation should track validation states`() {
        val validationResult = CaptureValidationManager.ValidationResult(
            canCapture = true,
            guideState = CaptureValidationManager.GuideState.READY,
            message = "Ready to capture",
            confidence = 0.9f
        )
        metrics.logCaptureValidation(validationResult, 25L)
        metrics.logCaptureValidation(validationResult, 30L)
        runTest {
            val summary = metrics.getMetricsSummary()
            assertEquals(2, summary.validationStates["READY"])
            assertEquals(27L, summary.avgValidationTime)
        }
    }
    @Test
    fun `logPreprocessingPerformance should accumulate time`() {
        val filters = listOf("contrast", "brightness", "noise_reduction")
        metrics.logPreprocessingPerformance(200L, filters)
        metrics.logPreprocessingPerformance(180L, filters)
        runTest {
            val summary = metrics.getMetricsSummary()
            assertEquals(380L, summary.avgPreprocessingTime)
        }
    }
    @Test
    fun `logFrameProcessingTime should maintain history limit`() = runTest {
        repeat(60) { index ->
            metrics.logFrameProcessingTime(index.toLong())
        }
        val history = metrics.getFrameProcessingHistory()
        assertEquals(50, history.size)
        assertEquals(10L, history.first())
        assertEquals(59L, history.last())
    }
    @Test
    fun `logMemoryUsage should maintain history limit`() = runTest {
        repeat(25) { index ->
            val usedMemory = (index + 1) * 1024L * 1024L
            val totalMemory = 8L * 1024L * 1024L * 1024L
            metrics.logMemoryUsage(usedMemory, totalMemory)
        }
        val history = metrics.getMemoryUsageHistory()
        assertEquals(20, history.size)
        val firstSnapshot = history.first()
        assertEquals(6L * 1024L * 1024L, firstSnapshot.usedMemory)
        val lastSnapshot = history.last()
        assertEquals(25L * 1024L * 1024L, lastSnapshot.usedMemory)
    }
    @Test
    fun `getMetricsSummary should calculate averages correctly with zero attempts`() = runTest {
        val summary = metrics.getMetricsSummary()
        assertEquals(0, summary.detectionAttempts)
        assertEquals(0, summary.successfulDetections)
        assertEquals(0, summary.detectionSuccessRate)
        assertEquals(0L, summary.avgDetectionTime)
        assertEquals(0L, summary.avgQualityAnalysisTime)
        assertEquals(0L, summary.avgValidationTime)
        assertNull(summary.currentMemoryUsage)
    }
    @Test
    fun `exportMetrics should generate readable report`() = runTest {
        metrics.logDetectionAttempt(true, 100L)
        metrics.logDetectionAttempt(false, 200L)
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.7f,
            brightness = 100f,
            contrast = 0.5f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        metrics.logQualityAnalysis(qualityMetrics, 50L)
        val report = metrics.exportMetrics()
        assertTrue(report.contains("CAPTURE GUIDANCE METRICS"))
        assertTrue(report.contains("Detection Attempts: 2"))
        assertTrue(report.contains("Successful Detections: 1"))
        assertTrue(report.contains("Success Rate: 50%"))
        assertTrue(report.contains("Avg Detection Time: 150ms"))
    }
    @Test
    fun `reset should clear all metrics`() = runTest {
        metrics.logDetectionAttempt(true, 100L)
        metrics.logFrameProcessingTime(50L)
        metrics.logMemoryUsage(1024L, 8192L)
        metrics.reset()
        val summary = metrics.getMetricsSummary()
        assertEquals(0, summary.detectionAttempts)
        assertEquals(0, summary.successfulDetections)
        val frameHistory = metrics.getFrameProcessingHistory()
        assertTrue(frameHistory.isEmpty())
        val memoryHistory = metrics.getMemoryUsageHistory()
        assertTrue(memoryHistory.isEmpty())
        val qualityHistory = metrics.getQualityMetricsHistory()
        assertTrue(qualityHistory.isEmpty())
    }
    @Test
    fun `singleton instance should be consistent`() {
        val instance1 = CaptureMetrics.getInstance()
        val instance2 = CaptureMetrics.getInstance()
        assertSame(instance1, instance2)
    }
    @Test
    fun `concurrent access should be thread safe`() = runTest {
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        repeat(10) { index ->
            val job = kotlinx.coroutines.launch {
                repeat(10) {
                    metrics.logDetectionAttempt(index % 2 == 0, (index * 10).toLong())
                    metrics.logFrameProcessingTime((index * 5).toLong())
                }
            }
            jobs.add(job)
        }
        jobs.forEach { it.join() }
        val summary = metrics.getMetricsSummary()
        assertEquals(100, summary.detectionAttempts)
        assertEquals(50, summary.successfulDetections)
    }
    @Test
    fun `quality metrics history should handle edge cases`() = runTest {
        val extremeMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = Float.MAX_VALUE,
            brightness = Float.MIN_VALUE,
            contrast = 0f,
            isBlurry = true,
            isOverexposed = true,
            isUnderexposed = true
        )
        metrics.logQualityAnalysis(extremeMetrics, Long.MAX_VALUE)
        val history = metrics.getQualityMetricsHistory()
        assertEquals(1, history.size)
        val snapshot = history.first()
        assertEquals(Float.MAX_VALUE, snapshot.sharpness)
        assertEquals(Float.MIN_VALUE, snapshot.brightness)
        assertEquals(Long.MAX_VALUE, snapshot.processingTime)
        assertTrue(snapshot.isBlurry)
        assertTrue(snapshot.isOverexposed)
        assertTrue(snapshot.isUnderexposed)
    }
}
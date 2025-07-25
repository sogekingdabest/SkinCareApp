package es.monsteraltech.skincare_tfm.camera.guidance

import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

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
        // Arrange & Act
        metrics.logDetectionAttempt(true, 100L)
        metrics.logDetectionAttempt(false, 150L)
        metrics.logDetectionAttempt(true, 120L)
        
        // Assert
        runTest {
            val summary = metrics.getMetricsSummary()
            assertEquals(3, summary.detectionAttempts)
            assertEquals(2, summary.successfulDetections)
            assertEquals(67, summary.detectionSuccessRate) // 2/3 * 100 = 66.67 -> 67
            assertEquals(123L, summary.avgDetectionTime) // (100+150+120)/3 = 123.33 -> 123
        }
    }
    
    @Test
    fun `logQualityAnalysis should store metrics correctly`() = runTest {
        // Arrange
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.8f,
            brightness = 120f,
            contrast = 0.6f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        
        // Act
        metrics.logQualityAnalysis(qualityMetrics, 50L)
        
        // Assert
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
        // Arrange
        val validationResult = CaptureValidationManager.ValidationResult(
            canCapture = true,
            guideState = CaptureValidationManager.GuideState.READY,
            message = "Ready to capture",
            confidence = 0.9f
        )
        
        // Act
        metrics.logCaptureValidation(validationResult, 25L)
        metrics.logCaptureValidation(validationResult, 30L)
        
        // Assert
        runTest {
            val summary = metrics.getMetricsSummary()
            assertEquals(2, summary.validationStates["READY"])
            assertEquals(27L, summary.avgValidationTime) // (25+30)/2 = 27.5 -> 27
        }
    }
    
    @Test
    fun `logPreprocessingPerformance should accumulate time`() {
        // Arrange
        val filters = listOf("contrast", "brightness", "noise_reduction")
        
        // Act
        metrics.logPreprocessingPerformance(200L, filters)
        metrics.logPreprocessingPerformance(180L, filters)
        
        // Assert
        runTest {
            val summary = metrics.getMetricsSummary()
            assertEquals(380L, summary.avgPreprocessingTime) // Total time since only 2 calls
        }
    }
    
    @Test
    fun `logFrameProcessingTime should maintain history limit`() = runTest {
        // Act - Add more than the limit (50)
        repeat(60) { index ->
            metrics.logFrameProcessingTime(index.toLong())
        }
        
        // Assert
        val history = metrics.getFrameProcessingHistory()
        assertEquals(50, history.size) // Should be limited to 50
        assertEquals(10L, history.first()) // Should start from 10 (removed 0-9)
        assertEquals(59L, history.last()) // Should end at 59
    }
    
    @Test
    fun `logMemoryUsage should maintain history limit`() = runTest {
        // Act - Add more than the limit (20)
        repeat(25) { index ->
            val usedMemory = (index + 1) * 1024L * 1024L // MB
            val totalMemory = 8L * 1024L * 1024L * 1024L // 8GB
            metrics.logMemoryUsage(usedMemory, totalMemory)
        }
        
        // Assert
        val history = metrics.getMemoryUsageHistory()
        assertEquals(20, history.size) // Should be limited to 20
        
        val firstSnapshot = history.first()
        assertEquals(6L * 1024L * 1024L, firstSnapshot.usedMemory) // Should start from 6MB (removed 1-5)
        
        val lastSnapshot = history.last()
        assertEquals(25L * 1024L * 1024L, lastSnapshot.usedMemory) // Should end at 25MB
    }
    
    @Test
    fun `getMetricsSummary should calculate averages correctly with zero attempts`() = runTest {
        // Act - No operations logged
        val summary = metrics.getMetricsSummary()
        
        // Assert
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
        // Arrange
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
        
        // Act
        val report = metrics.exportMetrics()
        
        // Assert
        assertTrue(report.contains("CAPTURE GUIDANCE METRICS"))
        assertTrue(report.contains("Detection Attempts: 2"))
        assertTrue(report.contains("Successful Detections: 1"))
        assertTrue(report.contains("Success Rate: 50%"))
        assertTrue(report.contains("Avg Detection Time: 150ms"))
    }
    
    @Test
    fun `reset should clear all metrics`() = runTest {
        // Arrange - Add some data
        metrics.logDetectionAttempt(true, 100L)
        metrics.logFrameProcessingTime(50L)
        metrics.logMemoryUsage(1024L, 8192L)
        
        // Act
        metrics.reset()
        
        // Assert
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
        // Act
        val instance1 = CaptureMetrics.getInstance()
        val instance2 = CaptureMetrics.getInstance()
        
        // Assert
        assertSame(instance1, instance2)
    }
    
    @Test
    fun `concurrent access should be thread safe`() = runTest {
        // Arrange
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        
        // Act - Simulate concurrent access
        repeat(10) { index ->
            val job = kotlinx.coroutines.launch {
                repeat(10) {
                    metrics.logDetectionAttempt(index % 2 == 0, (index * 10).toLong())
                    metrics.logFrameProcessingTime((index * 5).toLong())
                }
            }
            jobs.add(job)
        }
        
        // Wait for all jobs to complete
        jobs.forEach { it.join() }
        
        // Assert
        val summary = metrics.getMetricsSummary()
        assertEquals(100, summary.detectionAttempts) // 10 threads * 10 attempts each
        assertEquals(50, summary.successfulDetections) // Half should be successful
    }
    
    @Test
    fun `quality metrics history should handle edge cases`() = runTest {
        // Test with extreme values
        val extremeMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = Float.MAX_VALUE,
            brightness = Float.MIN_VALUE,
            contrast = 0f,
            isBlurry = true,
            isOverexposed = true,
            isUnderexposed = true
        )
        
        // Act
        metrics.logQualityAnalysis(extremeMetrics, Long.MAX_VALUE)
        
        // Assert
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
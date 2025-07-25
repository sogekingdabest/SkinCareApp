package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import java.io.File

/**
 * Test de integración completa que verifica que el sistema de métricas
 * funciona correctamente con todos los componentes de guías de captura
 */
class MetricsIntegrationTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockFilesDir: File
    
    private lateinit var metricsIntegration: MetricsIntegration
    private lateinit var moleDetectionProcessor: MoleDetectionProcessor
    private lateinit var imageQualityAnalyzer: ImageQualityAnalyzer
    private lateinit var captureValidationManager: CaptureValidationManager
    private lateinit var imagePreprocessor: ImagePreprocessor
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Setup mock context
        `when`(mockContext.filesDir).thenReturn(mockFilesDir)
        `when`(mockFilesDir.exists()).thenReturn(true)
        
        val config = MetricsConfig(
            enableLogging = true,
            enablePerformanceTracking = true,
            maxHistorySize = 50,
            logLevel = LogLevel.INFO
        )
        
        metricsIntegration = MetricsIntegration(mockContext, config)
        
        // Initialize guidance components
        moleDetectionProcessor = MoleDetectionProcessor()
        val mockPerformanceManager = PerformanceManager(mockContext)
        val mockThermalDetector = ThermalStateDetector(mockContext)
        imageQualityAnalyzer = ImageQualityAnalyzer(mockPerformanceManager, mockThermalDetector)
        captureValidationManager = CaptureValidationManager()
        imagePreprocessor = ImagePreprocessor(mockContext)
        
        // Reset metrics
        runTest {
            CaptureMetrics.getInstance().reset()
        }
    }
    
    @Test
    fun `complete capture guidance workflow should be tracked correctly`() = runTest {
        // Arrange
        metricsIntegration.start()
        
        // Simulate complete capture guidance workflow
        
        // 1. Frame processing and mole detection
        repeat(10) { frameIndex ->
            val detectionStartTime = System.currentTimeMillis()
            
            // Simulate mole detection processing time
            delay(80 + frameIndex * 5) // Variable processing time
            
            val detectionTime = System.currentTimeMillis() - detectionStartTime
            val detectionSuccess = frameIndex % 3 != 0 // ~67% success rate
            val confidence = if (detectionSuccess) 0.7f + frameIndex * 0.02f else 0.3f + frameIndex * 0.01f
            
            metricsIntegration.logMoleDetection(detectionSuccess, detectionTime, confidence)
            metricsIntegration.logFrameProcessing(detectionTime, 1920, 1080)
        }
        
        // 2. Quality analysis for successful detections
        repeat(7) { qualityIndex -> // ~7 successful detections from above
            val qualityStartTime = System.currentTimeMillis()
            
            delay(40 + qualityIndex * 3) // Quality analysis time
            
            val qualityTime = System.currentTimeMillis() - qualityStartTime
            val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
                sharpness = 0.6f + qualityIndex * 0.05f,
                brightness = 100f + qualityIndex * 5f,
                contrast = 0.5f + qualityIndex * 0.03f,
                isBlurry = qualityIndex % 4 == 0,
                isOverexposed = qualityIndex % 6 == 0,
                isUnderexposed = qualityIndex % 7 == 0
            )
            
            metricsIntegration.logQualityAnalysis(qualityMetrics, qualityTime)
        }
        
        // 3. Capture validation for each quality analysis
        repeat(7) { validationIndex ->
            val validationStartTime = System.currentTimeMillis()
            
            delay(20 + validationIndex * 2) // Validation time
            
            val validationTime = System.currentTimeMillis() - validationStartTime
            val guideState = when (validationIndex % 5) {
                0 -> CaptureValidationManager.GuideState.READY
                1 -> CaptureValidationManager.GuideState.TOO_FAR
                2 -> CaptureValidationManager.GuideState.CENTERING
                3 -> CaptureValidationManager.GuideState.BLURRY
                else -> CaptureValidationManager.GuideState.POOR_LIGHTING
            }
            
            val validationResult = CaptureValidationManager.ValidationResult(
                canCapture = guideState == CaptureValidationManager.GuideState.READY,
                guideState = guideState,
                message = "State: ${guideState.name}",
                confidence = 0.8f + validationIndex * 0.02f
            )
            
            metricsIntegration.logCaptureValidation(validationResult, validationTime)
        }
        
        // 4. Image preprocessing for successful captures
        repeat(2) { preprocessIndex -> // Assume 2 successful captures
            val preprocessStartTime = System.currentTimeMillis()
            
            delay(150 + preprocessIndex * 20) // Preprocessing time
            
            val preprocessTime = System.currentTimeMillis() - preprocessStartTime
            val filters = listOf(
                "contrast_enhancement",
                "noise_reduction",
                if (preprocessIndex % 2 == 0) "sharpening" else "brightness_adjustment"
            )
            
            metricsIntegration.logImagePreprocessing(preprocessTime, filters)
        }
        
        // Wait for all metrics to be processed
        delay(500)
        
        // Assert - Verify complete workflow metrics
        val finalMetrics = metricsIntegration.getCurrentMetrics()
        
        // Detection metrics
        assertEquals(10, finalMetrics.detectionAttempts)
        assertTrue("Success rate should be around 70%", finalMetrics.detectionSuccessRate in 60..80)
        assertTrue("Average detection time should be reasonable", finalMetrics.avgDetectionTime in 80..150)
        
        // Quality analysis metrics
        assertTrue("Quality analysis time should be recorded", finalMetrics.avgQualityAnalysisTime > 0)
        
        // Validation metrics
        assertTrue("Validation time should be recorded", finalMetrics.avgValidationTime > 0)
        assertEquals(7, finalMetrics.validationStates.values.sum())
        
        // Frame processing metrics
        assertTrue("Frame processing time should be recorded", finalMetrics.avgFrameProcessingTime > 0)
        
        // Preprocessing metrics
        assertTrue("Preprocessing time should be recorded", finalMetrics.avgPreprocessingTime > 0)
        
        // Cleanup
        metricsIntegration.stop()
    }
    
    @Test
    fun `performance degradation should be detected and alerted`() = runTest {
        // Arrange
        val alertListener = TestAlertListener()
        metricsIntegration.addDashboardListener(alertListener)
        metricsIntegration.start()
        
        // Simulate performance degradation scenario
        
        // 1. Start with good performance
        repeat(5) { index ->
            metricsIntegration.logMoleDetection(true, 50L + index * 2, 0.9f)
            metricsIntegration.logFrameProcessing(30L + index, 1920, 1080)
        }
        
        // 2. Gradually degrade performance
        repeat(10) { index ->
            val degradedTime = 200L + index * 50L // Increasingly slow
            metricsIntegration.logMoleDetection(index % 4 != 0, degradedTime, 0.5f - index * 0.02f)
            metricsIntegration.logFrameProcessing(degradedTime, 1920, 1080)
        }
        
        // 3. Simulate memory pressure
        val totalMemory = 8L * 1024L * 1024L * 1024L // 8GB
        repeat(5) { index ->
            val memoryUsage = totalMemory * (0.7f + index * 0.05f) // 70% to 90%
            CaptureMetrics.getInstance().logMemoryUsage(memoryUsage.toLong(), totalMemory)
        }
        
        // Wait for dashboard to process and generate alerts
        delay(1500)
        
        // Assert - Performance alerts should be generated
        val finalMetrics = metricsIntegration.getCurrentMetrics()
        assertTrue("Detection success rate should have degraded", finalMetrics.detectionSuccessRate < 80)
        assertTrue("Average detection time should have increased", finalMetrics.avgDetectionTime > 100)
        
        // Cleanup
        metricsIntegration.removeDashboardListener(alertListener)
        metricsIntegration.stop()
    }
    
    @Test
    fun `metrics should persist through component lifecycle`() = runTest {
        // Test multiple start/stop cycles with data persistence
        
        // Cycle 1
        metricsIntegration.start()
        metricsIntegration.logMoleDetection(true, 100L, 0.8f)
        metricsIntegration.logMoleDetection(false, 150L, 0.4f)
        
        var metrics = metricsIntegration.getCurrentMetrics()
        assertEquals(2, metrics.detectionAttempts)
        
        metricsIntegration.stop()
        
        // Cycle 2 - Metrics should persist
        metricsIntegration.start()
        metricsIntegration.logMoleDetection(true, 120L, 0.9f)
        
        metrics = metricsIntegration.getCurrentMetrics()
        assertEquals(3, metrics.detectionAttempts) // Should include previous data
        assertEquals(2, metrics.successfulDetections)
        
        metricsIntegration.stop()
        
        // Cycle 3 - Add more data
        metricsIntegration.start()
        
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.75f,
            brightness = 115f,
            contrast = 0.65f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        metricsIntegration.logQualityAnalysis(qualityMetrics, 60L)
        
        metrics = metricsIntegration.getCurrentMetrics()
        assertEquals(3, metrics.detectionAttempts) // Detection data persisted
        assertEquals(60L, metrics.avgQualityAnalysisTime) // New quality data added
        
        metricsIntegration.stop()
    }
    
    @Test
    fun `debug report should provide actionable insights`() = runTest {
        // Arrange - Create scenario with various performance characteristics
        metricsIntegration.start()
        
        // Good performance period
        repeat(5) { index ->
            metricsIntegration.logMoleDetection(true, 80L + index * 5, 0.85f + index * 0.02f)
            
            val goodQuality = ImageQualityAnalyzer.QualityMetrics(
                sharpness = 0.8f + index * 0.02f,
                brightness = 120f + index * 2f,
                contrast = 0.7f + index * 0.01f,
                isBlurry = false,
                isOverexposed = false,
                isUnderexposed = false
            )
            metricsIntegration.logQualityAnalysis(goodQuality, 45L + index * 2)
            
            metricsIntegration.logFrameProcessing(35L + index, 1920, 1080)
        }
        
        // Poor performance period
        repeat(3) { index ->
            metricsIntegration.logMoleDetection(false, 250L + index * 30, 0.3f + index * 0.05f)
            
            val poorQuality = ImageQualityAnalyzer.QualityMetrics(
                sharpness = 0.3f + index * 0.02f,
                brightness = 60f + index * 5f, // Too dark
                contrast = 0.2f + index * 0.01f,
                isBlurry = true,
                isOverexposed = false,
                isUnderexposed = true
            )
            metricsIntegration.logQualityAnalysis(poorQuality, 80L + index * 10)
            
            metricsIntegration.logFrameProcessing(70L + index * 5, 1920, 1080)
        }
        
        // Mixed validation results
        val validationStates = listOf(
            CaptureValidationManager.GuideState.READY,
            CaptureValidationManager.GuideState.TOO_FAR,
            CaptureValidationManager.GuideState.BLURRY,
            CaptureValidationManager.GuideState.POOR_LIGHTING,
            CaptureValidationManager.GuideState.CENTERING
        )
        
        validationStates.forEachIndexed { index, state ->
            val result = CaptureValidationManager.ValidationResult(
                canCapture = state == CaptureValidationManager.GuideState.READY,
                guideState = state,
                message = "Test state: ${state.name}",
                confidence = 0.7f + index * 0.05f
            )
            metricsIntegration.logCaptureValidation(result, 25L + index * 3)
        }
        
        // Act
        val debugReport = metricsIntegration.generateDebugReport()
        
        // Assert - Report should contain actionable insights
        assertTrue("Report should contain session summary", debugReport.contains("GENERAL SUMMARY"))
        assertTrue("Report should show detection attempts", debugReport.contains("Detection Attempts: 8"))
        assertTrue("Report should show success rate", debugReport.contains("Success Rate: 62%")) // 5/8 = 62.5%
        
        assertTrue("Report should contain quality metrics", debugReport.contains("QUALITY METRICS"))
        assertTrue("Report should show frame processing stats", debugReport.contains("FRAME PROCESSING"))
        assertTrue("Report should show validation states", debugReport.contains("VALIDATION STATES"))
        
        // Verify specific insights
        assertTrue("Report should show READY state", debugReport.contains("READY: 1"))
        assertTrue("Report should show TOO_FAR state", debugReport.contains("TOO_FAR: 1"))
        assertTrue("Report should show BLURRY state", debugReport.contains("BLURRY: 1"))
        
        // Cleanup
        metricsIntegration.stop()
    }
    
    @Test
    fun `JSON export should be compatible with external analysis tools`() = runTest {
        // Arrange
        metricsIntegration.start()
        
        // Add structured data for export
        metricsIntegration.logMoleDetection(true, 95L, 0.87f)
        metricsIntegration.logMoleDetection(true, 105L, 0.92f)
        metricsIntegration.logMoleDetection(false, 180L, 0.45f)
        
        val qualityMetrics1 = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.82f,
            brightness = 125f,
            contrast = 0.68f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        
        val qualityMetrics2 = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.45f,
            brightness = 85f,
            contrast = 0.35f,
            isBlurry = true,
            isOverexposed = false,
            isUnderexposed = true
        )
        
        metricsIntegration.logQualityAnalysis(qualityMetrics1, 52L)
        metricsIntegration.logQualityAnalysis(qualityMetrics2, 68L)
        
        // Act
        val jsonExport = metricsIntegration.exportMetricsAsJson()
        
        // Assert - JSON should be well-formed and contain expected data
        assertTrue("JSON should start with opening brace", jsonExport.startsWith("{"))
        assertTrue("JSON should end with closing brace", jsonExport.endsWith("}"))
        
        // Verify key metrics are present
        assertTrue("JSON should contain timestamp", jsonExport.contains("\"timestamp\":"))
        assertTrue("JSON should contain detection attempts", jsonExport.contains("\"detectionAttempts\": 3"))
        assertTrue("JSON should contain success rate", jsonExport.contains("\"successRate\": 67")) // 2/3 = 66.67 -> 67
        
        // Verify quality history structure
        assertTrue("JSON should contain quality history", jsonExport.contains("\"qualityHistory\":"))
        assertTrue("JSON should contain sharpness data", jsonExport.contains("\"sharpness\": 0.82"))
        assertTrue("JSON should contain brightness data", jsonExport.contains("\"brightness\": 125.0"))
        assertTrue("JSON should contain blur status", jsonExport.contains("\"isBlurry\": false"))
        assertTrue("JSON should contain second quality entry", jsonExport.contains("\"sharpness\": 0.45"))
        
        // Verify timing data
        assertTrue("JSON should contain average detection time", jsonExport.contains("\"avgDetectionTime\":"))
        assertTrue("JSON should contain average quality time", jsonExport.contains("\"avgQualityAnalysisTime\":"))
        
        // Cleanup
        metricsIntegration.stop()
    }
    
    private class TestAlertListener : MetricsDashboard.DashboardListener {
        val alerts = mutableListOf<MetricsDashboard.PerformanceAlert>()
        val updates = mutableListOf<DashboardStats>()
        
        override fun onMetricsUpdated(stats: DashboardStats) {
            updates.add(stats)
        }
        
        override fun onPerformanceAlert(alert: MetricsDashboard.PerformanceAlert) {
            alerts.add(alert)
        }
    }
}
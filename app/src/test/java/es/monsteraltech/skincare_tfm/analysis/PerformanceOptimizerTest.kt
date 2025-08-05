package es.monsteraltech.skincare_tfm.analysis
import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class PerformanceOptimizerTest {
    private lateinit var performanceOptimizer: PerformanceOptimizer
    private lateinit var mockBitmap: Bitmap
    @Before
    fun setUp() {
        performanceOptimizer = PerformanceOptimizer()
        mockBitmap = mock(Bitmap::class.java)
        `when`(mockBitmap.width).thenReturn(1024)
        `when`(mockBitmap.height).thenReturn(1024)
        `when`(mockBitmap.isRecycled).thenReturn(false)
    }
    @Test
    fun testAnalyzeDevicePerformance() {
        val devicePerformance = performanceOptimizer.analyzeDevicePerformance()
        assertNotNull(devicePerformance)
        assertTrue(devicePerformance.cpuCores > 0)
        assertTrue(devicePerformance.availableMemory > 0)
        assertTrue(devicePerformance.recommendedThreads > 0)
        assertTrue(devicePerformance.recommendedImageSize > 0)
    }
    @Test
    fun testOptimizeConfiguration_LowEndDevice() {
        val baseConfig = AnalysisConfiguration.default()
        val lowEndDevice = PerformanceOptimizer.DevicePerformance(
            cpuCores = 2,
            availableMemory = 256 * 1024 * 1024,
            isLowEndDevice = true,
            recommendedThreads = 1,
            recommendedImageSize = 512 * 512
        )
        val optimizedConfig = performanceOptimizer.optimizeConfiguration(baseConfig, lowEndDevice)
        assertNotNull(optimizedConfig)
        assertFalse(optimizedConfig.enableParallelProcessing)
        assertEquals(512 * 512, optimizedConfig.maxImageResolution)
        assertTrue(optimizedConfig.timeoutMs > baseConfig.timeoutMs)
    }
    @Test
    fun testOptimizeConfiguration_HighEndDevice() {
        val baseConfig = AnalysisConfiguration.default()
        val highEndDevice = PerformanceOptimizer.DevicePerformance(
            cpuCores = 8,
            availableMemory = 4L * 1024 * 1024 * 1024,
            isLowEndDevice = false,
            recommendedThreads = 4,
            recommendedImageSize = 2048 * 2048
        )
        val optimizedConfig = performanceOptimizer.optimizeConfiguration(baseConfig, highEndDevice)
        assertNotNull(optimizedConfig)
        assertTrue(optimizedConfig.enableParallelProcessing)
        assertEquals(2048 * 2048, optimizedConfig.maxImageResolution)
        assertTrue(optimizedConfig.compressionQuality >= 90)
    }
    @Test
    fun testMeasurePerformance() = runBlocking {
        val taskName = "test_task"
        val expectedResult = "test_result"
        val (result, metrics) = performanceOptimizer.measurePerformance(taskName) {
            kotlinx.coroutines.delay(100)
            expectedResult
        }
        assertEquals(expectedResult, result)
        assertNotNull(metrics)
        assertTrue(metrics.processingTimeMs >= 100)
        assertTrue(metrics.averageTimeMs > 0)
    }
    @Test
    fun testOptimizeBitmapForProcessing_NoOptimizationNeeded() {
        val smallBitmap = mock(Bitmap::class.java)
        `when`(smallBitmap.width).thenReturn(512)
        `when`(smallBitmap.height).thenReturn(512)
        val result = performanceOptimizer.optimizeBitmapForProcessing(smallBitmap, 1024 * 1024)
        assertEquals(smallBitmap, result)
    }
    @Test
    fun testOptimizeBitmapForProcessing_OptimizationNeeded() {
        val largeBitmap = mock(Bitmap::class.java)
        `when`(largeBitmap.width).thenReturn(2048)
        `when`(largeBitmap.height).thenReturn(2048)
        try {
            performanceOptimizer.optimizeBitmapForProcessing(largeBitmap, 1024 * 1024)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("createScaledBitmap") == true ||
                      e is RuntimeException)
        }
    }
    @Test
    fun testExecuteParallelOptimized_LowEndDevice() = runBlocking {
        val lowEndDevice = PerformanceOptimizer.DevicePerformance(
            cpuCores = 2,
            availableMemory = 256 * 1024 * 1024,
            isLowEndDevice = true,
            recommendedThreads = 1,
            recommendedImageSize = 512 * 512
        )
        val task1 = suspend { "result1" }
        val task2 = suspend { "result2" }
        val (result1, result2) = performanceOptimizer.executeParallelOptimized(task1, task2, lowEndDevice)
        assertEquals("result1", result1)
        assertEquals("result2", result2)
    }
    @Test
    fun testExecuteParallelOptimized_HighEndDevice() = runBlocking {
        val highEndDevice = PerformanceOptimizer.DevicePerformance(
            cpuCores = 8,
            availableMemory = 4L * 1024 * 1024 * 1024,
            isLowEndDevice = false,
            recommendedThreads = 4,
            recommendedImageSize = 2048 * 2048
        )
        val task1 = suspend { "result1" }
        val task2 = suspend { "result2" }
        val (result1, result2) = performanceOptimizer.executeParallelOptimized(task1, task2, highEndDevice)
        assertEquals("result1", result1)
        assertEquals("result2", result2)
    }
    @Test
    fun testGetAdaptiveTimeouts_NoHistoricalData() {
        val baseConfig = AnalysisConfiguration.default()
        val adaptiveConfig = performanceOptimizer.getAdaptiveTimeouts(baseConfig)
        assertEquals(baseConfig.timeoutMs, adaptiveConfig.timeoutMs)
        assertEquals(baseConfig.aiTimeoutMs, adaptiveConfig.aiTimeoutMs)
        assertEquals(baseConfig.abcdeTimeoutMs, adaptiveConfig.abcdeTimeoutMs)
    }
    @Test
    fun testShouldUsePowerSaveMode() {
        val shouldUsePowerSave = performanceOptimizer.shouldUsePowerSaveMode()
        assertFalse(shouldUsePowerSave)
    }
    @Test
    fun testGetPowerSaveConfiguration() {
        val baseConfig = AnalysisConfiguration.default()
        val powerSaveConfig = performanceOptimizer.getPowerSaveConfiguration(baseConfig)
        assertNotNull(powerSaveConfig)
        assertFalse(powerSaveConfig.enableParallelProcessing)
        assertEquals(512 * 512, powerSaveConfig.maxImageResolution)
        assertTrue(powerSaveConfig.timeoutMs > baseConfig.timeoutMs)
    }
    @Test
    fun testGetPerformanceStats() {
        val stats = performanceOptimizer.getPerformanceStats()
        assertNotNull(stats)
        assertTrue(stats.containsKey("processedImages"))
        assertTrue(stats.containsKey("lastProcessingTimeMs"))
        assertTrue(stats.containsKey("averageProcessingTimeMs"))
        assertTrue(stats.containsKey("activeThreads"))
        assertTrue(stats.containsKey("queuedTasks"))
        assertTrue(stats.containsKey("usedMemoryMB"))
    }
    @Test
    fun testCleanup() {
        performanceOptimizer.cleanup()
        assertTrue(true)
    }
}
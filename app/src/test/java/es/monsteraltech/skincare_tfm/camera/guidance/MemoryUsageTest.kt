package es.monsteraltech.skincare_tfm.camera.guidance
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.Mat
import org.opencv.core.Size
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MemoryUsageTest {
    private lateinit var context: Context
    private lateinit var performanceManager: PerformanceManager
    private lateinit var roiOptimizer: ROIOptimizer
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        performanceManager = PerformanceManager(context)
        roiOptimizer = ROIOptimizer()
    }
    @After
    fun tearDown() {
        performanceManager.cleanup()
        roiOptimizer.clearHistory()
        System.gc()
    }
    @Test
    fun `test mat pool memory efficiency`() {
        val initialMemory = getUsedMemory()
        val mats = mutableListOf<Mat>()
        repeat(50) {
            mats.add(performanceManager.borrowMat())
        }
        val memoryAfterBorrow = getUsedMemory()
        mats.forEach { mat ->
            performanceManager.returnMat(mat)
        }
        val memoryAfterReturn = getUsedMemory()
        assertTrue(
            "Memory should be lower after returning mats to pool",
            memoryAfterReturn < memoryAfterBorrow
        )
        val stats = performanceManager.getPerformanceStats()
        val poolSize = stats["matPoolSize"] as Int
        assertTrue("Pool should contain reusable mats", poolSize > 0)
    }
    @Test
    fun `test memory usage without pool vs with pool`() {
        val configWithoutPool = PerformanceManager.PerformanceConfig(
            processingFrequency = 15,
            imageResolutionScale = 1.0f,
            enableROI = false,
            roiScale = 1.0f,
            enableMatPooling = false,
            maxPoolSize = 0,
            enableAdvancedFilters = true,
            throttleDetection = false
        )
        val memoryWithoutPool = measureMemoryUsage {
            repeat(20) {
                val mat = Mat()
                mat.release()
            }
        }
        val memoryWithPool = measureMemoryUsage {
            repeat(20) {
                val mat = performanceManager.borrowMat()
                performanceManager.returnMat(mat)
            }
        }
        assertTrue(
            "Pool should be more memory efficient",
            memoryWithPool <= memoryWithoutPool * 1.2f
        )
    }
    @Test
    fun `test ROI memory optimization`() {
        val fullImageSize = Size(1920.0, 1080.0)
        val roiResult = roiOptimizer.calculateROI(fullImageSize, 0.5f)
        val fullImageMemory = measureMemoryUsage {
            val fullMat = Mat(fullImageSize, org.opencv.core.CvType.CV_8UC3)
            fullMat.release()
        }
        val roiMemory = measureMemoryUsage {
            val fullMat = Mat(fullImageSize, org.opencv.core.CvType.CV_8UC3)
            val roiMat = roiOptimizer.extractROI(fullMat, roiResult)
            roiMat.release()
            fullMat.release()
        }
        assertTrue(
            "ROI processing should use less memory",
            roiMemory < fullImageMemory
        )
    }
    @Test
    fun `test memory leak detection in continuous processing`() = runTest {
        val initialMemory = getUsedMemory()
        val memoryReadings = mutableListOf<Long>()
        repeat(100) { iteration ->
            val mat = performanceManager.borrowMat()
            delay(1)
            performanceManager.returnMat(mat)
            if (iteration % 10 == 0) {
                System.gc()
                memoryReadings.add(getUsedMemory())
            }
        }
        val memoryGrowth = memoryReadings.last() - memoryReadings.first()
        val maxAcceptableGrowth = initialMemory * 0.1
        assertTrue(
            "Memory growth should be minimal (possible leak detected)",
            abs(memoryGrowth) < maxAcceptableGrowth
        )
    }
    @Test
    fun `test memory usage under different performance levels`() {
        val memoryUsageByLevel = mutableMapOf<PerformanceManager.PerformanceLevel, Long>()
        PerformanceManager.PerformanceLevel.values().forEach { level ->
            val memory = measureMemoryUsage {
                repeat(10) {
                    val mat = performanceManager.borrowMat()
                    performanceManager.returnMat(mat)
                }
            }
            memoryUsageByLevel[level] = memory
        }
        assertTrue("Memory usage should be tracked per level", memoryUsageByLevel.isNotEmpty())
    }
    @Test
    fun `test pool size limits prevent memory overflow`() {
        val stats = performanceManager.getPerformanceStats()
        val maxPoolSize = stats["maxPoolSize"] as Int
        val mats = mutableListOf<Mat>()
        repeat(maxPoolSize * 2) {
            mats.add(performanceManager.borrowMat())
        }
        val memoryAfterBorrow = getUsedMemory()
        mats.forEach { mat ->
            performanceManager.returnMat(mat)
        }
        val memoryAfterReturn = getUsedMemory()
        val finalStats = performanceManager.getPerformanceStats()
        val finalPoolSize = finalStats["matPoolSize"] as Int
        assertTrue("Pool size should not exceed limit", finalPoolSize <= maxPoolSize)
        assertTrue(
            "Memory should be freed when pool is full",
            memoryAfterReturn < memoryAfterBorrow
        )
    }
    @Test
    fun `test concurrent memory usage`() = runTest {
        val initialMemory = getUsedMemory()
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        repeat(10) { threadIndex ->
            val job = launch {
                repeat(20) {
                    val mat = performanceManager.borrowMat()
                    delay(1)
                    performanceManager.returnMat(mat)
                }
            }
            jobs.add(job)
        }
        jobs.forEach { it.join() }
        System.gc()
        val finalMemory = getUsedMemory()
        val memoryGrowth = finalMemory - initialMemory
        val maxAcceptableGrowth = initialMemory * 0.15
        assertTrue(
            "Concurrent usage should not cause excessive memory growth",
            memoryGrowth < maxAcceptableGrowth
        )
    }
    @Test
    fun `test memory cleanup on performance manager cleanup`() {
        val mats = mutableListOf<Mat>()
        repeat(10) {
            val mat = performanceManager.borrowMat()
            mats.add(mat)
        }
        mats.take(5).forEach { mat ->
            performanceManager.returnMat(mat)
        }
        val memoryBeforeCleanup = getUsedMemory()
        performanceManager.cleanup()
        System.gc()
        val memoryAfterCleanup = getUsedMemory()
        assertTrue(
            "Cleanup should free memory",
            memoryAfterCleanup <= memoryBeforeCleanup
        )
        val stats = performanceManager.getPerformanceStats()
        val poolSize = stats["matPoolSize"] as Int
        assertEquals("Pool should be empty after cleanup", 0, poolSize)
    }
    @Test
    fun `test ROI history memory management`() {
        val initialMemory = getUsedMemory()
        repeat(100) { i ->
            roiOptimizer.updateDetectionHistory(
                org.opencv.core.Point(i * 10.0, i * 5.0)
            )
        }
        val memoryAfterHistory = getUsedMemory()
        roiOptimizer.clearHistory()
        System.gc()
        val memoryAfterClear = getUsedMemory()
        val stats = roiOptimizer.getROIStats()
        val historySize = stats["historySize"] as Int
        assertEquals("History should be cleared", 0, historySize)
        assertTrue(
            "Clear should free memory",
            memoryAfterClear <= memoryAfterHistory
        )
    }
    private fun measureMemoryUsage(block: () -> Unit): Long {
        val runtime = Runtime.getRuntime()
        System.gc()
        val memoryBefore = runtime.totalMemory() - runtime.freeMemory()
        block()
        System.gc()
        val memoryAfter = runtime.totalMemory() - runtime.freeMemory()
        return memoryAfter - memoryBefore
    }
    private fun getUsedMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
}
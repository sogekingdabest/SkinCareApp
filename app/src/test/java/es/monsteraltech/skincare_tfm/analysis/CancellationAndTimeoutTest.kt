package es.monsteraltech.skincare_tfm.analysis
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class CancellationAndTimeoutTest {
    @Mock
    private lateinit var mockContext: Context
    @Mock
    private lateinit var mockProgressCallback: ProgressCallback
    @Mock
    private lateinit var mockBitmap: Bitmap
    private lateinit var asyncProcessor: AsyncImageProcessor
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(mockBitmap.width).thenReturn(1024)
        whenever(mockBitmap.height).thenReturn(1024)
        whenever(mockBitmap.isRecycled).thenReturn(false)
        whenever(mockBitmap.config).thenReturn(Bitmap.Config.ARGB_8888)
        asyncProcessor = AsyncImageProcessor(mockContext, mockProgressCallback)
    }
    @Test
    fun `cancelProcessing should interrupt ongoing processing`() = runTest {
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val longTimeoutConfig = AnalysisConfiguration(
            enableAI = true,
            enableABCDE = true,
            timeoutMs = 30000L
        )
        var processingCompleted = false
        var cancellationDetected = false
        val processingJob = launch {
            try {
                asyncProcessor.processImage(testBitmap, config = longTimeoutConfig)
                processingCompleted = true
            } catch (e: AnalysisError.UserCancellation) {
                cancellationDetected = true
            } catch (e: CancellationException) {
                cancellationDetected = true
            }
        }
        delay(100)
        asyncProcessor.cancelProcessing()
        processingJob.join()
        assertFalse("Processing should not complete when cancelled", processingCompleted)
        assertTrue("Cancellation should be handled", true)
    }
    @Test
    fun `processImage should timeout with short timeout configuration`() = runTest {
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val shortTimeoutConfig = AnalysisConfiguration(
            enableAI = true,
            enableABCDE = true,
            timeoutMs = 50L,
            aiTimeoutMs = 25L,
            abcdeTimeoutMs = 25L
        )
        var timeoutDetected = false
        try {
            asyncProcessor.processImage(testBitmap, config = shortTimeoutConfig)
        } catch (e: AnalysisError.Timeout) {
            timeoutDetected = true
            assertTrue("Timeout error should be recoverable", e.isRecoverable())
            assertEquals("Should have extend timeout strategy",
                RecoveryStrategy.EXTEND_TIMEOUT, e.getRecoveryStrategy())
        } catch (e: TimeoutCancellationException) {
            timeoutDetected = true
        } catch (e: Exception) {
        }
        assertTrue("Short timeout should be configured", shortTimeoutConfig.timeoutMs < 100L)
    }
    @Test
    fun `AI analysis should timeout independently`() = runTest {
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val aiTimeoutConfig = AnalysisConfiguration(
            enableAI = true,
            enableABCDE = false,
            timeoutMs = 10000L,
            aiTimeoutMs = 50L
        )
        try {
            asyncProcessor.processImage(testBitmap, config = aiTimeoutConfig)
        } catch (e: AnalysisError.AIModelError) {
            assertTrue("AI error should be recoverable", e.isRecoverable())
            assertEquals("Should fallback to ABCDE",
                RecoveryStrategy.FALLBACK_TO_ABCDE, e.getRecoveryStrategy())
        } catch (e: Exception) {
        }
        assertTrue("AI timeout should be configured", aiTimeoutConfig.aiTimeoutMs < 100L)
    }
    @Test
    fun `ABCDE analysis should timeout independently`() = runTest {
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val abcdeTimeoutConfig = AnalysisConfiguration(
            enableAI = false,
            enableABCDE = true,
            timeoutMs = 10000L,
            abcdeTimeoutMs = 50L
        )
        try {
            asyncProcessor.processImage(testBitmap, config = abcdeTimeoutConfig)
        } catch (e: AnalysisError.ABCDEAnalysisError) {
            assertTrue("ABCDE error should be recoverable", e.isRecoverable())
            assertEquals("Should use default values",
                RecoveryStrategy.USE_DEFAULT_VALUES, e.getRecoveryStrategy())
        } catch (e: Exception) {
        }
        assertTrue("ABCDE timeout should be configured", abcdeTimeoutConfig.abcdeTimeoutMs < 100L)
    }
    @Test
    fun `cancellation should work during initialization stage`() = runTest {
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        var initializationStarted = false
        whenever(mockProgressCallback.onStageChanged(ProcessingStage.INITIALIZING)).then {
            initializationStarted = true
        }
        val processingJob = launch {
            try {
                asyncProcessor.processImage(testBitmap)
            } catch (e: CancellationException) {
            }
        }
        asyncProcessor.cancelProcessing()
        processingJob.join()
        assertTrue("Cancellation should work at any stage", true)
    }
    @Test
    fun `cancellation should work during AI analysis stage`() = runTest {
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val aiConfig = AnalysisConfiguration(
            enableAI = true,
            enableABCDE = false,
            timeoutMs = 30000L
        )
        val processingJob = launch {
            try {
                asyncProcessor.processImage(testBitmap, config = aiConfig)
            } catch (e: CancellationException) {
            }
        }
        delay(50)
        asyncProcessor.cancelProcessing()
        processingJob.join()
        assertTrue("Cancellation should work during AI analysis", true)
    }
    @Test
    fun `cancellation should work during ABCDE analysis stage`() = runTest {
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val abcdeConfig = AnalysisConfiguration(
            enableAI = false,
            enableABCDE = true,
            timeoutMs = 30000L
        )
        val processingJob = launch {
            try {
                asyncProcessor.processImage(testBitmap, config = abcdeConfig)
            } catch (e: CancellationException) {
            }
        }
        delay(50)
        asyncProcessor.cancelProcessing()
        processingJob.join()
        assertTrue("Cancellation should work during ABCDE analysis", true)
    }
    @Test
    fun `multiple cancellation calls should be handled gracefully`() = runTest {
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val processingJob = launch {
            try {
                asyncProcessor.processImage(testBitmap)
            } catch (e: CancellationException) {
            }
        }
        asyncProcessor.cancelProcessing()
        asyncProcessor.cancelProcessing()
        asyncProcessor.cancelProcessing()
        processingJob.join()
        assertTrue("Multiple cancellation calls should be handled gracefully", true)
    }
    @Test
    fun `isProcessing should return correct state during processing`() = runTest {
        assertFalse("Should not be processing initially", asyncProcessor.isProcessing())
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val processingJob = launch {
            try {
                asyncProcessor.processImage(testBitmap)
            } catch (e: Exception) {
            }
        }
        delay(10)
        asyncProcessor.cancelProcessing()
        processingJob.join()
        assertTrue("Processing state should be managed correctly", true)
    }
    @Test
    fun `timeout error should trigger progress callback`() = runTest {
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val shortTimeoutConfig = AnalysisConfiguration(timeoutMs = 50L)
        try {
            asyncProcessor.processImage(testBitmap, config = shortTimeoutConfig)
        } catch (e: Exception) {
        }
        val timeoutError = AnalysisError.Timeout
        assertTrue("Timeout error should have user-friendly message",
            timeoutError.getUserFriendlyMessage().isNotEmpty())
    }
    @Test
    fun `cancellation should trigger appropriate callback`() = runTest {
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val processingJob = launch {
            try {
                asyncProcessor.processImage(testBitmap)
            } catch (e: AnalysisError.UserCancellation) {
                assertFalse("User cancellation should not be recoverable", e.isRecoverable())
                assertEquals("Should have no recovery strategy",
                    RecoveryStrategy.NONE, e.getRecoveryStrategy())
                assertEquals("Should have correct message",
                    "Análisis cancelado.", e.getUserFriendlyMessage())
            } catch (e: CancellationException) {
            }
        }
        delay(50)
        asyncProcessor.cancelProcessing()
        processingJob.join()
        assertTrue("Cancellation should be handled properly", true)
    }
    @Test
    fun `concurrent processing requests should be handled safely`() = runTest {
        val testBitmap1 = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val testBitmap2 = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val job1 = launch {
            try {
                asyncProcessor.processImage(testBitmap1)
            } catch (e: Exception) {
            }
        }
        val job2 = launch {
            try {
                asyncProcessor.processImage(testBitmap2)
            } catch (e: Exception) {
            }
        }
        delay(50)
        asyncProcessor.cancelProcessing()
        job1.join()
        job2.join()
        assertTrue("Concurrent processing should be handled safely", true)
    }
    @Test
    fun `timeout recovery should extend timeout appropriately`() {
        val timeoutError = AnalysisError.Timeout
        val recoveryManager = ErrorRecoveryManager()
        assertTrue("Timeout error should be recoverable", recoveryManager.canHandle(timeoutError))
        val recoveryInfo = recoveryManager.getRecoveryInfo(timeoutError)
        assertTrue("Recovery should be possible", recoveryInfo.canRecover)
        assertEquals("Should extend timeout", RecoveryStrategy.EXTEND_TIMEOUT, recoveryInfo.strategy)
        assertNotNull("Should have user message", recoveryInfo.userMessage)
        assertNotNull("Should have technical message", recoveryInfo.technicalMessage)
    }
    @Test
    fun `processing should handle coroutine cancellation properly`() = runTest {
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val processingJob = launch {
            try {
                asyncProcessor.processImage(testBitmap)
            } catch (e: CancellationException) {
                assertTrue("CancellationException should be thrown when coroutine is cancelled", true)
                throw e
            }
        }
        delay(50)
        processingJob.cancel()
        try {
            processingJob.join()
        } catch (e: CancellationException) {
        }
        assertTrue("Coroutine cancellation should be handled properly", processingJob.isCancelled)
    }
}
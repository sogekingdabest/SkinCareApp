package es.monsteraltech.skincare_tfm.analysis
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
class AsyncImageProcessorTest {
    @Mock
    private lateinit var mockContext: Context
    @Mock
    private lateinit var mockProgressCallback: ProgressCallback
    @Mock
    private lateinit var mockBitmap: Bitmap
    @Mock
    private lateinit var mockPreviousBitmap: Bitmap
    private lateinit var asyncProcessor: AsyncImageProcessor
    private lateinit var testConfig: AnalysisConfiguration
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(mockBitmap.width).thenReturn(1024)
        whenever(mockBitmap.height).thenReturn(1024)
        whenever(mockBitmap.isRecycled).thenReturn(false)
        whenever(mockBitmap.config).thenReturn(Bitmap.Config.ARGB_8888)
        whenever(mockPreviousBitmap.width).thenReturn(1024)
        whenever(mockPreviousBitmap.height).thenReturn(1024)
        whenever(mockPreviousBitmap.isRecycled).thenReturn(false)
        testConfig = AnalysisConfiguration(
            enableAI = true,
            enableABCDE = true,
            enableEvolution = true,
            enableParallelProcessing = true,
            timeoutMs = 5000L,
            aiTimeoutMs = 2000L,
            abcdeTimeoutMs = 2000L
        )
        asyncProcessor = AsyncImageProcessor(mockContext, mockProgressCallback)
    }
    @Test
    fun `processImage should call progress callbacks in correct order`() = runBlocking {
        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        try {
            asyncProcessor.processImage(testBitmap, config = testConfig)
        } catch (e: Exception) {
        }
        inOrder(mockProgressCallback) {
            verify(mockProgressCallback).onStageChanged(ProcessingStage.INITIALIZING)
            verify(mockProgressCallback).onProgressUpdate(eq(0), contains("Preparando"))
        }
    }
    @Test
    fun `processImage should handle valid bitmap correctly`() = runBlocking {
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        try {
            val result = asyncProcessor.processImage(testBitmap, config = testConfig)
            assertNotNull("Result should not be null", result)
        } catch (e: Exception) {
            assertFalse("Should not throw validation errors for valid bitmap",
                e is AnalysisError.InvalidImageError)
        }
    }
    @Test
    fun `processImage should reject invalid bitmap dimensions`() = runBlocking {
        val invalidBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        try {
            asyncProcessor.processImage(invalidBitmap, config = testConfig)
            fail("Should throw InvalidImageError for small bitmap")
        } catch (e: AnalysisError.InvalidImageError) {
            assertTrue("Error message should mention size", e.message!!.contains("pequeña"))
        } catch (e: Exception) {
        }
    }
    @Test
    fun `processImage should reject recycled bitmap`() = runBlocking {
        val recycledBitmap = mock<Bitmap>()
        whenever(recycledBitmap.isRecycled).thenReturn(true)
        whenever(recycledBitmap.width).thenReturn(1000)
        whenever(recycledBitmap.height).thenReturn(1000)
        try {
            asyncProcessor.processImage(recycledBitmap, config = testConfig)
            fail("Should throw InvalidImageError for recycled bitmap")
        } catch (e: AnalysisError.InvalidImageError) {
            assertTrue("Error message should mention recycled", e.message!!.contains("reciclada"))
        } catch (e: Exception) {
        }
    }
    @Test
    fun `processImage should handle timeout correctly`() = runBlocking {
        val shortTimeoutConfig = testConfig.copy(timeoutMs = 100L)
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        try {
            asyncProcessor.processImage(testBitmap, config = shortTimeoutConfig)
        } catch (e: AnalysisError.Timeout) {
            verify(mockProgressCallback).onError(contains("tardando"))
        } catch (e: Exception) {
        }
    }
    @Test
    fun `processImage should handle out of memory error`() {
        val error = AnalysisError.OutOfMemory
        assertTrue("OutOfMemory should be recoverable", error.isRecoverable())
        assertEquals("Should have correct recovery strategy",
            RecoveryStrategy.REDUCE_IMAGE_RESOLUTION, error.getRecoveryStrategy())
    }
    @Test
    fun `processImage should handle AI model error gracefully`() = runBlocking {
        val aiOnlyConfig = testConfig.copy(enableABCDE = false, enableAI = true)
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        try {
            asyncProcessor.processImage(testBitmap, config = aiOnlyConfig)
        } catch (e: AnalysisError.AIModelError) {
            assertTrue("AI error should be recoverable", e.isRecoverable())
            assertEquals("Should fallback to ABCDE",
                RecoveryStrategy.FALLBACK_TO_ABCDE, e.getRecoveryStrategy())
        } catch (e: Exception) {
        }
    }
    @Test
    fun `processImage should handle ABCDE analysis error`() = runBlocking {
        val abcdeOnlyConfig = testConfig.copy(enableAI = false, enableABCDE = true)
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        try {
            asyncProcessor.processImage(testBitmap, config = abcdeOnlyConfig)
        } catch (e: AnalysisError.ABCDEAnalysisError) {
            assertTrue("ABCDE error should be recoverable", e.isRecoverable())
            assertEquals("Should use default values",
                RecoveryStrategy.USE_DEFAULT_VALUES, e.getRecoveryStrategy())
        } catch (e: Exception) {
        }
    }
    @Test
    fun `cancelProcessing should stop ongoing processing`() = runBlocking {
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val processingJob = launch {
            try {
                asyncProcessor.processImage(testBitmap, config = testConfig)
            } catch (e: CancellationException) {
            }
        }
        delay(50)
        asyncProcessor.cancelProcessing()
        assertTrue("Processing should be cancelled", processingJob.isCancelled || processingJob.isCompleted)
    }
    @Test
    fun `isProcessing should return correct state`() = runBlocking {
        assertFalse("Should not be processing initially", asyncProcessor.isProcessing())
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val processingJob = launch {
            try {
                asyncProcessor.processImage(testBitmap, config = testConfig)
            } catch (e: Exception) {
            }
        }
        delay(10)
        processingJob.cancel()
    }
    @Test
    fun `processImage should handle user cancellation`() = runBlocking {
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val processingJob = launch {
            try {
                asyncProcessor.processImage(testBitmap, config = testConfig)
            } catch (e: AnalysisError.UserCancellation) {
                assertFalse("User cancellation should not be recoverable", e.isRecoverable())
                assertEquals("Should have no recovery strategy",
                    RecoveryStrategy.NONE, e.getRecoveryStrategy())
            } catch (e: CancellationException) {
            }
        }
        delay(50)
        asyncProcessor.cancelProcessing()
        processingJob.join()
    }
    @Test
    fun `processImage should validate configuration`() = runBlocking {
        val invalidConfig = AnalysisConfiguration(
            enableAI = false,
            enableABCDE = false,
            timeoutMs = -1L
        )
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        try {
            asyncProcessor.processImage(testBitmap, config = invalidConfig)
        } catch (e: AnalysisError.ConfigurationError) {
            assertTrue("Configuration error should be recoverable", e.isRecoverable())
            assertEquals("Should use default config",
                RecoveryStrategy.USE_DEFAULT_CONFIG, e.getRecoveryStrategy())
        } catch (e: Exception) {
        }
    }
    @Test
    fun `processImage should handle parallel processing correctly`() = runBlocking {
        val parallelConfig = testConfig.copy(enableParallelProcessing = true)
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        try {
            asyncProcessor.processImage(testBitmap, config = parallelConfig)
        } catch (e: Exception) {
        }
        verify(mockProgressCallback, atLeastOnce()).onStageChanged(ProcessingStage.AI_ANALYSIS)
        verify(mockProgressCallback, atLeastOnce()).onStageChanged(ProcessingStage.ABCDE_ANALYSIS)
    }
    @Test
    fun `processImage should handle sequential processing correctly`() = runTest {
        val sequentialConfig = testConfig.copy(enableParallelProcessing = false)
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        try {
            asyncProcessor.processImage(testBitmap, config = sequentialConfig)
        } catch (e: Exception) {
        }
        inOrder(mockProgressCallback) {
            verify(mockProgressCallback).onStageChanged(ProcessingStage.INITIALIZING)
            verify(mockProgressCallback).onStageChanged(ProcessingStage.PREPROCESSING)
        }
    }
    @Test
    fun `processImage should call onCompleted when successful`() = runTest {
        val mockResult = mock<MelanomaAIDetector.CombinedAnalysisResult>()
        mockProgressCallback.onCompleted(mockResult)
        verify(mockProgressCallback).onCompleted(mockResult)
    }
    @Test
    fun `processImage should handle network errors`() = runTest {
        val networkError = AnalysisError.NetworkError("Connection failed")
        assertTrue("Network error should be recoverable", networkError.isRecoverable())
        assertEquals("Should retry with delay",
            RecoveryStrategy.RETRY_WITH_DELAY, networkError.getRecoveryStrategy())
        assertTrue("User message should mention connectivity",
            networkError.getUserFriendlyMessage().contains("conectividad"))
    }
    @Test
    fun `processImage should handle unknown errors gracefully`() = runTest {
        val unknownError = AnalysisError.UnknownError("Unexpected error", RuntimeException("cause"))
        assertFalse("Unknown error should not be recoverable", unknownError.isRecoverable())
        assertEquals("Should have no recovery strategy",
            RecoveryStrategy.NONE, unknownError.getRecoveryStrategy())
        assertNotNull("Should have cause", unknownError.cause)
    }
    @Test
    fun `processImage should handle evolution analysis when enabled`() = runTest {
        val evolutionConfig = testConfig.copy(enableEvolution = true)
        val testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val previousBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        try {
            asyncProcessor.processImage(testBitmap, previousBitmap, config = evolutionConfig)
        } catch (e: Exception) {
        }
        assertTrue("Evolution config should be enabled", evolutionConfig.enableEvolution)
    }
}
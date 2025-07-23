package es.monsteraltech.skincare_tfm.analysis

import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * Tests unitarios para ErrorRecoveryManager
 */
class ErrorRecoveryManagerTest {

    @Mock
    private lateinit var mockBitmap: Bitmap

    private lateinit var recoveryManager: ErrorRecoveryManager
    private lateinit var defaultConfig: AnalysisConfiguration

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        recoveryManager = ErrorRecoveryManager()
        defaultConfig = AnalysisConfiguration.default()
        
        // Configurar mock bitmap
        `when`(mockBitmap.width).thenReturn(1024)
        `when`(mockBitmap.height).thenReturn(1024)
        `when`(mockBitmap.isRecycled).thenReturn(false)
    }

    @Test
    fun `should handle timeout error with extended timeout`() = runBlocking {
        val error = AnalysisError.Timeout
        
        assertTrue(recoveryManager.canHandle(error))
        
        val result = recoveryManager.attemptRecovery(error, mockBitmap, defaultConfig, 1)
        
        assertNotNull(result)
        assertEquals(RecoveryStrategy.EXTEND_TIMEOUT, result!!.strategy)
        assertTrue(result.config.timeoutMs > defaultConfig.timeoutMs)
        assertTrue(result.config.aiTimeoutMs > defaultConfig.aiTimeoutMs)
        assertTrue(result.config.abcdeTimeoutMs > defaultConfig.abcdeTimeoutMs)
        assertTrue(result.message.contains("tiempo"))
    }

    @Test
    fun `should handle out of memory error with reduced resolution`() = runBlocking {
        val error = AnalysisError.OutOfMemory
        val scaledBitmap = mock(Bitmap::class.java)
        
        // Mock para Bitmap.createScaledBitmap
        mockStatic(Bitmap::class.java).use { mockedBitmap ->
            mockedBitmap.`when`<Bitmap> { 
                Bitmap.createScaledBitmap(any(), anyInt(), anyInt(), anyBoolean()) 
            }.thenReturn(scaledBitmap)
            
            assertTrue(recoveryManager.canHandle(error))
            
            val result = recoveryManager.attemptRecovery(error, mockBitmap, defaultConfig, 1)
            
            assertNotNull(result)
            assertEquals(RecoveryStrategy.REDUCE_IMAGE_RESOLUTION, result!!.strategy)
            assertEquals(scaledBitmap, result.bitmap)
            assertTrue(result.config.maxImageResolution < defaultConfig.maxImageResolution)
            assertTrue(result.message.contains("memoria"))
        }
    }

    @Test
    fun `should handle AI model error with fallback to ABCDE`() = runBlocking {
        val error = AnalysisError.AIModelError("Model failed")
        
        assertTrue(recoveryManager.canHandle(error))
        
        val result = recoveryManager.attemptRecovery(error, mockBitmap, defaultConfig, 1)
        
        assertNotNull(result)
        assertEquals(RecoveryStrategy.FALLBACK_TO_ABCDE, result!!.strategy)
        assertFalse(result.config.enableAI)
        assertTrue(result.config.enableABCDE)
        assertFalse(result.config.enableParallelProcessing)
        assertTrue(result.message.contains("ABCDE"))
    }

    @Test
    fun `should handle network error with retry delay`() = runBlocking {
        val error = AnalysisError.NetworkError("Connection failed")
        val startTime = System.currentTimeMillis()
        
        assertTrue(recoveryManager.canHandle(error))
        
        val result = recoveryManager.attemptRecovery(error, mockBitmap, defaultConfig, 1)
        
        val endTime = System.currentTimeMillis()
        val elapsedTime = endTime - startTime
        
        assertNotNull(result)
        assertEquals(RecoveryStrategy.RETRY_WITH_DELAY, result!!.strategy)
        assertEquals(mockBitmap, result.bitmap)
        assertEquals(defaultConfig, result.config)
        assertTrue(result.message.contains("Reintentando"))
        assertTrue("Should have delayed for at least 1 second", elapsedTime >= 1000)
    }

    @Test
    fun `should handle ABCDE analysis error with default values`() = runBlocking {
        val error = AnalysisError.ABCDEAnalysisError("OpenCV failed")
        
        assertTrue(recoveryManager.canHandle(error))
        
        val result = recoveryManager.attemptRecovery(error, mockBitmap, defaultConfig, 1)
        
        assertNotNull(result)
        assertEquals(RecoveryStrategy.USE_DEFAULT_VALUES, result!!.strategy)
        assertEquals(mockBitmap, result.bitmap)
        assertEquals(defaultConfig, result.config)
        assertTrue(result.message.contains("defecto"))
    }

    @Test
    fun `should handle configuration error with default config`() = runBlocking {
        val error = AnalysisError.ConfigurationError("Invalid config")
        
        assertTrue(recoveryManager.canHandle(error))
        
        val result = recoveryManager.attemptRecovery(error, mockBitmap, defaultConfig, 1)
        
        assertNotNull(result)
        assertEquals(RecoveryStrategy.USE_DEFAULT_CONFIG, result!!.strategy)
        assertEquals(mockBitmap, result.bitmap)
        assertEquals(AnalysisConfiguration.default(), result.config)
        assertTrue(result.message.contains("configuración"))
    }

    @Test
    fun `should not handle non-recoverable errors`() = runBlocking {
        val nonRecoverableErrors = listOf(
            AnalysisError.ImageProcessingError("test"),
            AnalysisError.InvalidImageError("test"),
            AnalysisError.UserCancellation,
            AnalysisError.UnknownError("test")
        )
        
        nonRecoverableErrors.forEach { error ->
            assertFalse("Should not handle ${error.javaClass.simpleName}", 
                recoveryManager.canHandle(error))
            
            val result = recoveryManager.attemptRecovery(error, mockBitmap, defaultConfig, 1)
            assertNull("Should return null for ${error.javaClass.simpleName}", result)
        }
    }

    @Test
    fun `should return null after max retry attempts`() = runBlocking {
        val error = AnalysisError.Timeout
        
        val result = recoveryManager.attemptRecovery(error, mockBitmap, defaultConfig, 4) // > MAX_RETRY_ATTEMPTS
        
        assertNull(result)
    }

    @Test
    fun `should return null for memory recovery with null bitmap`() = runBlocking {
        val error = AnalysisError.OutOfMemory
        
        val result = recoveryManager.attemptRecovery(error, null, defaultConfig, 1)
        
        assertNull(result)
    }

    @Test
    fun `should return null for memory recovery when minimum resolution reached`() = runBlocking {
        val error = AnalysisError.OutOfMemory
        val smallBitmap = mock(Bitmap::class.java)
        `when`(smallBitmap.width).thenReturn(100)
        `when`(smallBitmap.height).thenReturn(100)
        `when`(smallBitmap.isRecycled).thenReturn(false)
        
        val result = recoveryManager.attemptRecovery(error, smallBitmap, defaultConfig, 3)
        
        assertNull(result)
    }

    @Test
    fun `should provide correct recovery info`() {
        val recoverableError = AnalysisError.Timeout
        val nonRecoverableError = AnalysisError.UserCancellation
        
        val recoverableInfo = recoveryManager.getRecoveryInfo(recoverableError)
        assertTrue(recoverableInfo.canRecover)
        assertEquals(RecoveryStrategy.EXTEND_TIMEOUT, recoverableInfo.strategy)
        assertNotNull(recoverableInfo.userMessage)
        assertNotNull(recoverableInfo.technicalMessage)
        
        val nonRecoverableInfo = recoveryManager.getRecoveryInfo(nonRecoverableError)
        assertFalse(nonRecoverableInfo.canRecover)
        assertEquals(RecoveryStrategy.NONE, nonRecoverableInfo.strategy)
        assertNotNull(nonRecoverableInfo.userMessage)
        assertNotNull(nonRecoverableInfo.technicalMessage)
    }

    @Test
    fun `timeout multiplier should increase with attempts`() = runBlocking {
        val error = AnalysisError.Timeout
        
        val result1 = recoveryManager.attemptRecovery(error, mockBitmap, defaultConfig, 1)
        val result2 = recoveryManager.attemptRecovery(error, mockBitmap, defaultConfig, 2)
        
        assertNotNull(result1)
        assertNotNull(result2)
        assertTrue("Second attempt should have longer timeout", 
            result2!!.config.timeoutMs > result1!!.config.timeoutMs)
    }

    @Test
    fun `memory reduction should be more aggressive with higher attempts`() = runBlocking {
        val error = AnalysisError.OutOfMemory
        val scaledBitmap1 = mock(Bitmap::class.java)
        val scaledBitmap2 = mock(Bitmap::class.java)
        
        mockStatic(Bitmap::class.java).use { mockedBitmap ->
            mockedBitmap.`when`<Bitmap> { 
                Bitmap.createScaledBitmap(any(), anyInt(), anyInt(), anyBoolean()) 
            }.thenReturn(scaledBitmap1, scaledBitmap2)
            
            val result1 = recoveryManager.attemptRecovery(error, mockBitmap, defaultConfig, 1)
            val result2 = recoveryManager.attemptRecovery(error, mockBitmap, defaultConfig, 2)
            
            assertNotNull(result1)
            assertNotNull(result2)
            assertTrue("Second attempt should have lower resolution", 
                result2!!.config.maxImageResolution < result1!!.config.maxImageResolution)
            assertTrue("Second attempt should have lower compression quality", 
                result2.config.compressionQuality < result1.config.compressionQuality)
        }
    }
}
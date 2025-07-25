package es.monsteraltech.skincare_tfm.camera.guidance

import android.graphics.Bitmap
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

/**
 * Tests de rendimiento para ImagePreprocessor
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ImagePreprocessorPerformanceTest {
    
    private lateinit var imagePreprocessor: ImagePreprocessor
    
    @Before
    fun setUp() {
        imagePreprocessor = ImagePreprocessor()
    }
    
    @Test
    fun `preprocessing should complete within reasonable time for small images`() = runTest {
        // Given
        val smallBitmap = createTestBitmap(100, 100)
        val maxProcessingTime = 1000L // 1 segundo
        
        // When
        val result = imagePreprocessor.preprocessImage(smallBitmap)
        
        // Then
        assertTrue(result.processingTime < maxProcessingTime, 
            "Processing took ${result.processingTime}ms, expected < ${maxProcessingTime}ms")
        assertTrue(result.isSuccessful())
    }
    
    @Test
    fun `preprocessing should complete within reasonable time for medium images`() = runTest {
        // Given
        val mediumBitmap = createTestBitmap(500, 500)
        val maxProcessingTime = 3000L // 3 segundos
        
        // When
        val result = imagePreprocessor.preprocessImage(mediumBitmap)
        
        // Then
        assertTrue(result.processingTime < maxProcessingTime,
            "Processing took ${result.processingTime}ms, expected < ${maxProcessingTime}ms")
        assertTrue(result.isSuccessful())
    }
    
    @Test
    fun `preprocessing should complete within reasonable time for large images`() = runTest {
        // Given
        val largeBitmap = createTestBitmap(1000, 1000)
        val maxProcessingTime = 8000L // 8 segundos
        
        // When
        val result = imagePreprocessor.preprocessImage(largeBitmap)
        
        // Then
        assertTrue(result.processingTime < maxProcessingTime,
            "Processing took ${result.processingTime}ms, expected < ${maxProcessingTime}ms")
        assertTrue(result.isSuccessful())
    }
    
    @Test
    fun `dermatology preprocessing should be optimized for performance`() = runTest {
        // Given
        val testBitmap = createTestBitmap(800, 600) // Tamaño típico de cámara
        val maxProcessingTime = 5000L // 5 segundos
        
        // When
        val result = imagePreprocessor.preprocessForDermatologyAnalysis(testBitmap)
        
        // Then
        assertTrue(result.processingTime < maxProcessingTime,
            "Dermatology processing took ${result.processingTime}ms, expected < ${maxProcessingTime}ms")
        assertTrue(result.isSuccessful())
        assertEquals(4, result.appliedFilters.size)
    }
    
    @Test
    fun `low light preprocessing should be faster than full processing`() = runTest {
        // Given
        val testBitmap = createTestBitmap(400, 400)
        
        // When
        val fullResult = imagePreprocessor.preprocessImage(testBitmap)
        val lowLightResult = imagePreprocessor.preprocessForLowLight(testBitmap)
        
        // Then
        assertTrue(lowLightResult.processingTime <= fullResult.processingTime,
            "Low light processing (${lowLightResult.processingTime}ms) should be <= full processing (${fullResult.processingTime}ms)")
        assertTrue(lowLightResult.appliedFilters.size < fullResult.appliedFilters.size)
    }
    
    @Test
    fun `overexposure preprocessing should be faster than full processing`() = runTest {
        // Given
        val testBitmap = createTestBitmap(400, 400)
        
        // When
        val fullResult = imagePreprocessor.preprocessImage(testBitmap)
        val overexposureResult = imagePreprocessor.preprocessForOverexposure(testBitmap)
        
        // Then
        assertTrue(overexposureResult.processingTime <= fullResult.processingTime,
            "Overexposure processing (${overexposureResult.processingTime}ms) should be <= full processing (${fullResult.processingTime}ms)")
        assertTrue(overexposureResult.appliedFilters.size < fullResult.appliedFilters.size)
    }
    
    @Test
    fun `quality analysis should complete quickly`() = runTest {
        // Given
        val originalBitmap = createTestBitmap(300, 300)
        val processedResult = imagePreprocessor.preprocessImage(originalBitmap)
        val maxAnalysisTime = 500L // 0.5 segundos
        
        // When
        val startTime = System.currentTimeMillis()
        val improvement = imagePreprocessor.analyzeQualityImprovement(
            originalBitmap, 
            processedResult.processedBitmap
        )
        val analysisTime = System.currentTimeMillis() - startTime
        
        // Then
        assertTrue(analysisTime < maxAnalysisTime,
            "Quality analysis took ${analysisTime}ms, expected < ${maxAnalysisTime}ms")
        assertTrue(improvement >= -1f && improvement <= 1f)
    }
    
    @Test
    fun `processing time should scale reasonably with image size`() = runTest {
        // Given
        val smallBitmap = createTestBitmap(100, 100)
        val largeBitmap = createTestBitmap(400, 400) // 16x más píxeles
        
        // When
        val smallResult = imagePreprocessor.preprocessImage(smallBitmap)
        val largeResult = imagePreprocessor.preprocessImage(largeBitmap)
        
        // Then
        val timeRatio = largeResult.processingTime.toDouble() / smallResult.processingTime
        assertTrue(timeRatio < 50, // No debería ser más de 50x más lento
            "Time ratio ${timeRatio} is too high (large: ${largeResult.processingTime}ms, small: ${smallResult.processingTime}ms)")
        assertTrue(timeRatio > 1, "Large image should take more time than small image")
    }
    
    @Test
    fun `multiple consecutive processings should maintain performance`() = runTest {
        // Given
        val testBitmap = createTestBitmap(300, 300)
        val iterations = 5
        val processingTimes = mutableListOf<Long>()
        
        // When
        repeat(iterations) {
            val result = imagePreprocessor.preprocessImage(testBitmap)
            processingTimes.add(result.processingTime)
            assertTrue(result.isSuccessful())
        }
        
        // Then
        val avgTime = processingTimes.average()
        val maxTime = processingTimes.maxOrNull() ?: 0L
        val minTime = processingTimes.minOrNull() ?: 0L
        
        // La variación no debería ser excesiva (factor de 3)
        assertTrue(maxTime <= minTime * 3,
            "Performance variation too high: min=${minTime}ms, max=${maxTime}ms, avg=${avgTime}ms")
    }
    
    @Test
    fun `memory usage should be reasonable for typical image sizes`() = runTest {
        // Given
        val typicalBitmap = createTestBitmap(800, 600) // Tamaño típico de cámara móvil
        
        // When
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val result = imagePreprocessor.preprocessImage(typicalBitmap)
        System.gc() // Sugerir recolección de basura
        Thread.sleep(100) // Dar tiempo para GC
        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        // Then
        assertTrue(result.isSuccessful())
        val memoryIncrease = finalMemory - initialMemory
        val maxAcceptableIncrease = 50 * 1024 * 1024 // 50MB
        
        assertTrue(memoryIncrease < maxAcceptableIncrease,
            "Memory increase ${memoryIncrease / (1024 * 1024)}MB exceeds limit ${maxAcceptableIncrease / (1024 * 1024)}MB")
    }
    
    @Test
    fun `disabled filters should improve performance significantly`() = runTest {
        // Given
        val testBitmap = createTestBitmap(400, 400)
        val fullConfig = ImagePreprocessor.PreprocessingConfig()
        val minimalConfig = ImagePreprocessor.PreprocessingConfig(
            enableIlluminationNormalization = true,
            enableContrastEnhancement = false,
            enableNoiseReduction = false,
            enableSharpening = false
        )
        
        // When
        val fullResult = imagePreprocessor.preprocessImage(testBitmap, fullConfig)
        val minimalResult = imagePreprocessor.preprocessImage(testBitmap, minimalConfig)
        
        // Then
        assertTrue(minimalResult.processingTime < fullResult.processingTime,
            "Minimal processing (${minimalResult.processingTime}ms) should be faster than full processing (${fullResult.processingTime}ms)")
        assertEquals(1, minimalResult.appliedFilters.size)
        assertEquals(4, fullResult.appliedFilters.size)
    }
    
    private fun createTestBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Crear patrón de prueba con variación de intensidad
        for (x in 0 until width) {
            for (y in 0 until height) {
                val intensity = ((x + y) % 256)
                val color = (0xFF000000 or (intensity shl 16) or (intensity shl 8) or intensity).toInt()
                bitmap.setPixel(x, y, color)
            }
        }
        
        return bitmap
    }
}
package es.monsteraltech.skincare_tfm.camera.guidance

import android.graphics.Bitmap
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

/**
 * Tests unitarios para ImagePreprocessor
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ImagePreprocessorTest {
    
    private lateinit var imagePreprocessor: ImagePreprocessor
    private lateinit var testBitmap: Bitmap
    
    @Before
    fun setUp() {
        imagePreprocessor = ImagePreprocessor()
        
        // Crear bitmap de prueba
        testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        
        // Llenar con patrón de prueba
        for (x in 0 until 100) {
            for (y in 0 until 100) {
                val color = if ((x + y) % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                testBitmap.setPixel(x, y, color)
            }
        }
    }
    
    @Test
    fun `preprocessImage should return successful result with default config`() = runTest {
        // Given
        val config = ImagePreprocessor.PreprocessingConfig()
        
        // When
        val result = imagePreprocessor.preprocessImage(testBitmap, config)
        
        // Then
        assertTrue(result.isSuccessful())
        assertNotNull(result.processedBitmap)
        assertTrue(result.appliedFilters.isNotEmpty())
        assertTrue(result.processingTime > 0)
        assertEquals(testBitmap, result.originalBitmap)
    }
    
    @Test
    fun `preprocessImage should apply all filters when enabled`() = runTest {
        // Given
        val config = ImagePreprocessor.PreprocessingConfig(
            enableIlluminationNormalization = true,
            enableContrastEnhancement = true,
            enableNoiseReduction = true,
            enableSharpening = true
        )
        
        // When
        val result = imagePreprocessor.preprocessImage(testBitmap, config)
        
        // Then
        assertEquals(4, result.appliedFilters.size)
        assertTrue(result.appliedFilters.contains("Normalización de iluminación"))
        assertTrue(result.appliedFilters.contains("Mejora de contraste"))
        assertTrue(result.appliedFilters.contains("Reducción de ruido"))
        assertTrue(result.appliedFilters.contains("Enfoque"))
    }
    
    @Test
    fun `preprocessImage should skip disabled filters`() = runTest {
        // Given
        val config = ImagePreprocessor.PreprocessingConfig(
            enableIlluminationNormalization = true,
            enableContrastEnhancement = false,
            enableNoiseReduction = true,
            enableSharpening = false
        )
        
        // When
        val result = imagePreprocessor.preprocessImage(testBitmap, config)
        
        // Then
        assertEquals(2, result.appliedFilters.size)
        assertTrue(result.appliedFilters.contains("Normalización de iluminación"))
        assertTrue(result.appliedFilters.contains("Reducción de ruido"))
        assertFalse(result.appliedFilters.contains("Mejora de contraste"))
        assertFalse(result.appliedFilters.contains("Enfoque"))
    }
    
    @Test
    fun `preprocessForDermatologyAnalysis should use optimized config`() = runTest {
        // When
        val result = imagePreprocessor.preprocessForDermatologyAnalysis(testBitmap)
        
        // Then
        assertTrue(result.isSuccessful())
        assertEquals(4, result.appliedFilters.size) // Todos los filtros habilitados
        assertTrue(result.processingTime > 0)
    }
    
    @Test
    fun `preprocessForLowLight should disable sharpening`() = runTest {
        // When
        val result = imagePreprocessor.preprocessForLowLight(testBitmap)
        
        // Then
        assertTrue(result.isSuccessful())
        assertEquals(3, result.appliedFilters.size) // Sin enfoque
        assertFalse(result.appliedFilters.contains("Enfoque"))
        assertTrue(result.appliedFilters.contains("Normalización de iluminación"))
        assertTrue(result.appliedFilters.contains("Mejora de contraste"))
        assertTrue(result.appliedFilters.contains("Reducción de ruido"))
    }
    
    @Test
    fun `preprocessForOverexposure should disable contrast enhancement`() = runTest {
        // When
        val result = imagePreprocessor.preprocessForOverexposure(testBitmap)
        
        // Then
        assertTrue(result.isSuccessful())
        assertEquals(2, result.appliedFilters.size) // Sin mejora de contraste ni reducción de ruido
        assertFalse(result.appliedFilters.contains("Mejora de contraste"))
        assertFalse(result.appliedFilters.contains("Reducción de ruido"))
        assertTrue(result.appliedFilters.contains("Normalización de iluminación"))
        assertTrue(result.appliedFilters.contains("Enfoque"))
    }
    
    @Test
    fun `analyzeQualityImprovement should return improvement metric`() = runTest {
        // Given
        val processedResult = imagePreprocessor.preprocessImage(testBitmap)
        
        // When
        val improvement = imagePreprocessor.analyzeQualityImprovement(
            testBitmap, 
            processedResult.processedBitmap
        )
        
        // Then
        assertTrue(improvement >= -1f && improvement <= 1f)
    }
    
    @Test
    fun `PreprocessingResult should provide correct summary`() = runTest {
        // Given
        val result = imagePreprocessor.preprocessImage(testBitmap)
        
        // When
        val summary = result.getProcessingSummary()
        
        // Then
        assertTrue(summary.contains("Procesamiento completado"))
        assertTrue(summary.contains("ms"))
        assertTrue(summary.contains("Filtros aplicados"))
    }
    
    @Test
    fun `PreprocessingResult isSuccessful should return true when filters applied`() {
        // Given
        val result = ImagePreprocessor.PreprocessingResult(
            originalBitmap = testBitmap,
            processedBitmap = testBitmap,
            appliedFilters = listOf("Test filter"),
            processingTime = 100L
        )
        
        // When & Then
        assertTrue(result.isSuccessful())
    }
    
    @Test
    fun `PreprocessingResult isSuccessful should return false when no filters applied`() {
        // Given
        val result = ImagePreprocessor.PreprocessingResult(
            originalBitmap = testBitmap,
            processedBitmap = testBitmap,
            appliedFilters = emptyList(),
            processingTime = 100L
        )
        
        // When & Then
        assertFalse(result.isSuccessful())
    }
    
    @Test
    fun `PreprocessingConfig should have correct default values`() {
        // Given
        val config = ImagePreprocessor.PreprocessingConfig()
        
        // Then
        assertTrue(config.enableIlluminationNormalization)
        assertTrue(config.enableContrastEnhancement)
        assertTrue(config.enableNoiseReduction)
        assertTrue(config.enableSharpening)
        assertEquals(2.0, config.claheClipLimit)
        assertEquals(0.5, config.sharpeningStrength)
    }
    
    @Test
    fun `preprocessImage should handle small images`() = runTest {
        // Given
        val smallBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        
        // When
        val result = imagePreprocessor.preprocessImage(smallBitmap)
        
        // Then
        assertTrue(result.isSuccessful())
        assertEquals(10, result.processedBitmap.width)
        assertEquals(10, result.processedBitmap.height)
    }
    
    @Test
    fun `preprocessImage should handle large images`() = runTest {
        // Given
        val largeBitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        
        // When
        val result = imagePreprocessor.preprocessImage(largeBitmap)
        
        // Then
        assertTrue(result.isSuccessful())
        assertEquals(1000, result.processedBitmap.width)
        assertEquals(1000, result.processedBitmap.height)
        assertTrue(result.processingTime > 0)
    }
    
    @Test
    fun `preprocessImage should preserve bitmap dimensions`() = runTest {
        // Given
        val config = ImagePreprocessor.PreprocessingConfig()
        
        // When
        val result = imagePreprocessor.preprocessImage(testBitmap, config)
        
        // Then
        assertEquals(testBitmap.width, result.processedBitmap.width)
        assertEquals(testBitmap.height, result.processedBitmap.height)
    }
    
    @Test
    fun `preprocessImage should measure processing time accurately`() = runTest {
        // When
        val result = imagePreprocessor.preprocessImage(testBitmap)
        
        // Then
        assertTrue(result.processingTime > 0)
        assertTrue(result.processingTime < 10000) // Debería ser menos de 10 segundos
    }
}
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
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ImagePreprocessorTest {
    private lateinit var imagePreprocessor: ImagePreprocessor
    private lateinit var testBitmap: Bitmap
    @Before
    fun setUp() {
        imagePreprocessor = ImagePreprocessor()
        testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (x in 0 until 100) {
            for (y in 0 until 100) {
                val color = if ((x + y) % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                testBitmap.setPixel(x, y, color)
            }
        }
    }
    @Test
    fun `preprocessImage should return successful result with default config`() = runTest {
        val config = ImagePreprocessor.PreprocessingConfig()
        val result = imagePreprocessor.preprocessImage(testBitmap, config)
        assertTrue(result.isSuccessful())
        assertNotNull(result.processedBitmap)
        assertTrue(result.appliedFilters.isNotEmpty())
        assertTrue(result.processingTime > 0)
        assertEquals(testBitmap, result.originalBitmap)
    }
    @Test
    fun `preprocessImage should apply all filters when enabled`() = runTest {
        val config = ImagePreprocessor.PreprocessingConfig(
            enableIlluminationNormalization = true,
            enableContrastEnhancement = true,
            enableNoiseReduction = true,
            enableSharpening = true
        )
        val result = imagePreprocessor.preprocessImage(testBitmap, config)
        assertEquals(4, result.appliedFilters.size)
        assertTrue(result.appliedFilters.contains("Normalización de iluminación"))
        assertTrue(result.appliedFilters.contains("Mejora de contraste"))
        assertTrue(result.appliedFilters.contains("Reducción de ruido"))
        assertTrue(result.appliedFilters.contains("Enfoque"))
    }
    @Test
    fun `preprocessImage should skip disabled filters`() = runTest {
        val config = ImagePreprocessor.PreprocessingConfig(
            enableIlluminationNormalization = true,
            enableContrastEnhancement = false,
            enableNoiseReduction = true,
            enableSharpening = false
        )
        val result = imagePreprocessor.preprocessImage(testBitmap, config)
        assertEquals(2, result.appliedFilters.size)
        assertTrue(result.appliedFilters.contains("Normalización de iluminación"))
        assertTrue(result.appliedFilters.contains("Reducción de ruido"))
        assertFalse(result.appliedFilters.contains("Mejora de contraste"))
        assertFalse(result.appliedFilters.contains("Enfoque"))
    }
    @Test
    fun `preprocessForDermatologyAnalysis should use optimized config`() = runTest {
        val result = imagePreprocessor.preprocessForDermatologyAnalysis(testBitmap)
        assertTrue(result.isSuccessful())
        assertEquals(4, result.appliedFilters.size)
        assertTrue(result.processingTime > 0)
    }
    @Test
    fun `preprocessForLowLight should disable sharpening`() = runTest {
        val result = imagePreprocessor.preprocessForLowLight(testBitmap)
        assertTrue(result.isSuccessful())
        assertEquals(3, result.appliedFilters.size)
        assertFalse(result.appliedFilters.contains("Enfoque"))
        assertTrue(result.appliedFilters.contains("Normalización de iluminación"))
        assertTrue(result.appliedFilters.contains("Mejora de contraste"))
        assertTrue(result.appliedFilters.contains("Reducción de ruido"))
    }
    @Test
    fun `preprocessForOverexposure should disable contrast enhancement`() = runTest {
        val result = imagePreprocessor.preprocessForOverexposure(testBitmap)
        assertTrue(result.isSuccessful())
        assertEquals(2, result.appliedFilters.size)
        assertFalse(result.appliedFilters.contains("Mejora de contraste"))
        assertFalse(result.appliedFilters.contains("Reducción de ruido"))
        assertTrue(result.appliedFilters.contains("Normalización de iluminación"))
        assertTrue(result.appliedFilters.contains("Enfoque"))
    }
    @Test
    fun `analyzeQualityImprovement should return improvement metric`() = runTest {
        val processedResult = imagePreprocessor.preprocessImage(testBitmap)
        val improvement = imagePreprocessor.analyzeQualityImprovement(
            testBitmap,
            processedResult.processedBitmap
        )
        assertTrue(improvement >= -1f && improvement <= 1f)
    }
    @Test
    fun `PreprocessingResult should provide correct summary`() = runTest {
        val result = imagePreprocessor.preprocessImage(testBitmap)
        val summary = result.getProcessingSummary()
        assertTrue(summary.contains("Procesamiento completado"))
        assertTrue(summary.contains("ms"))
        assertTrue(summary.contains("Filtros aplicados"))
    }
    @Test
    fun `PreprocessingResult isSuccessful should return true when filters applied`() {
        val result = ImagePreprocessor.PreprocessingResult(
            originalBitmap = testBitmap,
            processedBitmap = testBitmap,
            appliedFilters = listOf("Test filter"),
            processingTime = 100L
        )
        assertTrue(result.isSuccessful())
    }
    @Test
    fun `PreprocessingResult isSuccessful should return false when no filters applied`() {
        val result = ImagePreprocessor.PreprocessingResult(
            originalBitmap = testBitmap,
            processedBitmap = testBitmap,
            appliedFilters = emptyList(),
            processingTime = 100L
        )
        assertFalse(result.isSuccessful())
    }
    @Test
    fun `PreprocessingConfig should have correct default values`() {
        val config = ImagePreprocessor.PreprocessingConfig()
        assertTrue(config.enableIlluminationNormalization)
        assertTrue(config.enableContrastEnhancement)
        assertTrue(config.enableNoiseReduction)
        assertTrue(config.enableSharpening)
        assertEquals(2.0, config.claheClipLimit)
        assertEquals(0.5, config.sharpeningStrength)
    }
    @Test
    fun `preprocessImage should handle small images`() = runTest {
        val smallBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val result = imagePreprocessor.preprocessImage(smallBitmap)
        assertTrue(result.isSuccessful())
        assertEquals(10, result.processedBitmap.width)
        assertEquals(10, result.processedBitmap.height)
    }
    @Test
    fun `preprocessImage should handle large images`() = runTest {
        val largeBitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val result = imagePreprocessor.preprocessImage(largeBitmap)
        assertTrue(result.isSuccessful())
        assertEquals(1000, result.processedBitmap.width)
        assertEquals(1000, result.processedBitmap.height)
        assertTrue(result.processingTime > 0)
    }
    @Test
    fun `preprocessImage should preserve bitmap dimensions`() = runTest {
        val config = ImagePreprocessor.PreprocessingConfig()
        val result = imagePreprocessor.preprocessImage(testBitmap, config)
        assertEquals(testBitmap.width, result.processedBitmap.width)
        assertEquals(testBitmap.height, result.processedBitmap.height)
    }
    @Test
    fun `preprocessImage should measure processing time accurately`() = runTest {
        val result = imagePreprocessor.preprocessImage(testBitmap)
        assertTrue(result.processingTime > 0)
        assertTrue(result.processingTime < 10000)
    }
}
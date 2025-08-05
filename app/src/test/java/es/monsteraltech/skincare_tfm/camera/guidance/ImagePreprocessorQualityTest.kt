package es.monsteraltech.skincare_tfm.camera.guidance
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.*
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ImagePreprocessorQualityTest {
    private lateinit var imagePreprocessor: ImagePreprocessor
    @Before
    fun setUp() {
        imagePreprocessor = ImagePreprocessor()
    }
    @Test
    fun `preprocessing should improve low contrast images`() = runTest {
        val lowContrastBitmap = createLowContrastBitmap(200, 200)
        val result = imagePreprocessor.preprocessImage(lowContrastBitmap)
        val improvement = imagePreprocessor.analyzeQualityImprovement(
            lowContrastBitmap,
            result.processedBitmap
        )
        assertTrue(result.isSuccessful())
        assertTrue(improvement > 0, "Expected positive improvement, got $improvement")
        assertTrue(result.appliedFilters.contains("Mejora de contraste"))
    }
    @Test
    fun `preprocessing should handle dark images appropriately`() = runTest {
        val darkBitmap = createDarkBitmap(200, 200)
        val result = imagePreprocessor.preprocessForLowLight(darkBitmap)
        assertTrue(result.isSuccessful())
        assertTrue(result.appliedFilters.contains("Normalización de iluminación"))
        assertFalse(result.appliedFilters.contains("Enfoque"))
        val originalBrightness = calculateAverageBrightness(darkBitmap)
        val processedBrightness = calculateAverageBrightness(result.processedBitmap)
        assertTrue(processedBrightness > originalBrightness,
            "Processed image should be brighter: original=$originalBrightness, processed=$processedBrightness")
    }
    @Test
    fun `preprocessing should handle bright images appropriately`() = runTest {
        val brightBitmap = createBrightBitmap(200, 200)
        val result = imagePreprocessor.preprocessForOverexposure(brightBitmap)
        assertTrue(result.isSuccessful())
        assertTrue(result.appliedFilters.contains("Normalización de iluminación"))
        assertFalse(result.appliedFilters.contains("Mejora de contraste"))
        val originalBrightness = calculateAverageBrightness(brightBitmap)
        val processedBrightness = calculateAverageBrightness(result.processedBitmap)
        assertTrue(processedBrightness < originalBrightness,
            "Processed image should be less bright: original=$originalBrightness, processed=$processedBrightness")
    }
    @Test
    fun `preprocessing should preserve important image details`() = runTest {
        val detailedBitmap = createDetailedBitmap(200, 200)
        val result = imagePreprocessor.preprocessImage(detailedBitmap)
        assertTrue(result.isSuccessful())
        val originalEdges = countEdgePixels(detailedBitmap)
        val processedEdges = countEdgePixels(result.processedBitmap)
        val edgePreservation = processedEdges.toDouble() / originalEdges
        assertTrue(edgePreservation > 0.8,
            "Edge preservation too low: $edgePreservation (${processedEdges}/${originalEdges})")
    }
    @Test
    fun `noise reduction should reduce noise while preserving edges`() = runTest {
        val noisyBitmap = createNoisyBitmap(200, 200)
        val config = ImagePreprocessor.PreprocessingConfig(
            enableIlluminationNormalization = false,
            enableContrastEnhancement = false,
            enableNoiseReduction = true,
            enableSharpening = false
        )
        val result = imagePreprocessor.preprocessImage(noisyBitmap, config)
        assertTrue(result.isSuccessful())
        assertTrue(result.appliedFilters.contains("Reducción de ruido"))
        val originalNoise = calculateNoiseLevel(noisyBitmap)
        val processedNoise = calculateNoiseLevel(result.processedBitmap)
        assertTrue(processedNoise < originalNoise,
            "Noise should be reduced: original=$originalNoise, processed=$processedNoise")
    }
    @Test
    fun `sharpening should improve image sharpness`() = runTest {
        val blurryBitmap = createBlurryBitmap(200, 200)
        val config = ImagePreprocessor.PreprocessingConfig(
            enableIlluminationNormalization = false,
            enableContrastEnhancement = false,
            enableNoiseReduction = false,
            enableSharpening = true
        )
        val result = imagePreprocessor.preprocessImage(blurryBitmap, config)
        assertTrue(result.isSuccessful())
        assertTrue(result.appliedFilters.contains("Enfoque"))
        val originalSharpness = calculateSharpness(blurryBitmap)
        val processedSharpness = calculateSharpness(result.processedBitmap)
        assertTrue(processedSharpness > originalSharpness,
            "Sharpness should improve: original=$originalSharpness, processed=$processedSharpness")
    }
    @Test
    fun `dermatology preprocessing should optimize for skin analysis`() = runTest {
        val skinBitmap = createSkinLikeBitmap(300, 300)
        val result = imagePreprocessor.preprocessForDermatologyAnalysis(skinBitmap)
        assertTrue(result.isSuccessful())
        assertEquals(4, result.appliedFilters.size)
        val improvement = imagePreprocessor.analyzeQualityImprovement(
            skinBitmap,
            result.processedBitmap
        )
        assertTrue(improvement >= 0, "Dermatology preprocessing should not degrade quality")
    }
    @Test
    fun `preprocessing should handle uniform images gracefully`() = runTest {
        val uniformBitmap = createUniformBitmap(100, 100, Color.GRAY)
        val result = imagePreprocessor.preprocessImage(uniformBitmap)
        assertTrue(result.isSuccessful())
        assertNotNull(result.processedBitmap)
    }
    @Test
    fun `preprocessing should handle high contrast images`() = runTest {
        val highContrastBitmap = createHighContrastBitmap(200, 200)
        val result = imagePreprocessor.preprocessImage(highContrastBitmap)
        assertTrue(result.isSuccessful())
        val originalContrast = calculateContrast(highContrastBitmap)
        val processedContrast = calculateContrast(result.processedBitmap)
        val contrastRatio = processedContrast / originalContrast
        assertTrue(contrastRatio > 0.5 && contrastRatio < 2.0,
            "Contrast ratio $contrastRatio is outside acceptable range")
    }
    @Test
    fun `quality improvement analysis should be consistent`() = runTest {
        val testBitmap = createTestBitmap(150, 150)
        val result = imagePreprocessor.preprocessImage(testBitmap)
        val improvements = mutableListOf<Float>()
        repeat(3) {
            val improvement = imagePreprocessor.analyzeQualityImprovement(
                testBitmap,
                result.processedBitmap
            )
            improvements.add(improvement)
        }
        val avgImprovement = improvements.average()
        improvements.forEach { improvement ->
            val difference = abs(improvement - avgImprovement)
            assertTrue(difference < 0.1f,
                "Quality analysis inconsistent: $improvement vs avg $avgImprovement")
        }
    }
    private fun createLowContrastBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val intensity = 100 + ((x + y) % 50)
                val color = Color.rgb(intensity, intensity, intensity)
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }
    private fun createDarkBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val intensity = ((x + y) % 80)
                val color = Color.rgb(intensity, intensity, intensity)
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }
    private fun createBrightBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val intensity = 200 + ((x + y) % 55)
                val color = Color.rgb(intensity, intensity, intensity)
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }
    private fun createDetailedBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val intensity = if ((x / 10 + y / 10) % 2 == 0) 50 else 200
                val color = Color.rgb(intensity, intensity, intensity)
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }
    private fun createNoisyBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val base = 128
                val noise = (Math.random() * 100 - 50).toInt()
                val intensity = (base + noise).coerceIn(0, 255)
                val color = Color.rgb(intensity, intensity, intensity)
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }
    private fun createBlurryBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val intensity = (128 + 100 * Math.sin(x * 0.1) * Math.cos(y * 0.1)).toInt().coerceIn(0, 255)
                val color = Color.rgb(intensity, intensity, intensity)
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }
    private fun createSkinLikeBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val r = (200 + ((x + y) % 40)).coerceIn(0, 255)
                val g = (150 + ((x * y) % 30)).coerceIn(0, 255)
                val b = (120 + ((x - y) % 25)).coerceIn(0, 255)
                val color = Color.rgb(r, g, b)
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }
    private fun createHighContrastBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val intensity = if ((x + y) % 2 == 0) 0 else 255
                val color = Color.rgb(intensity, intensity, intensity)
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }
    private fun createUniformBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
    }
    private fun createTestBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val intensity = ((x + y) % 256)
                val color = Color.rgb(intensity, intensity, intensity)
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }
    private fun calculateAverageBrightness(bitmap: Bitmap): Double {
        var totalBrightness = 0.0
        val pixels = bitmap.width * bitmap.height
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val brightness = (r + g + b) / 3.0
                totalBrightness += brightness
            }
        }
        return totalBrightness / pixels
    }
    private fun countEdgePixels(bitmap: Bitmap): Int {
        var edgeCount = 0
        for (x in 1 until bitmap.width - 1) {
            for (y in 1 until bitmap.height - 1) {
                val center = getBrightness(bitmap.getPixel(x, y))
                val neighbors = listOf(
                    getBrightness(bitmap.getPixel(x-1, y)),
                    getBrightness(bitmap.getPixel(x+1, y)),
                    getBrightness(bitmap.getPixel(x, y-1)),
                    getBrightness(bitmap.getPixel(x, y+1))
                )
                val maxDiff = neighbors.maxOfOrNull { abs(it - center) } ?: 0.0
                if (maxDiff > 30) {
                    edgeCount++
                }
            }
        }
        return edgeCount
    }
    private fun calculateNoiseLevel(bitmap: Bitmap): Double {
        var totalVariation = 0.0
        var count = 0
        for (x in 1 until bitmap.width - 1) {
            for (y in 1 until bitmap.height - 1) {
                val center = getBrightness(bitmap.getPixel(x, y))
                val neighbors = listOf(
                    getBrightness(bitmap.getPixel(x-1, y)),
                    getBrightness(bitmap.getPixel(x+1, y)),
                    getBrightness(bitmap.getPixel(x, y-1)),
                    getBrightness(bitmap.getPixel(x, y+1))
                )
                val avgNeighbor = neighbors.average()
                totalVariation += abs(center - avgNeighbor)
                count++
            }
        }
        return if (count > 0) totalVariation / count else 0.0
    }
    private fun calculateSharpness(bitmap: Bitmap): Double {
        var totalGradient = 0.0
        var count = 0
        for (x in 1 until bitmap.width - 1) {
            for (y in 1 until bitmap.height - 1) {
                val center = getBrightness(bitmap.getPixel(x, y))
                val right = getBrightness(bitmap.getPixel(x+1, y))
                val down = getBrightness(bitmap.getPixel(x, y+1))
                val gradientX = abs(right - center)
                val gradientY = abs(down - center)
                val gradient = Math.sqrt(gradientX * gradientX + gradientY * gradientY)
                totalGradient += gradient
                count++
            }
        }
        return if (count > 0) totalGradient / count else 0.0
    }
    private fun calculateContrast(bitmap: Bitmap): Double {
        val brightnesses = mutableListOf<Double>()
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                brightnesses.add(getBrightness(bitmap.getPixel(x, y)))
            }
        }
        val mean = brightnesses.average()
        val variance = brightnesses.map { (it - mean) * (it - mean) }.average()
        return Math.sqrt(variance)
    }
    private fun getBrightness(pixel: Int): Double {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return (r + g + b) / 3.0
    }
}
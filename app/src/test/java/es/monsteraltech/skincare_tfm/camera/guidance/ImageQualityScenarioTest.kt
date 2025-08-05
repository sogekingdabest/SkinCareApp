package es.monsteraltech.skincare_tfm.camera.guidance
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageQualityScenarioTest {
    private lateinit var analyzer: ImageQualityAnalyzer
    companion object {
        init {
            try {
            } catch (e: Exception) {
            }
        }
    }
    @Before
    fun setUp() {
        val mockContext = ApplicationProvider.getApplicationContext<Context>()
        val mockPerformanceManager = PerformanceManager(mockContext)
        val mockThermalDetector = ThermalStateDetector(mockContext)
        analyzer = ImageQualityAnalyzer(mockPerformanceManager, mockThermalDetector)
    }
    @Test
    fun `scenario indoor lighting with artificial light`() = runTest {
        val indoorImage = createIndoorLightingImage()
        val metrics = analyzer.analyzeQuality(indoorImage)
        assertTrue("Indoor lighting should have moderate brightness",
            metrics.brightness in 60f..150f)
        assertFalse("Indoor lighting should not be overexposed", metrics.isOverexposed)
        indoorImage.release()
    }
    @Test
    fun `scenario outdoor bright sunlight`() = runTest {
        val sunlightImage = createBrightSunlightImage()
        val metrics = analyzer.analyzeQuality(sunlightImage)
        assertTrue("Bright sunlight should be detected as overexposed",
            metrics.isOverexposed)
        assertTrue("Sunlight should have very high brightness", metrics.brightness > 200f)
        assertEquals("Should recommend finding shade",
            "Demasiada luz - busca sombra", metrics.getFeedbackMessage())
        sunlightImage.release()
    }
    @Test
    fun `scenario low light evening conditions`() = runTest {
        val lowLightImage = createLowLightImage()
        val metrics = analyzer.analyzeQuality(lowLightImage)
        assertTrue("Low light should be detected as underexposed",
            metrics.isUnderexposed)
        assertTrue("Low light should have low brightness", metrics.brightness < 60f)
        assertEquals("Should recommend more light",
            "Necesitas más luz", metrics.getFeedbackMessage())
        lowLightImage.release()
    }
    @Test
    fun `scenario camera shake blur`() = runTest {
        val shakeBlurImage = createCameraShakeBlur()
        val metrics = analyzer.analyzeQuality(shakeBlurImage)
        assertTrue("Camera shake should be detected as blurry", metrics.isBlurry)
        assertTrue("Shake blur should have low sharpness", metrics.sharpness < 0.2f)
        assertEquals("Should recommend steady camera",
            "Imagen borrosa - mantén firme la cámara", metrics.getFeedbackMessage())
        shakeBlurImage.release()
    }
    @Test
    fun `scenario focus blur out of focus`() = runTest {
        val focusBlurImage = createFocusBlur()
        val metrics = analyzer.analyzeQuality(focusBlurImage)
        assertTrue("Out of focus should be detected as blurry", metrics.isBlurry)
        assertEquals("Should recommend steady camera",
            "Imagen borrosa - mantén firme la cámara", metrics.getFeedbackMessage())
        focusBlurImage.release()
    }
    @Test
    fun `scenario optimal lighting conditions`() = runTest {
        val optimalImage = createOptimalLightingImage()
        val metrics = analyzer.analyzeQuality(optimalImage)
        assertTrue("Optimal conditions should have good quality", metrics.isGoodQuality())
        assertFalse("Should not be blurry", metrics.isBlurry)
        assertFalse("Should not be overexposed", metrics.isOverexposed)
        assertFalse("Should not be underexposed", metrics.isUnderexposed)
        assertTrue("Should have good brightness", metrics.brightness in 100f..160f)
        assertTrue("Should have good contrast", metrics.contrast > 20f)
        optimalImage.release()
    }
    @Test
    fun `scenario mixed lighting conditions`() = runTest {
        val mixedLightImage = createMixedLightingImage()
        val metrics = analyzer.analyzeQuality(mixedLightImage)
        assertTrue("Mixed lighting should have moderate brightness",
            metrics.brightness in 50f..200f)
        assertTrue("Mixed lighting should have high contrast", metrics.contrast > 30f)
        mixedLightImage.release()
    }
    @Test
    fun `scenario flash photography`() = runTest {
        val flashImage = createFlashPhotographyImage()
        val metrics = analyzer.analyzeQuality(flashImage)
        val histogramAnalysis = analyzer.analyzeHistogram(flashImage)
        assertFalse("Flash photo should not have well-distributed histogram",
            histogramAnalysis.isWellDistributed)
        flashImage.release()
    }
    @Test
    fun `scenario high contrast skin mole`() = runTest {
        val highContrastImage = createHighContrastMoleImage()
        val metrics = analyzer.analyzeQuality(highContrastImage)
        assertTrue("High contrast mole should have good contrast", metrics.contrast > 25f)
        assertTrue("Should have good quality for analysis", metrics.isGoodQuality())
        highContrastImage.release()
    }
    @Test
    fun `scenario low contrast skin mole`() = runTest {
        val lowContrastImage = createLowContrastMoleImage()
        val metrics = analyzer.analyzeQuality(lowContrastImage)
        assertTrue("Low contrast mole should have low contrast", metrics.contrast < 15f)
        lowContrastImage.release()
    }
    @Test
    fun `scenario performance with large images`() = runTest {
        val largeImage = createLargeTestImage()
        val startTime = System.currentTimeMillis()
        val metrics = analyzer.analyzeQuality(largeImage)
        val processingTime = System.currentTimeMillis() - startTime
        assertTrue("Large image analysis should complete quickly",
            processingTime < 500)
        assertNotNull("Should produce valid metrics", metrics)
        largeImage.release()
    }
    private fun createIndoorLightingImage(): Mat {
        val image = Mat.zeros(Size(200.0, 200.0), CvType.CV_8UC1)
        for (i in 0 until 200) {
            for (j in 0 until 200) {
                val baseValue = 110.0
                val variation = Math.sin(i * 0.1) * 20.0
                val value = Math.max(80.0, Math.min(140.0, baseValue + variation))
                image.put(i, j, value)
            }
        }
        return image
    }
    private fun createBrightSunlightImage(): Mat {
        val image = Mat.ones(Size(200.0, 200.0), CvType.CV_8UC1)
        val totalPixels = 200 * 200
        val brightPixels = (totalPixels * 0.7).toInt()
        var count = 0
        for (i in 0 until 200) {
            for (j in 0 until 200) {
                val value = if (count < brightPixels) 245.0 else 180.0
                image.put(i, j, value)
                count++
            }
        }
        return image
    }
    private fun createLowLightImage(): Mat {
        val image = Mat.zeros(Size(200.0, 200.0), CvType.CV_8UC1)
        val totalPixels = 200 * 200
        val darkPixels = (totalPixels * 0.8).toInt()
        var count = 0
        for (i in 0 until 200) {
            for (j in 0 until 200) {
                val value = if (count < darkPixels) 15.0 else 60.0
                image.put(i, j, value)
                count++
            }
        }
        return image
    }
    private fun createCameraShakeBlur(): Mat {
        val sharpImage = createOptimalLightingImage()
        val blurredImage = Mat()
        val kernel = Mat.zeros(Size(9.0, 9.0), CvType.CV_32F)
        for (i in 0 until 9) {
            kernel.put(i, i, 1.0 / 9.0)
        }
        Imgproc.filter2D(sharpImage, blurredImage, -1, kernel)
        sharpImage.release()
        kernel.release()
        return blurredImage
    }
    private fun createFocusBlur(): Mat {
        val sharpImage = createOptimalLightingImage()
        val blurredImage = Mat()
        Imgproc.GaussianBlur(sharpImage, blurredImage, Size(11.0, 11.0), 3.0)
        sharpImage.release()
        return blurredImage
    }
    private fun createOptimalLightingImage(): Mat {
        val image = Mat.zeros(Size(200.0, 200.0), CvType.CV_8UC1)
        for (i in 0 until 200) {
            for (j in 0 until 200) {
                val baseValue = 120.0
                val pattern = if ((i / 10 + j / 10) % 2 == 0) 20.0 else -20.0
                val value = Math.max(80.0, Math.min(160.0, baseValue + pattern))
                image.put(i, j, value)
            }
        }
        return image
    }
    private fun createMixedLightingImage(): Mat {
        val image = Mat.zeros(Size(200.0, 200.0), CvType.CV_8UC1)
        for (i in 0 until 200) {
            for (j in 0 until 200) {
                val value = if (j < 100) 200.0 else 50.0
                image.put(i, j, value)
            }
        }
        return image
    }
    private fun createFlashPhotographyImage(): Mat {
        val image = Mat.zeros(Size(200.0, 200.0), CvType.CV_8UC1)
        val center = Point(100.0, 100.0)
        for (i in 0 until 200) {
            for (j in 0 until 200) {
                val distance = Math.sqrt((i - center.x) * (i - center.x) + (j - center.y) * (j - center.y))
                val maxDistance = 141.0
                val brightness = 250.0 - (distance / maxDistance) * 200.0
                val value = Math.max(30.0, Math.min(250.0, brightness))
                image.put(i, j, value)
            }
        }
        return image
    }
    private fun createHighContrastMoleImage(): Mat {
        val image = Mat.ones(Size(200.0, 200.0), CvType.CV_8UC1)
        image.setTo(Scalar(180.0))
        val moleCenter = Point(100.0, 100.0)
        val moleRadius = 30.0
        for (i in 0 until 200) {
            for (j in 0 until 200) {
                val distance = Math.sqrt((i - moleCenter.x) * (i - moleCenter.x) + (j - moleCenter.y) * (j - moleCenter.y))
                if (distance <= moleRadius) {
                    image.put(i, j, 40.0)
                }
            }
        }
        return image
    }
    private fun createLowContrastMoleImage(): Mat {
        val image = Mat.ones(Size(200.0, 200.0), CvType.CV_8UC1)
        image.setTo(Scalar(120.0))
        val moleCenter = Point(100.0, 100.0)
        val moleRadius = 30.0
        for (i in 0 until 200) {
            for (j in 0 until 200) {
                val distance = Math.sqrt((i - moleCenter.x) * (i - moleCenter.x) + (j - moleCenter.y) * (j - moleCenter.y))
                if (distance <= moleRadius) {
                    image.put(i, j, 100.0)
                }
            }
        }
        return image
    }
    private fun createLargeTestImage(): Mat {
        val image = Mat.zeros(Size(1000.0, 1000.0), CvType.CV_8UC1)
        for (i in 0 until 1000) {
            for (j in 0 until 1000) {
                val value = (Math.sin(i * 0.01) * Math.cos(j * 0.01) * 127.0 + 128.0)
                image.put(i, j, value)
            }
        }
        return image
    }
}
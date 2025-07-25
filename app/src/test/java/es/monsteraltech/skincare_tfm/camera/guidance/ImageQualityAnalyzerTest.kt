package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

/**
 * Tests unitarios para ImageQualityAnalyzer
 * Verifica el análisis de calidad de imagen bajo diferentes condiciones
 */
@RunWith(RobolectricTestRunner::class)
class ImageQualityAnalyzerTest {
    
    private lateinit var analyzer: ImageQualityAnalyzer
    
    companion object {
        // Inicializar OpenCV para tests (mock)
        init {
            try {
                // En un entorno real, esto sería System.loadLibrary("opencv_java4")
                // Para tests, usamos mocks o stubs
            } catch (e: Exception) {
                // Ignorar errores de carga de OpenCV en tests
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
    fun `analyzeQuality should detect sharp image correctly`() = runTest {
        // Crear imagen nítida con bordes definidos
        val sharpImage = createSharpTestImage()
        
        val metrics = analyzer.analyzeQuality(sharpImage)
        
        assertFalse("Sharp image should not be detected as blurry", metrics.isBlurry)
        assertTrue("Sharp image should have high sharpness", metrics.sharpness > 0.3f)
        
        sharpImage.release()
    }
    
    @Test
    fun `analyzeQuality should detect blurry image correctly`() = runTest {
        // Crear imagen borrosa
        val blurryImage = createBlurryTestImage()
        
        val metrics = analyzer.analyzeQuality(blurryImage)
        
        assertTrue("Blurry image should be detected as blurry", metrics.isBlurry)
        assertTrue("Blurry image should have low sharpness", metrics.sharpness < 0.3f)
        
        blurryImage.release()
    }
    
    @Test
    fun `analyzeQuality should detect overexposed image correctly`() = runTest {
        // Crear imagen sobreexpuesta
        val overexposedImage = createOverexposedTestImage()
        
        val metrics = analyzer.analyzeQuality(overexposedImage)
        
        assertTrue("Overexposed image should be detected", metrics.isOverexposed)
        assertTrue("Overexposed image should have high brightness", metrics.brightness > 200f)
        assertEquals("Should show overexposure message", 
            "Demasiada luz - busca sombra", metrics.getFeedbackMessage())
        
        overexposedImage.release()
    }
    
    @Test
    fun `analyzeQuality should detect underexposed image correctly`() = runTest {
        // Crear imagen subexpuesta
        val underexposedImage = createUnderexposedTestImage()
        
        val metrics = analyzer.analyzeQuality(underexposedImage)
        
        assertTrue("Underexposed image should be detected", metrics.isUnderexposed)
        assertTrue("Underexposed image should have low brightness", metrics.brightness < 50f)
        assertEquals("Should show underexposure message", 
            "Necesitas más luz", metrics.getFeedbackMessage())
        
        underexposedImage.release()
    }
    
    @Test
    fun `analyzeQuality should detect good quality image`() = runTest {
        // Crear imagen de buena calidad
        val goodImage = createGoodQualityTestImage()
        
        val metrics = analyzer.analyzeQuality(goodImage)
        
        assertTrue("Good image should have good quality", metrics.isGoodQuality())
        assertFalse("Good image should not be blurry", metrics.isBlurry)
        assertFalse("Good image should not be overexposed", metrics.isOverexposed)
        assertFalse("Good image should not be underexposed", metrics.isUnderexposed)
        assertTrue("Good image should have adequate brightness", 
            metrics.brightness in 80f..180f)
        assertEquals("Should show good quality message", 
            "Calidad de imagen buena", metrics.getFeedbackMessage())
        
        goodImage.release()
    }
    
    @Test
    fun `calculateSharpness should work with different image types`() = runTest {
        // Test con imagen en color
        val colorImage = createColorTestImage()
        val colorMetrics = analyzer.analyzeQuality(colorImage)
        
        // Test con imagen en escala de grises
        val grayImage = Mat()
        Imgproc.cvtColor(colorImage, grayImage, Imgproc.COLOR_BGR2GRAY)
        val grayMetrics = analyzer.analyzeQuality(grayImage)
        
        // Ambas deberían producir métricas válidas
        assertTrue("Color image sharpness should be positive", colorMetrics.sharpness >= 0)
        assertTrue("Gray image sharpness should be positive", grayMetrics.sharpness >= 0)
        
        colorImage.release()
        grayImage.release()
    }
    
    @Test
    fun `analyzeHistogram should provide detailed analysis`() {
        val testImage = createGoodQualityTestImage()
        
        val histogramAnalysis = analyzer.analyzeHistogram(testImage)
        
        assertNotNull("Histogram distribution should not be null", histogramAnalysis.distribution)
        assertEquals("Histogram should have 256 bins", 256, histogramAnalysis.distribution.size)
        assertTrue("Peak should be within valid range", 
            histogramAnalysis.peak in 0..255)
        
        testImage.release()
    }
    
    @Test
    fun `brightness calculation should be accurate`() = runTest {
        // Imagen muy brillante
        val brightImage = Mat.ones(Size(100.0, 100.0), CvType.CV_8UC1)
        brightImage.setTo(Scalar(200.0))
        
        val brightMetrics = analyzer.analyzeQuality(brightImage)
        assertTrue("Bright image should have high brightness", brightMetrics.brightness > 150f)
        
        // Imagen muy oscura
        val darkImage = Mat.zeros(Size(100.0, 100.0), CvType.CV_8UC1)
        darkImage.setTo(Scalar(20.0))
        
        val darkMetrics = analyzer.analyzeQuality(darkImage)
        assertTrue("Dark image should have low brightness", darkMetrics.brightness < 50f)
        
        brightImage.release()
        darkImage.release()
    }
    
    @Test
    fun `contrast calculation should detect low contrast images`() = runTest {
        // Imagen de bajo contraste (todos los píxeles similares)
        val lowContrastImage = Mat.ones(Size(100.0, 100.0), CvType.CV_8UC1)
        lowContrastImage.setTo(Scalar(128.0))
        
        val metrics = analyzer.analyzeQuality(lowContrastImage)
        assertTrue("Low contrast image should have low contrast value", 
            metrics.contrast < 10f)
        
        lowContrastImage.release()
    }
    
    @Test
    fun `exposure detection should handle edge cases`() = runTest {
        // Imagen con algunos píxeles sobreexpuestos pero no suficientes
        val partialOverexposure = createPartialOverexposureImage()
        
        val metrics = analyzer.analyzeQuality(partialOverexposure)
        
        // No debería detectarse como sobreexpuesta si es menos del 10%
        assertFalse("Partial overexposure should not trigger detection", 
            metrics.isOverexposed)
        
        partialOverexposure.release()
    }
    
    // Métodos auxiliares para crear imágenes de test
    
    private fun createSharpTestImage(): Mat {
        val image = Mat.zeros(Size(200.0, 200.0), CvType.CV_8UC1)
        
        // Crear patrón de tablero de ajedrez para máxima nitidez
        for (i in 0 until 200 step 20) {
            for (j in 0 until 200 step 20) {
                val color = if ((i / 20 + j / 20) % 2 == 0) 255.0 else 0.0
                val rect = Rect(i, j, 20, 20)
                image.submat(rect).setTo(Scalar(color))
            }
        }
        
        return image
    }
    
    private fun createBlurryTestImage(): Mat {
        val sharpImage = createSharpTestImage()
        val blurryImage = Mat()
        
        // Aplicar desenfoque gaussiano fuerte
        Imgproc.GaussianBlur(sharpImage, blurryImage, Size(15.0, 15.0), 5.0)
        
        sharpImage.release()
        return blurryImage
    }
    
    private fun createOverexposedTestImage(): Mat {
        val image = Mat.ones(Size(100.0, 100.0), CvType.CV_8UC1)
        
        // 80% de píxeles sobreexpuestos
        val totalPixels = (100 * 100 * 0.8).toInt()
        var count = 0
        
        for (i in 0 until 100) {
            for (j in 0 until 100) {
                if (count < totalPixels) {
                    image.put(i, j, 250.0) // Píxeles muy brillantes
                    count++
                } else {
                    image.put(i, j, 128.0) // Píxeles normales
                }
            }
        }
        
        return image
    }
    
    private fun createUnderexposedTestImage(): Mat {
        val image = Mat.ones(Size(100.0, 100.0), CvType.CV_8UC1)
        
        // 80% de píxeles subexpuestos
        val totalPixels = (100 * 100 * 0.8).toInt()
        var count = 0
        
        for (i in 0 until 100) {
            for (j in 0 until 100) {
                if (count < totalPixels) {
                    image.put(i, j, 10.0) // Píxeles muy oscuros
                    count++
                } else {
                    image.put(i, j, 128.0) // Píxeles normales
                }
            }
        }
        
        return image
    }
    
    private fun createGoodQualityTestImage(): Mat {
        val image = Mat.zeros(Size(100.0, 100.0), CvType.CV_8UC1)
        
        // Crear gradiente suave con buen contraste y brillo medio
        for (i in 0 until 100) {
            for (j in 0 until 100) {
                val value = 80.0 + (i + j) * 0.5 // Rango 80-180
                image.put(i, j, value)
            }
        }
        
        return image
    }
    
    private fun createColorTestImage(): Mat {
        val image = Mat.zeros(Size(100.0, 100.0), CvType.CV_8UC3)
        
        // Crear imagen en color con diferentes canales
        for (i in 0 until 100) {
            for (j in 0 until 100) {
                val blue = (i * 2.55).toInt()
                val green = (j * 2.55).toInt()
                val red = ((i + j) * 1.275).toInt()
                image.put(i, j, blue.toDouble(), green.toDouble(), red.toDouble())
            }
        }
        
        return image
    }
    
    private fun createPartialOverexposureImage(): Mat {
        val image = Mat.ones(Size(100.0, 100.0), CvType.CV_8UC1)
        image.setTo(Scalar(128.0)) // Brillo medio base
        
        // Solo 5% de píxeles sobreexpuestos (menos del umbral del 10%)
        val overexposedPixels = (100 * 100 * 0.05).toInt()
        
        for (i in 0 until overexposedPixels) {
            val row = i / 100
            val col = i % 100
            image.put(row, col, 250.0)
        }
        
        return image
    }
}
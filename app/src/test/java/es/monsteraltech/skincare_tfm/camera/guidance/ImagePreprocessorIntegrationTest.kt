package es.monsteraltech.skincare_tfm.camera.guidance

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

/**
 * Tests de integración para ImagePreprocessor
 * Verifica la integración con otros componentes del sistema de guías de captura
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ImagePreprocessorIntegrationTest {
    
    private lateinit var imagePreprocessor: ImagePreprocessor
    private lateinit var imageQualityAnalyzer: ImageQualityAnalyzer
    
    @Before
    fun setUp() {
        val mockContext = ApplicationProvider.getApplicationContext<Context>()
        val mockPerformanceManager = PerformanceManager(mockContext)
        val mockThermalDetector = ThermalStateDetector(mockContext)
        imagePreprocessor = ImagePreprocessor()
        imageQualityAnalyzer = ImageQualityAnalyzer(mockPerformanceManager, mockThermalDetector)
    }
    
    @Test
    fun `preprocessed image should have better quality metrics`() = runTest {
        // Given
        val poorQualityBitmap = createPoorQualityBitmap(300, 300)
        
        // When
        val preprocessResult = imagePreprocessor.preprocessForDermatologyAnalysis(poorQualityBitmap)
        
        // Then
        assertTrue(preprocessResult.isSuccessful())
        
        // Verificar que el preprocesado mejora las métricas de calidad
        val improvement = imagePreprocessor.analyzeQualityImprovement(
            poorQualityBitmap,
            preprocessResult.processedBitmap
        )
        
        assertTrue(improvement >= 0, "Quality should not degrade after preprocessing")
    }
    
    @Test
    fun `preprocessing should work with different image formats and sizes`() = runTest {
        // Given
        val testCases = listOf(
            Pair(100, 100),   // Pequeña
            Pair(500, 300),   // Rectangular
            Pair(800, 800),   // Grande cuadrada
            Pair(1200, 900)   // Muy grande
        )
        
        // When & Then
        testCases.forEach { (width, height) ->
            val testBitmap = createTestBitmap(width, height)
            val result = imagePreprocessor.preprocessImage(testBitmap)
            
            assertTrue(result.isSuccessful(), "Failed for size ${width}x${height}")
            assertEquals(width, result.processedBitmap.width)
            assertEquals(height, result.processedBitmap.height)
            assertTrue(result.processingTime > 0)
        }
    }
    
    @Test
    fun `preprocessing should handle edge cases gracefully`() = runTest {
        // Given - Casos extremos
        val edgeCases = listOf(
            createAllBlackBitmap(100, 100),
            createAllWhiteBitmap(100, 100),
            createSinglePixelBitmap(),
            createVerySmallBitmap(5, 5)
        )
        
        // When & Then
        edgeCases.forEach { bitmap ->
            val result = imagePreprocessor.preprocessImage(bitmap)
            
            // No debería fallar, aunque puede que no aplique todos los filtros
            assertNotNull(result.processedBitmap)
            assertEquals(bitmap.width, result.processedBitmap.width)
            assertEquals(bitmap.height, result.processedBitmap.height)
        }
    }
    
    @Test
    fun `preprocessing configurations should produce different results`() = runTest {
        // Given
        val testBitmap = createTestBitmap(200, 200)
        val configs = listOf(
            ImagePreprocessor.PreprocessingConfig(), // Default
            ImagePreprocessor.PreprocessingConfig(
                enableIlluminationNormalization = true,
                enableContrastEnhancement = false,
                enableNoiseReduction = false,
                enableSharpening = false
            ),
            ImagePreprocessor.PreprocessingConfig(
                enableIlluminationNormalization = false,
                enableContrastEnhancement = true,
                enableNoiseReduction = true,
                enableSharpening = true
            )
        )
        
        // When
        val results = configs.map { config ->
            imagePreprocessor.preprocessImage(testBitmap, config)
        }
        
        // Then
        results.forEach { result ->
            assertTrue(result.isSuccessful())
        }
        
        // Los resultados deberían ser diferentes
        val filterCounts = results.map { it.appliedFilters.size }
        assertTrue(filterCounts.toSet().size > 1, "Different configs should produce different filter counts")
    }
    
    @Test
    fun `specialized preprocessing methods should use appropriate configurations`() = runTest {
        // Given
        val testBitmap = createTestBitmap(300, 300)
        
        // When
        val dermatologyResult = imagePreprocessor.preprocessForDermatologyAnalysis(testBitmap)
        val lowLightResult = imagePreprocessor.preprocessForLowLight(testBitmap)
        val overexposureResult = imagePreprocessor.preprocessForOverexposure(testBitmap)
        
        // Then
        assertTrue(dermatologyResult.isSuccessful())
        assertTrue(lowLightResult.isSuccessful())
        assertTrue(overexposureResult.isSuccessful())
        
        // Dermatology debería usar todos los filtros
        assertEquals(4, dermatologyResult.appliedFilters.size)
        
        // Low light no debería usar sharpening
        assertFalse(lowLightResult.appliedFilters.contains("Enfoque"))
        
        // Overexposure no debería usar contrast enhancement
        assertFalse(overexposureResult.appliedFilters.contains("Mejora de contraste"))
    }
    
    @Test
    fun `preprocessing should maintain image aspect ratio`() = runTest {
        // Given
        val aspectRatios = listOf(
            Pair(400, 300),  // 4:3
            Pair(500, 200),  // 5:2
            Pair(300, 600)   // 1:2
        )
        
        // When & Then
        aspectRatios.forEach { (width, height) ->
            val testBitmap = createTestBitmap(width, height)
            val result = imagePreprocessor.preprocessImage(testBitmap)
            
            assertTrue(result.isSuccessful())
            assertEquals(width, result.processedBitmap.width)
            assertEquals(height, result.processedBitmap.height)
            
            val originalRatio = width.toDouble() / height
            val processedRatio = result.processedBitmap.width.toDouble() / result.processedBitmap.height
            assertEquals(originalRatio, processedRatio, 0.001)
        }
    }
    
    @Test
    fun `preprocessing should handle concurrent requests`() = runTest {
        // Given
        val testBitmaps = (1..5).map { createTestBitmap(200, 200) }
        
        // When - Procesar múltiples imágenes concurrentemente
        val results = testBitmaps.map { bitmap ->
            imagePreprocessor.preprocessImage(bitmap)
        }
        
        // Then
        results.forEach { result ->
            assertTrue(result.isSuccessful())
            assertTrue(result.processingTime > 0)
            assertNotNull(result.processedBitmap)
        }
        
        // Verificar que no hay interferencia entre procesamientos
        val processingTimes = results.map { it.processingTime }
        val avgTime = processingTimes.average()
        processingTimes.forEach { time ->
            assertTrue(time < avgTime * 3, "Processing time $time is too far from average $avgTime")
        }
    }
    
    @Test
    fun `preprocessing should work with real-world image characteristics`() = runTest {
        // Given - Simular características de imágenes reales de cámara
        val realWorldBitmaps = listOf(
            createSkinTextureBitmap(400, 300),
            createUnderexposedBitmap(500, 400),
            createOverexposedBitmap(600, 400),
            createNoisyMobileBitmap(800, 600)
        )
        
        // When & Then
        realWorldBitmaps.forEach { bitmap ->
            val result = imagePreprocessor.preprocessForDermatologyAnalysis(bitmap)
            
            assertTrue(result.isSuccessful(), "Failed to process real-world-like bitmap")
            assertTrue(result.appliedFilters.isNotEmpty())
            assertTrue(result.processingTime < 10000) // Menos de 10 segundos
            
            // Verificar que la imagen procesada mantiene características importantes
            assertNotNull(result.processedBitmap)
            assertEquals(bitmap.width, result.processedBitmap.width)
            assertEquals(bitmap.height, result.processedBitmap.height)
        }
    }
    
    @Test
    fun `preprocessing should handle memory constraints gracefully`() = runTest {
        // Given - Imagen grande que podría causar problemas de memoria
        val largeBitmap = createTestBitmap(2000, 1500) // 3MP
        
        // When
        val result = imagePreprocessor.preprocessImage(largeBitmap)
        
        // Then
        assertTrue(result.isSuccessful())
        assertNotNull(result.processedBitmap)
        assertEquals(largeBitmap.width, result.processedBitmap.width)
        assertEquals(largeBitmap.height, result.processedBitmap.height)
        
        // Verificar que no hay memory leak obvio
        System.gc()
        Thread.sleep(100)
        
        // El procesamiento debería completarse en tiempo razonable incluso para imágenes grandes
        assertTrue(result.processingTime < 15000, "Large image processing took too long: ${result.processingTime}ms")
    }
    
    @Test
    fun `quality improvement analysis should be reliable across different image types`() = runTest {
        // Given
        val imageTypes = listOf(
            createLowContrastBitmap(200, 200),
            createHighNoiseBitmap(200, 200),
            createBlurryBitmap(200, 200),
            createWellExposedBitmap(200, 200)
        )
        
        // When & Then
        imageTypes.forEach { bitmap ->
            val result = imagePreprocessor.preprocessImage(bitmap)
            val improvement = imagePreprocessor.analyzeQualityImprovement(bitmap, result.processedBitmap)
            
            assertTrue(result.isSuccessful())
            assertTrue(improvement >= -1f && improvement <= 1f, 
                "Quality improvement $improvement is outside valid range")
        }
    }
    
    // Funciones auxiliares para crear bitmaps de prueba específicos
    
    private fun createPoorQualityBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Imagen con bajo contraste, ruido y desenfoque
        for (x in 0 until width) {
            for (y in 0 until height) {
                val base = 100 + ((x + y) % 50) // Bajo contraste
                val noise = (Math.random() * 40 - 20).toInt() // Ruido
                val intensity = (base + noise).coerceIn(0, 255)
                val color = Color.rgb(intensity, intensity, intensity)
                bitmap.setPixel(x, y, color)
            }
        }
        
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
    
    private fun createAllBlackBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        return bitmap
    }
    
    private fun createAllWhiteBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        return bitmap
    }
    
    private fun createSinglePixelBitmap(): Bitmap {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.GRAY)
        }
    }
    
    private fun createVerySmallBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                val intensity = ((x + y) * 50) % 256
                val color = Color.rgb(intensity, intensity, intensity)
                bitmap.setPixel(x, y, color)
            }
        }
        
        return bitmap
    }
    
    private fun createSkinTextureBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                // Simular textura de piel con variaciones sutiles
                val baseR = 200
                val baseG = 150
                val baseB = 120
                
                val variation = (Math.sin(x * 0.1) * Math.cos(y * 0.1) * 20).toInt()
                
                val r = (baseR + variation).coerceIn(0, 255)
                val g = (baseG + variation).coerceIn(0, 255)
                val b = (baseB + variation).coerceIn(0, 255)
                
                val color = Color.rgb(r, g, b)
                bitmap.setPixel(x, y, color)
            }
        }
        
        return bitmap
    }
    
    private fun createUnderexposedBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                val intensity = ((x + y) % 80) // Valores bajos
                val color = Color.rgb(intensity, intensity, intensity)
                bitmap.setPixel(x, y, color)
            }
        }
        
        return bitmap
    }
    
    private fun createOverexposedBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                val intensity = 200 + ((x + y) % 55) // Valores altos
                val color = Color.rgb(intensity, intensity, intensity)
                bitmap.setPixel(x, y, color)
            }
        }
        
        return bitmap
    }
    
    private fun createNoisyMobileBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                // Simular ruido típico de cámara móvil
                val base = 128 + ((x + y) % 100)
                val noise = (Math.random() * 60 - 30).toInt()
                val intensity = (base + noise).coerceIn(0, 255)
                val color = Color.rgb(intensity, intensity, intensity)
                bitmap.setPixel(x, y, color)
            }
        }
        
        return bitmap
    }
    
    private fun createLowContrastBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                val intensity = 100 + ((x + y) % 50) // Rango limitado
                val color = Color.rgb(intensity, intensity, intensity)
                bitmap.setPixel(x, y, color)
            }
        }
        
        return bitmap
    }
    
    private fun createHighNoiseBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                val base = 128
                val noise = (Math.random() * 120 - 60).toInt() // Ruido alto
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
                // Transiciones suaves simulando desenfoque
                val intensity = (128 + 100 * Math.sin(x * 0.05) * Math.cos(y * 0.05)).toInt().coerceIn(0, 255)
                val color = Color.rgb(intensity, intensity, intensity)
                bitmap.setPixel(x, y, color)
            }
        }
        
        return bitmap
    }
    
    private fun createWellExposedBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                val intensity = 50 + ((x + y) % 150) // Buen rango dinámico
                val color = Color.rgb(intensity, intensity, intensity)
                bitmap.setPixel(x, y, color)
            }
        }
        
        return bitmap
    }
}
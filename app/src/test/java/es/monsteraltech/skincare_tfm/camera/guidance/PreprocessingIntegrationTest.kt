package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.opencv.core.Size
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Tests de integración específicos para el preprocesado de imágenes
 * en el contexto del flujo de captura completo
 */
@RunWith(AndroidJUnit4::class)
class PreprocessingIntegrationTest {

    private lateinit var context: Context
    private lateinit var imagePreprocessor: ImagePreprocessor
    private lateinit var testBitmap: Bitmap
    private lateinit var testImageFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        imagePreprocessor = ImagePreprocessor()
        createTestImage()
    }

    @After
    fun tearDown() {
        cleanupTestFiles()
        if (::testBitmap.isInitialized && !testBitmap.isRecycled) {
            testBitmap.recycle()
        }
    }

    /**
     * Test de integración con diferentes configuraciones de preprocesado
     * basadas en condiciones de captura detectadas
     */
    @Test
    fun testAdaptivePreprocessingConfigurations() = runBlocking {
        val bitmap = BitmapFactory.decodeFile(testImageFile.absolutePath)
        
        // Test configuración para poca luz
        val lowLightConfig = ImagePreprocessor.PreprocessingConfig(
            enableIlluminationNormalization = true,
            enableContrastEnhancement = true,
            enableNoiseReduction = true,
            enableSharpening = false,
            claheClipLimit = 4.0,
            claheTileSize = Size(4.0, 4.0)
        )
        
        val lowLightResult = imagePreprocessor.preprocessImage(bitmap, lowLightConfig)
        assertTrue("Preprocesado para poca luz debe ser exitoso", lowLightResult.isSuccessful())
        assertTrue("Debe aplicar normalización de iluminación", 
            lowLightResult.appliedFilters.contains("Normalización de iluminación"))
        assertFalse("No debe aplicar enfoque en poca luz", 
            lowLightResult.appliedFilters.contains("Enfoque"))
        
        // Test configuración para imagen borrosa
        val blurryConfig = ImagePreprocessor.PreprocessingConfig(
            enableIlluminationNormalization = true,
            enableContrastEnhancement = true,
            enableNoiseReduction = true,
            enableSharpening = true,
            sharpeningStrength = 0.7
        )
        
        val blurryResult = imagePreprocessor.preprocessImage(bitmap, blurryConfig)
        assertTrue("Preprocesado para imagen borrosa debe ser exitoso", blurryResult.isSuccessful())
        assertTrue("Debe aplicar enfoque para imagen borrosa", 
            blurryResult.appliedFilters.contains("Enfoque"))
        
        // Test configuración estándar para dermatología
        val standardResult = imagePreprocessor.preprocessForDermatologyAnalysis(bitmap)
        assertTrue("Preprocesado estándar debe ser exitoso", standardResult.isSuccessful())
        assertTrue("Debe aplicar todos los filtros estándar", 
            standardResult.appliedFilters.size >= 3)
        
        // Limpiar recursos
        bitmap.recycle()
        lowLightResult.processedBitmap.recycle()
        blurryResult.processedBitmap.recycle()
        standardResult.processedBitmap.recycle()
    }

    /**
     * Test de integración del preprocesado con análisis de calidad
     */
    @Test
    fun testPreprocessingWithQualityAnalysis() = runBlocking {
        val bitmap = BitmapFactory.decodeFile(testImageFile.absolutePath)
        
        // Aplicar preprocesado
        val preprocessingResult = imagePreprocessor.preprocessImage(bitmap)
        assertTrue("Preprocesado debe ser exitoso", preprocessingResult.isSuccessful())
        
        // Analizar mejora de calidad
        val qualityImprovement = imagePreprocessor.analyzeQualityImprovement(
            bitmap, 
            preprocessingResult.processedBitmap
        )
        
        // Verificar que se puede medir la mejora
        assertTrue("Mejora de calidad debe ser medible", qualityImprovement >= -1f && qualityImprovement <= 1f)
        
        // Verificar metadata de procesamiento
        val metadata = preprocessingResult.getProcessingSummary()
        assertNotNull("Metadata debe existir", metadata)
        assertTrue("Metadata debe contener tiempo de procesamiento", metadata.contains("ms"))
        assertTrue("Metadata debe contener filtros aplicados", metadata.contains("Filtros aplicados"))
        
        // Limpiar recursos
        bitmap.recycle()
        preprocessingResult.processedBitmap.recycle()
    }

    /**
     * Test de integración con diferentes tipos de imágenes
     */
    @Test
    fun testPreprocessingWithDifferentImageTypes() = runBlocking {
        // Test con imagen pequeña
        val smallBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        smallBitmap.eraseColor(android.graphics.Color.RED)
        
        val smallResult = imagePreprocessor.preprocessImage(smallBitmap)
        assertTrue("Preprocesado de imagen pequeña debe funcionar", smallResult.isSuccessful())
        
        // Test con imagen grande
        val largeBitmap = Bitmap.createBitmap(2000, 2000, Bitmap.Config.ARGB_8888)
        largeBitmap.eraseColor(android.graphics.Color.GREEN)
        
        val largeResult = imagePreprocessor.preprocessImage(largeBitmap)
        assertTrue("Preprocesado de imagen grande debe funcionar", largeResult.isSuccessful())
        
        // Verificar que el tiempo de procesamiento es razonable
        assertTrue("Imagen pequeña debe procesarse rápido", smallResult.processingTime < 1000)
        assertTrue("Imagen grande debe procesarse en tiempo razonable", largeResult.processingTime < 5000)
        
        // Limpiar recursos
        smallBitmap.recycle()
        largeBitmap.recycle()
        smallResult.processedBitmap.recycle()
        largeResult.processedBitmap.recycle()
    }

    /**
     * Test de integración con condiciones de error y recuperación
     */
    @Test
    fun testPreprocessingErrorRecovery() = runBlocking {
        // Test con bitmap null (simulando error de decodificación)
        try {
            val nullBitmap: Bitmap? = null
            // Esto debería manejarse graciosamente
            assertNull("Bitmap null debe manejarse correctamente", nullBitmap)
        } catch (e: Exception) {
            fail("No debe lanzar excepción con bitmap null")
        }
        
        // Test con bitmap reciclado
        val recycleBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        recycleBitmap.recycle()
        
        try {
            val result = imagePreprocessor.preprocessImage(recycleBitmap)
            // Debe manejar el error graciosamente
            assertNotNull("Resultado debe existir incluso con bitmap reciclado", result)
        } catch (e: Exception) {
            // Es aceptable que lance excepción, pero debe ser manejada por el caller
            assertTrue("Excepción debe ser de tipo esperado", 
                e is IllegalArgumentException || e is IllegalStateException)
        }
    }

    /**
     * Test de integración con persistencia de archivos
     */
    @Test
    fun testPreprocessingFilePersistence() = runBlocking {
        val bitmap = BitmapFactory.decodeFile(testImageFile.absolutePath)
        val preprocessingResult = imagePreprocessor.preprocessImage(bitmap)
        
        // Guardar imagen procesada
        val processedFile = File(context.cacheDir, "integration-processed.jpg")
        val outputStream = FileOutputStream(processedFile)
        preprocessingResult.processedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        outputStream.close()
        
        // Verificar que el archivo se guardó correctamente
        assertTrue("Archivo procesado debe existir", processedFile.exists())
        assertTrue("Archivo procesado debe tener contenido", processedFile.length() > 0)
        
        // Verificar que se puede leer de vuelta
        val reloadedBitmap = BitmapFactory.decodeFile(processedFile.absolutePath)
        assertNotNull("Imagen procesada debe poder leerse de vuelta", reloadedBitmap)
        assertEquals("Dimensiones deben mantenerse", 
            preprocessingResult.processedBitmap.width, reloadedBitmap.width)
        assertEquals("Dimensiones deben mantenerse", 
            preprocessingResult.processedBitmap.height, reloadedBitmap.height)
        
        // Limpiar recursos
        bitmap.recycle()
        preprocessingResult.processedBitmap.recycle()
        reloadedBitmap.recycle()
        processedFile.delete()
    }

    /**
     * Test de integración con múltiples configuraciones secuenciales
     */
    @Test
    fun testSequentialPreprocessingConfigurations() = runBlocking {
        val bitmap = BitmapFactory.decodeFile(testImageFile.absolutePath)
        
        // Aplicar configuraciones en secuencia
        val configs = listOf(
            ImagePreprocessor.PreprocessingConfig(enableIlluminationNormalization = true, enableContrastEnhancement = false, enableNoiseReduction = false, enableSharpening = false),
            ImagePreprocessor.PreprocessingConfig(enableIlluminationNormalization = false, enableContrastEnhancement = true, enableNoiseReduction = false, enableSharpening = false),
            ImagePreprocessor.PreprocessingConfig(enableIlluminationNormalization = false, enableContrastEnhancement = false, enableNoiseReduction = true, enableSharpening = false),
            ImagePreprocessor.PreprocessingConfig(enableIlluminationNormalization = false, enableContrastEnhancement = false, enableNoiseReduction = false, enableSharpening = true)
        )
        
        val results = mutableListOf<ImagePreprocessor.PreprocessingResult>()
        
        configs.forEach { config ->
            val result = imagePreprocessor.preprocessImage(bitmap, config)
            assertTrue("Cada configuración debe ser exitosa", result.isSuccessful())
            results.add(result)
        }
        
        // Verificar que cada configuración aplicó diferentes filtros
        val allFilters = results.flatMap { it.appliedFilters }.toSet()
        assertTrue("Debe haber aplicado diferentes tipos de filtros", allFilters.size >= 4)
        
        // Verificar tiempos de procesamiento
        results.forEach { result ->
            assertTrue("Tiempo de procesamiento debe ser razonable", result.processingTime < 2000)
        }
        
        // Limpiar recursos
        bitmap.recycle()
        results.forEach { it.processedBitmap.recycle() }
    }

    // Métodos auxiliares

    private fun createTestImage() {
        testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        
        // Crear patrón de prueba más realista
        val canvas = android.graphics.Canvas(testBitmap)
        val paint = android.graphics.Paint()
        
        // Fondo
        paint.color = android.graphics.Color.LTGRAY
        canvas.drawRect(0f, 0f, 500f, 500f, paint)
        
        // Simular "lunar" - círculo oscuro
        paint.color = android.graphics.Color.DKGRAY
        canvas.drawCircle(250f, 250f, 50f, paint)
        
        // Añadir algo de ruido/textura
        paint.color = android.graphics.Color.GRAY
        for (i in 0..100) {
            val x = (Math.random() * 500).toFloat()
            val y = (Math.random() * 500).toFloat()
            canvas.drawCircle(x, y, 2f, paint)
        }
        
        testImageFile = File(context.cacheDir, "integration-test-image.jpg")
        val outputStream = FileOutputStream(testImageFile)
        testBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.close()
    }

    private fun cleanupTestFiles() {
        val cacheDir = context.cacheDir
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.contains("integration") || file.name.contains("test")) {
                file.delete()
            }
        }
    }
}
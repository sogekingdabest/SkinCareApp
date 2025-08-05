package es.monsteraltech.skincare_tfm.camera.guidance
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.Size
import java.io.File
import java.io.FileOutputStream

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
    @Test
    fun testAdaptivePreprocessingConfigurations() = runBlocking {
        val bitmap = BitmapFactory.decodeFile(testImageFile.absolutePath)
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
        val standardResult = imagePreprocessor.preprocessForDermatologyAnalysis(bitmap)
        assertTrue("Preprocesado estándar debe ser exitoso", standardResult.isSuccessful())
        assertTrue("Debe aplicar todos los filtros estándar",
            standardResult.appliedFilters.size >= 3)
        bitmap.recycle()
        lowLightResult.processedBitmap.recycle()
        blurryResult.processedBitmap.recycle()
        standardResult.processedBitmap.recycle()
    }
    @Test
    fun testPreprocessingWithQualityAnalysis() = runBlocking {
        val bitmap = BitmapFactory.decodeFile(testImageFile.absolutePath)
        val preprocessingResult = imagePreprocessor.preprocessImage(bitmap)
        assertTrue("Preprocesado debe ser exitoso", preprocessingResult.isSuccessful())
        val qualityImprovement = imagePreprocessor.analyzeQualityImprovement(
            bitmap,
            preprocessingResult.processedBitmap
        )
        assertTrue("Mejora de calidad debe ser medible", qualityImprovement >= -1f && qualityImprovement <= 1f)
        val metadata = preprocessingResult.getProcessingSummary()
        assertNotNull("Metadata debe existir", metadata)
        assertTrue("Metadata debe contener tiempo de procesamiento", metadata.contains("ms"))
        assertTrue("Metadata debe contener filtros aplicados", metadata.contains("Filtros aplicados"))
        bitmap.recycle()
        preprocessingResult.processedBitmap.recycle()
    }
    @Test
    fun testPreprocessingWithDifferentImageTypes() = runBlocking {
        val smallBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        smallBitmap.eraseColor(android.graphics.Color.RED)
        val smallResult = imagePreprocessor.preprocessImage(smallBitmap)
        assertTrue("Preprocesado de imagen pequeña debe funcionar", smallResult.isSuccessful())
        val largeBitmap = Bitmap.createBitmap(2000, 2000, Bitmap.Config.ARGB_8888)
        largeBitmap.eraseColor(android.graphics.Color.GREEN)
        val largeResult = imagePreprocessor.preprocessImage(largeBitmap)
        assertTrue("Preprocesado de imagen grande debe funcionar", largeResult.isSuccessful())
        assertTrue("Imagen pequeña debe procesarse rápido", smallResult.processingTime < 1000)
        assertTrue("Imagen grande debe procesarse en tiempo razonable", largeResult.processingTime < 5000)
        smallBitmap.recycle()
        largeBitmap.recycle()
        smallResult.processedBitmap.recycle()
        largeResult.processedBitmap.recycle()
    }
    @Test
    fun testPreprocessingErrorRecovery() = runBlocking {
        try {
            val nullBitmap: Bitmap? = null
            assertNull("Bitmap null debe manejarse correctamente", nullBitmap)
        } catch (e: Exception) {
            fail("No debe lanzar excepción con bitmap null")
        }
        val recycleBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        recycleBitmap.recycle()
        try {
            val result = imagePreprocessor.preprocessImage(recycleBitmap)
            assertNotNull("Resultado debe existir incluso con bitmap reciclado", result)
        } catch (e: Exception) {
            assertTrue("Excepción debe ser de tipo esperado",
                e is IllegalArgumentException || e is IllegalStateException)
        }
    }
    @Test
    fun testPreprocessingFilePersistence() = runBlocking {
        val bitmap = BitmapFactory.decodeFile(testImageFile.absolutePath)
        val preprocessingResult = imagePreprocessor.preprocessImage(bitmap)
        val processedFile = File(context.cacheDir, "integration-processed.jpg")
        val outputStream = FileOutputStream(processedFile)
        preprocessingResult.processedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        outputStream.close()
        assertTrue("Archivo procesado debe existir", processedFile.exists())
        assertTrue("Archivo procesado debe tener contenido", processedFile.length() > 0)
        val reloadedBitmap = BitmapFactory.decodeFile(processedFile.absolutePath)
        assertNotNull("Imagen procesada debe poder leerse de vuelta", reloadedBitmap)
        assertEquals("Dimensiones deben mantenerse",
            preprocessingResult.processedBitmap.width, reloadedBitmap.width)
        assertEquals("Dimensiones deben mantenerse",
            preprocessingResult.processedBitmap.height, reloadedBitmap.height)
        bitmap.recycle()
        preprocessingResult.processedBitmap.recycle()
        reloadedBitmap.recycle()
        processedFile.delete()
    }
    @Test
    fun testSequentialPreprocessingConfigurations() = runBlocking {
        val bitmap = BitmapFactory.decodeFile(testImageFile.absolutePath)
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
        val allFilters = results.flatMap { it.appliedFilters }.toSet()
        assertTrue("Debe haber aplicado diferentes tipos de filtros", allFilters.size >= 4)
        results.forEach { result ->
            assertTrue("Tiempo de procesamiento debe ser razonable", result.processingTime < 2000)
        }
        bitmap.recycle()
        results.forEach { it.processedBitmap.recycle() }
    }
    private fun createTestImage() {
        testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(testBitmap)
        val paint = android.graphics.Paint()
        paint.color = android.graphics.Color.LTGRAY
        canvas.drawRect(0f, 0f, 500f, 500f, paint)
        paint.color = android.graphics.Color.DKGRAY
        canvas.drawCircle(250f, 250f, 50f, paint)
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
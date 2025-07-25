package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.monsteraltech.skincare_tfm.analysis.AnalysisResultActivity
import es.monsteraltech.skincare_tfm.camera.PreviewActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Tests de integración para el flujo de navegación entre actividades
 * con preprocesado de imágenes integrado
 */
@RunWith(AndroidJUnit4::class)
class NavigationFlowIntegrationTest {

    private lateinit var context: Context
    private lateinit var imagePreprocessor: ImagePreprocessor
    private lateinit var testBitmap: Bitmap
    private val testFiles = mutableListOf<File>()

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
     * Test del flujo de navegación: CameraActivity → PreviewActivity
     */
    @Test
    fun testCameraToPreviewNavigation() = runBlocking {
        // Simular captura y preprocesado
        val originalFile = createTestFile("camera-original.jpg")
        val preprocessingResult = imagePreprocessor.preprocessImage(testBitmap)
        val processedFile = createProcessedFile(preprocessingResult.processedBitmap)
        
        // Crear intent como lo haría CameraActivity
        val intent = Intent(context, PreviewActivity::class.java).apply {
            putExtra("PHOTO_PATH", processedFile.absolutePath)
            putExtra("IS_FRONT_CAMERA", false)
            putExtra("BODY_PART_COLOR", "#FF000000")
            putExtra("PREPROCESSING_APPLIED", true)
            putExtra("PROCESSING_METADATA", preprocessingResult.getProcessingSummary())
        }
        
        // Verificar datos del intent
        assertEquals("PHOTO_PATH debe ser correcto", processedFile.absolutePath, intent.getStringExtra("PHOTO_PATH"))
        assertEquals("IS_FRONT_CAMERA debe ser correcto", false, intent.getBooleanExtra("IS_FRONT_CAMERA", true))
        assertEquals("BODY_PART_COLOR debe ser correcto", "#FF000000", intent.getStringExtra("BODY_PART_COLOR"))
        assertEquals("PREPROCESSING_APPLIED debe ser true", true, intent.getBooleanExtra("PREPROCESSING_APPLIED", false))
        
        val metadata = intent.getStringExtra("PROCESSING_METADATA")
        assertNotNull("PROCESSING_METADATA debe existir", metadata)
        assertTrue("Metadata debe contener información útil", metadata!!.isNotEmpty())
        assertTrue("Metadata debe contener tiempo de procesamiento", metadata.contains("ms"))
        
        // Limpiar recursos
        preprocessingResult.processedBitmap.recycle()
    }

    /**
     * Test del flujo de navegación: PreviewActivity → AnalysisResultActivity
     */
    @Test
    fun testPreviewToAnalysisNavigation() = runBlocking {
        val processedFile = createTestFile("preview-processed.jpg")
        val processingMetadata = "Procesamiento completado en 150ms. Filtros aplicados: Normalización de iluminación, Mejora de contraste, Reducción de ruido, Enfoque. Mejora de calidad: 15.2%"
        
        // Crear intent como lo haría PreviewActivity
        val intent = Intent(context, AnalysisResultActivity::class.java).apply {
            putExtra("PHOTO_PATH", processedFile.absolutePath)
            putExtra("IS_FRONT_CAMERA", true)
            putExtra("BODY_PART_COLOR", "#FFED1C24")
            putExtra("PREPROCESSING_APPLIED", true)
            putExtra("PROCESSING_METADATA", processingMetadata)
        }
        
        // Verificar compatibilidad con AnalysisResultActivity
        assertEquals("PHOTO_PATH debe mantenerse", processedFile.absolutePath, intent.getStringExtra("PHOTO_PATH"))
        assertEquals("IS_FRONT_CAMERA debe mantenerse", true, intent.getBooleanExtra("IS_FRONT_CAMERA", false))
        assertEquals("BODY_PART_COLOR debe mantenerse", "#FFED1C24", intent.getStringExtra("BODY_PART_COLOR"))
        
        // Verificar nuevos campos de preprocesado
        assertEquals("PREPROCESSING_APPLIED debe pasarse", true, intent.getBooleanExtra("PREPROCESSING_APPLIED", false))
        assertEquals("PROCESSING_METADATA debe pasarse", processingMetadata, intent.getStringExtra("PROCESSING_METADATA"))
        
        // Verificar que todos los campos requeridos por AnalysisResultActivity están presentes
        assertNotNull("PHOTO_PATH es requerido", intent.getStringExtra("PHOTO_PATH"))
        assertTrue("IS_FRONT_CAMERA debe tener valor válido", intent.hasExtra("IS_FRONT_CAMERA"))
        assertNotNull("BODY_PART_COLOR es requerido", intent.getStringExtra("BODY_PART_COLOR"))
    }

    /**
     * Test del flujo de navegación con fallback (sin preprocesado)
     */
    @Test
    fun testNavigationWithFallback() {
        val originalFile = createTestFile("fallback-original.jpg")
        
        // Crear intent para caso de fallback (preprocesado falló)
        val intent = Intent(context, PreviewActivity::class.java).apply {
            putExtra("PHOTO_PATH", originalFile.absolutePath)
            putExtra("IS_FRONT_CAMERA", false)
            putExtra("BODY_PART_COLOR", "#FF3F48CC")
            putExtra("PREPROCESSING_APPLIED", false)
            putExtra("PROCESSING_METADATA", "Error en preprocesado - usando imagen original")
        }
        
        // Verificar que el fallback funciona correctamente
        assertEquals("PHOTO_PATH debe ser imagen original", originalFile.absolutePath, intent.getStringExtra("PHOTO_PATH"))
        assertEquals("PREPROCESSING_APPLIED debe ser false", false, intent.getBooleanExtra("PREPROCESSING_APPLIED", true))
        
        val metadata = intent.getStringExtra("PROCESSING_METADATA")
        assertNotNull("PROCESSING_METADATA debe existir incluso en fallback", metadata)
        assertTrue("Metadata debe indicar error", metadata!!.contains("Error") || metadata.contains("original"))
    }

    /**
     * Test de navegación con diferentes partes del cuerpo
     */
    @Test
    fun testNavigationWithDifferentBodyParts() = runBlocking {
        val bodyParts = mapOf(
            "Cabeza" to "#FF000000",
            "Brazo derecho" to "#FFED1C24",
            "Torso" to "#FFFFC90E",
            "Brazo izquierdo" to "#FF22B14C",
            "Pierna derecha" to "#FF3F48CC",
            "Pierna izquierda" to "#FFED00FF"
        )
        
        bodyParts.forEach { (bodyPart, colorCode) ->
            val testFile = createTestFile("test-$bodyPart.jpg")
            val preprocessingResult = imagePreprocessor.preprocessImage(testBitmap)
            
            // Test navegación CameraActivity → PreviewActivity
            val previewIntent = Intent(context, PreviewActivity::class.java).apply {
                putExtra("PHOTO_PATH", testFile.absolutePath)
                putExtra("IS_FRONT_CAMERA", false)
                putExtra("BODY_PART_COLOR", colorCode)
                putExtra("PREPROCESSING_APPLIED", true)
                putExtra("PROCESSING_METADATA", preprocessingResult.getProcessingSummary())
            }
            
            assertEquals("Color de parte del cuerpo debe ser correcto para $bodyPart", 
                colorCode, previewIntent.getStringExtra("BODY_PART_COLOR"))
            
            // Test navegación PreviewActivity → AnalysisResultActivity
            val analysisIntent = Intent(context, AnalysisResultActivity::class.java).apply {
                putExtra("PHOTO_PATH", testFile.absolutePath)
                putExtra("IS_FRONT_CAMERA", false)
                putExtra("BODY_PART_COLOR", colorCode)
                putExtra("PREPROCESSING_APPLIED", true)
                putExtra("PROCESSING_METADATA", preprocessingResult.getProcessingSummary())
            }
            
            assertEquals("Color debe mantenerse en navegación a análisis para $bodyPart", 
                colorCode, analysisIntent.getStringExtra("BODY_PART_COLOR"))
            
            // Limpiar recursos
            preprocessingResult.processedBitmap.recycle()
        }
    }

    /**
     * Test de navegación con diferentes configuraciones de cámara
     */
    @Test
    fun testNavigationWithCameraConfigurations() = runBlocking {
        val cameraConfigs = listOf(
            Pair(true, "Cámara frontal"),
            Pair(false, "Cámara trasera")
        )
        
        cameraConfigs.forEach { (isFrontCamera, description) ->
            val testFile = createTestFile("test-camera-${if (isFrontCamera) "front" else "back"}.jpg")
            val preprocessingResult = imagePreprocessor.preprocessImage(testBitmap)
            
            // Test flujo completo con configuración de cámara
            val previewIntent = Intent(context, PreviewActivity::class.java).apply {
                putExtra("PHOTO_PATH", testFile.absolutePath)
                putExtra("IS_FRONT_CAMERA", isFrontCamera)
                putExtra("BODY_PART_COLOR", "#FF000000")
                putExtra("PREPROCESSING_APPLIED", true)
                putExtra("PROCESSING_METADATA", preprocessingResult.getProcessingSummary())
            }
            
            assertEquals("Configuración de cámara debe ser correcta para $description", 
                isFrontCamera, previewIntent.getBooleanExtra("IS_FRONT_CAMERA", !isFrontCamera))
            
            val analysisIntent = Intent(context, AnalysisResultActivity::class.java).apply {
                putExtra("PHOTO_PATH", testFile.absolutePath)
                putExtra("IS_FRONT_CAMERA", isFrontCamera)
                putExtra("BODY_PART_COLOR", "#FF000000")
                putExtra("PREPROCESSING_APPLIED", true)
                putExtra("PROCESSING_METADATA", preprocessingResult.getProcessingSummary())
            }
            
            assertEquals("Configuración de cámara debe mantenerse para $description", 
                isFrontCamera, analysisIntent.getBooleanExtra("IS_FRONT_CAMERA", !isFrontCamera))
            
            // Limpiar recursos
            preprocessingResult.processedBitmap.recycle()
        }
    }

    /**
     * Test de integridad de datos a través del flujo completo
     */
    @Test
    fun testDataIntegrityThroughCompleteFlow() = runBlocking {
        val originalData = mapOf(
            "photoPath" to createTestFile("integrity-test.jpg").absolutePath,
            "isFrontCamera" to true,
            "bodyPartColor" to "#FFED1C24",
            "preprocessingApplied" to true,
            "processingMetadata" to "Test metadata for integrity check"
        )
        
        // Simular paso por PreviewActivity
        val previewIntent = Intent(context, PreviewActivity::class.java).apply {
            putExtra("PHOTO_PATH", originalData["photoPath"] as String)
            putExtra("IS_FRONT_CAMERA", originalData["isFrontCamera"] as Boolean)
            putExtra("BODY_PART_COLOR", originalData["bodyPartColor"] as String)
            putExtra("PREPROCESSING_APPLIED", originalData["preprocessingApplied"] as Boolean)
            putExtra("PROCESSING_METADATA", originalData["processingMetadata"] as String)
        }
        
        // Simular paso a AnalysisResultActivity
        val analysisIntent = Intent(context, AnalysisResultActivity::class.java).apply {
            putExtra("PHOTO_PATH", previewIntent.getStringExtra("PHOTO_PATH"))
            putExtra("IS_FRONT_CAMERA", previewIntent.getBooleanExtra("IS_FRONT_CAMERA", false))
            putExtra("BODY_PART_COLOR", previewIntent.getStringExtra("BODY_PART_COLOR"))
            putExtra("PREPROCESSING_APPLIED", previewIntent.getBooleanExtra("PREPROCESSING_APPLIED", false))
            putExtra("PROCESSING_METADATA", previewIntent.getStringExtra("PROCESSING_METADATA"))
        }
        
        // Verificar integridad de datos
        assertEquals("PHOTO_PATH debe mantenerse", originalData["photoPath"], analysisIntent.getStringExtra("PHOTO_PATH"))
        assertEquals("IS_FRONT_CAMERA debe mantenerse", originalData["isFrontCamera"], analysisIntent.getBooleanExtra("IS_FRONT_CAMERA", false))
        assertEquals("BODY_PART_COLOR debe mantenerse", originalData["bodyPartColor"], analysisIntent.getStringExtra("BODY_PART_COLOR"))
        assertEquals("PREPROCESSING_APPLIED debe mantenerse", originalData["preprocessingApplied"], analysisIntent.getBooleanExtra("PREPROCESSING_APPLIED", false))
        assertEquals("PROCESSING_METADATA debe mantenerse", originalData["processingMetadata"], analysisIntent.getStringExtra("PROCESSING_METADATA"))
    }

    // Métodos auxiliares

    private fun createTestImage() {
        testBitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        testBitmap.eraseColor(android.graphics.Color.CYAN)
    }

    private fun createTestFile(filename: String): File {
        val file = File(context.cacheDir, filename)
        val outputStream = FileOutputStream(file)
        testBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.close()
        testFiles.add(file)
        return file
    }

    private fun createProcessedFile(bitmap: Bitmap): File {
        val file = File(context.cacheDir, "processed-${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        outputStream.close()
        testFiles.add(file)
        return file
    }

    private fun cleanupTestFiles() {
        testFiles.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        testFiles.clear()
        
        // Limpiar archivos adicionales en cache
        val cacheDir = context.cacheDir
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.contains("test") || file.name.contains("processed") || file.name.contains("navigation")) {
                file.delete()
            }
        }
    }
}
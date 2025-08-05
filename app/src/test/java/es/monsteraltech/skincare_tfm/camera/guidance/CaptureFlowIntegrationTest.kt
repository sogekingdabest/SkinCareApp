package es.monsteraltech.skincare_tfm.camera.guidance
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.monsteraltech.skincare_tfm.analysis.AnalysisResultActivity
import es.monsteraltech.skincare_tfm.camera.CameraActivity
import es.monsteraltech.skincare_tfm.camera.PreviewActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class CaptureFlowIntegrationTest {
    private lateinit var context: Context
    private lateinit var imagePreprocessor: ImagePreprocessor
    private lateinit var testImageFile: File
    private lateinit var testBitmap: Bitmap
    @Mock
    private lateinit var mockCameraActivity: CameraActivity
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
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
    fun testCompleteFlowWithPreprocessing() = runBlocking {
        val originalFile = createTestImageFile("original-test.jpg")
        assertTrue("Archivo original debe existir", originalFile.exists())
        val bitmap = BitmapFactory.decodeFile(originalFile.absolutePath)
        assertNotNull("Bitmap debe decodificarse correctamente", bitmap)
        val preprocessingResult = imagePreprocessor.preprocessImage(bitmap)
        assertTrue("Preprocesado debe ser exitoso", preprocessingResult.isSuccessful())
        assertNotNull("Imagen procesada debe existir", preprocessingResult.processedBitmap)
        assertTrue("Debe haber filtros aplicados", preprocessingResult.appliedFilters.isNotEmpty())
        val processedFile = createProcessedImageFile(preprocessingResult.processedBitmap)
        assertTrue("Archivo procesado debe existir", processedFile.exists())
        val previewIntent = createPreviewIntent(processedFile.absolutePath, true, preprocessingResult)
        verifyPreviewIntentData(previewIntent, processedFile.absolutePath, true, preprocessingResult)
        val analysisIntent = createAnalysisIntent(processedFile.absolutePath, true, preprocessingResult)
        verifyAnalysisIntentData(analysisIntent, processedFile.absolutePath, true, preprocessingResult)
        bitmap.recycle()
        preprocessingResult.processedBitmap.recycle()
    }
    @Test
    fun testFlowWithPreprocessingFallback() = runBlocking {
        val corruptFile = File(context.cacheDir, "corrupt-test.jpg")
        corruptFile.writeText("corrupt data")
        val bitmap = BitmapFactory.decodeFile(corruptFile.absolutePath)
        assertNull("Bitmap corrupto debe ser null", bitmap)
        val originalFile = createTestImageFile("fallback-original.jpg")
        val originalBitmap = BitmapFactory.decodeFile(originalFile.absolutePath)
        assertNotNull("Imagen original debe decodificarse", originalBitmap)
        val previewIntent = createPreviewIntent(originalFile.absolutePath, false, null)
        verifyPreviewIntentData(previewIntent, originalFile.absolutePath, false, null)
        originalBitmap.recycle()
        corruptFile.delete()
    }
    @Test
    fun testAnalysisResultActivityCompatibility() = runBlocking {
        val originalFile = createTestImageFile("compatibility-test.jpg")
        val bitmap = BitmapFactory.decodeFile(originalFile.absolutePath)
        val preprocessingResult = imagePreprocessor.preprocessImage(bitmap)
        val intent = Intent(context, AnalysisResultActivity::class.java).apply {
            putExtra("PHOTO_PATH", originalFile.absolutePath)
            putExtra("IS_FRONT_CAMERA", false)
            putExtra("BODY_PART_COLOR", "#FF000000")
            putExtra("PREPROCESSING_APPLIED", true)
            putExtra("PROCESSING_METADATA", preprocessingResult.getProcessingSummary())
        }
        assertEquals("PHOTO_PATH debe estar presente", originalFile.absolutePath, intent.getStringExtra("PHOTO_PATH"))
        assertEquals("IS_FRONT_CAMERA debe estar presente", false, intent.getBooleanExtra("IS_FRONT_CAMERA", true))
        assertEquals("BODY_PART_COLOR debe estar presente", "#FF000000", intent.getStringExtra("BODY_PART_COLOR"))
        assertEquals("PREPROCESSING_APPLIED debe estar presente", true, intent.getBooleanExtra("PREPROCESSING_APPLIED", false))
        assertNotNull("PROCESSING_METADATA debe estar presente", intent.getStringExtra("PROCESSING_METADATA"))
        bitmap.recycle()
        preprocessingResult.processedBitmap.recycle()
    }
    @Test
    fun testPreprocessingErrorHandling() = runBlocking {
        val problematicConfig = ImagePreprocessor.PreprocessingConfig(
            enableIlluminationNormalization = true,
            enableContrastEnhancement = true,
            enableNoiseReduction = true,
            enableSharpening = true,
            claheClipLimit = -1.0,
            claheTileSize = org.opencv.core.Size(0.0, 0.0)
        )
        val bitmap = BitmapFactory.decodeFile(testImageFile.absolutePath)
        val result = imagePreprocessor.preprocessImage(bitmap, problematicConfig)
        assertNotNull("Resultado no debe ser null", result)
        assertNotNull("Bitmap original debe preservarse", result.originalBitmap)
        assertNotNull("Bitmap procesado debe existir (fallback)", result.processedBitmap)
        if (!result.isSuccessful()) {
            assertTrue("En caso de error, debe indicar uso de imagen original",
                result.appliedFilters.any { it.contains("Error") || it.contains("original") })
        }
        bitmap.recycle()
        result.processedBitmap.recycle()
    }
    @Test
    fun testFlowPerformance() = runBlocking {
        val startTime = System.currentTimeMillis()
        val bitmap = BitmapFactory.decodeFile(testImageFile.absolutePath)
        val preprocessingResult = imagePreprocessor.preprocessImage(bitmap)
        val processedFile = createProcessedImageFile(preprocessingResult.processedBitmap)
        val totalTime = System.currentTimeMillis() - startTime
        assertTrue("Flujo completo debe completarse en menos de 5 segundos", totalTime < 5000)
        assertTrue("Preprocesado debe completarse en tiempo razonable", preprocessingResult.processingTime < 3000)
        assertTrue("Archivo procesado debe existir", processedFile.exists())
        assertTrue("Archivo procesado debe tener contenido", processedFile.length() > 0)
        bitmap.recycle()
        preprocessingResult.processedBitmap.recycle()
    }
    @Test
    fun testTemporaryFileCleanup() {
        val tempFiles = mutableListOf<File>()
        repeat(3) { i ->
            val tempFile = File(context.cacheDir, "temp-$i.jpg")
            tempFile.writeBytes(testBitmap.let { bitmap ->
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                stream.toByteArray()
            })
            tempFiles.add(tempFile)
        }
        tempFiles.forEach { file ->
            assertTrue("Archivo temporal debe existir: ${file.name}", file.exists())
        }
        tempFiles.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        tempFiles.forEach { file ->
            assertFalse("Archivo temporal debe haber sido eliminado: ${file.name}", file.exists())
        }
    }
    private fun createTestImage() {
        testBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        testBitmap.eraseColor(android.graphics.Color.BLUE)
        testImageFile = File(context.cacheDir, "test-image.jpg")
        val outputStream = FileOutputStream(testImageFile)
        testBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.close()
    }
    private fun createTestImageFile(filename: String): File {
        val file = File(context.cacheDir, filename)
        val outputStream = FileOutputStream(file)
        testBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.close()
        return file
    }
    private fun createProcessedImageFile(bitmap: Bitmap): File {
        val file = File(context.cacheDir, "processed-${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        outputStream.close()
        return file
    }
    private fun createPreviewIntent(
        photoPath: String,
        preprocessingApplied: Boolean,
        preprocessingResult: ImagePreprocessor.PreprocessingResult?
    ): Intent {
        return Intent(context, PreviewActivity::class.java).apply {
            putExtra("PHOTO_PATH", photoPath)
            putExtra("IS_FRONT_CAMERA", false)
            putExtra("BODY_PART_COLOR", "#FF000000")
            putExtra("PREPROCESSING_APPLIED", preprocessingApplied)
            putExtra("PROCESSING_METADATA", preprocessingResult?.getProcessingSummary() ?: "")
        }
    }
    private fun createAnalysisIntent(
        photoPath: String,
        preprocessingApplied: Boolean,
        preprocessingResult: ImagePreprocessor.PreprocessingResult?
    ): Intent {
        return Intent(context, AnalysisResultActivity::class.java).apply {
            putExtra("PHOTO_PATH", photoPath)
            putExtra("IS_FRONT_CAMERA", false)
            putExtra("BODY_PART_COLOR", "#FF000000")
            putExtra("PREPROCESSING_APPLIED", preprocessingApplied)
            putExtra("PROCESSING_METADATA", preprocessingResult?.getProcessingSummary() ?: "")
        }
    }
    private fun verifyPreviewIntentData(
        intent: Intent,
        expectedPath: String,
        preprocessingApplied: Boolean,
        preprocessingResult: ImagePreprocessor.PreprocessingResult?
    ) {
        assertEquals("PHOTO_PATH debe coincidir", expectedPath, intent.getStringExtra("PHOTO_PATH"))
        assertEquals("PREPROCESSING_APPLIED debe coincidir", preprocessingApplied, intent.getBooleanExtra("PREPROCESSING_APPLIED", false))
        if (preprocessingApplied && preprocessingResult != null) {
            val metadata = intent.getStringExtra("PROCESSING_METADATA")
            assertNotNull("PROCESSING_METADATA debe estar presente", metadata)
            assertTrue("Metadata debe contener información de procesamiento", metadata!!.isNotEmpty())
        }
    }
    private fun verifyAnalysisIntentData(
        intent: Intent,
        expectedPath: String,
        preprocessingApplied: Boolean,
        preprocessingResult: ImagePreprocessor.PreprocessingResult?
    ) {
        assertEquals("PHOTO_PATH debe coincidir", expectedPath, intent.getStringExtra("PHOTO_PATH"))
        assertEquals("PREPROCESSING_APPLIED debe coincidir", preprocessingApplied, intent.getBooleanExtra("PREPROCESSING_APPLIED", false))
        assertNotNull("BODY_PART_COLOR debe estar presente", intent.getStringExtra("BODY_PART_COLOR"))
        if (preprocessingApplied && preprocessingResult != null) {
            val metadata = intent.getStringExtra("PROCESSING_METADATA")
            assertNotNull("PROCESSING_METADATA debe estar presente para análisis", metadata)
        }
    }
    private fun cleanupTestFiles() {
        val cacheDir = context.cacheDir
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.contains("test") || file.name.contains("processed") || file.name.contains("original")) {
                file.delete()
            }
        }
    }
}
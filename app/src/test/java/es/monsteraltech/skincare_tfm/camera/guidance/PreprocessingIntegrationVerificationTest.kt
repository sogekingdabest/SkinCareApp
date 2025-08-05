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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
@RunWith(AndroidJUnit4::class)
class PreprocessingIntegrationVerificationTest {
    private lateinit var context: Context
    private lateinit var imagePreprocessor: ImagePreprocessor
    private lateinit var testBitmap: Bitmap
    private val testFiles = mutableListOf<File>()
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        imagePreprocessor = ImagePreprocessor()
        testBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        testBitmap.eraseColor(android.graphics.Color.BLUE)
    }
    @After
    fun tearDown() {
        cleanupTestFiles()
        if (::testBitmap.isInitialized && !testBitmap.isRecycled) {
            testBitmap.recycle()
        }
    }
    @Test
    fun testBasicPreprocessingIntegration() = runBlocking {
        val result = imagePreprocessor.preprocessImage(testBitmap)
        assertNotNull("Resultado no debe ser null", result)
        assertNotNull("Bitmap original debe existir", result.originalBitmap)
        assertNotNull("Bitmap procesado debe existir", result.processedBitmap)
        assertTrue("Debe haber filtros aplicados", result.appliedFilters.isNotEmpty())
        assertTrue("Tiempo de procesamiento debe ser positivo", result.processingTime > 0)
        val testFile = createTestFile("verification-test.jpg", result.processedBitmap)
        assertTrue("Archivo debe existir", testFile.exists())
        assertTrue("Archivo debe tener contenido", testFile.length() > 0)
        result.processedBitmap.recycle()
    }
    @Test
    fun testNavigationWithMetadata() = runBlocking {
        val testFile = createTestFile("navigation-test.jpg", testBitmap)
        val result = imagePreprocessor.preprocessImage(testBitmap)
        val previewIntent = Intent(context, PreviewActivity::class.java).apply {
            putExtra("PHOTO_PATH", testFile.absolutePath)
            putExtra("IS_FRONT_CAMERA", false)
            putExtra("BODY_PART_COLOR", "#FF000000")
            putExtra("PREPROCESSING_APPLIED", true)
            putExtra("PROCESSING_METADATA", result.getProcessingSummary())
        }
        assertEquals("PHOTO_PATH debe estar presente", testFile.absolutePath, previewIntent.getStringExtra("PHOTO_PATH"))
        assertEquals("PREPROCESSING_APPLIED debe ser true", true, previewIntent.getBooleanExtra("PREPROCESSING_APPLIED", false))
        assertNotNull("PROCESSING_METADATA debe estar presente", previewIntent.getStringExtra("PROCESSING_METADATA"))
        val analysisIntent = Intent(context, AnalysisResultActivity::class.java).apply {
            putExtra("PHOTO_PATH", previewIntent.getStringExtra("PHOTO_PATH"))
            putExtra("IS_FRONT_CAMERA", previewIntent.getBooleanExtra("IS_FRONT_CAMERA", false))
            putExtra("BODY_PART_COLOR", previewIntent.getStringExtra("BODY_PART_COLOR"))
            putExtra("PREPROCESSING_APPLIED", previewIntent.getBooleanExtra("PREPROCESSING_APPLIED", false))
            putExtra("PROCESSING_METADATA", previewIntent.getStringExtra("PROCESSING_METADATA"))
        }
        assertEquals("PHOTO_PATH debe mantenerse", testFile.absolutePath, analysisIntent.getStringExtra("PHOTO_PATH"))
        assertEquals("PREPROCESSING_APPLIED debe mantenerse", true, analysisIntent.getBooleanExtra("PREPROCESSING_APPLIED", false))
        result.processedBitmap.recycle()
    }
    @Test
    fun testFallbackBehavior() {
        val testFile = createTestFile("fallback-test.jpg", testBitmap)
        val intent = Intent(context, PreviewActivity::class.java).apply {
            putExtra("PHOTO_PATH", testFile.absolutePath)
            putExtra("IS_FRONT_CAMERA", false)
            putExtra("BODY_PART_COLOR", "#FF000000")
            putExtra("PREPROCESSING_APPLIED", false)
            putExtra("PROCESSING_METADATA", "Error en preprocesado - usando imagen original")
        }
        assertEquals("PREPROCESSING_APPLIED debe ser false en fallback", false, intent.getBooleanExtra("PREPROCESSING_APPLIED", true))
        val metadata = intent.getStringExtra("PROCESSING_METADATA")
        assertNotNull("Metadata debe existir incluso en fallback", metadata)
        assertTrue("Metadata debe indicar error o uso de original",
            metadata!!.contains("Error") || metadata.contains("original"))
    }
    private fun createTestFile(filename: String, bitmap: Bitmap): File {
        val file = File(context.cacheDir, filename)
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
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
    }
}
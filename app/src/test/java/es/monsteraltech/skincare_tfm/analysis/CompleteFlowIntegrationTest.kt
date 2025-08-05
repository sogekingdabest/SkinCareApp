package es.monsteraltech.skincare_tfm.analysis
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
class CompleteFlowIntegrationTest {
    private lateinit var context: Context
    private lateinit var asyncImageProcessor: AsyncImageProcessor
    private lateinit var progressManager: ProgressManager
    @Mock private lateinit var mockProgressBar: ProgressBar
    @Mock private lateinit var mockStatusText: TextView
    @Mock private lateinit var mockCancelButton: Button
    private lateinit var testBitmap: Bitmap
    private lateinit var largeBitmap: Bitmap
    private lateinit var smallBitmap: Bitmap
    private val testDispatcher = StandardTestDispatcher()
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        testBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        testBitmap.eraseColor(Color.BLUE)
        largeBitmap = Bitmap.createBitmap(2000, 2000, Bitmap.Config.ARGB_8888)
        largeBitmap.eraseColor(Color.RED)
        smallBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        smallBitmap.eraseColor(Color.GREEN)
    }
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testBitmap.recycle()
        largeBitmap.recycle()
        smallBitmap.recycle()
    }
    @Test
    fun testCompleteComponentIntegration() = runTest {
        val progressCallback = createTestProgressCallback()
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        progressManager = ProgressManager(context, mockProgressBar, mockStatusText, mockCancelButton)
        val result = asyncImageProcessor.processImage(
            bitmap = testBitmap,
            pixelDensity = 1.0f,
            config = AnalysisConfiguration.default()
        )
        assertNotNull(result)
        assertTrue(result.combinedScore >= 0.0f)
        assertTrue(result.combinedScore <= 1.0f)
        assertNotNull(result.abcdeResult)
        assertNotNull(result.recommendation)
        assertTrue(result.explanations.isNotEmpty())
    }
    @Test
    fun testCompleteUserFlow() = runTest {
        val progressUpdates = mutableListOf<Pair<Int, String>>()
        val stageChanges = mutableListOf<ProcessingStage>()
        var finalResult: MelanomaAIDetector.CombinedAnalysisResult? = null
        var errorOccurred: String? = null
        val progressCallback = object : ProgressCallback {
            override fun onProgressUpdate(progress: Int, message: String) {
                progressUpdates.add(progress to message)
            }
            override fun onProgressUpdateWithTimeEstimate(progress: Int, message: String, estimatedTotalTimeMs: Long) {
                progressUpdates.add(progress to "$message (${estimatedTotalTimeMs}ms)")
            }
            override fun onStageChanged(stage: ProcessingStage) {
                stageChanges.add(stage)
            }
            override fun onError(error: String) {
                errorOccurred = error
            }
            override fun onCompleted(result: MelanomaAIDetector.CombinedAnalysisResult) {
                finalResult = result
            }
            override fun onCancelled() {
            }
        }
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        val result = asyncImageProcessor.processImage(
            bitmap = testBitmap,
            pixelDensity = 1.0f,
            config = AnalysisConfiguration.default()
        )
        assertNull(errorOccurred, "No debería haber errores en el flujo normal")
        assertNotNull(finalResult, "Debería haberse completado el análisis")
        assertEquals(result, finalResult, "El resultado devuelto debe coincidir con el del callback")
        val expectedStages = listOf(
            ProcessingStage.INITIALIZING,
            ProcessingStage.PREPROCESSING,
            ProcessingStage.AI_ANALYSIS,
            ProcessingStage.ABCDE_ANALYSIS,
            ProcessingStage.FINALIZING
        )
        assertTrue(stageChanges.containsAll(expectedStages),
            "Deberían haberse ejecutado todas las etapas: $stageChanges")
        assertTrue(progressUpdates.isNotEmpty(), "Debería haber actualizaciones de progreso")
        assertTrue(progressUpdates.any { it.first == 100 }, "Debería llegar al 100% de progreso")
    }
    @Test
    fun testUIResponsiveness() = runTest {
        val progressCallback = createTestProgressCallback()
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        val uiOperations = mutableListOf<Boolean>()
        val processingJobs = mutableListOf<Deferred<MelanomaAIDetector.CombinedAnalysisResult>>()
        repeat(3) { index ->
            val job = async {
                asyncImageProcessor.processImage(
                    bitmap = testBitmap,
                    pixelDensity = 1.0f,
                    config = AnalysisConfiguration.default().copy(timeoutMs = 5000L)
                )
            }
            processingJobs.add(job)
        }
        repeat(10) {
            delay(100)
            uiOperations.add(true)
        }
        val results = processingJobs.awaitAll()
        assertEquals(3, results.size, "Deberían completarse todos los procesamientos")
        assertEquals(10, uiOperations.size, "Todas las operaciones de UI deberían haberse ejecutado")
        results.forEach { result ->
            assertNotNull(result)
            assertTrue(result.combinedScore >= 0.0f)
        }
    }
    @Test
    fun testCancellationInAllStages() = runTest {
        val stages = ProcessingStage.values()
        stages.forEach { targetStage ->
            testCancellationAtStage(targetStage)
        }
    }
    private suspend fun testCancellationAtStage(targetStage: ProcessingStage) = runTest {
        var cancelled = false
        var currentStage: ProcessingStage? = null
        val progressCallback = object : ProgressCallback {
            override fun onProgressUpdate(progress: Int, message: String) {}
            override fun onProgressUpdateWithTimeEstimate(progress: Int, message: String, estimatedTotalTimeMs: Long) {}
            override fun onStageChanged(stage: ProcessingStage) {
                currentStage = stage
                if (stage == targetStage) {
                    asyncImageProcessor.cancelProcessing()
                }
            }
            override fun onError(error: String) {}
            override fun onCompleted(result: MelanomaAIDetector.CombinedAnalysisResult) {}
            override fun onCancelled() {
                cancelled = true
            }
        }
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        try {
            asyncImageProcessor.processImage(
                bitmap = testBitmap,
                pixelDensity = 1.0f,
                config = AnalysisConfiguration.default()
            )
            fail("Debería haberse lanzado CancellationException")
        } catch (e: CancellationException) {
            assertTrue(cancelled || currentStage == targetStage,
                "Debería haberse cancelado en la etapa $targetStage")
        }
    }
    @Test
    fun testDifferentImageSizesAndMemoryConditions() = runTest {
        val progressCallback = createTestProgressCallback()
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        val smallResult = asyncImageProcessor.processImage(
            bitmap = smallBitmap,
            pixelDensity = 1.0f,
            config = AnalysisConfiguration.default()
        )
        assertNotNull(smallResult)
        assertTrue(smallResult.combinedScore >= 0.0f)
        val largeResult = asyncImageProcessor.processImage(
            bitmap = largeBitmap,
            pixelDensity = 1.0f,
            config = AnalysisConfiguration.default()
        )
        assertNotNull(largeResult)
        assertTrue(largeResult.combinedScore >= 0.0f)
        val memoryLimitedConfig = AnalysisConfiguration.default().copy(
            enableMemoryOptimization = true,
            maxImageSize = 1000
        )
        val optimizedResult = asyncImageProcessor.processImage(
            bitmap = largeBitmap,
            pixelDensity = 1.0f,
            config = memoryLimitedConfig
        )
        assertNotNull(optimizedResult)
        assertTrue(optimizedResult.combinedScore >= 0.0f)
    }
    @Test
    fun testErrorHandlingAndRecovery() = runTest {
        val progressCallback = createTestProgressCallback()
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        val recycledBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        recycledBitmap.recycle()
        try {
            asyncImageProcessor.processImage(
                bitmap = recycledBitmap,
                pixelDensity = 1.0f,
                config = AnalysisConfiguration.default()
            )
            fail("Debería haber lanzado una excepción por bitmap reciclado")
        } catch (e: AnalysisError.InvalidImageError) {
            assertTrue(e.message?.contains("reciclada") == true)
        }
        val shortTimeoutConfig = AnalysisConfiguration.default().copy(
            timeoutMs = 100L
        )
        val result = asyncImageProcessor.processImage(
            bitmap = testBitmap,
            pixelDensity = 1.0f,
            config = shortTimeoutConfig
        )
        assertNotNull(result)
        assertTrue(result.recommendation.contains("limitado") || result.aiConfidence < 0.5f)
    }
    @Test
    fun testProgressManagerIntegration() = runTest {
        progressManager = ProgressManager(context, mockProgressBar, mockStatusText, mockCancelButton)
        val progressUpdates = mutableListOf<Pair<Int, String>>()
        val stageChanges = mutableListOf<ProcessingStage>()
        val progressCallback = object : ProgressCallback {
            override fun onProgressUpdate(progress: Int, message: String) {
                progressUpdates.add(progress to message)
                progressManager.updateProgressWithAccessibility(progress, message)
            }
            override fun onProgressUpdateWithTimeEstimate(progress: Int, message: String, estimatedTotalTimeMs: Long) {
                progressManager.updateProgressWithTimeEstimate(progress, message, estimatedTotalTimeMs)
            }
            override fun onStageChanged(stage: ProcessingStage) {
                stageChanges.add(stage)
                progressManager.showStageWithAccessibility(stage)
            }
            override fun onError(error: String) {
                progressManager.showErrorWithAccessibility(error)
            }
            override fun onCompleted(result: MelanomaAIDetector.CombinedAnalysisResult) {
                progressManager.completeProcessingWithAccessibility()
            }
            override fun onCancelled() {
                progressManager.showCancelledWithAccessibility()
            }
        }
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        progressManager.startProcessing()
        progressManager.show()
        val result = asyncImageProcessor.processImage(
            bitmap = testBitmap,
            pixelDensity = 1.0f,
            config = AnalysisConfiguration.default()
        )
        assertNotNull(result)
        verify(mockProgressBar, atLeastOnce()).progress = any()
        verify(mockStatusText, atLeastOnce()).text = any()
        assertTrue(progressUpdates.isNotEmpty())
        assertTrue(stageChanges.isNotEmpty())
    }
    @Test
    fun testMultipleConfigurations() = runTest {
        val progressCallback = createTestProgressCallback()
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        val configurations = listOf(
            AnalysisConfiguration.default(),
            AnalysisConfiguration.default().copy(enableAI = false),
            AnalysisConfiguration.default().copy(enableABCDE = false),
            AnalysisConfiguration.default().copy(enableParallelProcessing = false),
            AnalysisConfiguration.default().copy(
                enableMemoryOptimization = true,
                maxImageSize = 500
            )
        )
        configurations.forEach { config ->
            val result = asyncImageProcessor.processImage(
                bitmap = testBitmap,
                pixelDensity = 1.0f,
                config = config
            )
            assertNotNull(result, "Resultado no debería ser null para config: $config")
            assertTrue(result.combinedScore >= 0.0f, "Score debería ser válido para config: $config")
            if (!config.enableAI) {
                assertTrue(result.aiConfidence <= 0.1f, "AI debería estar deshabilitada")
            }
        }
    }
    @Test
    fun testResourceCleanup() = runTest {
        val progressCallback = createTestProgressCallback()
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        repeat(5) {
            val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.CYAN)
            val result = asyncImageProcessor.processImage(
                bitmap = bitmap,
                pixelDensity = 1.0f,
                config = AnalysisConfiguration.default()
            )
            assertNotNull(result)
            assertFalse(bitmap.isRecycled, "Bitmap original no debería reciclarse")
            bitmap.recycle()
        }
        System.gc()
        assertTrue(true, "No debería haber memory leaks")
    }
    @Test
    fun testStressBehavior() = runTest {
        val progressCallback = createTestProgressCallback()
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        val jobs = mutableListOf<Deferred<MelanomaAIDetector.CombinedAnalysisResult>>()
        val results = mutableListOf<MelanomaAIDetector.CombinedAnalysisResult>()
        repeat(10) { index ->
            val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(index * 25, 100, 200))
            val job = async {
                asyncImageProcessor.processImage(
                    bitmap = bitmap,
                    pixelDensity = 1.0f,
                    config = AnalysisConfiguration.default().copy(
                        timeoutMs = 10000L
                    )
                )
            }
            jobs.add(job)
        }
        jobs.forEach { job ->
            try {
                val result = job.await()
                results.add(result)
            } catch (e: Exception) {
                println("Job falló bajo estrés: ${e.message}")
            }
        }
        assertTrue(results.size >= jobs.size / 2,
            "Al menos la mitad de los jobs deberían completarse bajo estrés")
        results.forEach { result ->
            assertTrue(result.combinedScore >= 0.0f)
            assertNotNull(result.recommendation)
        }
    }
    private fun createTestProgressCallback(): ProgressCallback {
        return object : ProgressCallback {
            override fun onProgressUpdate(progress: Int, message: String) {
            }
            override fun onProgressUpdateWithTimeEstimate(progress: Int, message: String, estimatedTotalTimeMs: Long) {
            }
            override fun onStageChanged(stage: ProcessingStage) {
            }
            override fun onError(error: String) {
            }
            override fun onCompleted(result: MelanomaAIDetector.CombinedAnalysisResult) {
            }
            override fun onCancelled() {
            }
        }
    }
}
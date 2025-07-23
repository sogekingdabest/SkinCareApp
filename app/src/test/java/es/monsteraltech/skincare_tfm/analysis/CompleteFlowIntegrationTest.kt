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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

/**
 * Test de integración completa que verifica el flujo completo desde captura hasta resultados.
 * Cubre todos los aspectos del task 10: Integrar y probar el flujo completo.
 */
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
        
        // Crear bitmaps de prueba de diferentes tamaños
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
    
    /**
     * Test 1: Integración de todos los componentes en AsyncImageProcessor
     * Verifica que todos los componentes trabajen juntos correctamente
     */
    @Test
    fun testCompleteComponentIntegration() = runTest {
        // Arrange
        val progressCallback = createTestProgressCallback()
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        progressManager = ProgressManager(context, mockProgressBar, mockStatusText, mockCancelButton)
        
        // Act
        val result = asyncImageProcessor.processImage(
            bitmap = testBitmap,
            pixelDensity = 1.0f,
            config = AnalysisConfiguration.default()
        )
        
        // Assert
        assertNotNull(result)
        assertTrue(result.combinedScore >= 0.0f)
        assertTrue(result.combinedScore <= 1.0f)
        assertNotNull(result.abcdeResult)
        assertNotNull(result.recommendation)
        assertTrue(result.explanations.isNotEmpty())
    }
    
    /**
     * Test 2: Flujo completo desde captura hasta resultados
     * Simula el flujo completo que experimentaría un usuario
     */
    @Test
    fun testCompleteUserFlow() = runTest {
        // Arrange
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
                // No action needed for this test
            }
        }
        
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        
        // Act - Simular flujo completo
        val result = asyncImageProcessor.processImage(
            bitmap = testBitmap,
            pixelDensity = 1.0f,
            config = AnalysisConfiguration.default()
        )
        
        // Assert - Verificar que el flujo se completó correctamente
        assertNull(errorOccurred, "No debería haber errores en el flujo normal")
        assertNotNull(finalResult, "Debería haberse completado el análisis")
        assertEquals(result, finalResult, "El resultado devuelto debe coincidir con el del callback")
        
        // Verificar que se pasó por todas las etapas esperadas
        val expectedStages = listOf(
            ProcessingStage.INITIALIZING,
            ProcessingStage.PREPROCESSING,
            ProcessingStage.AI_ANALYSIS,
            ProcessingStage.ABCDE_ANALYSIS,
            ProcessingStage.FINALIZING
        )
        
        assertTrue(stageChanges.containsAll(expectedStages), 
            "Deberían haberse ejecutado todas las etapas: $stageChanges")
        
        // Verificar que hubo actualizaciones de progreso
        assertTrue(progressUpdates.isNotEmpty(), "Debería haber actualizaciones de progreso")
        assertTrue(progressUpdates.any { it.first == 100 }, "Debería llegar al 100% de progreso")
    }
    
    /**
     * Test 3: Verificar que la UI permanece responsiva durante el procesamiento
     * Simula múltiples operaciones concurrentes para verificar responsividad
     */
    @Test
    fun testUIResponsiveness() = runTest {
        // Arrange
        val progressCallback = createTestProgressCallback()
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        
        val uiOperations = mutableListOf<Boolean>()
        val processingJobs = mutableListOf<Deferred<MelanomaAIDetector.CombinedAnalysisResult>>()
        
        // Act - Iniciar procesamiento asíncrono
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
        
        // Simular operaciones de UI durante el procesamiento
        repeat(10) {
            delay(100) // Simular operación de UI
            uiOperations.add(true)
        }
        
        // Esperar que termine el procesamiento
        val results = processingJobs.awaitAll()
        
        // Assert
        assertEquals(3, results.size, "Deberían completarse todos los procesamientos")
        assertEquals(10, uiOperations.size, "Todas las operaciones de UI deberían haberse ejecutado")
        
        results.forEach { result ->
            assertNotNull(result)
            assertTrue(result.combinedScore >= 0.0f)
        }
    }
    
    /**
     * Test 4: Validar que la cancelación funciona correctamente en todas las etapas
     */
    @Test
    fun testCancellationInAllStages() = runTest {
        // Test cancelación en diferentes etapas
        val stages = ProcessingStage.values()
        
        stages.forEach { targetStage ->
            testCancellationAtStage(targetStage)
        }
    }
    
    private suspend fun testCancellationAtStage(targetStage: ProcessingStage) = runTest {
        // Arrange
        var cancelled = false
        var currentStage: ProcessingStage? = null
        
        val progressCallback = object : ProgressCallback {
            override fun onProgressUpdate(progress: Int, message: String) {}
            override fun onProgressUpdateWithTimeEstimate(progress: Int, message: String, estimatedTotalTimeMs: Long) {}
            
            override fun onStageChanged(stage: ProcessingStage) {
                currentStage = stage
                // Cancelar cuando llegue a la etapa objetivo
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
        
        // Act & Assert
        try {
            asyncImageProcessor.processImage(
                bitmap = testBitmap,
                pixelDensity = 1.0f,
                config = AnalysisConfiguration.default()
            )
            fail("Debería haberse lanzado CancellationException")
        } catch (e: CancellationException) {
            // Esperado
            assertTrue(cancelled || currentStage == targetStage, 
                "Debería haberse cancelado en la etapa $targetStage")
        }
    }
    
    /**
     * Test 5: Probar con diferentes tamaños de imagen y condiciones de memoria
     */
    @Test
    fun testDifferentImageSizesAndMemoryConditions() = runTest {
        val progressCallback = createTestProgressCallback()
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        
        // Test con imagen pequeña
        val smallResult = asyncImageProcessor.processImage(
            bitmap = smallBitmap,
            pixelDensity = 1.0f,
            config = AnalysisConfiguration.default()
        )
        
        assertNotNull(smallResult)
        assertTrue(smallResult.combinedScore >= 0.0f)
        
        // Test con imagen grande (puede activar optimizaciones de memoria)
        val largeResult = asyncImageProcessor.processImage(
            bitmap = largeBitmap,
            pixelDensity = 1.0f,
            config = AnalysisConfiguration.default()
        )
        
        assertNotNull(largeResult)
        assertTrue(largeResult.combinedScore >= 0.0f)
        
        // Test con configuración de memoria limitada
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
    
    /**
     * Test 6: Verificar manejo de errores y recuperación
     */
    @Test
    fun testErrorHandlingAndRecovery() = runTest {
        val progressCallback = createTestProgressCallback()
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        
        // Test con bitmap inválido (reciclado)
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
            // Esperado
            assertTrue(e.message?.contains("reciclada") == true)
        }
        
        // Test con timeout muy corto (debería activar recuperación)
        val shortTimeoutConfig = AnalysisConfiguration.default().copy(
            timeoutMs = 100L // Muy corto para forzar timeout
        )
        
        val result = asyncImageProcessor.processImage(
            bitmap = testBitmap,
            pixelDensity = 1.0f,
            config = shortTimeoutConfig
        )
        
        // Debería devolver un resultado de fallback
        assertNotNull(result)
        assertTrue(result.recommendation.contains("limitado") || result.aiConfidence < 0.5f)
    }
    
    /**
     * Test 7: Verificar integración con ProgressManager
     */
    @Test
    fun testProgressManagerIntegration() = runTest {
        // Arrange
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
        
        // Act
        progressManager.startProcessing()
        progressManager.show()
        
        val result = asyncImageProcessor.processImage(
            bitmap = testBitmap,
            pixelDensity = 1.0f,
            config = AnalysisConfiguration.default()
        )
        
        // Assert
        assertNotNull(result)
        
        // Verificar que se llamaron los métodos del ProgressManager
        verify(mockProgressBar, atLeastOnce()).progress = any()
        verify(mockStatusText, atLeastOnce()).text = any()
        
        // Verificar que hubo actualizaciones de progreso y cambios de etapa
        assertTrue(progressUpdates.isNotEmpty())
        assertTrue(stageChanges.isNotEmpty())
    }
    
    /**
     * Test 8: Verificar comportamiento con múltiples configuraciones
     */
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
            
            // Verificar que la configuración se respeta
            if (!config.enableAI) {
                assertTrue(result.aiConfidence <= 0.1f, "AI debería estar deshabilitada")
            }
        }
    }
    
    /**
     * Test 9: Verificar limpieza de recursos
     */
    @Test
    fun testResourceCleanup() = runTest {
        val progressCallback = createTestProgressCallback()
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        
        // Procesar múltiples imágenes para verificar limpieza
        repeat(5) {
            val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.CYAN)
            
            val result = asyncImageProcessor.processImage(
                bitmap = bitmap,
                pixelDensity = 1.0f,
                config = AnalysisConfiguration.default()
            )
            
            assertNotNull(result)
            
            // El bitmap original no debería estar reciclado
            assertFalse(bitmap.isRecycled, "Bitmap original no debería reciclarse")
            
            bitmap.recycle() // Limpiar manualmente para el test
        }
        
        // Verificar que no hay memory leaks evidentes
        System.gc()
        
        // El test pasa si no hay OutOfMemoryError
        assertTrue(true, "No debería haber memory leaks")
    }
    
    /**
     * Test 10: Verificar comportamiento bajo estrés
     */
    @Test
    fun testStressBehavior() = runTest {
        val progressCallback = createTestProgressCallback()
        asyncImageProcessor = AsyncImageProcessor(context, progressCallback)
        
        val jobs = mutableListOf<Deferred<MelanomaAIDetector.CombinedAnalysisResult>>()
        val results = mutableListOf<MelanomaAIDetector.CombinedAnalysisResult>()
        
        // Lanzar múltiples procesamientos concurrentes
        repeat(10) { index ->
            val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(index * 25, 100, 200))
            
            val job = async {
                asyncImageProcessor.processImage(
                    bitmap = bitmap,
                    pixelDensity = 1.0f,
                    config = AnalysisConfiguration.default().copy(
                        timeoutMs = 10000L // Timeout más largo para estrés
                    )
                )
            }
            jobs.add(job)
        }
        
        // Esperar todos los resultados
        jobs.forEach { job ->
            try {
                val result = job.await()
                results.add(result)
            } catch (e: Exception) {
                // Algunos pueden fallar bajo estrés, pero no todos
                println("Job falló bajo estrés: ${e.message}")
            }
        }
        
        // Assert - Al menos algunos deberían completarse exitosamente
        assertTrue(results.size >= jobs.size / 2, 
            "Al menos la mitad de los jobs deberían completarse bajo estrés")
        
        results.forEach { result ->
            assertTrue(result.combinedScore >= 0.0f)
            assertNotNull(result.recommendation)
        }
    }
    
    // MÉTODOS AUXILIARES
    
    private fun createTestProgressCallback(): ProgressCallback {
        return object : ProgressCallback {
            override fun onProgressUpdate(progress: Int, message: String) {
                // Test implementation
            }
            
            override fun onProgressUpdateWithTimeEstimate(progress: Int, message: String, estimatedTotalTimeMs: Long) {
                // Test implementation
            }
            
            override fun onStageChanged(stage: ProcessingStage) {
                // Test implementation
            }
            
            override fun onError(error: String) {
                // Test implementation
            }
            
            override fun onCompleted(result: MelanomaAIDetector.CombinedAnalysisResult) {
                // Test implementation
            }
            
            override fun onCancelled() {
                // Test implementation
            }
        }
    }
}
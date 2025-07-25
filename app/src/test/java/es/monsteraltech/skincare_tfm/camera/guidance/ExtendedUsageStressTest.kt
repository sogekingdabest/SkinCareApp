package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import kotlin.test.*

/**
 * Tests de estrés para uso prolongado de cámara.
 * Verifica estabilidad, rendimiento y gestión de recursos durante sesiones largas.
 * 
 * Cubre requisitos de estabilidad y rendimiento a largo plazo para todos los componentes
 * del sistema de guías de captura inteligente.
 */
@RunWith(AndroidJUnit4::class)
class ExtendedUsageStressTest {

    private lateinit var context: Context
    private lateinit var moleDetector: MoleDetectionProcessor
    private lateinit var qualityAnalyzer: ImageQualityAnalyzer
    private lateinit var validationManager: CaptureValidationManager
    private lateinit var preprocessor: ImagePreprocessor
    private lateinit var overlay: CaptureGuidanceOverlay
    private lateinit var performanceManager: PerformanceManager
    private lateinit var captureMetrics: CaptureMetrics
    private lateinit var accessibilityManager: AccessibilityManager
    private lateinit var hapticManager: HapticFeedbackManager
    
    private val testDispatcher = StandardTestDispatcher()
    private val config = CaptureGuidanceConfig()
    
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        
        context = ApplicationProvider.getApplicationContext()
        initializeComponents()
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        // Forzar limpieza de memoria
        System.gc()
    }
    
    private fun initializeComponents() {
        moleDetector = MoleDetectionProcessor(context)
        qualityAnalyzer = ImageQualityAnalyzer(context)
        validationManager = CaptureValidationManager(config)
        preprocessor = ImagePreprocessor(context)
        overlay = CaptureGuidanceOverlay(context)
        performanceManager = PerformanceManager(context)
        captureMetrics = CaptureMetrics()
        accessibilityManager = AccessibilityManager(context)
        hapticManager = HapticFeedbackManager(context)
    }
    
    /**
     * Test 1: Sesión de uso prolongado (30 minutos simulados)
     * Simula una sesión real de uso continuo de la cámara
     */
    @Test
    fun testExtendedCameraSession() = runTest {
        val sessionDurationMinutes = 30
        val framesPerSecond = 15
        val totalFrames = sessionDurationMinutes * 60 * framesPerSecond
        
        val processingTimes = mutableListOf<Long>()
        val memorySnapshots = mutableListOf<Long>()
        val detectionSuccessRate = mutableListOf<Boolean>()
        val validationResults = mutableListOf<CaptureValidationManager.ValidationResult>()
        
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        println("Iniciando sesión de estrés de $sessionDurationMinutes minutos ($totalFrames frames)")
        
        repeat(totalFrames) { frameIndex ->
            val frameStartTime = System.currentTimeMillis()
            
            try {
                // Crear frame simulado
                val frameBitmap = createVariedTestFrame(frameIndex)
                val guideArea = RectF(300f, 200f, 500f, 400f)
                
                // Procesamiento completo del frame
                val detection = moleDetector.detectMole(bitmapToMat(frameBitmap))
                val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(frameBitmap))
                val validation = validationManager.validateCapture(detection, quality, guideArea)
                
                // Actualizar UI (simulado)
                overlay.updateMolePosition(
                    detection?.let { PointF(it.centerPoint.x.toFloat(), it.centerPoint.y.toFloat()) },
                    detection?.confidence ?: 0f
                )
                overlay.updateGuideState(validation.guideState)
                
                // Registrar métricas
                captureMetrics.logDetectionAttempt(detection != null, System.currentTimeMillis() - frameStartTime)
                captureMetrics.logQualityAnalysis(quality)
                captureMetrics.logCaptureValidation(validation)
                
                detectionSuccessRate.add(detection != null)
                validationResults.add(validation)
                
                frameBitmap.recycle()
                
            } catch (e: Exception) {
                println("Error en frame $frameIndex: ${e.message}")
                detectionSuccessRate.add(false)
            }
            
            val frameEndTime = System.currentTimeMillis()
            processingTimes.add(frameEndTime - frameStartTime)
            
            // Tomar snapshot de memoria cada 1000 frames
            if (frameIndex % 1000 == 0) {
                System.gc()
                delay(10)
                val currentMemory = runtime.totalMemory() - runtime.freeMemory()
                memorySnapshots.add(currentMemory)
                
                val progress = (frameIndex.toFloat() / totalFrames * 100).toInt()
                println("Progreso: $progress% - Memoria: ${currentMemory / 1024 / 1024} MB")
            }
            
            // Simular intervalo entre frames (66ms para 15 FPS)
            delay(66L)
        }
        
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Análisis de resultados
        val averageProcessingTime = processingTimes.average()
        val maxProcessingTime = processingTimes.maxOrNull() ?: 0L
        val minProcessingTime = processingTimes.minOrNull() ?: 0L
        val successRate = detectionSuccessRate.count { it } / detectionSuccessRate.size.toFloat()
        val memoryIncrease = finalMemory - initialMemory
        val memoryIncreasePercent = (memoryIncrease.toFloat() / initialMemory) * 100
        
        // Assert - Verificar estabilidad a largo plazo
        assertTrue(averageProcessingTime < 100, 
            "Tiempo promedio debería mantenerse < 100ms: ${averageProcessingTime}ms")
        assertTrue(maxProcessingTime < 500,
            "Tiempo máximo no debería exceder 500ms: ${maxProcessingTime}ms")
        assertTrue(successRate > 0.7f,
            "Tasa de éxito debería mantenerse > 70%: ${successRate * 100}%")
        assertTrue(memoryIncreasePercent < 100,
            "Incremento de memoria debería ser < 100%: ${memoryIncreasePercent}%")
        
        // Verificar estabilidad de memoria
        if (memorySnapshots.size > 2) {
            val memoryTrend = calculateMemoryTrend(memorySnapshots)
            assertTrue(memoryTrend < initialMemory / 10, // Crecimiento < 10% de memoria inicial
                "Tendencia de memoria debería ser estable: ${memoryTrend / 1024 / 1024} MB/snapshot")
        }
        
        println("Resultados de sesión prolongada:")
        println("- Frames procesados: $totalFrames")
        println("- Tiempo promedio: ${averageProcessingTime}ms")
        println("- Tiempo min/max: ${minProcessingTime}ms / ${maxProcessingTime}ms")
        println("- Tasa de éxito: ${successRate * 100}%")
        println("- Memoria inicial/final: ${initialMemory / 1024 / 1024} / ${finalMemory / 1024 / 1024} MB")
        println("- Incremento memoria: ${memoryIncreasePercent}%")
    }
    
    /**
     * Test 2: Estrés de memoria con múltiples capturas
     * Simula múltiples capturas y procesamientos consecutivos
     */
    @Test
    fun testMultipleCaptureMemoryStress() = runTest {
        val numberOfCaptures = 100
        val captureResults = mutableListOf<ImagePreprocessor.PreprocessingResult>()
        val memoryUsage = mutableListOf<Long>()
        val processingTimes = mutableListOf<Long>()
        
        val runtime = Runtime.getRuntime()
        
        repeat(numberOfCaptures) { captureIndex ->
            val startTime = System.currentTimeMillis()
            val memoryBefore = runtime.totalMemory() - runtime.freeMemory()
            
            try {
                // Crear imagen de captura variada
                val captureBitmap = createLargeCaptureImage(captureIndex)
                
                // Procesar imagen completa
                val preprocessingResult = preprocessor.preprocessImage(captureBitmap)
                captureResults.add(preprocessingResult)
                
                // Simular uso de la imagen procesada
                val processedBitmap = preprocessingResult.processedBitmap
                val pixels = IntArray(processedBitmap.width * processedBitmap.height)
                processedBitmap.getPixels(pixels, 0, processedBitmap.width, 0, 0, 
                    processedBitmap.width, processedBitmap.height)
                
                captureBitmap.recycle()
                
                val endTime = System.currentTimeMillis()
                processingTimes.add(endTime - startTime)
                
                val memoryAfter = runtime.totalMemory() - runtime.freeMemory()
                memoryUsage.add(memoryAfter - memoryBefore)
                
                // Limpiar cada 10 capturas para simular comportamiento real
                if (captureIndex % 10 == 9) {
                    // Limpiar resultados antiguos
                    captureResults.take(5).forEach { result ->
                        result.processedBitmap.recycle()
                    }
                    captureResults.removeAll(captureResults.take(5))
                    
                    System.gc()
                    delay(50)
                }
                
            } catch (e: OutOfMemoryError) {
                println("OutOfMemoryError en captura $captureIndex")
                // Limpiar agresivamente
                captureResults.forEach { it.processedBitmap.recycle() }
                captureResults.clear()
                System.gc()
                delay(100)
            } catch (e: Exception) {
                println("Error en captura $captureIndex: ${e.message}")
            }
            
            delay(100) // Pausa entre capturas
        }
        
        // Limpiar resultados restantes
        captureResults.forEach { it.processedBitmap.recycle() }
        
        // Análisis de resultados
        val averageProcessingTime = processingTimes.average()
        val averageMemoryUsage = memoryUsage.average()
        val maxMemoryUsage = memoryUsage.maxOrNull() ?: 0L
        val successfulCaptures = processingTimes.size
        
        // Assert
        assertTrue(successfulCaptures >= numberOfCaptures * 0.8, 
            "Al menos 80% de capturas deberían ser exitosas: $successfulCaptures/$numberOfCaptures")
        assertTrue(averageProcessingTime < 2000,
            "Tiempo promedio de procesamiento debería ser < 2s: ${averageProcessingTime}ms")
        assertTrue(averageMemoryUsage < 50 * 1024 * 1024, // 50MB promedio
            "Uso promedio de memoria debería ser razonable: ${averageMemoryUsage / 1024 / 1024} MB")
        
        println("Estrés de múltiples capturas:")
        println("- Capturas exitosas: $successfulCaptures/$numberOfCaptures")
        println("- Tiempo promedio: ${averageProcessingTime}ms")
        println("- Memoria promedio: ${averageMemoryUsage / 1024 / 1024} MB")
        println("- Memoria máxima: ${maxMemoryUsage / 1024 / 1024} MB")
    }
    
    /**
     * Test 3: Estrés térmico simulado
     * Simula condiciones de calentamiento del dispositivo durante uso prolongado
     */
    @Test
    fun testThermalStressSimulation() = runTest {
        val thermalStates = listOf(
            ThermalState.NONE,
            ThermalState.LIGHT,
            ThermalState.MODERATE,
            ThermalState.SEVERE,
            ThermalState.CRITICAL,
            ThermalState.EMERGENCY
        )
        
        val stateResults = mutableMapOf<ThermalState, MutableList<Long>>()
        val stateFrequencies = mutableMapOf<ThermalState, Int>()
        
        thermalStates.forEach { thermalState ->
            stateResults[thermalState] = mutableListOf()
            
            // Configurar estado térmico
            performanceManager.adaptToThermalState(thermalState)
            val frequency = performanceManager.getCurrentProcessingFrequency()
            stateFrequencies[thermalState] = frequency
            
            // Ejecutar procesamiento durante este estado
            val framesForState = when (thermalState) {
                ThermalState.NONE, ThermalState.LIGHT -> 100
                ThermalState.MODERATE -> 80
                ThermalState.SEVERE -> 60
                ThermalState.CRITICAL -> 40
                ThermalState.EMERGENCY -> 20
            }
            
            repeat(framesForState) { frameIndex ->
                val startTime = System.currentTimeMillis()
                
                try {
                    val frameBitmap = createTestFrame(frameIndex)
                    val detection = moleDetector.detectMole(bitmapToMat(frameBitmap))
                    val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(frameBitmap))
                    
                    frameBitmap.recycle()
                    
                    val endTime = System.currentTimeMillis()
                    stateResults[thermalState]?.add(endTime - startTime)
                    
                } catch (e: Exception) {
                    println("Error en estado térmico $thermalState, frame $frameIndex: ${e.message}")
                }
                
                // Respetar frecuencia configurada
                delay(1000L / frequency)
            }
            
            println("Estado térmico $thermalState completado: $framesForState frames a $frequency FPS")
        }
        
        // Análisis de resultados
        thermalStates.forEach { state ->
            val times = stateResults[state] ?: emptyList()
            val frequency = stateFrequencies[state] ?: 0
            
            if (times.isNotEmpty()) {
                val avgTime = times.average()
                val maxTime = times.maxOrNull() ?: 0L
                
                // Assert - Verificar que el sistema se adapta apropiadamente
                when (state) {
                    ThermalState.NONE, ThermalState.LIGHT -> {
                        assertTrue(avgTime < 100, "Tiempo normal debería ser < 100ms: ${avgTime}ms")
                        assertTrue(frequency >= 10, "Frecuencia normal debería ser >= 10 FPS: $frequency")
                    }
                    ThermalState.MODERATE -> {
                        assertTrue(avgTime < 150, "Tiempo moderado debería ser < 150ms: ${avgTime}ms")
                        assertTrue(frequency >= 8, "Frecuencia moderada debería ser >= 8 FPS: $frequency")
                    }
                    ThermalState.SEVERE -> {
                        assertTrue(avgTime < 200, "Tiempo severo debería ser < 200ms: ${avgTime}ms")
                        assertTrue(frequency >= 5, "Frecuencia severa debería ser >= 5 FPS: $frequency")
                    }
                    ThermalState.CRITICAL, ThermalState.EMERGENCY -> {
                        assertTrue(avgTime < 300, "Tiempo crítico debería ser < 300ms: ${avgTime}ms")
                        assertTrue(frequency >= 3, "Frecuencia crítica debería ser >= 3 FPS: $frequency")
                    }
                }
                
                println("Estado $state: ${avgTime}ms promedio, ${maxTime}ms máximo, $frequency FPS")
            }
        }
    }
    
    /**
     * Test 4: Estrés de concurrencia con múltiples hilos
     * Simula múltiples operaciones concurrentes durante uso intensivo
     */
    @Test
    fun testConcurrencyStressTest() = runTest {
        val concurrentJobs = 5
        val framesPerJob = 50
        val jobResults = mutableListOf<Deferred<JobResult>>()
        
        // Lanzar jobs concurrentes
        repeat(concurrentJobs) { jobIndex ->
            val job = async {
                val jobTimes = mutableListOf<Long>()
                val jobErrors = mutableListOf<String>()
                var successfulFrames = 0
                
                repeat(framesPerJob) { frameIndex ->
                    val startTime = System.currentTimeMillis()
                    
                    try {
                        val frameBitmap = createTestFrame(frameIndex + jobIndex * 1000)
                        
                        // Procesamiento completo
                        val detection = moleDetector.detectMole(bitmapToMat(frameBitmap))
                        val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(frameBitmap))
                        val validation = validationManager.validateCapture(
                            detection, quality, RectF(300f, 200f, 500f, 400f)
                        )
                        
                        // Simular captura ocasional
                        if (validation.canCapture && frameIndex % 10 == 0) {
                            val preprocessingResult = preprocessor.preprocessImage(frameBitmap)
                            preprocessingResult.processedBitmap.recycle()
                        }
                        
                        frameBitmap.recycle()
                        successfulFrames++
                        
                    } catch (e: Exception) {
                        jobErrors.add("Job $jobIndex, Frame $frameIndex: ${e.message}")
                    }
                    
                    val endTime = System.currentTimeMillis()
                    jobTimes.add(endTime - startTime)
                    
                    delay(50) // Intervalo entre frames
                }
                
                JobResult(jobIndex, jobTimes, jobErrors, successfulFrames)
            }
            jobResults.add(job)
        }
        
        // Esperar resultados
        val results = jobResults.awaitAll()
        
        // Análisis de resultados
        val allTimes = results.flatMap { it.processingTimes }
        val allErrors = results.flatMap { it.errors }
        val totalSuccessfulFrames = results.sumOf { it.successfulFrames }
        val totalExpectedFrames = concurrentJobs * framesPerJob
        
        val averageTime = allTimes.average()
        val maxTime = allTimes.maxOrNull() ?: 0L
        val successRate = totalSuccessfulFrames.toFloat() / totalExpectedFrames
        
        // Assert
        assertTrue(averageTime < 200, 
            "Tiempo promedio con concurrencia debería ser < 200ms: ${averageTime}ms")
        assertTrue(maxTime < 1000,
            "Tiempo máximo con concurrencia debería ser < 1s: ${maxTime}ms")
        assertTrue(successRate > 0.8f,
            "Tasa de éxito con concurrencia debería ser > 80%: ${successRate * 100}%")
        assertTrue(allErrors.size < totalExpectedFrames * 0.1,
            "Errores deberían ser < 10% del total: ${allErrors.size}/$totalExpectedFrames")
        
        println("Estrés de concurrencia:")
        println("- Jobs concurrentes: $concurrentJobs")
        println("- Frames exitosos: $totalSuccessfulFrames/$totalExpectedFrames")
        println("- Tiempo promedio: ${averageTime}ms")
        println("- Tiempo máximo: ${maxTime}ms")
        println("- Errores totales: ${allErrors.size}")
        
        if (allErrors.isNotEmpty()) {
            println("Primeros errores:")
            allErrors.take(5).forEach { println("  - $it") }
        }
    }
    
    /**
     * Test 5: Estrés de accesibilidad durante uso prolongado
     * Verifica que las funciones de accesibilidad se mantengan estables
     */
    @Test
    fun testAccessibilityStressDuringExtendedUse() = runTest {
        val sessionFrames = 1000
        val talkBackMessages = mutableListOf<String>()
        val hapticEvents = mutableListOf<String>()
        val stateChanges = mutableListOf<CaptureGuidanceOverlay.GuideState>()
        
        // Configurar callbacks de accesibilidad
        accessibilityManager.setTalkBackCallback { message ->
            talkBackMessages.add(message)
        }
        
        hapticManager.setVibrationCallback { pattern ->
            hapticEvents.add(pattern)
        }
        
        var lastState: CaptureGuidanceOverlay.GuideState? = null
        
        repeat(sessionFrames) { frameIndex ->
            try {
                val frameBitmap = createVariedTestFrame(frameIndex)
                val guideArea = RectF(300f, 200f, 500f, 400f)
                
                val detection = moleDetector.detectMole(bitmapToMat(frameBitmap))
                val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(frameBitmap))
                val validation = validationManager.validateCapture(detection, quality, guideArea)
                
                // Actualizar estado si cambió
                if (validation.guideState != lastState) {
                    overlay.updateGuideState(validation.guideState)
                    stateChanges.add(validation.guideState)
                    
                    // Activar accesibilidad
                    accessibilityManager.announceStateChange(validation.guideState, validation.message)
                    hapticManager.provideStateChangeFeedback(validation.guideState)
                    
                    lastState = validation.guideState
                }
                
                frameBitmap.recycle()
                
            } catch (e: Exception) {
                println("Error en frame de accesibilidad $frameIndex: ${e.message}")
            }
            
            delay(66) // ~15 FPS
        }
        
        // Análisis de resultados
        val uniqueStates = stateChanges.toSet()
        val messageCount = talkBackMessages.size
        val hapticCount = hapticEvents.size
        val stateChangeCount = stateChanges.size
        
        // Assert
        assertTrue(uniqueStates.size >= 3, 
            "Deberían haberse detectado al menos 3 estados diferentes: $uniqueStates")
        assertTrue(messageCount == stateChangeCount,
            "Cada cambio de estado debería generar un mensaje TalkBack: $messageCount vs $stateChangeCount")
        assertTrue(hapticCount == stateChangeCount,
            "Cada cambio de estado debería generar retroalimentación háptica: $hapticCount vs $stateChangeCount")
        
        // Verificar que no hay duplicados excesivos (indicaría mal funcionamiento)
        val messageDuplicateRatio = (messageCount - talkBackMessages.toSet().size).toFloat() / messageCount
        assertTrue(messageDuplicateRatio < 0.8f,
            "No debería haber demasiados mensajes duplicados: ${messageDuplicateRatio * 100}%")
        
        println("Estrés de accesibilidad:")
        println("- Frames procesados: $sessionFrames")
        println("- Estados únicos: ${uniqueStates.size}")
        println("- Cambios de estado: $stateChangeCount")
        println("- Mensajes TalkBack: $messageCount")
        println("- Eventos hápticos: $hapticCount")
        println("- Ratio duplicados: ${messageDuplicateRatio * 100}%")
    }
    
    // MÉTODOS AUXILIARES
    
    private fun createVariedTestFrame(frameIndex: Int): Bitmap {
        val width = 640 + (frameIndex % 3) * 80 // Variación de tamaño
        val height = 480 + (frameIndex % 3) * 60
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Color de fondo variado (simula diferentes condiciones de piel)
        val skinTones = listOf(
            Color.rgb(240, 220, 200),
            Color.rgb(220, 180, 140),
            Color.rgb(200, 160, 120),
            Color.rgb(180, 140, 100)
        )
        bitmap.eraseColor(skinTones[frameIndex % skinTones.size])
        
        // Añadir lunar en posición variable
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = Color.rgb(80 + frameIndex % 40, 60, 40)
            isAntiAlias = true
        }
        
        val centerX = width / 2f + (frameIndex % 100 - 50) * 2f // Movimiento horizontal
        val centerY = height / 2f + ((frameIndex / 10) % 100 - 50) * 1.5f // Movimiento vertical
        val radius = 15f + (frameIndex % 10) * 2f // Tamaño variable
        
        canvas.drawCircle(centerX, centerY, radius, paint)
        
        return bitmap
    }
    
    private fun createTestFrame(frameIndex: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(240, 220, 200))
        
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = Color.rgb(80, 60, 40)
            isAntiAlias = true
        }
        
        val centerX = 320f + (frameIndex % 50 - 25) * 4f
        val centerY = 240f + ((frameIndex / 5) % 50 - 25) * 3f
        
        canvas.drawCircle(centerX, centerY, 20f, paint)
        
        return bitmap
    }
    
    private fun createLargeCaptureImage(captureIndex: Int): Bitmap {
        val sizes = listOf(
            Pair(1920, 1080), // Full HD
            Pair(2560, 1440), // QHD
            Pair(3840, 2160)  // 4K
        )
        
        val (width, height) = sizes[captureIndex % sizes.size]
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(240, 220, 200))
        
        // Añadir múltiples elementos para hacer el procesamiento más intensivo
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }
        
        // Lunar principal
        paint.color = Color.rgb(80, 60, 40)
        canvas.drawCircle(width / 2f, height / 2f, 50f, paint)
        
        // Elementos adicionales (pecas, manchas)
        repeat(10) { i ->
            paint.color = Color.rgb(120 + i * 5, 100, 80)
            val x = (width * 0.2f + i * width * 0.06f).toFloat()
            val y = (height * 0.3f + i * height * 0.04f).toFloat()
            canvas.drawCircle(x, y, 5f + i, paint)
        }
        
        return bitmap
    }
    
    private fun bitmapToMat(bitmap: Bitmap): org.opencv.core.Mat {
        // Simulación para tests
        return org.opencv.core.Mat()
    }
    
    private fun calculateMemoryTrend(memorySnapshots: List<Long>): Long {
        if (memorySnapshots.size < 2) return 0L
        
        // Calcular tendencia lineal simple
        val n = memorySnapshots.size
        val sumX = (0 until n).sum()
        val sumY = memorySnapshots.sum()
        val sumXY = memorySnapshots.mapIndexed { index, memory -> index * memory }.sum()
        val sumX2 = (0 until n).map { it * it }.sum()
        
        // Pendiente de la línea de tendencia
        val slope = (n * sumXY - sumX * sumY).toFloat() / (n * sumX2 - sumX * sumX)
        return slope.toLong()
    }
    
    private data class JobResult(
        val jobIndex: Int,
        val processingTimes: List<Long>,
        val errors: List<String>,
        val successfulFrames: Int
    )
}
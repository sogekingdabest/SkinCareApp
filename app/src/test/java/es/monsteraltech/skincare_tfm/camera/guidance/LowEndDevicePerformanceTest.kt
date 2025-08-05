package es.monsteraltech.skincare_tfm.camera.guidance
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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

@RunWith(AndroidJUnit4::class)
class LowEndDevicePerformanceTest {
    private lateinit var context: Context
    private lateinit var moleDetector: MoleDetectionProcessor
    private lateinit var qualityAnalyzer: ImageQualityAnalyzer
    private lateinit var validationManager: CaptureValidationManager
    private lateinit var preprocessor: ImagePreprocessor
    private lateinit var performanceManager: PerformanceManager
    private lateinit var captureMetrics: CaptureMetrics
    private val testDispatcher = StandardTestDispatcher()
    private val lowEndConfig = CaptureGuidanceConfig(
        guideCircleRadius = 120f,
        centeringTolerance = 60f,
        minMoleAreaRatio = 0.12f,
        maxMoleAreaRatio = 0.85f,
        qualityThresholds = QualityThresholds(
            minSharpness = 0.25f,
            minBrightness = 70f,
            maxBrightness = 190f,
            minContrast = 0.15f
        ),
        enableHapticFeedback = true,
        enableAutoCapture = false
    )
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        initializeLowEndComponents()
    }
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    private fun initializeLowEndComponents() {
        moleDetector = MoleDetectionProcessor(context, lowEndConfig.toDetectionConfig())
        qualityAnalyzer = ImageQualityAnalyzer(context, lowEndConfig.qualityThresholds)
        validationManager = CaptureValidationManager(lowEndConfig)
        preprocessor = ImagePreprocessor(context, enableOptimizations = true)
        performanceManager = PerformanceManager(context)
        captureMetrics = CaptureMetrics()
        performanceManager.adjustProcessingFrequency(DevicePerformance.LOW)
        performanceManager.optimizeForBattery(25)
    }
    @Test
    fun testLowResolutionDetectionPerformance() = runTest {
        val lowResBitmaps = listOf(
            createTestBitmap(320, 240),
            createTestBitmap(480, 320),
            createTestBitmap(640, 480)
        )
        val detectionTimes = mutableListOf<Long>()
        val successfulDetections = mutableListOf<Boolean>()
        lowResBitmaps.forEach { bitmap ->
            repeat(5) {
                val startTime = System.currentTimeMillis()
                val detection = moleDetector.detectMole(bitmapToMat(bitmap))
                val endTime = System.currentTimeMillis()
                val processingTime = endTime - startTime
                detectionTimes.add(processingTime)
                successfulDetections.add(detection != null)
                captureMetrics.logDetectionAttempt(detection != null, processingTime)
            }
            bitmap.recycle()
        }
        val averageTime = detectionTimes.average()
        val maxTime = detectionTimes.maxOrNull() ?: 0L
        val successRate = successfulDetections.count { it } / successfulDetections.size.toFloat()
        assertTrue(averageTime < 200, "Tiempo promedio debería ser < 200ms: ${averageTime}ms")
        assertTrue(maxTime < 500, "Tiempo máximo debería ser < 500ms: ${maxTime}ms")
        assertTrue(successRate > 0.6f, "Tasa de éxito debería ser > 60%: ${successRate * 100}%")
        println("Rendimiento detección gama baja:")
        println("- Tiempo promedio: ${averageTime}ms")
        println("- Tiempo máximo: ${maxTime}ms")
        println("- Tasa de éxito: ${successRate * 100}%")
    }
    @Test
    fun testQualityAnalysisWithLimitedResources() = runTest {
        val testBitmap = createTestBitmap(640, 480)
        val analysisResults = mutableListOf<ImageQualityAnalyzer.QualityMetrics>()
        val analysisTimes = mutableListOf<Long>()
        repeat(20) {
            val startTime = System.currentTimeMillis()
            val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(testBitmap))
            val endTime = System.currentTimeMillis()
            analysisTimes.add(endTime - startTime)
            analysisResults.add(quality)
            captureMetrics.logQualityAnalysis(quality)
            delay(50L)
        }
        testBitmap.recycle()
        val averageAnalysisTime = analysisTimes.average()
        val maxAnalysisTime = analysisTimes.maxOrNull() ?: 0L
        assertTrue(averageAnalysisTime < 100,
            "Análisis de calidad debería ser < 100ms: ${averageAnalysisTime}ms")
        assertTrue(maxAnalysisTime < 250,
            "Tiempo máximo de análisis debería ser < 250ms: ${maxAnalysisTime}ms")
        val sharpnessValues = analysisResults.map { it.sharpness }
        val brightnessValues = analysisResults.map { it.brightness }
        val sharpnessStdDev = calculateStandardDeviation(sharpnessValues)
        val brightnessStdDev = calculateStandardDeviation(brightnessValues)
        assertTrue(sharpnessStdDev < 0.1f, "Sharpness debería ser consistente")
        assertTrue(brightnessStdDev < 20f, "Brightness debería ser consistente")
        println("Rendimiento análisis calidad:")
        println("- Tiempo promedio: ${averageAnalysisTime}ms")
        println("- Desviación sharpness: $sharpnessStdDev")
        println("- Desviación brightness: $brightnessStdDev")
    }
    @Test
    fun testCaptureValidationUnderMemoryStress() = runTest {
        val guideArea = android.graphics.RectF(200f, 150f, 400f, 350f)
        val validationResults = mutableListOf<CaptureValidationManager.ValidationResult>()
        val validationTimes = mutableListOf<Long>()
        val stressBitmaps = mutableListOf<Bitmap>()
        repeat(10) {
            stressBitmaps.add(createTestBitmap(800, 600))
        }
        try {
            stressBitmaps.forEachIndexed { index, bitmap ->
                val startTime = System.currentTimeMillis()
                val detection = createMockDetection(index)
                val quality = createMockQuality(index)
                val validation = validationManager.validateCapture(detection, quality, guideArea)
                val endTime = System.currentTimeMillis()
                validationTimes.add(endTime - startTime)
                validationResults.add(validation)
                captureMetrics.logCaptureValidation(validation)
            }
        } finally {
            stressBitmaps.forEach { it.recycle() }
        }
        val averageValidationTime = validationTimes.average()
        val maxValidationTime = validationTimes.maxOrNull() ?: 0L
        val validCaptureCount = validationResults.count { it.canCapture }
        assertTrue(averageValidationTime < 50,
            "Validación debería ser < 50ms: ${averageValidationTime}ms")
        assertTrue(maxValidationTime < 150,
            "Tiempo máximo validación debería ser < 150ms: ${maxValidationTime}ms")
        assertTrue(validCaptureCount > 0, "Al menos algunas validaciones deberían ser exitosas")
        println("Rendimiento validación bajo estrés:")
        println("- Tiempo promedio: ${averageValidationTime}ms")
        println("- Validaciones exitosas: $validCaptureCount/${validationResults.size}")
    }
    @Test
    fun testOptimizedPreprocessingForLowEnd() = runTest {
        val testBitmaps = listOf(
            createTestBitmap(640, 480),
            createTestBitmap(800, 600),
            createTestBitmap(1024, 768)
        )
        val preprocessingResults = mutableListOf<ImagePreprocessor.PreprocessingResult>()
        val processingTimes = mutableListOf<Long>()
        testBitmaps.forEach { bitmap ->
            val startTime = System.currentTimeMillis()
            val result = preprocessor.preprocessImage(bitmap)
            val endTime = System.currentTimeMillis()
            processingTimes.add(endTime - startTime)
            preprocessingResults.add(result)
            captureMetrics.logPreprocessingPerformance(
                endTime - startTime,
                result.appliedFilters
            )
            bitmap.recycle()
            result.processedBitmap.recycle()
        }
        val averageProcessingTime = processingTimes.average()
        val maxProcessingTime = processingTimes.maxOrNull() ?: 0L
        assertTrue(averageProcessingTime < 1000,
            "Preprocesado debería ser < 1s: ${averageProcessingTime}ms")
        assertTrue(maxProcessingTime < 2000,
            "Tiempo máximo preprocesado debería ser < 2s: ${maxProcessingTime}ms")
        preprocessingResults.forEach { result ->
            assertTrue(result.appliedFilters.isNotEmpty(), "Deberían aplicarse filtros")
            assertTrue(result.processingTime > 0, "Tiempo de procesamiento debería registrarse")
            assertNotNull(result.processedBitmap, "Debería generar imagen procesada")
        }
        println("Rendimiento preprocesado optimizado:")
        println("- Tiempo promedio: ${averageProcessingTime}ms")
        println("- Filtros promedio: ${preprocessingResults.map { it.appliedFilters.size }.average()}")
    }
    @Test
    fun testAdaptivePerformanceManagement() = runTest {
        val initialFrequency = performanceManager.getCurrentProcessingFrequency()
        performanceManager.adjustProcessingFrequency(DevicePerformance.LOW)
        val lowPerfFrequency = performanceManager.getCurrentProcessingFrequency()
        performanceManager.optimizeForBattery(10)
        val batteryOptimizedFrequency = performanceManager.getCurrentProcessingFrequency()
        performanceManager.adaptToThermalState(ThermalState.SEVERE)
        val thermalOptimizedFrequency = performanceManager.getCurrentProcessingFrequency()
        assertTrue(lowPerfFrequency <= initialFrequency,
            "Frecuencia debería reducirse con rendimiento bajo")
        assertTrue(batteryOptimizedFrequency <= lowPerfFrequency,
            "Frecuencia debería reducirse más con batería baja")
        assertTrue(thermalOptimizedFrequency <= batteryOptimizedFrequency,
            "Frecuencia debería ser mínima con throttling severo")
        assertTrue(thermalOptimizedFrequency >= 5, "Frecuencia mínima debería ser >= 5 FPS")
        assertTrue(lowPerfFrequency <= 15, "Frecuencia gama baja debería ser <= 15 FPS")
        println("Gestión adaptativa de rendimiento:")
        println("- Inicial: ${initialFrequency} FPS")
        println("- Gama baja: ${lowPerfFrequency} FPS")
        println("- Batería baja: ${batteryOptimizedFrequency} FPS")
        println("- Throttling: ${thermalOptimizedFrequency} FPS")
    }
    @Test
    fun testMemoryUsageDuringExtendedOperation() = runTest {
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        val memorySnapshots = mutableListOf<Long>()
        val processingTimes = mutableListOf<Long>()
        repeat(100) { frameIndex ->
            val frameStartTime = System.currentTimeMillis()
            val frameBitmap = createTestBitmap(640, 480)
            val detection = moleDetector.detectMole(bitmapToMat(frameBitmap))
            val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(frameBitmap))
            val validation = validationManager.validateCapture(
                detection, quality, android.graphics.RectF(200f, 150f, 400f, 350f)
            )
            frameBitmap.recycle()
            val frameEndTime = System.currentTimeMillis()
            processingTimes.add(frameEndTime - frameStartTime)
            if (frameIndex % 10 == 0) {
                System.gc()
                delay(50)
                val currentMemory = runtime.totalMemory() - runtime.freeMemory()
                memorySnapshots.add(currentMemory)
            }
        }
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = finalMemory - initialMemory
        val averageProcessingTime = processingTimes.average()
        assertTrue(averageProcessingTime < 150,
            "Tiempo promedio por frame debería ser < 150ms: ${averageProcessingTime}ms")
        val memoryIncreasePercent = (memoryIncrease.toFloat() / initialMemory) * 100
        assertTrue(memoryIncreasePercent < 50,
            "Incremento de memoria debería ser < 50%: ${memoryIncreasePercent}%")
        val memoryVariation = calculateStandardDeviation(memorySnapshots.map { it.toFloat() })
        val maxMemory = memorySnapshots.maxOrNull() ?: 0L
        val minMemory = memorySnapshots.minOrNull() ?: 0L
        val memoryRange = maxMemory - minMemory
        assertTrue(memoryRange < initialMemory / 2,
            "Rango de memoria debería ser estable")
        println("Uso de memoria durante operación prolongada:")
        println("- Memoria inicial: ${initialMemory / 1024 / 1024} MB")
        println("- Memoria final: ${finalMemory / 1024 / 1024} MB")
        println("- Incremento: ${memoryIncreasePercent}%")
        println("- Tiempo promedio por frame: ${averageProcessingTime}ms")
    }
    @Test
    fun testMultithreadingPerformanceOnLowEnd() = runTest {
        val concurrentJobs = 3
        val framesPerJob = 10
        val jobResults = mutableListOf<Deferred<List<Long>>>()
        repeat(concurrentJobs) { jobIndex ->
            val job = async {
                val jobTimes = mutableListOf<Long>()
                repeat(framesPerJob) { frameIndex ->
                    val startTime = System.currentTimeMillis()
                    val bitmap = createTestBitmap(480, 320)
                    val detection = moleDetector.detectMole(bitmapToMat(bitmap))
                    val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(bitmap))
                    bitmap.recycle()
                    val endTime = System.currentTimeMillis()
                    jobTimes.add(endTime - startTime)
                    delay(100)
                }
                jobTimes
            }
            jobResults.add(job)
        }
        val allResults = jobResults.awaitAll()
        val allTimes = allResults.flatten()
        val averageTime = allTimes.average()
        val maxTime = allTimes.maxOrNull() ?: 0L
        val totalFrames = allTimes.size
        assertTrue(averageTime < 200,
            "Tiempo promedio con concurrencia debería ser < 200ms: ${averageTime}ms")
        assertTrue(maxTime < 500,
            "Tiempo máximo con concurrencia debería ser < 500ms: ${maxTime}ms")
        assertEquals(concurrentJobs * framesPerJob, totalFrames,
            "Deberían procesarse todos los frames")
        println("Rendimiento multihilo en gama baja:")
        println("- Jobs concurrentes: $concurrentJobs")
        println("- Frames por job: $framesPerJob")
        println("- Tiempo promedio: ${averageTime}ms")
        println("- Tiempo máximo: ${maxTime}ms")
    }
    private fun createTestBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(240, 220, 200))
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = Color.rgb(80, 60, 40)
            isAntiAlias = true
        }
        canvas.drawCircle(width / 2f, height / 2f, 20f, paint)
        return bitmap
    }
    private fun createMockDetection(index: Int): MoleDetectionProcessor.MoleDetection {
        return MoleDetectionProcessor.MoleDetection(
            boundingBox = android.graphics.Rect(
                300 + index * 5, 250 + index * 3,
                340 + index * 5, 290 + index * 3
            ),
            centerPoint = org.opencv.core.Point(
                (320 + index * 5).toDouble(),
                (270 + index * 3).toDouble()
            ),
            confidence = 0.7f + (index % 3) * 0.1f,
            area = 1600.0 + index * 100
        )
    }
    private fun createMockQuality(index: Int): ImageQualityAnalyzer.QualityMetrics {
        return ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.6f + (index % 4) * 0.05f,
            brightness = 110f + (index % 5) * 10f,
            contrast = 0.5f + (index % 3) * 0.1f,
            isBlurry = index % 8 == 0,
            isOverexposed = index % 12 == 0,
            isUnderexposed = index % 10 == 0
        )
    }
    private fun bitmapToMat(bitmap: Bitmap): org.opencv.core.Mat {
        return org.opencv.core.Mat()
    }
    private fun calculateStandardDeviation(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return kotlin.math.sqrt(variance)
    }
    private fun CaptureGuidanceConfig.toDetectionConfig(): MoleDetectionProcessor.DetectionConfig {
        return MoleDetectionProcessor.DetectionConfig(
            minMoleSize = 30,
            maxMoleSize = 300,
            confidenceThreshold = 0.6f,
            colorThreshold = org.opencv.core.Scalar(25.0, 60.0, 60.0)
        )
    }
    @Test
    fun testExtremelyLowEndConditions() = runTest {
        val extremeConfig = lowEndConfig.copy(
            guideCircleRadius = 100f,
            centeringTolerance = 80f,
            qualityThresholds = QualityThresholds(
                minSharpness = 0.2f,
                minBrightness = 60f,
                maxBrightness = 200f,
                minContrast = 0.1f
            )
        )
        validationManager = CaptureValidationManager(extremeConfig)
        performanceManager.adjustProcessingFrequency(DevicePerformance.VERY_LOW)
        performanceManager.optimizeForBattery(5)
        performanceManager.adaptToThermalState(ThermalState.CRITICAL)
        val processingTimes = mutableListOf<Long>()
        val successfulOperations = mutableListOf<Boolean>()
        repeat(20) { index ->
            val startTime = System.currentTimeMillis()
            var operationSuccessful = true
            try {
                val tinyBitmap = createTestBitmap(240, 180)
                val detection = moleDetector.detectMole(bitmapToMat(tinyBitmap))
                val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(tinyBitmap))
                val validation = validationManager.validateCapture(
                    detection, quality, android.graphics.RectF(80f, 60f, 160f, 120f)
                )
                tinyBitmap.recycle()
            } catch (e: Exception) {
                operationSuccessful = false
                println("Operación falló en condiciones extremas: ${e.message}")
            }
            val endTime = System.currentTimeMillis()
            processingTimes.add(endTime - startTime)
            successfulOperations.add(operationSuccessful)
            delay(200L)
        }
        val averageTime = processingTimes.average()
        val successRate = successfulOperations.count { it } / successfulOperations.size.toFloat()
        assertTrue(averageTime < 1000,
            "Incluso en condiciones extremas debería ser < 1s: ${averageTime}ms")
        assertTrue(successRate > 0.5f,
            "Al menos 50% de operaciones deberían funcionar: ${successRate * 100}%")
        println("Rendimiento en condiciones extremas:")
        println("- Tiempo promedio: ${averageTime}ms")
        println("- Tasa de éxito: ${successRate * 100}%")
    }
    @Test
    fun testGradualPerformanceDegradation() = runTest {
        val performanceSteps = listOf(
            Triple(DevicePerformance.HIGH, 80, ThermalState.NONE),
            Triple(DevicePerformance.MEDIUM, 60, ThermalState.LIGHT),
            Triple(DevicePerformance.LOW, 40, ThermalState.MODERATE),
            Triple(DevicePerformance.VERY_LOW, 20, ThermalState.SEVERE),
            Triple(DevicePerformance.VERY_LOW, 10, ThermalState.CRITICAL)
        )
        val stepResults = mutableListOf<Triple<Double, Int, Long>>()
        performanceSteps.forEach { (devicePerf, battery, thermal) ->
            performanceManager.adjustProcessingFrequency(devicePerf)
            performanceManager.optimizeForBattery(battery)
            performanceManager.adaptToThermalState(thermal)
            val frequency = performanceManager.getCurrentProcessingFrequency()
            val runtime = Runtime.getRuntime()
            val memoryBefore = runtime.totalMemory() - runtime.freeMemory()
            val processingTimes = mutableListOf<Long>()
            repeat(10) {
                val startTime = System.currentTimeMillis()
                val bitmap = createTestBitmap(480, 320)
                val detection = moleDetector.detectMole(bitmapToMat(bitmap))
                val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(bitmap))
                bitmap.recycle()
                val endTime = System.currentTimeMillis()
                processingTimes.add(endTime - startTime)
                delay(1000L / frequency)
            }
            val memoryAfter = runtime.totalMemory() - runtime.freeMemory()
            val averageTime = processingTimes.average()
            stepResults.add(Triple(averageTime, frequency, memoryAfter - memoryBefore))
            println("Paso $devicePerf (batería: $battery%, thermal: $thermal):")
            println("  - Frecuencia: $frequency FPS")
            println("  - Tiempo promedio: ${averageTime}ms")
            println("  - Uso memoria: ${(memoryAfter - memoryBefore) / 1024 / 1024} MB")
        }
        for (i in 1 until stepResults.size) {
            val (prevTime, prevFreq, _) = stepResults[i - 1]
            val (currTime, currFreq, _) = stepResults[i]
            assertTrue(currFreq <= prevFreq,
                "Frecuencia debería reducirse o mantenerse: $currFreq <= $prevFreq")
            assertTrue(currTime <= prevTime * 2,
                "Tiempo no debería aumentar dramáticamente: ${currTime}ms vs ${prevTime}ms")
        }
    }
    @Test
    fun testRecoveryAfterExtremeConditions() = runTest {
        performanceManager.adjustProcessingFrequency(DevicePerformance.VERY_LOW)
        performanceManager.optimizeForBattery(5)
        performanceManager.adaptToThermalState(ThermalState.CRITICAL)
        val extremeFrequency = performanceManager.getCurrentProcessingFrequency()
        val extremeTimes = mutableListOf<Long>()
        repeat(5) {
            val startTime = System.currentTimeMillis()
            val bitmap = createTestBitmap(320, 240)
            moleDetector.detectMole(bitmapToMat(bitmap))
            bitmap.recycle()
            val endTime = System.currentTimeMillis()
            extremeTimes.add(endTime - startTime)
            delay(200L)
        }
        performanceManager.optimizeForBattery(50)
        delay(100L)
        val batteryRecoveryFrequency = performanceManager.getCurrentProcessingFrequency()
        performanceManager.adaptToThermalState(ThermalState.LIGHT)
        delay(100L)
        val thermalRecoveryFrequency = performanceManager.getCurrentProcessingFrequency()
        performanceManager.adjustProcessingFrequency(DevicePerformance.MEDIUM)
        delay(100L)
        val fullRecoveryFrequency = performanceManager.getCurrentProcessingFrequency()
        val recoveryTimes = mutableListOf<Long>()
        repeat(5) {
            val startTime = System.currentTimeMillis()
            val bitmap = createTestBitmap(640, 480)
            moleDetector.detectMole(bitmapToMat(bitmap))
            bitmap.recycle()
            val endTime = System.currentTimeMillis()
            recoveryTimes.add(endTime - startTime)
            delay(50L)
        }
        assertTrue(batteryRecoveryFrequency >= extremeFrequency,
            "Frecuencia debería mejorar con batería: $batteryRecoveryFrequency >= $extremeFrequency")
        assertTrue(thermalRecoveryFrequency >= batteryRecoveryFrequency,
            "Frecuencia debería mejorar con temperatura: $thermalRecoveryFrequency >= $batteryRecoveryFrequency")
        assertTrue(fullRecoveryFrequency >= thermalRecoveryFrequency,
            "Frecuencia debería mejorar con CPU: $fullRecoveryFrequency >= $thermalRecoveryFrequency")
        val extremeAvgTime = extremeTimes.average()
        val recoveryAvgTime = recoveryTimes.average()
        assertTrue(recoveryAvgTime <= extremeAvgTime,
            "Tiempo debería mejorar después de recuperación: ${recoveryAvgTime}ms <= ${extremeAvgTime}ms")
        println("Recuperación después de condiciones extremas:")
        println("- Frecuencia extrema: $extremeFrequency FPS")
        println("- Frecuencia recuperada: $fullRecoveryFrequency FPS")
        println("- Tiempo extremo: ${extremeAvgTime}ms")
        println("- Tiempo recuperado: ${recoveryAvgTime}ms")
    }
}
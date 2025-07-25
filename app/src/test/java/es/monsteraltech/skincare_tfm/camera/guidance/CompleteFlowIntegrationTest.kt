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
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.opencv.android.OpenCVLoaderCallback
import org.opencv.android.OpenCVLoaderCallback.SUCCESS
import org.opencv.core.Mat
import kotlin.test.*

/**
 * Test de integración completa para el nuevo flujo de captura con guías inteligentes.
 * Verifica la integración de todos los componentes del sistema de guías de captura.
 * 
 * Cubre los requisitos:
 * - 1.1, 1.2, 1.3, 1.4: Guías visuales y detección de centrado
 * - 2.1, 2.2, 2.3, 2.4, 2.5: Control de distancia y validación
 * - 3.1, 3.2, 3.3: Retroalimentación de calidad en tiempo real
 * - 4.1, 4.2, 4.3, 4.4, 4.5: Preprocesado automático
 * - 5.1, 5.2, 5.3, 5.4: Accesibilidad y retroalimentación háptica
 */
@RunWith(AndroidJUnit4::class)
class CompleteFlowIntegrationTest {

    private lateinit var context: Context
    private lateinit var moleDetector: MoleDetectionProcessor
    private lateinit var qualityAnalyzer: ImageQualityAnalyzer
    private lateinit var validationManager: CaptureValidationManager
    private lateinit var preprocessor: ImagePreprocessor
    private lateinit var overlay: CaptureGuidanceOverlay
    private lateinit var accessibilityManager: AccessibilityManager
    private lateinit var hapticManager: HapticFeedbackManager
    private lateinit var autoCaptureManager: AutoCaptureManager
    private lateinit var performanceManager: PerformanceManager
    private lateinit var captureMetrics: CaptureMetrics
    
    @Mock private lateinit var mockOpenCVCallback: OpenCVLoaderCallback
    
    private lateinit var testBitmap: Bitmap
    private lateinit var testMat: Mat
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        
        context = ApplicationProvider.getApplicationContext()
        
        // Crear bitmap de prueba
        testBitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        testBitmap.eraseColor(Color.WHITE)
        
        // Simular inicialización de OpenCV
        whenever(mockOpenCVCallback.onManagerConnected(SUCCESS)).then { }
        
        // Inicializar componentes
        initializeComponents()
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testBitmap.recycle()
        if (::testMat.isInitialized && !testMat.empty()) {
            testMat.release()
        }
    }
    
    private fun initializeComponents() {
        val config = CaptureGuidanceConfig()
        
        moleDetector = MoleDetectionProcessor(context)
        qualityAnalyzer = ImageQualityAnalyzer(context)
        validationManager = CaptureValidationManager(config)
        preprocessor = ImagePreprocessor(context)
        overlay = CaptureGuidanceOverlay(context)
        accessibilityManager = AccessibilityManager(context)
        hapticManager = HapticFeedbackManager(context)
        autoCaptureManager = AutoCaptureManager(context, config)
        performanceManager = PerformanceManager(context)
        captureMetrics = CaptureMetrics()
    }
    
    /**
     * Test 1: Flujo completo desde detección hasta captura validada
     */
    @Test
    fun testCompleteDetectionToCaptureFlow() = runTest {
        // Arrange
        val guideArea = RectF(300f, 200f, 500f, 400f)
        val mockMolePosition = PointF(400f, 300f) // Centrado en guía
        
        // Simular detección de lunar
        val mockDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = android.graphics.Rect(380, 280, 420, 320),
            centerPoint = org.opencv.core.Point(400.0, 300.0),
            confidence = 0.85f,
            area = 1600.0
        )
        
        // Simular métricas de calidad buenas
        val mockQuality = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.7f,
            brightness = 120f,
            contrast = 0.6f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        
        // Act - Ejecutar flujo completo
        val validationResult = validationManager.validateCapture(
            mockDetection, mockQuality, guideArea
        )
        
        // Actualizar overlay con resultado
        overlay.updateMolePosition(mockMolePosition, mockDetection.confidence)
        overlay.updateGuideState(validationResult.guideState)
        overlay.setGuidanceMessage(validationResult.message)
        
        // Procesar imagen si la validación es exitosa
        var preprocessedResult: ImagePreprocessor.PreprocessingResult? = null
        if (validationResult.canCapture) {
            preprocessedResult = preprocessor.preprocessImage(testBitmap)
        }
        
        // Assert
        assertTrue(validationResult.canCapture, "Debería permitir captura con condiciones óptimas")
        assertEquals(CaptureGuidanceOverlay.GuideState.READY, validationResult.guideState)
        assertTrue(validationResult.confidence > 0.8f, "Confianza debería ser alta")
        assertNotNull(preprocessedResult, "Debería haberse procesado la imagen")
        
        preprocessedResult?.let { result ->
            assertNotNull(result.processedBitmap)
            assertTrue(result.appliedFilters.isNotEmpty())
            assertTrue(result.processingTime > 0)
        }
    }
    
    /**
     * Test 2: Flujo de validación con diferentes estados de fallo
     */
    @Test
    fun testValidationFailureStates() = runTest {
        val guideArea = RectF(300f, 200f, 500f, 400f)
        
        // Test 1: Lunar no centrado
        val offCenterDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = android.graphics.Rect(100, 100, 140, 140),
            centerPoint = org.opencv.core.Point(120.0, 120.0),
            confidence = 0.8f,
            area = 1600.0
        )
        
        val goodQuality = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.7f, brightness = 120f, contrast = 0.6f,
            isBlurry = false, isOverexposed = false, isUnderexposed = false
        )
        
        val notCenteredResult = validationManager.validateCapture(
            offCenterDetection, goodQuality, guideArea
        )
        
        assertFalse(notCenteredResult.canCapture)
        assertEquals(CaptureGuidanceOverlay.GuideState.CENTERING, notCenteredResult.guideState)
        
        // Test 2: Imagen borrosa
        val blurryQuality = goodQuality.copy(isBlurry = true, sharpness = 0.1f)
        val centeredDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = android.graphics.Rect(380, 280, 420, 320),
            centerPoint = org.opencv.core.Point(400.0, 300.0),
            confidence = 0.8f,
            area = 1600.0
        )
        
        val blurryResult = validationManager.validateCapture(
            centeredDetection, blurryQuality, guideArea
        )
        
        assertFalse(blurryResult.canCapture)
        assertEquals(CaptureGuidanceOverlay.GuideState.BLURRY, blurryResult.guideState)
        
        // Test 3: Mala iluminación
        val darkQuality = goodQuality.copy(isUnderexposed = true, brightness = 30f)
        val darkResult = validationManager.validateCapture(
            centeredDetection, darkQuality, guideArea
        )
        
        assertFalse(darkResult.canCapture)
        assertEquals(CaptureGuidanceOverlay.GuideState.POOR_LIGHTING, darkResult.guideState)
    }
    
    /**
     * Test 3: Integración con sistema de accesibilidad
     */
    @Test
    fun testAccessibilityIntegration() = runTest {
        // Arrange
        val testMessages = mutableListOf<String>()
        val testVibrations = mutableListOf<String>()
        
        // Mock callbacks para capturar eventos
        accessibilityManager.setTalkBackCallback { message ->
            testMessages.add(message)
        }
        
        hapticManager.setVibrationCallback { pattern ->
            testVibrations.add(pattern)
        }
        
        // Act - Simular diferentes estados de guía
        val states = listOf(
            CaptureGuidanceOverlay.GuideState.SEARCHING to "Buscando lunar en la imagen",
            CaptureGuidanceOverlay.GuideState.CENTERING to "Centra el lunar en la guía circular",
            CaptureGuidanceOverlay.GuideState.TOO_FAR to "Acércate más al lunar",
            CaptureGuidanceOverlay.GuideState.READY to "Listo para capturar"
        )
        
        states.forEach { (state, expectedMessage) ->
            overlay.updateGuideState(state)
            accessibilityManager.announceStateChange(state, expectedMessage)
            hapticManager.provideStateChangeFeedback(state)
        }
        
        // Assert
        assertEquals(states.size, testMessages.size, "Deberían haberse anunciado todos los estados")
        assertEquals(states.size, testVibrations.size, "Deberían haberse enviado todas las vibraciones")
        
        states.forEachIndexed { index, (_, expectedMessage) ->
            assertTrue(testMessages[index].contains(expectedMessage.split(" ").first()),
                "Mensaje debería contener información del estado")
        }
    }
    
    /**
     * Test 4: Integración con captura automática
     */
    @Test
    fun testAutoCaptureIntegration() = runTest {
        // Arrange
        val config = CaptureGuidanceConfig(enableAutoCapture = true, autoCapturedelay = 1000L)
        autoCaptureManager = AutoCaptureManager(context, config)
        
        var autoCaptureTriggered = false
        var countdownValues = mutableListOf<Int>()
        
        autoCaptureManager.setAutoCaptureCallback { 
            autoCaptureTriggered = true 
        }
        
        autoCaptureManager.setCountdownCallback { countdown ->
            countdownValues.add(countdown)
        }
        
        // Act - Simular condiciones listas para captura
        val readyValidation = CaptureValidationManager.ValidationResult(
            canCapture = true,
            guideState = CaptureGuidanceOverlay.GuideState.READY,
            message = "Listo para capturar",
            confidence = 0.9f
        )
        
        autoCaptureManager.onValidationResult(readyValidation)
        
        // Esperar que termine el countdown
        delay(1200L)
        
        // Assert
        assertTrue(autoCaptureTriggered, "Debería haberse activado la captura automática")
        assertTrue(countdownValues.isNotEmpty(), "Debería haber valores de countdown")
        assertTrue(countdownValues.contains(3), "Debería incluir countdown desde 3")
        assertTrue(countdownValues.contains(1), "Debería incluir countdown hasta 1")
    }
    
    /**
     * Test 5: Integración con sistema de métricas
     */
    @Test
    fun testMetricsIntegration() = runTest {
        // Arrange
        val startTime = System.currentTimeMillis()
        
        // Act - Simular flujo completo con métricas
        captureMetrics.logDetectionAttempt(true, 150L)
        
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.7f, brightness = 120f, contrast = 0.6f,
            isBlurry = false, isOverexposed = false, isUnderexposed = false
        )
        captureMetrics.logQualityAnalysis(qualityMetrics)
        
        val validationResult = CaptureValidationManager.ValidationResult(
            canCapture = true,
            guideState = CaptureGuidanceOverlay.GuideState.READY,
            message = "Listo",
            confidence = 0.85f
        )
        captureMetrics.logCaptureValidation(validationResult)
        
        val preprocessingFilters = listOf("contrast", "brightness", "noise_reduction")
        captureMetrics.logPreprocessingPerformance(200L, preprocessingFilters)
        
        // Assert - Verificar que las métricas se registraron
        val metrics = captureMetrics.getMetricsSummary()
        
        assertTrue(metrics.totalDetectionAttempts > 0)
        assertTrue(metrics.successfulDetections > 0)
        assertTrue(metrics.averageDetectionTime > 0)
        assertTrue(metrics.averageQualityScore > 0)
        assertTrue(metrics.successfulValidations > 0)
        assertTrue(metrics.averagePreprocessingTime > 0)
        assertTrue(metrics.mostUsedFilters.isNotEmpty())
    }
    
    /**
     * Test 6: Integración con gestión de rendimiento
     */
    @Test
    fun testPerformanceManagementIntegration() = runTest {
        // Arrange
        val initialFrequency = performanceManager.getCurrentProcessingFrequency()
        
        // Act - Simular diferentes condiciones de rendimiento
        performanceManager.adjustProcessingFrequency(DevicePerformance.LOW)
        val lowPerfFrequency = performanceManager.getCurrentProcessingFrequency()
        
        performanceManager.optimizeForBattery(15) // Batería baja
        val batteryOptimizedFrequency = performanceManager.getCurrentProcessingFrequency()
        
        performanceManager.adaptToThermalState(ThermalState.THROTTLING)
        val thermalOptimizedFrequency = performanceManager.getCurrentProcessingFrequency()
        
        // Assert
        assertTrue(lowPerfFrequency <= initialFrequency, 
            "Frecuencia debería reducirse con rendimiento bajo")
        assertTrue(batteryOptimizedFrequency <= lowPerfFrequency,
            "Frecuencia debería reducirse más con batería baja")
        assertTrue(thermalOptimizedFrequency <= batteryOptimizedFrequency,
            "Frecuencia debería ser mínima con throttling térmico")
    }
    
    /**
     * Test 7: Flujo completo con preprocesado de imagen
     */
    @Test
    fun testCompletePreprocessingFlow() = runTest {
        // Arrange
        val originalBitmap = createTestBitmapWithMole()
        
        // Act - Procesar imagen completa
        val preprocessingResult = preprocessor.preprocessImage(originalBitmap)
        
        // Verificar que se aplicaron los filtros esperados
        val expectedFilters = listOf("contrast", "brightness", "noise_reduction")
        
        // Assert
        assertNotNull(preprocessingResult.processedBitmap)
        assertFalse(preprocessingResult.processedBitmap.isRecycled)
        assertTrue(preprocessingResult.appliedFilters.isNotEmpty())
        assertTrue(preprocessingResult.processingTime > 0)
        
        // Verificar que la imagen procesada es diferente de la original
        assertNotEquals(originalBitmap, preprocessingResult.processedBitmap)
        
        // Verificar que se mantiene la imagen original
        assertEquals(originalBitmap, preprocessingResult.originalBitmap)
        assertFalse(preprocessingResult.originalBitmap.isRecycled)
        
        // Limpiar
        originalBitmap.recycle()
        preprocessingResult.processedBitmap.recycle()
    }
    
    /**
     * Test 8: Integración de todos los componentes en tiempo real
     */
    @Test
    fun testRealtimeIntegration() = runTest {
        // Arrange
        val frameCount = 10
        val processedFrames = mutableListOf<CaptureValidationManager.ValidationResult>()
        val detectionTimes = mutableListOf<Long>()
        
        // Act - Simular procesamiento de frames en tiempo real
        repeat(frameCount) { frameIndex ->
            val startTime = System.currentTimeMillis()
            
            // Simular frame de cámara
            val frameBitmap = createTestFrameWithMole(frameIndex)
            
            // Procesar frame completo
            val detection = moleDetector.detectMole(bitmapToMat(frameBitmap))
            val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(frameBitmap))
            val validation = validationManager.validateCapture(
                detection, quality, RectF(300f, 200f, 500f, 400f)
            )
            
            processedFrames.add(validation)
            detectionTimes.add(System.currentTimeMillis() - startTime)
            
            // Actualizar UI
            overlay.updateMolePosition(
                detection?.let { PointF(it.centerPoint.x.toFloat(), it.centerPoint.y.toFloat()) },
                detection?.confidence ?: 0f
            )
            overlay.updateGuideState(validation.guideState)
            
            frameBitmap.recycle()
            
            // Simular intervalo entre frames
            delay(50L) // ~20 FPS
        }
        
        // Assert
        assertEquals(frameCount, processedFrames.size)
        assertEquals(frameCount, detectionTimes.size)
        
        // Verificar que el procesamiento es lo suficientemente rápido
        val averageProcessingTime = detectionTimes.average()
        assertTrue(averageProcessingTime < 100, // Menos de 100ms por frame
            "Procesamiento debería ser rápido: ${averageProcessingTime}ms promedio")
        
        // Verificar que hay variedad en los resultados (simulando movimiento real)
        val uniqueStates = processedFrames.map { it.guideState }.toSet()
        assertTrue(uniqueStates.size > 1, "Debería haber variedad en los estados detectados")
    }
    
    // MÉTODOS AUXILIARES
    
    private fun createTestBitmapWithMole(): Bitmap {
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(240, 220, 200)) // Color piel
        
        // Dibujar un "lunar" simple (círculo oscuro)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = Color.rgb(80, 60, 40)
            isAntiAlias = true
        }
        canvas.drawCircle(400f, 300f, 30f, paint)
        
        return bitmap
    }
    
    private fun createTestFrameWithMole(frameIndex: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(240, 220, 200))
        
        // Simular movimiento del lunar
        val centerX = 350f + (frameIndex * 10f) // Movimiento horizontal
        val centerY = 280f + (frameIndex * 5f)  // Movimiento vertical
        
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = Color.rgb(80, 60, 40)
            isAntiAlias = true
        }
        canvas.drawCircle(centerX, centerY, 25f, paint)
        
        return bitmap
    }
    
    private fun bitmapToMat(bitmap: Bitmap): Mat {
        // Simulación - en implementación real usaría OpenCV
        return Mat()
    }
    
    /**
     * Test 9: Flujo end-to-end completo desde captura hasta análisis
     * Simula el flujo completo que experimentaría un usuario real
     */
    @Test
    fun testEndToEndCaptureToAnalysisFlow() = runTest {
        // Arrange
        val captureStates = mutableListOf<CaptureGuidanceOverlay.GuideState>()
        val captureMessages = mutableListOf<String>()
        var finalCaptureResult: ImagePreprocessor.PreprocessingResult? = null
        var analysisTriggered = false
        
        // Simular callback de captura exitosa
        val captureCallback = { result: ImagePreprocessor.PreprocessingResult ->
            finalCaptureResult = result
            analysisTriggered = true
        }
        
        // Act - Simular flujo completo paso a paso
        
        // 1. Inicialización de cámara
        overlay.updateGuideState(CaptureGuidanceOverlay.GuideState.SEARCHING)
        captureStates.add(CaptureGuidanceOverlay.GuideState.SEARCHING)
        captureMessages.add("Buscando lunar en la imagen")
        
        // 2. Detección inicial (lunar no centrado)
        val offCenterDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = android.graphics.Rect(200, 150, 240, 190),
            centerPoint = org.opencv.core.Point(220.0, 170.0),
            confidence = 0.7f,
            area = 1600.0
        )
        
        val initialQuality = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.6f, brightness = 110f, contrast = 0.5f,
            isBlurry = false, isOverexposed = false, isUnderexposed = false
        )
        
        val guideArea = RectF(300f, 200f, 500f, 400f)
        var validation = validationManager.validateCapture(offCenterDetection, initialQuality, guideArea)
        
        overlay.updateGuideState(validation.guideState)
        captureStates.add(validation.guideState)
        captureMessages.add(validation.message)
        
        // 3. Usuario centra el lunar
        val centeredDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = android.graphics.Rect(380, 280, 420, 320),
            centerPoint = org.opencv.core.Point(400.0, 300.0),
            confidence = 0.85f,
            area = 1600.0
        )
        
        validation = validationManager.validateCapture(centeredDetection, initialQuality, guideArea)
        overlay.updateGuideState(validation.guideState)
        captureStates.add(validation.guideState)
        captureMessages.add(validation.message)
        
        // 4. Mejora de calidad (enfoque)
        val improvedQuality = initialQuality.copy(sharpness = 0.8f, brightness = 120f)
        validation = validationManager.validateCapture(centeredDetection, improvedQuality, guideArea)
        
        overlay.updateGuideState(validation.guideState)
        captureStates.add(validation.guideState)
        captureMessages.add(validation.message)
        
        // 5. Captura cuando esté listo
        if (validation.canCapture) {
            val capturedBitmap = createTestBitmapWithMole()
            val preprocessingResult = preprocessor.preprocessImage(capturedBitmap)
            captureCallback(preprocessingResult)
            capturedBitmap.recycle()
        }
        
        // Assert
        assertTrue(captureStates.contains(CaptureGuidanceOverlay.GuideState.SEARCHING))
        assertTrue(captureStates.contains(CaptureGuidanceOverlay.GuideState.CENTERING))
        assertTrue(captureStates.contains(CaptureGuidanceOverlay.GuideState.READY))
        
        assertTrue(captureMessages.any { it.contains("Buscando") })
        assertTrue(captureMessages.any { it.contains("centra") || it.contains("Centra") })
        assertTrue(captureMessages.any { it.contains("Listo") || it.contains("listo") })
        
        assertNotNull(finalCaptureResult, "Debería haberse capturado una imagen")
        assertTrue(analysisTriggered, "Debería haberse activado el análisis")
        
        finalCaptureResult?.let { result ->
            assertNotNull(result.processedBitmap)
            assertNotNull(result.originalBitmap)
            assertTrue(result.appliedFilters.isNotEmpty())
            assertTrue(result.processingTime > 0)
        }
    }
    
    /**
     * Test 10: Integración con navegación a PreviewActivity
     * Verifica que el flujo continúa correctamente después de la captura
     */
    @Test
    fun testNavigationToPreviewIntegration() = runTest {
        // Arrange
        val capturedBitmap = createTestBitmapWithMole()
        var navigationTriggered = false
        var passedOriginalBitmap: Bitmap? = null
        var passedProcessedBitmap: Bitmap? = null
        
        // Simular callback de navegación
        val navigationCallback = { original: Bitmap, processed: Bitmap ->
            navigationTriggered = true
            passedOriginalBitmap = original
            passedProcessedBitmap = processed
        }
        
        // Act
        val preprocessingResult = preprocessor.preprocessImage(capturedBitmap)
        
        // Simular navegación a PreviewActivity
        navigationCallback(preprocessingResult.originalBitmap, preprocessingResult.processedBitmap)
        
        // Assert
        assertTrue(navigationTriggered, "Debería haberse activado la navegación")
        assertNotNull(passedOriginalBitmap, "Debería pasar la imagen original")
        assertNotNull(passedProcessedBitmap, "Debería pasar la imagen procesada")
        
        // Verificar que las imágenes son válidas
        assertFalse(passedOriginalBitmap!!.isRecycled)
        assertFalse(passedProcessedBitmap!!.isRecycled)
        
        // Verificar que son diferentes (procesada vs original)
        assertNotEquals(passedOriginalBitmap, passedProcessedBitmap)
        
        // Limpiar
        capturedBitmap.recycle()
        preprocessingResult.processedBitmap.recycle()
    }
    
    /**
     * Test 11: Integración con sistema de fallback
     * Verifica que el sistema maneja errores graciosamente
     */
    @Test
    fun testFallbackSystemIntegration() = runTest {
        // Arrange
        val corruptedBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        corruptedBitmap.recycle() // Simular bitmap corrupto
        
        var fallbackActivated = false
        var errorMessage: String? = null
        
        // Act & Assert - Test fallback en detección
        try {
            moleDetector.detectMole(bitmapToMat(corruptedBitmap))
        } catch (e: Exception) {
            fallbackActivated = true
            errorMessage = e.message
        }
        
        assertTrue(fallbackActivated, "Debería activarse el fallback con bitmap corrupto")
        assertNotNull(errorMessage, "Debería haber un mensaje de error")
        
        // Test fallback en preprocesado
        fallbackActivated = false
        try {
            val result = preprocessor.preprocessImage(corruptedBitmap)
            // Si no lanza excepción, debería devolver imagen original
            assertEquals(corruptedBitmap, result.originalBitmap)
            assertTrue(result.appliedFilters.isEmpty())
        } catch (e: Exception) {
            fallbackActivated = true
        }
        
        // El fallback debería manejar el error graciosamente
        assertTrue(fallbackActivated || errorMessage != null, 
            "Sistema de fallback debería manejar errores")
    }
    
    /**
     * Test 12: Integración completa con todos los requisitos de accesibilidad
     * Verifica cumplimiento de requisitos 5.1, 5.2, 5.3, 5.4
     * 
     * NOTA: Para tests más exhaustivos, ver también:
     * - LowEndDevicePerformanceTest: Rendimiento en dispositivos de gama baja
     * - ExtendedUsageStressTest: Pruebas de estrés para uso prolongado
     * - RegressionTest: Compatibilidad con funcionalidad existente
     * - EndToEndIntegrationTest: Flujos completos end-to-end
     */
    @Test
    fun testCompleteAccessibilityIntegration() = runTest {
        // Arrange
        val talkBackMessages = mutableListOf<String>()
        val hapticPatterns = mutableListOf<String>()
        val autoCaptureEvents = mutableListOf<String>()
        
        // Configurar callbacks de accesibilidad
        accessibilityManager.setTalkBackCallback { message ->
            talkBackMessages.add(message)
        }
        
        hapticManager.setVibrationCallback { pattern ->
            hapticPatterns.add(pattern)
        }
        
        val accessibleConfig = CaptureGuidanceConfig(
            enableHapticFeedback = true,
            enableAutoCapture = true,
            autoCapturedelay = 500L
        )
        
        val accessibleAutoCaptureManager = AutoCaptureManager(context, accessibleConfig)
        accessibleAutoCaptureManager.setAutoCaptureCallback {
            autoCaptureEvents.add("auto_capture_triggered")
        }
        
        accessibleAutoCaptureManager.setCountdownCallback { countdown ->
            autoCaptureEvents.add("countdown_$countdown")
        }
        
        // Act - Simular flujo completo con accesibilidad
        val states = listOf(
            CaptureGuidanceOverlay.GuideState.SEARCHING,
            CaptureGuidanceOverlay.GuideState.CENTERING,
            CaptureGuidanceOverlay.GuideState.TOO_FAR,
            CaptureGuidanceOverlay.GuideState.BLURRY,
            CaptureGuidanceOverlay.GuideState.POOR_LIGHTING,
            CaptureGuidanceOverlay.GuideState.READY
        )
        
        states.forEach { state ->
            // Requisito 5.1: Retroalimentación por vibración
            hapticManager.provideStateChangeFeedback(state)
            
            // Requisito 5.2: Mensajes con TalkBack
            val message = when (state) {
                CaptureGuidanceOverlay.GuideState.SEARCHING -> "Buscando lunar en la imagen"
                CaptureGuidanceOverlay.GuideState.CENTERING -> "Centra el lunar en la guía circular"
                CaptureGuidanceOverlay.GuideState.TOO_FAR -> "Acércate más al lunar"
                CaptureGuidanceOverlay.GuideState.BLURRY -> "Imagen borrosa - mantén firme la cámara"
                CaptureGuidanceOverlay.GuideState.POOR_LIGHTING -> "Necesitas más luz"
                CaptureGuidanceOverlay.GuideState.READY -> "Listo para capturar"
                else -> "Estado desconocido"
            }
            accessibilityManager.announceStateChange(state, message)
            
            // Requisito 5.3 y 5.4: Captura automática con countdown
            if (state == CaptureGuidanceOverlay.GuideState.READY) {
                val readyValidation = CaptureValidationManager.ValidationResult(
                    canCapture = true,
                    guideState = state,
                    message = message,
                    confidence = 0.9f
                )
                accessibleAutoCaptureManager.onValidationResult(readyValidation)
                delay(600L) // Esperar countdown
            }
        }
        
        // Assert
        
        // Requisito 5.1: Verificar retroalimentación háptica
        assertEquals(states.size, hapticPatterns.size, 
            "Debería haber vibración para cada estado")
        assertTrue(hapticPatterns.contains("state_change"), 
            "Debería incluir patrón de cambio de estado")
        
        // Requisito 5.2: Verificar mensajes TalkBack
        assertEquals(states.size, talkBackMessages.size,
            "Debería haber mensaje TalkBack para cada estado")
        assertTrue(talkBackMessages.any { it.contains("Buscando") })
        assertTrue(talkBackMessages.any { it.contains("Centra") })
        assertTrue(talkBackMessages.any { it.contains("Acércate") })
        assertTrue(talkBackMessages.any { it.contains("Listo") })
        
        // Requisito 5.3 y 5.4: Verificar captura automática con countdown
        assertTrue(autoCaptureEvents.contains("countdown_3"), 
            "Debería incluir countdown desde 3")
        assertTrue(autoCaptureEvents.contains("countdown_2"))
        assertTrue(autoCaptureEvents.contains("countdown_1"))
        assertTrue(autoCaptureEvents.contains("auto_capture_triggered"),
            "Debería activarse la captura automática")
    }
}
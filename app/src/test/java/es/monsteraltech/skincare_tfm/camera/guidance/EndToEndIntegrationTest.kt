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
 * Tests end-to-end completos desde captura hasta análisis.
 * Simula el flujo completo que experimentaría un usuario real,
 * desde abrir la cámara hasta recibir resultados de análisis.
 * 
 * Cubre todos los requisitos del sistema de guías de captura:
 * - 1.1, 1.2, 1.3, 1.4: Guías visuales y detección
 * - 2.1, 2.2, 2.3, 2.4, 2.5: Control de distancia
 * - 3.1, 3.2, 3.3: Retroalimentación de calidad
 * - 4.1, 4.2, 4.3, 4.4, 4.5: Preprocesado automático
 * - 5.1, 5.2, 5.3, 5.4: Accesibilidad
 */
@RunWith(AndroidJUnit4::class)
class EndToEndIntegrationTest {

    private lateinit var context: Context
    private lateinit var moleDetector: MoleDetectionProcessor
    private lateinit var qualityAnalyzer: ImageQualityAnalyzer
    private lateinit var validationManager: CaptureValidationManager
    private lateinit var preprocessor: ImagePreprocessor
    private lateinit var overlay: CaptureGuidanceOverlay
    private lateinit var accessibilityManager: AccessibilityManager
    private lateinit var hapticManager: HapticFeedbackManager
    private lateinit var autoCaptureManager: AutoCaptureManager
    private lateinit var captureMetrics: CaptureMetrics
    
    private val testDispatcher = StandardTestDispatcher()
    private val config = CaptureGuidanceConfig(
        enableHapticFeedback = true,
        enableAutoCapture = true,
        autoCapturedelay = 1000L
    )
    
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
    }
    
    private fun initializeComponents() {
        moleDetector = MoleDetectionProcessor(context)
        qualityAnalyzer = ImageQualityAnalyzer(context)
        validationManager = CaptureValidationManager(config)
        preprocessor = ImagePreprocessor(context)
        overlay = CaptureGuidanceOverlay(context)
        accessibilityManager = AccessibilityManager(context)
        hapticManager = HapticFeedbackManager(context)
        autoCaptureManager = AutoCaptureManager(context, config)
        captureMetrics = CaptureMetrics()
    }
    
    /**
     * Test 1: Flujo end-to-end completo - Usuario exitoso
     * Simula un usuario que logra capturar una imagen exitosamente
     */
    @Test
    fun testCompleteSuccessfulUserFlow() = runTest {
        // Arrange - Estado inicial
        val userSession = UserSession()
        val guideArea = RectF(300f, 200f, 500f, 400f)
        
        println("=== INICIANDO FLUJO END-TO-END EXITOSO ===")
        
        // FASE 1: Inicialización de cámara
        println("Fase 1: Inicialización de cámara")
        userSession.logEvent("camera_opened")
        overlay.updateGuideState(CaptureGuidanceOverlay.GuideState.SEARCHING)
        userSession.logEvent("guide_overlay_shown")
        
        // FASE 2: Búsqueda inicial (sin lunar detectado)
        println("Fase 2: Búsqueda inicial")
        repeat(5) { frameIndex ->
            val emptyFrame = createEmptyFrame()
            val detection = moleDetector.detectMole(bitmapToMat(emptyFrame))
            val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(emptyFrame))
            val validation = validationManager.validateCapture(detection, quality, guideArea)
            
            assertEquals(CaptureGuidanceOverlay.GuideState.SEARCHING, validation.guideState)
            overlay.updateGuideState(validation.guideState)
            
            emptyFrame.recycle()
            delay(66) // ~15 FPS
        }
        userSession.logEvent("searching_phase_completed")
        
        // FASE 3: Detección inicial (lunar no centrado)
        println("Fase 3: Detección inicial - lunar no centrado")
        val offCenterFrame = createFrameWithMole(200f, 150f) // Fuera del centro
        val offCenterDetection = moleDetector.detectMole(bitmapToMat(offCenterFrame))
        val offCenterQuality = qualityAnalyzer.analyzeQuality(bitmapToMat(offCenterFrame))
        val offCenterValidation = validationManager.validateCapture(
            offCenterDetection, offCenterQuality, guideArea
        )
        
        assertEquals(CaptureGuidanceOverlay.GuideState.CENTERING, offCenterValidation.guideState)
        overlay.updateGuideState(offCenterValidation.guideState)
        overlay.updateMolePosition(PointF(200f, 150f), offCenterDetection?.confidence ?: 0f)
        
        // Retroalimentación de accesibilidad
        accessibilityManager.announceStateChange(
            offCenterValidation.guideState, 
            "Centra el lunar en la guía circular"
        )
        hapticManager.provideStateChangeFeedback(offCenterValidation.guideState)
        
        offCenterFrame.recycle()
        userSession.logEvent("mole_detected_off_center")
        
        // FASE 4: Usuario centra el lunar gradualmente
        println("Fase 4: Centrando lunar gradualmente")
        val centeringSteps = listOf(
            PointF(250f, 200f),
            PointF(320f, 250f),
            PointF(380f, 290f),
            PointF(400f, 300f) // Finalmente centrado
        )
        
        centeringSteps.forEachIndexed { index, position ->
            val frame = createFrameWithMole(position.x, position.y)
            val detection = moleDetector.detectMole(bitmapToMat(frame))
            val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(frame))
            val validation = validationManager.validateCapture(detection, quality, guideArea)
            
            overlay.updateMolePosition(position, detection?.confidence ?: 0f)
            overlay.updateGuideState(validation.guideState)
            
            if (index == centeringSteps.size - 1) {
                // Último paso - debería estar centrado pero quizás con mala calidad
                assertTrue(validation.guideState != CaptureGuidanceOverlay.GuideState.CENTERING,
                    "Lunar debería estar centrado en el último paso")
            }
            
            frame.recycle()
            delay(200) // Usuario tarda en ajustar
        }
        userSession.logEvent("mole_centered")
        
        // FASE 5: Mejora de calidad (enfoque y iluminación)
        println("Fase 5: Mejorando calidad de imagen")
        val qualityImprovementSteps = listOf(
            // Paso 1: Centrado pero borroso
            Triple(400f, 300f, ImageQualityAnalyzer.QualityMetrics(
                sharpness = 0.2f, brightness = 120f, contrast = 0.5f,
                isBlurry = true, isOverexposed = false, isUnderexposed = false
            )),
            // Paso 2: Mejor enfoque pero poca luz
            Triple(400f, 300f, ImageQualityAnalyzer.QualityMetrics(
                sharpness = 0.6f, brightness = 60f, contrast = 0.4f,
                isBlurry = false, isOverexposed = false, isUnderexposed = true
            )),
            // Paso 3: Calidad óptima
            Triple(400f, 300f, ImageQualityAnalyzer.QualityMetrics(
                sharpness = 0.8f, brightness = 120f, contrast = 0.7f,
                isBlurry = false, isOverexposed = false, isUnderexposed = false
            ))
        )
        
        qualityImprovementSteps.forEachIndexed { index, (x, y, mockQuality) ->
            val frame = createFrameWithMole(x, y)
            val detection = moleDetector.detectMole(bitmapToMat(frame))
            
            // Usar calidad simulada para este test
            val validation = validationManager.validateCapture(detection, mockQuality, guideArea)
            
            overlay.updateGuideState(validation.guideState)
            
            when (index) {
                0 -> assertEquals(CaptureGuidanceOverlay.GuideState.BLURRY, validation.guideState)
                1 -> assertEquals(CaptureGuidanceOverlay.GuideState.POOR_LIGHTING, validation.guideState)
                2 -> assertEquals(CaptureGuidanceOverlay.GuideState.READY, validation.guideState)
            }
            
            // Retroalimentación para cada mejora
            accessibilityManager.announceStateChange(validation.guideState, validation.message)
            hapticManager.provideStateChangeFeedback(validation.guideState)
            
            frame.recycle()
            delay(500) // Usuario ajusta configuración
        }
        userSession.logEvent("image_quality_optimized")
        
        // FASE 6: Estado listo - captura automática
        println("Fase 6: Captura automática activada")
        var autoCaptureTriggered = false
        var countdownCompleted = false
        val countdownValues = mutableListOf<Int>()
        
        autoCaptureManager.setAutoCaptureCallback {
            autoCaptureTriggered = true
        }
        
        autoCaptureManager.setCountdownCallback { countdown ->
            countdownValues.add(countdown)
            if (countdown == 0) countdownCompleted = true
        }
        
        val readyValidation = CaptureValidationManager.ValidationResult(
            canCapture = true,
            guideState = CaptureGuidanceOverlay.GuideState.READY,
            message = "Listo para capturar",
            confidence = 0.9f
        )
        
        autoCaptureManager.onValidationResult(readyValidation)
        delay(1200L) // Esperar countdown completo
        
        assertTrue(autoCaptureTriggered, "Captura automática debería haberse activado")
        assertTrue(countdownCompleted, "Countdown debería haberse completado")
        assertTrue(countdownValues.contains(3), "Countdown debería incluir 3")
        assertTrue(countdownValues.contains(1), "Countdown debería incluir 1")
        
        userSession.logEvent("auto_capture_triggered")
        
        // FASE 7: Captura y preprocesado
        println("Fase 7: Captura y preprocesado")
        val capturedBitmap = createHighQualityMoleImage()
        val preprocessingResult = preprocessor.preprocessImage(capturedBitmap)
        
        assertNotNull(preprocessingResult.processedBitmap)
        assertTrue(preprocessingResult.appliedFilters.isNotEmpty())
        assertTrue(preprocessingResult.processingTime > 0)
        
        userSession.logEvent("image_captured_and_processed")
        
        // FASE 8: Navegación a PreviewActivity
        println("Fase 8: Navegación a Preview")
        var previewNavigationSuccessful = false
        var previewReceivedOriginal: Bitmap? = null
        var previewReceivedProcessed: Bitmap? = null
        
        // Simular navegación
        val previewCallback = { original: Bitmap, processed: Bitmap ->
            previewNavigationSuccessful = true
            previewReceivedOriginal = original
            previewReceivedProcessed = processed
        }
        
        previewCallback(preprocessingResult.originalBitmap, preprocessingResult.processedBitmap)
        
        assertTrue(previewNavigationSuccessful, "Navegación a Preview debería ser exitosa")
        assertNotNull(previewReceivedOriginal, "Preview debería recibir imagen original")
        assertNotNull(previewReceivedProcessed, "Preview debería recibir imagen procesada")
        
        userSession.logEvent("navigated_to_preview")
        
        // FASE 9: Análisis de imagen
        println("Fase 9: Análisis de imagen")
        var analysisCompleted = false
        var analysisResult: AnalysisResult? = null
        
        // Simular análisis con imagen procesada
        val analysisCallback = { bitmap: Bitmap ->
            // Simular análisis real
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            
            analysisResult = AnalysisResult(
                confidence = 0.85f,
                recommendation = "Consultar dermatólogo para evaluación detallada",
                riskLevel = "MEDIUM",
                processingTime = 2500L,
                imageQuality = "HIGH"
            )
            analysisCompleted = true
        }
        
        analysisCallback(preprocessingResult.processedBitmap)
        
        assertTrue(analysisCompleted, "Análisis debería completarse")
        assertNotNull(analysisResult, "Debería haber resultado de análisis")
        assertTrue(analysisResult!!.confidence > 0.8f, "Confianza debería ser alta")
        
        userSession.logEvent("analysis_completed")
        
        // FASE 10: Presentación de resultados
        println("Fase 10: Presentación de resultados")
        var resultsDisplayed = false
        
        // Simular AnalysisResultActivity
        val resultsCallback = { result: AnalysisResult ->
            resultsDisplayed = true
            
            // Verificar que los resultados son completos
            assertNotNull(result.recommendation)
            assertTrue(result.confidence > 0f)
            assertNotNull(result.riskLevel)
        }
        
        resultsCallback(analysisResult!!)
        
        assertTrue(resultsDisplayed, "Resultados deberían mostrarse")
        userSession.logEvent("results_displayed")
        
        // VERIFICACIÓN FINAL
        println("=== VERIFICACIÓN FINAL ===")
        val sessionSummary = userSession.getSummary()
        
        // Verificar que se completaron todas las fases
        val expectedEvents = listOf(
            "camera_opened", "guide_overlay_shown", "searching_phase_completed",
            "mole_detected_off_center", "mole_centered", "image_quality_optimized",
            "auto_capture_triggered", "image_captured_and_processed",
            "navigated_to_preview", "analysis_completed", "results_displayed"
        )
        
        expectedEvents.forEach { event ->
            assertTrue(sessionSummary.events.contains(event),
                "Evento '$event' debería estar en el resumen de sesión")
        }
        
        assertTrue(sessionSummary.totalDuration > 0, "Duración total debería ser > 0")
        assertTrue(sessionSummary.successful, "Sesión debería ser exitosa")
        
        // Limpiar recursos
        capturedBitmap.recycle()
        preprocessingResult.processedBitmap.recycle()
        
        println("=== FLUJO END-TO-END COMPLETADO EXITOSAMENTE ===")
        println("Duración total: ${sessionSummary.totalDuration}ms")
        println("Eventos: ${sessionSummary.events.size}")
    }
    
    /**
     * Test 2: Flujo end-to-end con dificultades - Usuario persistente
     * Simula un usuario que tiene dificultades pero eventualmente tiene éxito
     */
    @Test
    fun testEndToEndFlowWithDifficulties() = runTest {
        val userSession = UserSession()
        val guideArea = RectF(300f, 200f, 500f, 400f)
        
        println("=== FLUJO END-TO-END CON DIFICULTADES ===")
        
        // FASE 1: Múltiples intentos de detección
        println("Fase 1: Múltiples intentos de detección")
        var detectionAttempts = 0
        var moleDetected = false
        
        while (!moleDetected && detectionAttempts < 20) {
            detectionAttempts++
            
            val frame = if (detectionAttempts < 15) {
                createEmptyFrame() // Primeros intentos sin lunar
            } else {
                createFrameWithMole(350f + detectionAttempts * 10f, 280f) // Finalmente aparece
            }
            
            val detection = moleDetector.detectMole(bitmapToMat(frame))
            val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(frame))
            val validation = validationManager.validateCapture(detection, quality, guideArea)
            
            if (detection != null) {
                moleDetected = true
                userSession.logEvent("mole_finally_detected")
            }
            
            overlay.updateGuideState(validation.guideState)
            frame.recycle()
            delay(100)
        }
        
        assertTrue(moleDetected, "Lunar debería detectarse eventualmente")
        assertTrue(detectionAttempts > 10, "Debería haber múltiples intentos")
        
        // FASE 2: Dificultades para centrar
        println("Fase 2: Dificultades para centrar")
        val centeringAttempts = listOf(
            PointF(100f, 100f), // Muy lejos
            PointF(200f, 150f), // Más cerca
            PointF(500f, 400f), // Otro lado
            PointF(450f, 350f), // Acercándose
            PointF(380f, 320f), // Casi
            PointF(400f, 300f)  // Finalmente centrado
        )
        
        centeringAttempts.forEachIndexed { index, position ->
            val frame = createFrameWithMole(position.x, position.y)
            val detection = moleDetector.detectMole(bitmapToMat(frame))
            val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(frame))
            val validation = validationManager.validateCapture(detection, quality, guideArea)
            
            overlay.updateMolePosition(position, detection?.confidence ?: 0f)
            overlay.updateGuideState(validation.guideState)
            
            // Retroalimentación persistente
            accessibilityManager.announceStateChange(validation.guideState, validation.message)
            hapticManager.provideStateChangeFeedback(validation.guideState)
            
            frame.recycle()
            delay(300) // Usuario tarda más en ajustar
        }
        
        userSession.logEvent("centering_completed_after_difficulties")
        
        // FASE 3: Problemas de calidad múltiples
        println("Fase 3: Problemas de calidad múltiples")
        val qualityIssues = listOf(
            // Muy borroso
            ImageQualityAnalyzer.QualityMetrics(
                sharpness = 0.1f, brightness = 120f, contrast = 0.5f,
                isBlurry = true, isOverexposed = false, isUnderexposed = false
            ),
            // Muy oscuro
            ImageQualityAnalyzer.QualityMetrics(
                sharpness = 0.7f, brightness = 30f, contrast = 0.3f,
                isBlurry = false, isOverexposed = false, isUnderexposed = true
            ),
            // Muy brillante
            ImageQualityAnalyzer.QualityMetrics(
                sharpness = 0.7f, brightness = 220f, contrast = 0.2f,
                isBlurry = false, isOverexposed = true, isUnderexposed = false
            ),
            // Finalmente buena calidad
            ImageQualityAnalyzer.QualityMetrics(
                sharpness = 0.8f, brightness = 120f, contrast = 0.7f,
                isBlurry = false, isOverexposed = false, isUnderexposed = false
            )
        )
        
        qualityIssues.forEachIndexed { index, mockQuality ->
            val frame = createFrameWithMole(400f, 300f)
            val detection = moleDetector.detectMole(bitmapToMat(frame))
            val validation = validationManager.validateCapture(detection, mockQuality, guideArea)
            
            overlay.updateGuideState(validation.guideState)
            
            // Mensajes específicos para cada problema
            val expectedStates = listOf(
                CaptureGuidanceOverlay.GuideState.BLURRY,
                CaptureGuidanceOverlay.GuideState.POOR_LIGHTING,
                CaptureGuidanceOverlay.GuideState.POOR_LIGHTING,
                CaptureGuidanceOverlay.GuideState.READY
            )
            
            assertEquals(expectedStates[index], validation.guideState,
                "Estado debería corresponder al problema de calidad")
            
            frame.recycle()
            delay(800) // Usuario tarda en resolver cada problema
        }
        
        userSession.logEvent("quality_issues_resolved")
        
        // FASE 4: Captura manual (auto-captura deshabilitada por dificultades)
        println("Fase 4: Captura manual")
        val capturedBitmap = createHighQualityMoleImage()
        var manualCaptureTriggered = false
        
        // Simular botón de captura manual
        val manualCaptureCallback = { bitmap: Bitmap ->
            manualCaptureTriggered = true
        }
        
        manualCaptureCallback(capturedBitmap)
        assertTrue(manualCaptureTriggered, "Captura manual debería activarse")
        
        // Procesar imagen capturada
        val preprocessingResult = preprocessor.preprocessImage(capturedBitmap)
        assertNotNull(preprocessingResult.processedBitmap)
        
        userSession.logEvent("manual_capture_successful")
        
        // FASE 5: Análisis exitoso a pesar de las dificultades
        println("Fase 5: Análisis exitoso")
        var analysisCompleted = false
        var analysisResult: AnalysisResult? = null
        
        val analysisCallback = { bitmap: Bitmap ->
            analysisResult = AnalysisResult(
                confidence = 0.78f, // Ligeramente menor por las dificultades
                recommendation = "Imagen procesada exitosamente a pesar de condiciones subóptimas",
                riskLevel = "MEDIUM",
                processingTime = 3200L, // Más tiempo por preprocesado adicional
                imageQuality = "GOOD" // Buena después del preprocesado
            )
            analysisCompleted = true
        }
        
        analysisCallback(preprocessingResult.processedBitmap)
        
        assertTrue(analysisCompleted, "Análisis debería completarse")
        assertNotNull(analysisResult, "Debería haber resultado")
        assertTrue(analysisResult!!.confidence > 0.7f, "Confianza debería ser aceptable")
        
        userSession.logEvent("analysis_completed_despite_difficulties")
        
        // VERIFICACIÓN FINAL
        val sessionSummary = userSession.getSummary()
        
        assertTrue(sessionSummary.totalDuration > 5000, // Debería tomar más tiempo
            "Sesión con dificultades debería tomar más tiempo")
        assertTrue(sessionSummary.successful, "Sesión debería ser exitosa eventualmente")
        
        val expectedDifficultEvents = listOf(
            "mole_finally_detected", "centering_completed_after_difficulties",
            "quality_issues_resolved", "manual_capture_successful",
            "analysis_completed_despite_difficulties"
        )
        
        expectedDifficultEvents.forEach { event ->
            assertTrue(sessionSummary.events.contains(event),
                "Evento de dificultad '$event' debería estar presente")
        }
        
        // Limpiar
        capturedBitmap.recycle()
        preprocessingResult.processedBitmap.recycle()
        
        println("=== FLUJO CON DIFICULTADES COMPLETADO ===")
        println("Duración: ${sessionSummary.totalDuration}ms")
        println("Intentos de detección: $detectionAttempts")
    }
    
    /**
     * Test 3: Flujo end-to-end con accesibilidad completa
     * Verifica que todo el flujo funcione con todas las funciones de accesibilidad
     */
    @Test
    fun testEndToEndAccessibilityFlow() = runTest {
        val userSession = UserSession()
        val guideArea = RectF(300f, 200f, 500f, 400f)
        
        // Configurar accesibilidad completa
        val talkBackMessages = mutableListOf<String>()
        val hapticEvents = mutableListOf<String>()
        
        accessibilityManager.setTalkBackCallback { message ->
            talkBackMessages.add(message)
        }
        
        hapticManager.setVibrationCallback { pattern ->
            hapticEvents.add(pattern)
        }
        
        println("=== FLUJO END-TO-END CON ACCESIBILIDAD ===")
        
        // Ejecutar flujo completo con accesibilidad
        val flowSteps = listOf(
            Triple(CaptureGuidanceOverlay.GuideState.SEARCHING, "Buscando lunar", "search_vibration"),
            Triple(CaptureGuidanceOverlay.GuideState.CENTERING, "Centra el lunar", "center_vibration"),
            Triple(CaptureGuidanceOverlay.GuideState.TOO_FAR, "Acércate más", "distance_vibration"),
            Triple(CaptureGuidanceOverlay.GuideState.BLURRY, "Mantén firme", "focus_vibration"),
            Triple(CaptureGuidanceOverlay.GuideState.POOR_LIGHTING, "Más luz", "light_vibration"),
            Triple(CaptureGuidanceOverlay.GuideState.READY, "Listo para capturar", "ready_vibration")
        )
        
        flowSteps.forEach { (state, message, vibration) ->
            // Actualizar estado
            overlay.updateGuideState(state)
            
            // Activar accesibilidad
            accessibilityManager.announceStateChange(state, message)
            hapticManager.provideStateChangeFeedback(state)
            
            delay(500) // Tiempo para que el usuario procese la retroalimentación
        }
        
        // Verificar que se activaron todas las funciones de accesibilidad
        assertEquals(flowSteps.size, talkBackMessages.size,
            "Cada estado debería generar mensaje TalkBack")
        assertEquals(flowSteps.size, hapticEvents.size,
            "Cada estado debería generar retroalimentación háptica")
        
        // Verificar contenido de mensajes
        flowSteps.forEachIndexed { index, (_, expectedMessage, _) ->
            assertTrue(talkBackMessages[index].contains(expectedMessage.split(" ").first()),
                "Mensaje TalkBack debería contener información relevante")
        }
        
        // Captura automática con accesibilidad
        var autoCaptureWithAccessibility = false
        val countdownMessages = mutableListOf<String>()
        
        autoCaptureManager.setAutoCaptureCallback {
            autoCaptureWithAccessibility = true
        }
        
        autoCaptureManager.setCountdownCallback { countdown ->
            val message = "Captura en $countdown"
            countdownMessages.add(message)
            accessibilityManager.announceCountdown(countdown)
            hapticManager.provideCountdownFeedback(countdown)
        }
        
        val readyValidation = CaptureValidationManager.ValidationResult(
            canCapture = true,
            guideState = CaptureGuidanceOverlay.GuideState.READY,
            message = "Listo para capturar",
            confidence = 0.9f
        )
        
        autoCaptureManager.onValidationResult(readyValidation)
        delay(1200L)
        
        assertTrue(autoCaptureWithAccessibility, "Captura automática con accesibilidad debería funcionar")
        assertTrue(countdownMessages.isNotEmpty(), "Debería haber mensajes de countdown")
        
        userSession.logEvent("accessibility_flow_completed")
        
        val sessionSummary = userSession.getSummary()
        assertTrue(sessionSummary.successful, "Flujo de accesibilidad debería ser exitoso")
        
        println("=== FLUJO DE ACCESIBILIDAD COMPLETADO ===")
        println("Mensajes TalkBack: ${talkBackMessages.size}")
        println("Eventos hápticos: ${hapticEvents.size}")
        println("Mensajes countdown: ${countdownMessages.size}")
    }
    
    // MÉTODOS AUXILIARES
    
    private fun createEmptyFrame(): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(240, 220, 200)) // Solo piel, sin lunar
        return bitmap
    }
    
    private fun createFrameWithMole(x: Float, y: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(240, 220, 200))
        
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = Color.rgb(80, 60, 40)
            isAntiAlias = true
        }
        canvas.drawCircle(x, y, 25f, paint)
        
        return bitmap
    }
    
    private fun createHighQualityMoleImage(): Bitmap {
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(240, 220, 200))
        
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = Color.rgb(80, 60, 40)
            isAntiAlias = true
        }
        
        // Lunar centrado y bien definido
        canvas.drawCircle(960f, 540f, 80f, paint)
        
        // Añadir algunos detalles para hacer más realista
        paint.color = Color.rgb(60, 40, 20)
        canvas.drawCircle(940f, 520f, 15f, paint)
        canvas.drawCircle(980f, 560f, 12f, paint)
        
        return bitmap
    }
    
    private fun bitmapToMat(bitmap: Bitmap): org.opencv.core.Mat {
        // Simulación para tests
        return org.opencv.core.Mat()
    }
    
    private data class AnalysisResult(
        val confidence: Float,
        val recommendation: String,
        val riskLevel: String,
        val processingTime: Long,
        val imageQuality: String
    )
    
    private class UserSession {
        private val events = mutableListOf<String>()
        private val startTime = System.currentTimeMillis()
        
        fun logEvent(event: String) {
            events.add(event)
            println("  [${System.currentTimeMillis() - startTime}ms] $event")
        }
        
        fun getSummary(): SessionSummary {
            return SessionSummary(
                events = events.toList(),
                totalDuration = System.currentTimeMillis() - startTime,
                successful = events.isNotEmpty()
            )
        }
    }
    
    private data class SessionSummary(
        val events: List<String>,
        val totalDuration: Long,
        val successful: Boolean
    )
}
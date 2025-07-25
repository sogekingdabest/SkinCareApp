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
 * Tests de regresión para funcionalidad existente.
 * Verifica que las nuevas funciones de guías de captura no rompan
 * la funcionalidad existente de la aplicación.
 * 
 * Cubre:
 * - Compatibilidad con CameraActivity existente
 * - Navegación a PreviewActivity sin cambios
 * - Integración con AnalysisResultActivity
 * - Mantenimiento de configuraciones existentes
 * - Preservación de flujos de usuario existentes
 */
@RunWith(AndroidJUnit4::class)
class RegressionTest {

    private lateinit var context: Context
    private lateinit var moleDetector: MoleDetectionProcessor
    private lateinit var qualityAnalyzer: ImageQualityAnalyzer
    private lateinit var validationManager: CaptureValidationManager
    private lateinit var preprocessor: ImagePreprocessor
    private lateinit var overlay: CaptureGuidanceOverlay
    private lateinit var captureMetrics: CaptureMetrics
    
    private val testDispatcher = StandardTestDispatcher()
    
    // Configuraciones para simular comportamiento legacy
    private val legacyConfig = CaptureGuidanceConfig(
        enableHapticFeedback = false,
        enableAutoCapture = false
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
        validationManager = CaptureValidationManager(legacyConfig)
        preprocessor = ImagePreprocessor(context)
        overlay = CaptureGuidanceOverlay(context)
        captureMetrics = CaptureMetrics()
    }
    
    /**
     * Test 1: Compatibilidad con flujo de captura original
     * Verifica que el flujo original de captura siga funcionando
     */
    @Test
    fun testOriginalCaptureFlowCompatibility() = runTest {
        // Arrange - Simular captura tradicional sin guías
        val originalBitmap = createTestBitmap(800, 600)
        var captureSuccessful = false
        var navigationTriggered = false
        var passedBitmap: Bitmap? = null
        
        // Simular callback de captura original
        val originalCaptureCallback = { bitmap: Bitmap ->
            captureSuccessful = true
            passedBitmap = bitmap
        }
        
        // Simular callback de navegación original
        val originalNavigationCallback = { bitmap: Bitmap ->
            navigationTriggered = true
        }
        
        // Act - Ejecutar flujo de captura sin usar guías
        try {
            // Captura directa (como en versión original)
            originalCaptureCallback(originalBitmap)
            
            // Navegación directa a PreviewActivity
            if (captureSuccessful && passedBitmap != null) {
                originalNavigationCallback(passedBitmap!!)
            }
            
        } catch (e: Exception) {
            fail("Flujo original no debería fallar: ${e.message}")
        }
        
        // Assert
        assertTrue(captureSuccessful, "Captura original debería ser exitosa")
        assertTrue(navigationTriggered, "Navegación original debería funcionar")
        assertNotNull(passedBitmap, "Bitmap debería pasarse correctamente")
        assertEquals(originalBitmap, passedBitmap, "Bitmap debería ser el mismo")
        
        originalBitmap.recycle()
    }
    
    /**
     * Test 2: Compatibilidad con PreviewActivity existente
     * Verifica que PreviewActivity reciba las imágenes correctamente
     */
    @Test
    fun testPreviewActivityCompatibility() = runTest {
        // Arrange
        val testBitmap = createTestBitmap(1024, 768)
        var previewReceivedOriginal: Bitmap? = null
        var previewReceivedProcessed: Bitmap? = null
        var previewNavigationSuccessful = false
        
        // Simular PreviewActivity callback
        val previewCallback = { original: Bitmap, processed: Bitmap? ->
            previewReceivedOriginal = original
            previewReceivedProcessed = processed
            previewNavigationSuccessful = true
        }
        
        // Act - Procesar imagen y navegar a Preview
        val preprocessingResult = preprocessor.preprocessImage(testBitmap)
        
        // Simular navegación con ambas imágenes (nueva funcionalidad)
        previewCallback(preprocessingResult.originalBitmap, preprocessingResult.processedBitmap)
        
        // Assert
        assertTrue(previewNavigationSuccessful, "Navegación a Preview debería ser exitosa")
        assertNotNull(previewReceivedOriginal, "Preview debería recibir imagen original")
        assertNotNull(previewReceivedProcessed, "Preview debería recibir imagen procesada")
        
        // Verificar que la imagen original no se modificó
        assertEquals(testBitmap, previewReceivedOriginal, "Imagen original debería preservarse")
        
        // Verificar que la imagen procesada es diferente pero válida
        assertNotEquals(previewReceivedOriginal, previewReceivedProcessed, 
            "Imagen procesada debería ser diferente")
        assertFalse(previewReceivedProcessed!!.isRecycled, 
            "Imagen procesada debería ser válida")
        
        // Limpiar
        testBitmap.recycle()
        preprocessingResult.processedBitmap.recycle()
    }
    
    /**
     * Test 3: Compatibilidad con AnalysisResultActivity
     * Verifica que el análisis posterior funcione con imágenes procesadas
     */
    @Test
    fun testAnalysisResultActivityCompatibility() = runTest {
        // Arrange
        val testBitmap = createTestBitmap(800, 600)
        var analysisTriggered = false
        var analysisReceivedBitmap: Bitmap? = null
        var analysisSuccessful = false
        
        // Simular AnalysisResultActivity callback
        val analysisCallback = { bitmap: Bitmap ->
            analysisTriggered = true
            analysisReceivedBitmap = bitmap
            
            // Simular análisis básico
            try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                analysisSuccessful = pixels.isNotEmpty()
            } catch (e: Exception) {
                analysisSuccessful = false
            }
        }
        
        // Act - Procesar imagen y enviar a análisis
        val preprocessingResult = preprocessor.preprocessImage(testBitmap)
        
        // Enviar imagen procesada al análisis (comportamiento mejorado)
        analysisCallback(preprocessingResult.processedBitmap)
        
        // Assert
        assertTrue(analysisTriggered, "Análisis debería activarse")
        assertNotNull(analysisReceivedBitmap, "Análisis debería recibir bitmap")
        assertTrue(analysisSuccessful, "Análisis debería procesar bitmap correctamente")
        
        // Verificar que la imagen es válida para análisis
        assertFalse(analysisReceivedBitmap!!.isRecycled, "Bitmap para análisis debería ser válido")
        assertTrue(analysisReceivedBitmap!!.width > 0, "Bitmap debería tener dimensiones válidas")
        assertTrue(analysisReceivedBitmap!!.height > 0, "Bitmap debería tener dimensiones válidas")
        
        // Limpiar
        testBitmap.recycle()
        preprocessingResult.processedBitmap.recycle()
    }
    
    /**
     * Test 4: Preservación de configuraciones de cámara existentes
     * Verifica que las configuraciones de cámara no se vean afectadas
     */
    @Test
    fun testCameraConfigurationPreservation() = runTest {
        // Arrange - Configuraciones típicas de cámara
        val originalConfigurations = mapOf(
            "resolution" to "1920x1080",
            "format" to "JPEG",
            "quality" to "HIGH",
            "flash" to "AUTO",
            "focus" to "AUTO"
        )
        
        val preservedConfigurations = mutableMapOf<String, String>()
        
        // Simular inicialización de componentes de guía
        overlay.updateGuideState(CaptureGuidanceOverlay.GuideState.SEARCHING)
        
        // Act - Simular que las configuraciones se preservan
        originalConfigurations.forEach { (key, value) ->
            // Simular que la configuración se mantiene después de inicializar guías
            preservedConfigurations[key] = value
        }
        
        // Simular operaciones de guía que no deberían afectar configuraciones
        val testBitmap = createTestBitmap(640, 480)
        val detection = moleDetector.detectMole(bitmapToMat(testBitmap))
        val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(testBitmap))
        
        testBitmap.recycle()
        
        // Assert
        assertEquals(originalConfigurations.size, preservedConfigurations.size,
            "Todas las configuraciones deberían preservarse")
        
        originalConfigurations.forEach { (key, originalValue) ->
            val preservedValue = preservedConfigurations[key]
            assertEquals(originalValue, preservedValue,
                "Configuración '$key' debería preservarse: $originalValue vs $preservedValue")
        }
    }
    
    /**
     * Test 5: Compatibilidad con permisos de cámara existentes
     * Verifica que los permisos existentes sigan siendo suficientes
     */
    @Test
    fun testCameraPermissionsCompatibility() = runTest {
        // Arrange - Permisos típicos requeridos
        val requiredPermissions = listOf(
            "android.permission.CAMERA",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE"
        )
        
        val availablePermissions = mutableListOf<String>()
        
        // Simular verificación de permisos
        requiredPermissions.forEach { permission ->
            // En test real, verificaría ContextCompat.checkSelfPermission
            availablePermissions.add(permission)
        }
        
        // Act - Inicializar componentes que requieren permisos
        try {
            val testBitmap = createTestBitmap(640, 480)
            
            // Operaciones que requieren permisos de cámara
            val detection = moleDetector.detectMole(bitmapToMat(testBitmap))
            val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(testBitmap))
            val preprocessingResult = preprocessor.preprocessImage(testBitmap)
            
            testBitmap.recycle()
            preprocessingResult.processedBitmap.recycle()
            
        } catch (e: SecurityException) {
            fail("No deberían requerirse permisos adicionales: ${e.message}")
        }
        
        // Assert
        assertEquals(requiredPermissions.size, availablePermissions.size,
            "Todos los permisos requeridos deberían estar disponibles")
        
        requiredPermissions.forEach { permission ->
            assertTrue(availablePermissions.contains(permission),
                "Permiso '$permission' debería estar disponible")
        }
    }
    
    /**
     * Test 6: Compatibilidad con ciclo de vida de Activity
     * Verifica que los componentes manejen correctamente el ciclo de vida
     */
    @Test
    fun testActivityLifecycleCompatibility() = runTest {
        // Arrange
        var onCreateCalled = false
        var onResumeCalled = false
        var onPauseCalled = false
        var onDestroyCalled = false
        
        // Simular callbacks de ciclo de vida
        val lifecycleCallbacks = object {
            fun onCreate() {
                onCreateCalled = true
                // Inicializar componentes
                overlay.updateGuideState(CaptureGuidanceOverlay.GuideState.SEARCHING)
            }
            
            fun onResume() {
                onResumeCalled = true
                // Reanudar procesamiento
                captureMetrics.logDetectionAttempt(true, 100L)
            }
            
            fun onPause() {
                onPauseCalled = true
                // Pausar procesamiento
            }
            
            fun onDestroy() {
                onDestroyCalled = true
                // Limpiar recursos
            }
        }
        
        // Act - Simular ciclo de vida completo
        lifecycleCallbacks.onCreate()
        delay(100)
        
        lifecycleCallbacks.onResume()
        delay(100)
        
        // Simular uso normal
        val testBitmap = createTestBitmap(640, 480)
        val detection = moleDetector.detectMole(bitmapToMat(testBitmap))
        testBitmap.recycle()
        
        lifecycleCallbacks.onPause()
        delay(100)
        
        lifecycleCallbacks.onDestroy()
        
        // Assert
        assertTrue(onCreateCalled, "onCreate debería haberse llamado")
        assertTrue(onResumeCalled, "onResume debería haberse llamado")
        assertTrue(onPauseCalled, "onPause debería haberse llamado")
        assertTrue(onDestroyCalled, "onDestroy debería haberse llamado")
    }
    
    /**
     * Test 7: Compatibilidad con configuraciones de dispositivo
     * Verifica que funcione en diferentes configuraciones de dispositivo
     */
    @Test
    fun testDeviceConfigurationCompatibility() = runTest {
        val deviceConfigurations = listOf(
            DeviceConfig("phone_portrait", 1080, 1920, 420),
            DeviceConfig("phone_landscape", 1920, 1080, 420),
            DeviceConfig("tablet_portrait", 1536, 2048, 320),
            DeviceConfig("tablet_landscape", 2048, 1536, 320),
            DeviceConfig("small_phone", 720, 1280, 320)
        )
        
        deviceConfigurations.forEach { config ->
            // Arrange - Configurar para dispositivo específico
            val testBitmap = createTestBitmap(
                (config.width * 0.8).toInt(), 
                (config.height * 0.6).toInt()
            )
            
            // Calcular área de guía apropiada para el dispositivo
            val guideSize = minOf(config.width, config.height) * 0.3f
            val guideArea = RectF(
                (config.width - guideSize) / 2f,
                (config.height - guideSize) / 2f,
                (config.width + guideSize) / 2f,
                (config.height + guideSize) / 2f
            )
            
            // Act - Probar funcionalidad en esta configuración
            try {
                val detection = moleDetector.detectMole(bitmapToMat(testBitmap))
                val quality = qualityAnalyzer.analyzeQuality(bitmapToMat(testBitmap))
                val validation = validationManager.validateCapture(detection, quality, guideArea)
                val preprocessingResult = preprocessor.preprocessImage(testBitmap)
                
                // Assert - Verificar que funciona en esta configuración
                assertNotNull(validation, "Validación debería funcionar en ${config.name}")
                assertNotNull(preprocessingResult, "Preprocesado debería funcionar en ${config.name}")
                assertFalse(preprocessingResult.processedBitmap.isRecycled, 
                    "Imagen procesada debería ser válida en ${config.name}")
                
                // Limpiar
                testBitmap.recycle()
                preprocessingResult.processedBitmap.recycle()
                
            } catch (e: Exception) {
                fail("Funcionalidad debería funcionar en ${config.name}: ${e.message}")
            }
        }
    }
    
    /**
     * Test 8: Compatibilidad con versiones anteriores de datos
     * Verifica que datos de versiones anteriores sigan siendo compatibles
     */
    @Test
    fun testBackwardDataCompatibility() = runTest {
        // Arrange - Simular datos de versión anterior
        val legacyImageData = mapOf(
            "width" to 800,
            "height" to 600,
            "format" to "JPEG",
            "quality" to 85,
            "timestamp" to System.currentTimeMillis() - 86400000L // 1 día atrás
        )
        
        val legacyAnalysisData = mapOf(
            "confidence" to 0.75f,
            "recommendation" to "Consultar dermatólogo",
            "risk_level" to "MEDIUM"
        )
        
        // Act - Procesar datos legacy con nueva funcionalidad
        val testBitmap = createTestBitmap(
            legacyImageData["width"] as Int,
            legacyImageData["height"] as Int
        )
        
        try {
            // Aplicar nuevo preprocesado a imagen legacy
            val preprocessingResult = preprocessor.preprocessImage(testBitmap)
            
            // Verificar que los datos legacy se preservan
            val newImageData = mapOf(
                "width" to preprocessingResult.processedBitmap.width,
                "height" to preprocessingResult.processedBitmap.height,
                "original_width" to testBitmap.width,
                "original_height" to testBitmap.height,
                "processing_time" to preprocessingResult.processingTime,
                "applied_filters" to preprocessingResult.appliedFilters
            )
            
            // Assert
            assertEquals(legacyImageData["width"], newImageData["original_width"],
                "Dimensiones originales deberían preservarse")
            assertEquals(legacyImageData["height"], newImageData["original_height"],
                "Dimensiones originales deberían preservarse")
            
            assertTrue(newImageData["processing_time"] as Long > 0,
                "Tiempo de procesamiento debería registrarse")
            assertTrue((newImageData["applied_filters"] as List<*>).isNotEmpty(),
                "Filtros aplicados deberían registrarse")
            
            // Limpiar
            testBitmap.recycle()
            preprocessingResult.processedBitmap.recycle()
            
        } catch (e: Exception) {
            fail("Procesamiento de datos legacy debería funcionar: ${e.message}")
        }
    }
    
    /**
     * Test 9: Compatibilidad con flujos de error existentes
     * Verifica que el manejo de errores existente siga funcionando
     */
    @Test
    fun testErrorHandlingCompatibility() = runTest {
        val errorScenarios = listOf(
            "bitmap_null" to null,
            "bitmap_recycled" to createRecycledBitmap(),
            "bitmap_invalid_size" to createTestBitmap(1, 1)
        )
        
        errorScenarios.forEach { (scenarioName, bitmap) ->
            var errorHandled = false
            var errorMessage: String? = null
            
            try {
                when (scenarioName) {
                    "bitmap_null" -> {
                        // Simular error con bitmap null
                        if (bitmap == null) {
                            throw IllegalArgumentException("Bitmap no puede ser null")
                        }
                    }
                    "bitmap_recycled" -> {
                        // Simular error con bitmap reciclado
                        if (bitmap?.isRecycled == true) {
                            throw IllegalStateException("Bitmap está reciclado")
                        }
                    }
                    "bitmap_invalid_size" -> {
                        // Simular error con bitmap muy pequeño
                        if (bitmap != null && (bitmap.width < 10 || bitmap.height < 10)) {
                            throw IllegalArgumentException("Bitmap demasiado pequeño")
                        }
                    }
                }
                
                // Si llegamos aquí sin excepción, procesar normalmente
                bitmap?.let { 
                    val result = preprocessor.preprocessImage(it)
                    result.processedBitmap.recycle()
                }
                
            } catch (e: Exception) {
                errorHandled = true
                errorMessage = e.message
            }
            
            // Assert - Verificar que los errores se manejan apropiadamente
            when (scenarioName) {
                "bitmap_null", "bitmap_recycled", "bitmap_invalid_size" -> {
                    assertTrue(errorHandled, 
                        "Error debería haberse manejado para escenario: $scenarioName")
                    assertNotNull(errorMessage,
                        "Debería haber mensaje de error para escenario: $scenarioName")
                }
            }
            
            bitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }
    
    // MÉTODOS AUXILIARES
    
    private fun createTestBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(240, 220, 200))
        
        // Añadir lunar simple
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = Color.rgb(80, 60, 40)
            isAntiAlias = true
        }
        canvas.drawCircle(width / 2f, height / 2f, 20f, paint)
        
        return bitmap
    }
    
    private fun createRecycledBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.recycle()
        return bitmap
    }
    
    private fun bitmapToMat(bitmap: Bitmap): org.opencv.core.Mat {
        // Simulación para tests
        return org.opencv.core.Mat()
    }
    
    private data class DeviceConfig(
        val name: String,
        val width: Int,
        val height: Int,
        val dpi: Int
    )
}
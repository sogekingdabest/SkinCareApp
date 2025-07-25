package es.monsteraltech.skincare_tfm.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.camera.guidance.*
import kotlinx.coroutines.*
// OpenCV 4.12.0 compatible imports
// import org.opencv.android.BaseLoaderCallback
// import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import org.opencv.core.Size as OpenCVSize

class CameraActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CameraActivity"
        private const val ANALYSIS_THROTTLE_MS = 100L // Análisis cada 100ms (10 FPS)
    }

    // Componentes de cámara existentes
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var currentCameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isFlashEnabled: Boolean = false

    // Componentes de guía inteligente
    private lateinit var guidanceOverlay: CaptureGuidanceOverlay
    private lateinit var moleDetector: MoleDetectionProcessor
    private lateinit var qualityAnalyzer: ImageQualityAnalyzer
    private lateinit var validationManager: CaptureValidationManager
    private lateinit var preprocessor: ImagePreprocessor
    
    // Componentes de optimización de rendimiento
    private lateinit var performanceManager: PerformanceManager
    private lateinit var roiOptimizer: ROIOptimizer
    private lateinit var thermalDetector: ThermalStateDetector
    
    // Componentes de accesibilidad y retroalimentación
    private lateinit var hapticManager: HapticFeedbackManager
    private lateinit var accessibilityManager: AccessibilityManager
    private lateinit var autoCaptureManager: AutoCaptureManager

    // Estado y configuración
    private var guidanceConfig = CaptureGuidanceConfig()
    private var currentValidationResult: CaptureValidationManager.ValidationResult? = null
    private var isAnalysisEnabled = true
    private val lastAnalysisTime = AtomicLong(0)

    // UI y navegación
    private lateinit var previewLauncher: ActivityResultLauncher<Intent>
    private lateinit var captureButton: FloatingActionButton
    private lateinit var captureStatusIndicator: ImageView
    private var bodyPartColor: String? = null

    // OpenCV
    private var isOpenCVInitialized = false

    // Zoom digital
    private lateinit var digitalZoomController: DigitalZoomController
    private lateinit var zoomControlsView: ZoomControlsView
    private lateinit var guidanceMessageText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        bodyPartColor = intent.getStringExtra("BODY_PART_COLOR")

        // Inicializar componentes de guía
        initializeGuidanceComponents()

        // Configurar navegación
        previewLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val confirmedPath = result.data?.getStringExtra("CONFIRMED_PATH")
                Toast.makeText(this, "Foto confirmada: $confirmedPath", Toast.LENGTH_LONG).show()
            } else if (result.resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Foto descartada", Toast.LENGTH_SHORT).show()
            }
        }

        // Configurar UI
        setupUI()

        // Configurar executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Inicializar OpenCV
        initializeOpenCV()

        // Inicializar controles de zoom digital
        zoomControlsView = findViewById(R.id.zoom_controls_view)
        digitalZoomController = DigitalZoomController(this)
        guidanceMessageText = findViewById(R.id.guidance_message)
        Log.d("DEBUG", "guidanceMessageText inicializado: $guidanceMessageText")
        zoomControlsView.setZoomControlListener(object : ZoomControlsView.ZoomControlListener {
            override fun onZoomIn() { digitalZoomController.zoomIn() }
            override fun onZoomOut() { digitalZoomController.zoomOut() }
            override fun onZoomReset() { digitalZoomController.resetZoom() }
            override fun onZoomOptimal() { digitalZoomController.setOptimalZoomForSmallMoles() }
            override fun onZoomLevelChanged(level: Float) { digitalZoomController.setZoomLevel(level) }
        })
        // Observar cambios de zoom y actualizar UI
        lifecycleScope.launchWhenStarted {
            digitalZoomController.currentZoomLevel.collect { _ ->
                zoomControlsView.updateZoomInfo(digitalZoomController.getZoomInfo())
            }
        }

        // Solicitar permisos y configurar cámara
        val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, "Permiso de cámara requerido", Toast.LENGTH_LONG).show()
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initializeGuidanceComponents() {
        Log.d(TAG, "Inicializando componentes de guía inteligente con optimizaciones de rendimiento")
        
        // Inicializar gestores de rendimiento
        performanceManager = PerformanceManager(this)
        roiOptimizer = ROIOptimizer()
        thermalDetector = ThermalStateDetector(this)
        
        // Inicializar componentes principales con optimizaciones
        moleDetector = MoleDetectionProcessor(this, performanceManager, roiOptimizer, thermalDetector)
        qualityAnalyzer = ImageQualityAnalyzer(performanceManager, thermalDetector)
        validationManager = CaptureValidationManager()
        preprocessor = ImagePreprocessor()
        
        // Configurar listeners de rendimiento
        lifecycleScope.launch {
            performanceManager.currentPerformanceLevel.collect { level ->
                Log.d(TAG, "Nivel de rendimiento actualizado: $level")
                updateAnalysisFrequency(level)
            }
        }
        
        lifecycleScope.launch {
            thermalDetector.currentThermalState.collect { state ->
                Log.d(TAG, "Estado térmico actualizado: $state")
                handleThermalStateChange(state)
            }
        }
        
        // Inicializar componentes de accesibilidad y retroalimentación
        hapticManager = HapticFeedbackManager(this)
        accessibilityManager = AccessibilityManager(this)
        autoCaptureManager = AutoCaptureManager(this, hapticManager, accessibilityManager)
        
        // Configurar listener de captura automática
        autoCaptureManager.setListener(object : AutoCaptureManager.AutoCaptureListener {
            override fun onCountdownStarted(remainingSeconds: Int) {
                runOnUiThread {
                    // guidanceMessageText.text = "Captura automática en $remainingSeconds segundos"
                }
            }
            
            override fun onCountdownTick(remainingSeconds: Int) {
                runOnUiThread {
                    // guidanceMessageText.text = "Capturando en $remainingSeconds..."
                }
            }
            
            override fun onCountdownCancelled() {
                runOnUiThread {
                    // guidanceMessageText.text = "Captura automática cancelada"
                }
            }
            
            override fun onAutoCapture() {
                takePhotoWithValidation()
            }
        })
        
        Log.d(TAG, "Componentes de guía inicializados")
    }
    
    /**
     * Actualiza la frecuencia de análisis basada en el nivel de rendimiento
     */
    private fun updateAnalysisFrequency(level: PerformanceManager.PerformanceLevel) {
        val config = performanceManager.currentConfig.value
        val newThrottleMs = 1000L / config.processingFrequency
        
        // Actualizar throttling de análisis
        imageAnalyzer?.let { analyzer ->
            // Reconfigurar el analizador con nueva frecuencia
            analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageFrame(imageProxy)
            }
        }
        
        Log.d(TAG, "Frecuencia de análisis actualizada: ${config.processingFrequency} FPS")
    }
    

    
    /**
     * Maneja cambios en el estado térmico del dispositivo
     */
    private fun handleThermalStateChange(state: ThermalStateDetector.ThermalState) {
        when (state) {
            ThermalStateDetector.ThermalState.SEVERE,
            ThermalStateDetector.ThermalState.CRITICAL,
            ThermalStateDetector.ThermalState.EMERGENCY -> {
                // Mostrar advertencia al usuario
                runOnUiThread {
                    // guidanceMessageText.text = "Dispositivo caliente - reduciendo rendimiento"
                }
                
                // Pausar análisis temporalmente si es crítico
                if (state == ThermalStateDetector.ThermalState.EMERGENCY) {
                    isAnalysisEnabled = false
                    Handler(Looper.getMainLooper()).postDelayed({
                        isAnalysisEnabled = true
                    }, 10000) // Pausar por 10 segundos
                }
            }
            else -> {
                isAnalysisEnabled = true
            }
        }
    }

    private fun setupUI() {
        previewView = findViewById(R.id.preview_view)
        captureButton = findViewById(R.id.capture_button)
        captureStatusIndicator = findViewById(R.id.capture_status_indicator)

        // Inicializar overlay de guía y añadirlo al contenedor
        guidanceOverlay = CaptureGuidanceOverlay(this)
        guidanceOverlay.updateConfig(guidanceConfig)
        
        val overlayContainer = findViewById<FrameLayout>(R.id.guidance_overlay_container)
        overlayContainer.addView(guidanceOverlay)

        // Botón para capturar imagen (inicialmente deshabilitado)
        captureButton.isEnabled = false
        captureButton.setOnClickListener { takePhotoWithValidation() }
        
        // Configurar accesibilidad inicial
        setupInitialAccessibility()
        
        // Configurar captura automática si está habilitada en configuración
        autoCaptureManager.setEnabled(guidanceConfig.enableAutoCapture)
    }

    private fun initializeOpenCV() {
        // OpenCV 4.12.0 compatible initialization
        try {
            if (OpenCVLoader.initDebug()) {
                Log.d(TAG, "OpenCV library found inside package. Using it!")
                isOpenCVInitialized = true
                setupImageAnalysis()
            } else {
                Log.d(TAG, "Internal OpenCV library not found. Trying static initialization...")
                // Fallback: try static initialization
                if (OpenCVLoader.initLocal()) {
                    Log.d(TAG, "OpenCV initialized successfully with static loader")
                    isOpenCVInitialized = true
                    setupImageAnalysis()
                } else {
                    Log.e(TAG, "OpenCV initialization failed")
                    Toast.makeText(this, "Error inicializando análisis de imagen", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during OpenCV initialization", e)
            Toast.makeText(this, "Error inicializando OpenCV: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupImageAnalysis() {
        if (!isOpenCVInitialized) {
            Log.w(TAG, "OpenCV no inicializado, no se puede configurar análisis")
            return
        }
        
        Log.d(TAG, "Configurando análisis de imagen con OpenCV")
        startRealtimeAnalysis()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Configurar Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            // Configurar ImageCapture
            imageCapture = ImageCapture.Builder()
                .setFlashMode(if (isFlashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .setTargetRotation(previewView.display.rotation)
                .build()

            // Configurar ImageAnalysis para análisis en tiempo real
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetRotation(previewView.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analyzer ->
                    analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageFrame(imageProxy)
                    }
                }

            try {
                // Desenlazar todos los casos de uso previos
                cameraProvider.unbindAll()

                // Enlazar casos de uso al ciclo de vida
                cameraProvider.bindToLifecycle(
                    this, 
                    currentCameraSelector, 
                    preview, 
                    imageCapture, 
                    imageAnalyzer
                ).also { camera ->
                    // Inicializar el controlador de zoom con la cámara
                    digitalZoomController.initialize(camera)
                }

                Log.d(TAG, "Cámara iniciada con análisis en tiempo real")

            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando cámara", e)
                Toast.makeText(this, "Error iniciando cámara: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Inicia el análisis en tiempo real una vez que OpenCV está inicializado
     */
    private fun startRealtimeAnalysis() {
        if (!isOpenCVInitialized) {
            Log.w(TAG, "OpenCV no inicializado, no se puede iniciar análisis")
            return
        }

        isAnalysisEnabled = true
        Log.d(TAG, "Análisis en tiempo real iniciado")
    }

    /**
     * Procesa cada frame de la cámara para análisis en tiempo real
     */
    private fun processImageFrame(imageProxy: ImageProxy) {
        // Implementar throttling para limitar frecuencia de análisis
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalysisTime.get() < ANALYSIS_THROTTLE_MS) {
            imageProxy.close()
            return
        }

        if (!isAnalysisEnabled || !isOpenCVInitialized) {
            imageProxy.close()
            return
        }

        lastAnalysisTime.set(currentTime)

        // Procesar frame en corrutina
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                // Convertir ImageProxy a Mat
                val mat = imageProxyToMat(imageProxy)
                
                if (mat != null && !mat.empty()) {
                    // Pipeline de procesamiento: detección → calidad → validación
                    processAnalysisPipeline(mat)
                    mat.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando frame", e)
            } finally {
                imageProxy.close()
            }
        }
    }

    /**
     * Pipeline principal de procesamiento de análisis
     */
    private suspend fun processAnalysisPipeline(frame: Mat) {
        try {
            // 1. Detección de lunar
            val moleDetection = moleDetector.detectMole(frame)
            
            // 2. Análisis de calidad de imagen
            val qualityMetrics = qualityAnalyzer.analyzeQuality(frame)
            
            // 3. Validación de captura
            val guideArea = getGuideArea()
            val validationResult = validationManager.validateCapture(
                moleDetection, 
                qualityMetrics, 
                guideArea
            )
            
            // 4. Actualizar UI en hilo principal
            withContext(Dispatchers.Main) {
                updateUI(moleDetection, validationResult)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en pipeline de análisis", e)
        }
    }

    /**
     * Convierte ImageProxy a Mat de OpenCV
     */
    private fun imageProxyToMat(imageProxy: ImageProxy): Mat? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            
            // Crear Mat desde bytes
            val mat = Mat(imageProxy.height, imageProxy.width, CvType.CV_8UC1)
            mat.put(0, 0, bytes)
            mat
        } catch (e: Exception) {
            Log.e(TAG, "Error convirtiendo ImageProxy a Mat", e)
            null
        }
    }

    /**
     * Obtiene el área de la guía circular en coordenadas de pantalla
     */
    private fun getGuideArea(): RectF {
        val centerX = previewView.width / 2f
        val centerY = previewView.height / 2f
        val radius = guidanceConfig.guideCircleRadius
        
        return RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
    }

    /**
     * Actualiza la UI basándose en los resultados del análisis
     */
    private fun updateUI(
        moleDetection: MoleDetectionProcessor.MoleDetection?,
        validationResult: CaptureValidationManager.ValidationResult
    ) {
        currentValidationResult = validationResult
        
        // Actualizar posición del lunar en overlay
        val molePosition = moleDetection?.let { 
            PointF(it.centerPoint.x.toFloat(), it.centerPoint.y.toFloat()) 
        }
        guidanceOverlay.updateMolePosition(molePosition, moleDetection?.confidence ?: 0f)
        
        // Actualizar estado de la guía
        guidanceOverlay.updateGuideState(validationResult.guideState)
        // Actualizar mensaje de texto contextual
        val detailedMessage = validationManager.getDetailedGuidanceMessage(validationResult)
        guidanceOverlay.setGuidanceMessage(detailedMessage)
        
        // Configurar accesibilidad del overlay
        accessibilityManager.setupGuidanceOverlayAccessibility(
            guidanceOverlay,
            validationResult.guideState,
            moleDetection != null
        )
        
        // Actualizar indicador de estado
        val statusIcon = when (validationResult.guideState) {
            CaptureValidationManager.GuideState.SEARCHING -> R.drawable.ic_search
            CaptureValidationManager.GuideState.CENTERING -> R.drawable.ic_center_focus_weak
            CaptureValidationManager.GuideState.TOO_FAR -> R.drawable.ic_zoom_in
            CaptureValidationManager.GuideState.TOO_CLOSE -> R.drawable.ic_zoom_out
            CaptureValidationManager.GuideState.POOR_LIGHTING -> R.drawable.ic_wb_sunny
            CaptureValidationManager.GuideState.BLURRY -> R.drawable.ic_blur_on
            CaptureValidationManager.GuideState.READY -> R.drawable.ic_check_circle
        }
        captureStatusIndicator.setImageResource(statusIcon)
        
        // Habilitar/deshabilitar botón de captura
        captureButton.isEnabled = validationResult.canCapture
        
        // Configurar accesibilidad del botón de captura
        accessibilityManager.setupCaptureButtonAccessibility(
            captureButton,
            validationResult.canCapture,
            validationResult.guideState
        )
        
        // Cambiar color del botón según el estado
        val buttonColor = if (validationResult.canCapture) {
            ContextCompat.getColor(this, R.color.risk_very_low)
        } else {
            ContextCompat.getColor(this, R.color.risk_high)
        }
        captureButton.backgroundTintList = android.content.res.ColorStateList.valueOf(buttonColor)
        
        // Cambiar color del mensaje según el estado
        val messageColor = when (validationResult.guideState) {
            CaptureValidationManager.GuideState.READY -> ContextCompat.getColor(this, R.color.risk_very_low)
            CaptureValidationManager.GuideState.SEARCHING, CaptureValidationManager.GuideState.CENTERING -> ContextCompat.getColor(this, R.color.risk_moderate)
            else -> ContextCompat.getColor(this, R.color.risk_high)
        }
        guidanceMessageText.setTextColor(messageColor)
        Log.d("DEBUG", "Actualizando guidanceMessageText: $detailedMessage")
        guidanceMessageText.text = detailedMessage
        Log.d("DEBUG", "Texto actual guidanceMessageText: ${guidanceMessageText.text}")
        
        // Procesar para captura automática si está habilitada
        autoCaptureManager.processValidationResult(validationResult)
        
        // Anunciar progreso de centrado si es relevante
        if (validationResult.guideState == CaptureValidationManager.GuideState.CENTERING && moleDetection != null) {
            val guideCenter = PointF(getGuideArea().centerX(), getGuideArea().centerY())
            val moleCenter = PointF(moleDetection.centerPoint.x.toFloat(), moleDetection.centerPoint.y.toFloat())
            val centeringPercentage = validationManager.calculateCenteringPercentage(moleCenter, guideCenter)
            accessibilityManager.announceCenteringProgress(centeringPercentage)
        }
    }

    /**
     * Configura la accesibilidad inicial de la interfaz
     */
    private fun setupInitialAccessibility() {
        // Configurar accesibilidad del preview
        accessibilityManager.setupViewAccessibility(
            previewView,
            "Vista previa de la cámara para captura de lunares",
            "Mueve la cámara para encontrar y centrar un lunar"
        )
        
        // Configurar accesibilidad de botones
        accessibilityManager.setupViewAccessibility(
            captureButton,
            "Botón de captura",
            "Toca dos veces para capturar cuando esté habilitado"
        )
        
        // Anunciar instrucciones iniciales si TalkBack está activo
        if (accessibilityManager.isTalkBackEnabled()) {
            accessibilityManager.announceGestureInstructions()
        }
    }

    /**
     * Captura foto con validación previa
     */
    private fun takePhotoWithValidation() {
        val validationResult = currentValidationResult
        
        if (validationResult == null || !validationResult.canCapture) {
            hapticManager.provideErrorFeedback()
            accessibilityManager.announceForAccessibility("No se puede capturar - ajusta la posición")
            Toast.makeText(this, "Ajusta la posición antes de capturar", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Iniciando captura validada")
        
        // Cancelar captura automática si está activa
        autoCaptureManager.cancelCountdown()
        
        // Proporcionar retroalimentación de captura
        hapticManager.provideCaptureSuccessFeedback()
        accessibilityManager.announceForAccessibility("Capturando imagen")
        
        takePhoto()
    }

    /**
     * Método de captura con preprocesado inteligente integrado
     */
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val timestamp = System.currentTimeMillis()
        val originalFile = File(
            externalMediaDirs.firstOrNull(),
            "original-${timestamp}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(originalFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (originalFile.exists()) {
                        Log.d(TAG, "Imagen capturada exitosamente: ${originalFile.absolutePath}")
                        
                        // Aplicar preprocesado inteligente en background
                        lifecycleScope.launch(Dispatchers.IO) {
                            processAndNavigate(originalFile, timestamp)
                        }
                    } else {
                        Log.e(TAG, "Error: Archivo de imagen no encontrado después de captura")
                        runOnUiThread {
                            Toast.makeText(this@CameraActivity, "Error: Archivo no encontrado", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Error capturando foto", exception)
                    runOnUiThread {
                        Toast.makeText(this@CameraActivity, "Error al capturar foto: ${exception.message}", Toast.LENGTH_LONG).show()
                        hapticManager.provideErrorFeedback()
                        accessibilityManager.announceForAccessibility("Error al capturar imagen")
                    }
                }
            }
        )
    }

    /**
     * Procesa la imagen capturada y navega a la vista previa
     */
    private suspend fun processAndNavigate(originalFile: File, timestamp: Long) {
        var finalImagePath = originalFile.absolutePath
        var preprocessingApplied = false
        var processingMetadata = ""
        
        try {
            Log.d(TAG, "Iniciando preprocesado de imagen")
            
            // Decodificar imagen original
            val originalBitmap = android.graphics.BitmapFactory.decodeFile(originalFile.absolutePath)
            if (originalBitmap == null) {
                Log.e(TAG, "Error decodificando imagen original")
                withContext(Dispatchers.Main) {
                    navigateToPreviewWithFallback(originalFile.absolutePath)
                }
                return
            }
            
            // Determinar configuración de preprocesado basada en condiciones detectadas
            val preprocessingConfig = determinePreprocessingConfig()
            
            // Aplicar preprocesado
            val preprocessingResult = preprocessor.preprocessImage(originalBitmap, preprocessingConfig)
            
            if (preprocessingResult.isSuccessful()) {
                // Guardar imagen procesada
                val processedFile = File(
                    externalMediaDirs.firstOrNull(),
                    "processed-${timestamp}.jpg"
                )
                
                val outputStream = processedFile.outputStream()
                preprocessingResult.processedBitmap.compress(
                    android.graphics.Bitmap.CompressFormat.JPEG, 
                    95, 
                    outputStream
                )
                outputStream.close()
                
                finalImagePath = processedFile.absolutePath
                preprocessingApplied = true
                processingMetadata = preprocessingResult.getProcessingSummary()
                
                Log.d(TAG, "Preprocesado exitoso: $processingMetadata")
                
                // Limpiar bitmap para liberar memoria
                preprocessingResult.processedBitmap.recycle()
            } else {
                Log.w(TAG, "Preprocesado falló, usando imagen original")
            }
            
            // Limpiar bitmap original
            originalBitmap.recycle()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error durante preprocesado, usando imagen original", e)
            // Fallback a imagen original en caso de cualquier error
        }
        
        // Navegar a preview en hilo principal
        withContext(Dispatchers.Main) {
            val isFrontCamera = currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
            navigateToPreview(finalImagePath, isFrontCamera, preprocessingApplied, processingMetadata)
        }
    }

    /**
     * Determina la configuración de preprocesado basada en las condiciones actuales
     */
    private fun determinePreprocessingConfig(): ImagePreprocessor.PreprocessingConfig {
        val currentValidation = currentValidationResult
        
        return when {
            // Configuración para poca luz
            currentValidation?.guideState == CaptureValidationManager.GuideState.POOR_LIGHTING -> {
                Log.d(TAG, "Aplicando configuración para poca luz")
                ImagePreprocessor.PreprocessingConfig(
                    enableIlluminationNormalization = true,
                    enableContrastEnhancement = true,
                    enableNoiseReduction = true,
                    enableSharpening = false,
                    claheClipLimit = 4.0,
                    claheTileSize = OpenCVSize(4.0, 4.0)
                )
            }
            
            // Configuración para imagen borrosa
            currentValidation?.guideState == CaptureValidationManager.GuideState.BLURRY -> {
                Log.d(TAG, "Aplicando configuración para imagen borrosa")
                ImagePreprocessor.PreprocessingConfig(
                    enableIlluminationNormalization = true,
                    enableContrastEnhancement = true,
                    enableNoiseReduction = true,
                    enableSharpening = true,
                    sharpeningStrength = 0.7
                )
            }
            
            // Configuración estándar para análisis dermatológico
            else -> {
                Log.d(TAG, "Aplicando configuración estándar para dermatología")
                ImagePreprocessor.PreprocessingConfig(
                    enableIlluminationNormalization = true,
                    enableContrastEnhancement = true,
                    enableNoiseReduction = true,
                    enableSharpening = true,
                    claheClipLimit = 3.0,
                    claheTileSize = OpenCVSize(6.0, 6.0),
                    sharpeningStrength = 0.3
                )
            }
        }
    }

    /**
     * Navega a preview con fallback en caso de error
     */
    private fun navigateToPreviewWithFallback(imagePath: String) {
        val isFrontCamera = currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
        navigateToPreview(imagePath, isFrontCamera, false, "Error en preprocesado - usando imagen original")
    }

    /**
     * Navega a la vista previa con información de preprocesado
     */
    fun navigateToPreview(
        photoPath: String, 
        isFrontCamera: Boolean, 
        preprocessingApplied: Boolean = false,
        processingMetadata: String = ""
    ) {
        val intent = Intent(this, PreviewActivity::class.java)
        intent.putExtra("PHOTO_PATH", photoPath)
        intent.putExtra("IS_FRONT_CAMERA", isFrontCamera)
        intent.putExtra("BODY_PART_COLOR", bodyPartColor)
        intent.putExtra("PREPROCESSING_APPLIED", preprocessingApplied)
        intent.putExtra("PROCESSING_METADATA", processingMetadata)
        
        Log.d(TAG, "Navegando a PreviewActivity con imagen: $photoPath")
        if (preprocessingApplied) {
            Log.d(TAG, "Preprocesado aplicado: $processingMetadata")
        }
        
        previewLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        startCamera()
        isAnalysisEnabled = true
    }

    override fun onPause() {
        super.onPause()
        isAnalysisEnabled = false
        releaseCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        isAnalysisEnabled = false
        releaseCamera()
        cameraExecutor.shutdown()
        
        // Limpiar recursos de accesibilidad y retroalimentación
        hapticManager.cancelVibration()
        accessibilityManager.cleanup()
        autoCaptureManager.cleanup()
        
        // Limpiar recursos de optimización de rendimiento
        if (::performanceManager.isInitialized) {
            performanceManager.cleanup()
        }
        if (::roiOptimizer.isInitialized) {
            roiOptimizer.clearHistory()
        }
        if (::thermalDetector.isInitialized) {
            thermalDetector.cleanup()
        }
        // Limpiar recursos de zoom digital
        if (::digitalZoomController.isInitialized) {
            digitalZoomController.cleanup()
        }
    }

    private fun releaseCamera() {
        if (::cameraProvider.isInitialized) {
            cameraProvider.unbindAll()
        }
    }

}
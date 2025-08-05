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
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.camera.guidance.AccessibilityManager
import es.monsteraltech.skincare_tfm.camera.guidance.CaptureGuidanceConfig
import es.monsteraltech.skincare_tfm.camera.guidance.CaptureGuidanceOverlay
import es.monsteraltech.skincare_tfm.camera.guidance.CaptureValidationManager
import es.monsteraltech.skincare_tfm.camera.guidance.HapticFeedbackManager
import es.monsteraltech.skincare_tfm.camera.guidance.ImagePreprocessor
import es.monsteraltech.skincare_tfm.camera.guidance.ImageQualityAnalyzer
import es.monsteraltech.skincare_tfm.camera.guidance.MoleDetectionProcessor
import es.monsteraltech.skincare_tfm.camera.guidance.PerformanceManager
import es.monsteraltech.skincare_tfm.camera.guidance.ROIOptimizer
import es.monsteraltech.skincare_tfm.camera.guidance.ThermalStateDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        private const val ANALYSIS_THROTTLE_MS = 100L
    }
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var currentCameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isFlashEnabled: Boolean = false
    private lateinit var guidanceOverlay: CaptureGuidanceOverlay
    private lateinit var moleDetector: MoleDetectionProcessor
    private lateinit var qualityAnalyzer: ImageQualityAnalyzer
    private lateinit var validationManager: CaptureValidationManager
    private lateinit var preprocessor: ImagePreprocessor
    private lateinit var performanceManager: PerformanceManager
    private lateinit var roiOptimizer: ROIOptimizer
    private lateinit var thermalDetector: ThermalStateDetector
    private lateinit var hapticManager: HapticFeedbackManager
    private lateinit var accessibilityManager: AccessibilityManager
    private var guidanceConfig = CaptureGuidanceConfig()
    private var currentValidationResult: CaptureValidationManager.ValidationResult? = null
    private var isAnalysisEnabled = true
    private val lastAnalysisTime = AtomicLong(0)
    private lateinit var previewLauncher: ActivityResultLauncher<Intent>
    private lateinit var captureButton: FloatingActionButton
    private lateinit var captureStatusIndicator: ImageView
    private var bodyPartColor: String? = null
    private var isOpenCVInitialized = false
    private lateinit var digitalZoomController: DigitalZoomController
    private lateinit var zoomControlsView: ZoomControlsView
    private lateinit var guidanceMessageText: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        bodyPartColor = intent.getStringExtra("BODY_PART_COLOR")
        initializeGuidanceComponents()
        previewLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val confirmedPath = result.data?.getStringExtra("CONFIRMED_PATH")
                Toast.makeText(this, "Foto confirmada: $confirmedPath", Toast.LENGTH_LONG).show()
            } else if (result.resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Foto descartada", Toast.LENGTH_SHORT).show()
            }
        }
        setupUI()
        cameraExecutor = Executors.newSingleThreadExecutor()
        initializeOpenCV()
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
        lifecycleScope.launchWhenStarted {
            digitalZoomController.currentZoomLevel.collect { _ ->
                zoomControlsView.updateZoomInfo(digitalZoomController.getZoomInfo())
            }
        }
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
        performanceManager = PerformanceManager(this)
        roiOptimizer = ROIOptimizer()
        thermalDetector = ThermalStateDetector(this)
        moleDetector = MoleDetectionProcessor(this, performanceManager, roiOptimizer, thermalDetector)
        qualityAnalyzer = ImageQualityAnalyzer(performanceManager, thermalDetector)
        validationManager = CaptureValidationManager()
        preprocessor = ImagePreprocessor()
        lifecycleScope.launch {
            performanceManager.currentPerformanceLevel.collect {
                updateAnalysisFrequency()
            }
        }
        lifecycleScope.launch {
            thermalDetector.currentThermalState.collect { state ->
                Log.d(TAG, "Estado térmico actualizado: $state")
                handleThermalStateChange(state)
            }
        }
        hapticManager = HapticFeedbackManager(this)
        accessibilityManager = AccessibilityManager(this)
        Log.d(TAG, "Componentes de guía inicializados")
    }
    private fun updateAnalysisFrequency() {
        val config = performanceManager.currentConfig.value
        imageAnalyzer?.let { analyzer ->
            analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageFrame(imageProxy)
            }
        }
        Log.d(TAG, "Frecuencia de análisis actualizada: ${config.processingFrequency} FPS")
    }
    private fun handleThermalStateChange(state: ThermalStateDetector.ThermalState) {
        when (state) {
            ThermalStateDetector.ThermalState.SEVERE,
            ThermalStateDetector.ThermalState.CRITICAL,
            ThermalStateDetector.ThermalState.EMERGENCY -> {
                runOnUiThread {
                }
                if (state == ThermalStateDetector.ThermalState.EMERGENCY) {
                    isAnalysisEnabled = false
                    Handler(Looper.getMainLooper()).postDelayed({
                        isAnalysisEnabled = true
                    }, 10000)
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
        guidanceOverlay = CaptureGuidanceOverlay(this)
        guidanceOverlay.updateConfig(guidanceConfig)
        val overlayContainer = findViewById<FrameLayout>(R.id.guidance_overlay_container)
        overlayContainer.addView(guidanceOverlay)
        captureButton.isEnabled = false
        captureButton.setOnClickListener { takePhotoWithValidation() }
        setupInitialAccessibility()
    }
    private fun initializeOpenCV() {
        try {
            if (OpenCVLoader.initDebug()) {
                Log.d(TAG, "OpenCV library found inside package. Using it!")
                isOpenCVInitialized = true
                setupImageAnalysis()
            } else {
                Log.d(TAG, "Internal OpenCV library not found. Trying static initialization...")
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
            val preview = Preview.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
            imageCapture = ImageCapture.Builder()
                .setFlashMode(if (isFlashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(previewView.display.rotation)
                .build()
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
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    currentCameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                ).also { camera ->
                    digitalZoomController.initialize(camera)
                    setupStabilization(camera)
                }
                Log.d(TAG, "Cámara iniciada con análisis en tiempo real")
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando cámara", e)
                Toast.makeText(this, "Error iniciando cámara: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun setupStabilization(camera: Camera) {
        lifecycleScope.launch {
            digitalZoomController.currentZoomLevel.collect { zoomLevel ->
                if (zoomLevel >= 2.0f) {
                    camera.cameraControl.setExposureCompensationIndex(-1)
                } else {
                    camera.cameraControl.setExposureCompensationIndex(0)
                }
            }
        }
    }
    private fun startRealtimeAnalysis() {
        if (!isOpenCVInitialized) {
            Log.w(TAG, "OpenCV no inicializado, no se puede iniciar análisis")
            return
        }
        isAnalysisEnabled = true
        Log.d(TAG, "Análisis en tiempo real iniciado")
    }
    private fun processImageFrame(imageProxy: ImageProxy) {
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
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val mat = imageProxyToMat(imageProxy)
                if (mat != null && !mat.empty()) {
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
    private suspend fun processAnalysisPipeline(frame: Mat) {
        try {
            val moleDetection = moleDetector.detectMole(frame)
            val qualityMetrics = qualityAnalyzer.analyzeQuality(frame)
            val guideArea = getGuideArea()
            val validationResult = validationManager.validateCapture(
                moleDetection,
                qualityMetrics,
                guideArea
            )
            withContext(Dispatchers.Main) {
                updateUI(moleDetection, validationResult)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en pipeline de análisis", e)
        }
    }
    private fun imageProxyToMat(imageProxy: ImageProxy): Mat? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val mat = Mat(imageProxy.height, imageProxy.width, CvType.CV_8UC1)
            mat.put(0, 0, bytes)
            mat
        } catch (e: Exception) {
            Log.e(TAG, "Error convirtiendo ImageProxy a Mat", e)
            null
        }
    }
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
    private fun updateUI(
        moleDetection: MoleDetectionProcessor.MoleDetection?,
        validationResult: CaptureValidationManager.ValidationResult
    ) {
        currentValidationResult = validationResult
        val molePosition = moleDetection?.let {
            PointF(it.centerPoint.x.toFloat(), it.centerPoint.y.toFloat())
        }
        guidanceOverlay.updateMolePosition(molePosition, moleDetection?.confidence ?: 0f)
        guidanceOverlay.updateGuideState(validationResult.guideState)
        val detailedMessage = validationManager.getDetailedGuidanceMessage(validationResult)
        guidanceOverlay.setGuidanceMessage(detailedMessage)
        accessibilityManager.setupGuidanceOverlayAccessibility(
            guidanceOverlay,
            validationResult.guideState,
            moleDetection != null
        )
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
        captureButton.isEnabled = validationResult.canCapture
        accessibilityManager.setupCaptureButtonAccessibility(
            captureButton,
            validationResult.canCapture,
            validationResult.guideState
        )
        val buttonColor = if (validationResult.canCapture) {
            ContextCompat.getColor(this, R.color.risk_very_low)
        } else {
            ContextCompat.getColor(this, R.color.risk_high)
        }
        captureButton.backgroundTintList = android.content.res.ColorStateList.valueOf(buttonColor)
        val messageColor = when (validationResult.guideState) {
            CaptureValidationManager.GuideState.READY -> ContextCompat.getColor(this, R.color.risk_very_low)
            CaptureValidationManager.GuideState.SEARCHING, CaptureValidationManager.GuideState.CENTERING -> ContextCompat.getColor(this, R.color.risk_moderate)
            else -> ContextCompat.getColor(this, R.color.risk_high)
        }
        guidanceMessageText.setTextColor(messageColor)
        Log.d("DEBUG", "Actualizando guidanceMessageText: $detailedMessage")
        guidanceMessageText.text = detailedMessage
        Log.d("DEBUG", "Texto actual guidanceMessageText: ${guidanceMessageText.text}")
        if (validationResult.guideState == CaptureValidationManager.GuideState.CENTERING && moleDetection != null) {
            val guideCenter = PointF(getGuideArea().centerX(), getGuideArea().centerY())
            val moleCenter = PointF(moleDetection.centerPoint.x.toFloat(), moleDetection.centerPoint.y.toFloat())
            val centeringPercentage = validationManager.calculateCenteringPercentage(moleCenter, guideCenter)
            accessibilityManager.announceCenteringProgress(centeringPercentage)
        }
    }
    private fun setupInitialAccessibility() {
        accessibilityManager.setupViewAccessibility(
            previewView,
            "Vista previa de la cámara para captura de lunares",
            "Mueve la cámara para encontrar y centrar un lunar"
        )
        accessibilityManager.setupViewAccessibility(
            captureButton,
            "Botón de captura",
            "Toca dos veces para capturar cuando esté habilitado"
        )
        if (accessibilityManager.isTalkBackEnabled()) {
            accessibilityManager.announceGestureInstructions()
        }
    }
    private fun takePhotoWithValidation() {
        val validationResult = currentValidationResult
        if (validationResult == null || !validationResult.canCapture) {
            hapticManager.provideErrorFeedback()
            accessibilityManager.announceForAccessibility("No se puede capturar - ajusta la posición")
            Toast.makeText(this, "Ajusta la posición antes de capturar", Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "Iniciando captura validada")
        hapticManager.provideCaptureSuccessFeedback()
        accessibilityManager.announceForAccessibility("Capturando imagen")
        takePhoto()
    }
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
    private suspend fun processAndNavigate(originalFile: File, timestamp: Long) {
        var finalImagePath = originalFile.absolutePath
        var preprocessingApplied = false
        var processingMetadata = ""
        try {
            Log.d(TAG, "Iniciando preprocesado de imagen")
            val originalBitmap = android.graphics.BitmapFactory.decodeFile(originalFile.absolutePath)
            if (originalBitmap == null) {
                Log.e(TAG, "Error decodificando imagen original")
                withContext(Dispatchers.Main) {
                    navigateToPreviewWithFallback(originalFile.absolutePath)
                }
                return
            }
            val preprocessingConfig = determinePreprocessingConfig()
            val preprocessingResult = preprocessor.preprocessImage(originalBitmap, preprocessingConfig)
            if (preprocessingResult.isSuccessful()) {
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
                preprocessingResult.processedBitmap.recycle()
            } else {
                Log.w(TAG, "Preprocesado falló, usando imagen original")
            }
            originalBitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error durante preprocesado, usando imagen original", e)
        }
        withContext(Dispatchers.Main) {
            val isFrontCamera = currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
            navigateToPreview(finalImagePath, isFrontCamera, preprocessingApplied, processingMetadata)
        }
    }
    private fun determinePreprocessingConfig(): ImagePreprocessor.PreprocessingConfig {
        val currentValidation = currentValidationResult
        return when {
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
    private fun navigateToPreviewWithFallback(imagePath: String) {
        val isFrontCamera = currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
        navigateToPreview(imagePath, isFrontCamera, false, "Error en preprocesado - usando imagen original")
    }
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
        hapticManager.cancelVibration()
        accessibilityManager.cleanup()
        if (::performanceManager.isInitialized) {
            performanceManager.cleanup()
        }
        if (::roiOptimizer.isInitialized) {
            roiOptimizer.clearHistory()
        }
        if (::thermalDetector.isInitialized) {
            thermalDetector.cleanup()
        }
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
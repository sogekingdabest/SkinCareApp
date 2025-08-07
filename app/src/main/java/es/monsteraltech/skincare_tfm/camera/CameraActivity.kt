package es.monsteraltech.skincare_tfm.camera
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
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
import es.monsteraltech.skincare_tfm.camera.guidance.CaptureGuidanceConfig
import es.monsteraltech.skincare_tfm.camera.guidance.CaptureGuidanceOverlay
import es.monsteraltech.skincare_tfm.camera.guidance.GuideState
import es.monsteraltech.skincare_tfm.camera.guidance.SimplifiedGuidanceManager
import es.monsteraltech.skincare_tfm.utils.UIUtils
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
    private lateinit var simplifiedGuidanceManager: SimplifiedGuidanceManager
    private var guidanceConfig = CaptureGuidanceConfig()
    private var currentGuidanceResult: SimplifiedGuidanceManager.GuidanceResult? = null
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
        simplifiedGuidanceManager = SimplifiedGuidanceManager(this, guidanceConfig)
        
        previewLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                UIUtils.showSuccessToast(this, getString(R.string.ui_operation_completed))
            } else if (result.resultCode == RESULT_CANCELED) {
                UIUtils.showInfoToast(this, getString(R.string.ui_changes_discarded))
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
                UIUtils.showErrorToast(this, getString(R.string.error_permission_denied))
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
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
                    UIUtils.showErrorToast(this, getString(R.string.ui_error))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during OpenCV initialization", e)
            UIUtils.showErrorToast(this, getString(R.string.ui_error))
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
                UIUtils.showErrorToast(this, getString(R.string.ui_error))
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

            val guidanceResult = simplifiedGuidanceManager.processFrame(frame)
            
            withContext(Dispatchers.Main) {
                updateUI(guidanceResult)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en pipeline de análisis simplificado", e)
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

    private fun updateUI(guidanceResult: SimplifiedGuidanceManager.GuidanceResult) {
        currentGuidanceResult = guidanceResult

        val molePosition = guidanceResult.molePosition?.let {
            PointF(it.x.toFloat(), it.y.toFloat())
        }

        val confidence = when (val state = guidanceResult.state) {
            is es.monsteraltech.skincare_tfm.camera.guidance.CaptureState.MoleDetected -> state.confidence
            else -> 0f
        }
        
        guidanceOverlay.updateMolePosition(molePosition, confidence)
        guidanceOverlay.updateGuideState(guidanceResult.guideState)
        guidanceOverlay.setGuidanceMessage(guidanceResult.message)

        val statusIcon = when (guidanceResult.guideState) {
            GuideState.SEARCHING -> R.drawable.ic_search
            GuideState.CENTERING -> R.drawable.ic_center_focus_weak
            GuideState.POOR_LIGHTING -> R.drawable.ic_wb_sunny
            GuideState.READY -> R.drawable.ic_check_circle
        }
        captureStatusIndicator.setImageResource(statusIcon)

        val canCapture = guidanceResult.state is es.monsteraltech.skincare_tfm.camera.guidance.CaptureState.ReadyToCapture
        captureButton.isEnabled = canCapture

        val buttonColor = if (canCapture) {
            ContextCompat.getColor(this, R.color.risk_very_low)
        } else {
            ContextCompat.getColor(this, R.color.risk_high)
        }
        captureButton.backgroundTintList = android.content.res.ColorStateList.valueOf(buttonColor)

        val messageColor = when (guidanceResult.guideState) {
            GuideState.READY -> ContextCompat.getColor(this, R.color.risk_very_low)
            GuideState.SEARCHING, GuideState.CENTERING -> ContextCompat.getColor(this, R.color.risk_moderate)
            GuideState.POOR_LIGHTING -> ContextCompat.getColor(this, R.color.risk_high)
        }
        guidanceMessageText.setTextColor(messageColor)
        guidanceMessageText.text = guidanceResult.message

        updateQualityIndicators(guidanceResult)
        
        Log.d(TAG, "UI actualizada - Estado: ${guidanceResult.guideState}, Mensaje: ${guidanceResult.message}")
    }
    
    private fun updateQualityIndicators(guidanceResult: SimplifiedGuidanceManager.GuidanceResult) {
        val lightingIndicator = findViewById<ImageView>(R.id.lighting_indicator)
        val lightingText = findViewById<TextView>(R.id.lighting_indicator_text)
        
        val isLightingGood = guidanceResult.guideState != GuideState.POOR_LIGHTING
        val lightingColor = if (isLightingGood) {
            ContextCompat.getColor(this, R.color.risk_very_low)
        } else {
            ContextCompat.getColor(this, R.color.risk_high)
        }
        
        lightingIndicator.setColorFilter(lightingColor)
        lightingText.text = if (isLightingGood) "Luz ✓" else "Luz ✗"
        lightingText.setTextColor(lightingColor)

        val moleIndicator = findViewById<ImageView>(R.id.mole_detection_indicator)
        val moleText = findViewById<TextView>(R.id.mole_detection_indicator_text)
        
        val isMoleDetected = guidanceResult.state !is es.monsteraltech.skincare_tfm.camera.guidance.CaptureState.Searching
        val moleColor = when {
            guidanceResult.state is es.monsteraltech.skincare_tfm.camera.guidance.CaptureState.ReadyToCapture -> 
                ContextCompat.getColor(this, R.color.risk_very_low)
            isMoleDetected -> 
                ContextCompat.getColor(this, R.color.risk_moderate)
            else -> 
                ContextCompat.getColor(this, R.color.risk_high)
        }
        
        moleIndicator.setColorFilter(moleColor)
        moleText.text = when {
            guidanceResult.state is es.monsteraltech.skincare_tfm.camera.guidance.CaptureState.ReadyToCapture -> "Lunar ✓"
            isMoleDetected -> "Lunar ~"
            else -> "Lunar ✗"
        }
        moleText.setTextColor(moleColor)
    }
    private fun setupInitialAccessibility() {
        previewView.contentDescription = "Vista previa de la cámara para captura de lunares"
        captureButton.contentDescription = "Botón de captura"
    }
    private fun takePhotoWithValidation() {
        val guidanceResult = currentGuidanceResult
        val canCapture = guidanceResult?.state is es.monsteraltech.skincare_tfm.camera.guidance.CaptureState.ReadyToCapture
        
        if (guidanceResult == null || !canCapture) {
            UIUtils.showInfoToast(this, guidanceResult?.message ?: getString(R.string.guidance_centering))
            return
        }
        
        Log.d(TAG, "Iniciando captura validada")
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
                            processAndNavigate(originalFile)
                        }
                    } else {
                        Log.e(TAG, "Error: Archivo de imagen no encontrado después de captura")
                        runOnUiThread {
                            UIUtils.showErrorToast(this@CameraActivity, getString(R.string.error_data_not_found))
                        }
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Error capturando foto", exception)
                    runOnUiThread {
                        UIUtils.showErrorToast(this@CameraActivity, getString(R.string.ui_error))
                    }
                }
            }
        )
    }
    private suspend fun processAndNavigate(originalFile: File) {
        withContext(Dispatchers.Main) {
            val isFrontCamera = currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
            navigateToPreview(originalFile.absolutePath, isFrontCamera, false, "")
        }
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
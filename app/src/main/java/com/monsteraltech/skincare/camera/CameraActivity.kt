package com.monsteraltech.skincare.camera
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.monsteraltech.skincare.BuildConfig
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.camera.guidance.CaptureGuidanceConfig
import com.monsteraltech.skincare.camera.guidance.CaptureGuidanceOverlay
import com.monsteraltech.skincare.camera.guidance.CaptureState
import com.monsteraltech.skincare.camera.guidance.ExperimentalCapturePreferences
import com.monsteraltech.skincare.camera.guidance.GuideState
import com.monsteraltech.skincare.camera.guidance.SimplifiedGuidanceManager
import com.monsteraltech.skincare.utils.UIUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class CameraActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "CameraActivity"
        private const val ANALYSIS_THROTTLE_MS = 100L
        private const val HIGH_ZOOM_EXPOSURE_THRESHOLD = 2.0f
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null
    private var currentCameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isFlashEnabled: Boolean = false

    private lateinit var guidanceOverlay: CaptureGuidanceOverlay
    private lateinit var simplifiedGuidanceManager: SimplifiedGuidanceManager
    private val guidanceConfig = CaptureGuidanceConfig()
    private var currentGuidanceResult: SimplifiedGuidanceManager.GuidanceResult? = null
    private var captureValidityModelEnabled = false

    @Volatile private var isAnalysisEnabled = false
    private var isOpenCVInitialized = false
    private var isCameraBound = false
    private var boundCamera: Camera? = null
    private val lastAnalysisTime = AtomicLong(0)
    private val isProcessingFrame = AtomicBoolean(false)

    private lateinit var previewLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var captureButton: FloatingActionButton
    private lateinit var captureStatusIndicator: ImageView
    private lateinit var lightingIndicator: ImageView
    private lateinit var lightingIndicatorText: TextView
    private lateinit var moleIndicator: ImageView
    private lateinit var moleIndicatorText: TextView
    private lateinit var guidanceMessageText: TextView
    private lateinit var digitalZoomController: DigitalZoomController
    private lateinit var zoomControlsView: ZoomControlsView
    private var bodyPartColor: String? = null
    private var targetMoleId: String? = null
    private var targetMoleTitle: String? = null
    private var targetMoleDescription: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        bodyPartColor =
            intent.getStringExtra(
                MoleCaptureContract.EXTRA_BODY_PART_COLOR
            )
        targetMoleId =
            intent.getStringExtra(MoleCaptureContract.EXTRA_MOLE_ID)
                ?.takeIf(String::isNotBlank)
        targetMoleTitle =
            intent.getStringExtra(MoleCaptureContract.EXTRA_MOLE_TITLE)
        targetMoleDescription =
            intent.getStringExtra(
                MoleCaptureContract.EXTRA_MOLE_DESCRIPTION
            )
        val experimentalCapturePreferences = ExperimentalCapturePreferences(this)
        captureValidityModelEnabled =
            ExperimentalCapturePreferences.isCaptureValidityEffective(
                modelAvailable = BuildConfig.CAPTURE_VALIDITY_MODEL_ENABLED,
                userEnabled = experimentalCapturePreferences.captureValidityEnabled
            )
        simplifiedGuidanceManager = SimplifiedGuidanceManager(
            context = this,
            config = guidanceConfig,
            semanticGateEnabled = captureValidityModelEnabled
        )

        previewLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                UIUtils.showSuccessToast(this, getString(R.string.ui_operation_completed))
            } else if (result.resultCode == RESULT_CANCELED) {
                UIUtils.showInfoToast(this, getString(R.string.ui_changes_discarded))
            }
        }
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                UIUtils.showErrorToast(this, getString(R.string.error_permission_denied))
            }
        }

        setupUI()
        cameraExecutor = Executors.newSingleThreadExecutor()
        isOpenCVInitialized = initializeOpenCV()

        digitalZoomController = DigitalZoomController(this)
        zoomControlsView.setZoomControlListener(object : ZoomControlsView.ZoomControlListener {
            override fun onZoomIn() { digitalZoomController.zoomIn() }
            override fun onZoomOut() { digitalZoomController.zoomOut() }
            override fun onZoomReset() { digitalZoomController.resetZoom() }
            override fun onZoomOptimal() { digitalZoomController.setOptimalZoomForSmallMoles() }
            override fun onZoomLevelChanged(level: Float) { digitalZoomController.setZoomLevel(level) }
        })
        observeZoomState()

        if (!hasCameraPermission()) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupUI() {
        previewView = findViewById(R.id.preview_view)
        captureButton = findViewById(R.id.capture_button)
        captureStatusIndicator = findViewById(R.id.capture_status_indicator)
        guidanceMessageText = findViewById(R.id.guidance_message)
        zoomControlsView = findViewById(R.id.zoom_controls_view)
        lightingIndicator = findViewById(R.id.lighting_indicator)
        lightingIndicatorText = findViewById(R.id.lighting_indicator_text)
        moleIndicator = findViewById(R.id.mole_detection_indicator)
        moleIndicatorText = findViewById(R.id.mole_detection_indicator_text)

        guidanceOverlay = CaptureGuidanceOverlay(this)
        guidanceOverlay.updateConfig(guidanceConfig)
        findViewById<FrameLayout>(R.id.guidance_overlay_container).addView(guidanceOverlay)

        captureButton.isEnabled = false
        captureButton.setOnClickListener { takePhotoWithValidation() }
        previewView.contentDescription = getString(R.string.guidance_status_description)
        captureButton.contentDescription = getString(R.string.capture_button_description)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun initializeOpenCV(): Boolean {
        return try {
            val initialized = OpenCVLoader.initLocal()
            if (!initialized) {
                Log.e(TAG, "OpenCV initialization failed")
                UIUtils.showErrorToast(this, getString(R.string.ui_error))
            }
            initialized
        } catch (e: Exception) {
            Log.e(TAG, "Error during OpenCV initialization", e)
            UIUtils.showErrorToast(this, getString(R.string.ui_error))
            false
        }
    }

    /**
     * Único colector de zoom para toda la vida de la Activity: actualiza los
     * controles y aplica compensación de exposición con zoom alto. Se reinicia
     * automáticamente con el ciclo de vida (sin acumular colectores por rebind).
     */
    private fun observeZoomState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                digitalZoomController.currentZoomLevel.collect { zoomLevel ->
                    zoomControlsView.updateZoomInfo(digitalZoomController.getZoomInfo())
                    boundCamera?.cameraControl?.setExposureCompensationIndex(
                        if (zoomLevel >= HIGH_ZOOM_EXPOSURE_THRESHOLD) -1 else 0
                    )
                }
            }
        }
    }

    private fun startCamera() {
        if (isCameraBound) return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(if (isFlashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(previewView.display.rotation)
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetRotation(previewView.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analyzer ->
                    analyzer.setAnalyzer(cameraExecutor) { imageProxy -> processImageFrame(imageProxy) }
                }

            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    this,
                    currentCameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )
                boundCamera = camera
                digitalZoomController.initialize(camera)
                isCameraBound = true
                simplifiedGuidanceManager.reset()
                isAnalysisEnabled = isOpenCVInitialized
                Log.d(TAG, "Cámara iniciada con análisis en tiempo real")
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando cámara", e)
                UIUtils.showErrorToast(this, getString(R.string.ui_error))
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageFrame(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (!isAnalysisEnabled || !isOpenCVInitialized ||
            now - lastAnalysisTime.get() < ANALYSIS_THROTTLE_MS ||
            !isProcessingFrame.compareAndSet(false, true)
        ) {
            imageProxy.close()
            return
        }
        lastAnalysisTime.set(now)
        lifecycleScope.launch(Dispatchers.Default) {
            var frame: Mat? = null
            var colorFrame: Bitmap? = null
            try {
                if (captureValidityModelEnabled) {
                    colorFrame = imageProxyToOrientedBitmap(imageProxy)
                }
                frame = imageProxyToGrayMat(imageProxy)
                if (frame != null && !frame.empty()) {
                    val frameWidth = frame.cols()
                    val frameHeight = frame.rows()
                    val result = simplifiedGuidanceManager.processFrame(
                        frame,
                        colorFrame
                    )
                    withContext(Dispatchers.Main) {
                        updateUI(result, frameWidth, frameHeight)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando frame", e)
            } finally {
                colorFrame?.recycle()
                frame?.release()
                imageProxy.close()
                isProcessingFrame.set(false)
            }
        }
    }

    private fun imageProxyToOrientedBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val source = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
            if (rotation == 0f) {
                source
            } else {
                val oriented = Bitmap.createBitmap(
                    source,
                    0,
                    0,
                    source.width,
                    source.height,
                    Matrix().apply { postRotate(rotation) },
                    true
                )
                source.recycle()
                oriented
            }
        } catch (error: Exception) {
            Log.e(TAG, "Error convirtiendo ImageProxy a RGB", error)
            null
        }
    }

    /**
     * Extrae el plano de luminancia (Y) del frame YUV_420_888 respetando el
     * rowStride del sensor (si se ignora, la imagen queda distorsionada en
     * muchos dispositivos) y lo rota a la orientación de pantalla.
     */
    private fun imageProxyToGrayMat(imageProxy: ImageProxy): Mat? {
        return try {
            val plane = imageProxy.planes[0]
            val buffer = plane.buffer
            buffer.rewind()
            val width = imageProxy.width
            val height = imageProxy.height
            val rowStride = plane.rowStride
            val mat = Mat(height, width, CvType.CV_8UC1)

            if (rowStride == width && buffer.remaining() >= width * height) {
                val data = ByteArray(width * height)
                buffer.get(data, 0, data.size)
                mat.put(0, 0, data)
            } else {
                val row = ByteArray(width)
                for (y in 0 until height) {
                    buffer.position(y * rowStride)
                    buffer.get(row, 0, width)
                    mat.put(y, 0, row)
                }
            }
            rotateToDisplayOrientation(mat, imageProxy.imageInfo.rotationDegrees)
        } catch (e: Exception) {
            Log.e(TAG, "Error convirtiendo ImageProxy a Mat", e)
            null
        }
    }

    private fun rotateToDisplayOrientation(mat: Mat, rotationDegrees: Int): Mat {
        val rotateCode = when (rotationDegrees) {
            90 -> Core.ROTATE_90_CLOCKWISE
            180 -> Core.ROTATE_180
            270 -> Core.ROTATE_90_COUNTERCLOCKWISE
            else -> return mat
        }
        val rotated = Mat()
        Core.rotate(mat, rotated, rotateCode)
        mat.release()
        return rotated
    }

    private fun updateUI(
        guidanceResult: SimplifiedGuidanceManager.GuidanceResult,
        frameWidth: Int,
        frameHeight: Int
    ) {
        currentGuidanceResult = guidanceResult

        guidanceOverlay.setAnalysisFrameSize(frameWidth, frameHeight)
        val molePosition = guidanceResult.molePosition?.let { PointF(it.x.toFloat(), it.y.toFloat()) }
        guidanceOverlay.updateMolePosition(molePosition, guidanceResult.moleConfidence)
        guidanceOverlay.updateGuideState(guidanceResult.guideState)

        val statusIcon = when (guidanceResult.guideState) {
            GuideState.SEARCHING -> R.drawable.ic_search
            GuideState.CENTERING -> R.drawable.ic_center_focus_weak
            GuideState.POOR_LIGHTING -> R.drawable.ic_wb_sunny
            GuideState.BLURRY -> R.drawable.ic_blur_on
            GuideState.READY -> R.drawable.ic_check_circle
        }
        captureStatusIndicator.setImageResource(statusIcon)

        val canCapture = guidanceResult.state is CaptureState.ReadyToCapture
        captureButton.isEnabled = canCapture
        captureButton.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (canCapture) R.color.risk_very_low else R.color.risk_high)
        )

        val messageColor = when (guidanceResult.guideState) {
            GuideState.READY -> ContextCompat.getColor(this, R.color.risk_very_low)
            GuideState.POOR_LIGHTING -> ContextCompat.getColor(this, R.color.risk_high)
            else -> ContextCompat.getColor(this, R.color.risk_moderate)
        }
        guidanceMessageText.setTextColor(messageColor)
        guidanceMessageText.text = guidanceResult.message

        updateQualityIndicators(guidanceResult)
    }

    private fun updateQualityIndicators(guidanceResult: SimplifiedGuidanceManager.GuidanceResult) {
        val isLightingGood = guidanceResult.guideState != GuideState.POOR_LIGHTING
        val lightingColor = ContextCompat.getColor(
            this, if (isLightingGood) R.color.risk_very_low else R.color.risk_high
        )
        lightingIndicator.setColorFilter(lightingColor)
        lightingIndicatorText.text =
            getString(if (isLightingGood) R.string.quality_light_ok else R.string.quality_light_bad)
        lightingIndicatorText.setTextColor(lightingColor)

        val isReady = guidanceResult.state is CaptureState.ReadyToCapture
        val isMoleDetected = isReady || guidanceResult.state is CaptureState.MoleDetected
        val moleTextRes = when {
            isReady -> R.string.quality_mole_ok
            isMoleDetected -> R.string.quality_mole_partial
            else -> R.string.quality_mole_bad
        }
        val moleColor = ContextCompat.getColor(
            this,
            when {
                isReady -> R.color.risk_very_low
                isMoleDetected -> R.color.risk_moderate
                else -> R.color.risk_high
            }
        )
        moleIndicator.setColorFilter(moleColor)
        moleIndicatorText.text = getString(moleTextRes)
        moleIndicatorText.setTextColor(moleColor)
    }

    private fun takePhotoWithValidation() {
        val guidanceResult = currentGuidanceResult
        if (guidanceResult?.state !is CaptureState.ReadyToCapture) {
            UIUtils.showInfoToast(this, guidanceResult?.message ?: getString(R.string.guidance_centering))
            return
        }
        takePhoto()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val captureDirectory = File(cacheDir, "private_captures").apply { mkdirs() }
        val originalFile = File(
            captureDirectory,
            "original-${System.currentTimeMillis()}.jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(originalFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (originalFile.exists()) {
                        val isFrontCamera = currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
                        navigateToPreview(originalFile.absolutePath, isFrontCamera)
                    } else {
                        Log.e(TAG, "Error: Archivo de imagen no encontrado después de captura")
                        UIUtils.showErrorToast(this@CameraActivity, getString(R.string.error_data_not_found))
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Error capturando foto", exception)
                    UIUtils.showErrorToast(this@CameraActivity, getString(R.string.ui_error))
                }
            }
        )
    }

    fun navigateToPreview(
        photoPath: String,
        isFrontCamera: Boolean,
        preprocessingApplied: Boolean = false,
        processingMetadata: String = ""
    ) {
        val intent = Intent(this, PreviewActivity::class.java)
        intent.putExtra(MoleCaptureContract.EXTRA_PHOTO_PATH, photoPath)
        intent.putExtra(
            MoleCaptureContract.EXTRA_IS_FRONT_CAMERA,
            isFrontCamera
        )
        intent.putExtra(
            MoleCaptureContract.EXTRA_BODY_PART_COLOR,
            bodyPartColor
        )
        intent.putExtra(
            MoleCaptureContract.EXTRA_PREPROCESSING_APPLIED,
            preprocessingApplied
        )
        intent.putExtra(
            MoleCaptureContract.EXTRA_PROCESSING_METADATA,
            processingMetadata
        )
        intent.putExtra(
            MoleCaptureContract.EXTRA_MOLE_ID,
            targetMoleId
        )
        intent.putExtra(
            MoleCaptureContract.EXTRA_MOLE_TITLE,
            targetMoleTitle
        )
        intent.putExtra(
            MoleCaptureContract.EXTRA_MOLE_DESCRIPTION,
            targetMoleDescription
        )
        previewLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) {
            startCamera()
        }
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
        if (::simplifiedGuidanceManager.isInitialized) {
            simplifiedGuidanceManager.close()
        }
    }

    private fun releaseCamera() {
        cameraProvider?.unbindAll()
        boundCamera = null
        isCameraBound = false
    }
}

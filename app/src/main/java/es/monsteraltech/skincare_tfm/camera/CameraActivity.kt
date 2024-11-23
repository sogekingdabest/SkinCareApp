package es.monsteraltech.skincare_tfm.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import es.monsteraltech.skincare_tfm.R
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null
    private var currentCameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isFlashEnabled: Boolean = false

    private lateinit var previewLauncher: ActivityResultLauncher<Intent>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        previewLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val confirmedPath = result.data?.getStringExtra("CONFIRMED_PATH")
                Toast.makeText(this, "Foto confirmada: $confirmedPath", Toast.LENGTH_LONG).show()
                // Aquí puedes guardar la foto confirmada en tu lógica
            } else if (result.resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Foto descartada", Toast.LENGTH_SHORT).show()
            }
        }

        previewView = findViewById(R.id.preview_view)
        val captureButton: FloatingActionButton = findViewById(R.id.capture_button)
        val switchCameraButton: FloatingActionButton = findViewById(R.id.switch_camera_button)
        val flashButton: FloatingActionButton = findViewById(R.id.flash_button)

        cameraExecutor = Executors.newSingleThreadExecutor()

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

        // Botón para capturar imagen
        captureButton.setOnClickListener { takePhoto() }

        // Botón para cambiar de cámara
        switchCameraButton.setOnClickListener {
            currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }

        // Botón para alternar el flash
        flashButton.setOnClickListener {
            isFlashEnabled = !isFlashEnabled
            Toast.makeText(this, if (isFlashEnabled) "Flash Activado" else "Flash Desactivado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(if (isFlashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .setTargetRotation(previewView.display.rotation)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, currentCameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Error iniciando cámara: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            externalMediaDirs.firstOrNull(),
            "photo-${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (photoFile.exists()) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            navigateToPreview(photoFile.absolutePath)
                        }, 200)
                    } else {
                        Toast.makeText(this@CameraActivity, "Error: Archivo no encontrado", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@CameraActivity, "Error al capturar foto: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    fun navigateToPreview(photoPath: String) {
        val intent = Intent(this, PreviewActivity::class.java)
        intent.putExtra("PHOTO_PATH", photoPath)
        previewLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        startCamera()
    }

    override fun onPause() {
        super.onPause()
        releaseCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseCamera()
    }

    private fun releaseCamera() {
        cameraProvider.unbindAll()
    }

}
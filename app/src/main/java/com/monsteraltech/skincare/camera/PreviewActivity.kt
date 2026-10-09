package com.monsteraltech.skincare.camera
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.analysis.AnalysisResultActivity
import com.monsteraltech.skincare.databinding.ActivityPreviewBinding
import com.monsteraltech.skincare.utils.UIUtils
import java.io.File
class PreviewActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "PreviewActivity"
    }
    private lateinit var binding: ActivityPreviewBinding
    private var photoFile: File? = null
    private var isFrontCamera: Boolean = false
    private var bodyPartColorCode: String? = null
    private var preprocessingApplied: Boolean = false
    private var processingMetadata: String = ""
    private var targetMoleId: String? = null
    private var targetMoleTitle: String? = null
    private var targetMoleDescription: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val photoPath =
            intent.getStringExtra(MoleCaptureContract.EXTRA_PHOTO_PATH)
        isFrontCamera =
            intent.getBooleanExtra(
                MoleCaptureContract.EXTRA_IS_FRONT_CAMERA,
                false
            )
        bodyPartColorCode =
            intent.getStringExtra(
                MoleCaptureContract.EXTRA_BODY_PART_COLOR
            )
        preprocessingApplied =
            intent.getBooleanExtra(
                MoleCaptureContract.EXTRA_PREPROCESSING_APPLIED,
                false
            )
        processingMetadata =
            intent.getStringExtra(
                MoleCaptureContract.EXTRA_PROCESSING_METADATA
            ) ?: ""
        targetMoleId =
            intent.getStringExtra(MoleCaptureContract.EXTRA_MOLE_ID)
                ?.takeIf(String::isNotBlank)
        targetMoleTitle =
            intent.getStringExtra(MoleCaptureContract.EXTRA_MOLE_TITLE)
        targetMoleDescription =
            intent.getStringExtra(
                MoleCaptureContract.EXTRA_MOLE_DESCRIPTION
            )
        Log.d(TAG, "PreviewActivity iniciada con imagen: $photoPath")
        Log.d(TAG, "Preprocesado aplicado: $preprocessingApplied")
        if (preprocessingApplied) {
            Log.d(TAG, "Metadata de procesamiento: $processingMetadata")
        }
        if (photoPath != null) {
            photoFile = File(photoPath)
            if (photoFile!!.exists()) {
                displayImage(photoPath)
                setupProcessingInfo()
            } else {
                handleImageError("Imagen no encontrada")
            }
        } else {
            handleImageError("Ruta de imagen no válida")
        }
        setupButtons()
    }
    private fun displayImage(photoPath: String) {
        try {
            var bitmap = BitmapFactory.decodeFile(photoPath)
            if (bitmap == null) {
                handleImageError("Error decodificando imagen")
                return
            }
            if (isFrontCamera) {
                val matrix = Matrix()
                matrix.preScale(-1.0f, 1.0f)
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
            binding.previewImage.setImageBitmap(bitmap)
            Log.d(TAG, "Imagen mostrada exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando imagen", e)
            handleImageError("Error procesando imagen: ${e.message}")
        }
    }
    private fun setupProcessingInfo() {
        if (preprocessingApplied && processingMetadata.isNotEmpty()) {
            val processingInfo = if (processingMetadata.contains("Error")) {
                "⚠️ $processingMetadata"
            } else {
                "✅ Imagen mejorada automáticamente\n$processingMetadata"
            }
            UIUtils.showSuccessToast(this, getString(R.string.ui_operation_completed))
            Log.d(TAG, "Info de procesamiento: $processingInfo")
        }
    }
    private fun setupButtons() {
        binding.confirmButton.setOnClickListener {
            confirmAndNavigateToAnalysis()
        }
        binding.discardButton.setOnClickListener {
            discardPhoto()
        }
    }
    private fun confirmAndNavigateToAnalysis() {
        val photoPath = photoFile?.absolutePath
        if (photoPath == null || !File(photoPath).exists()) {
            UIUtils.showErrorToast(this, getString(R.string.error_data_not_found))
            return
        }
        Log.d(TAG, "Confirmando imagen y navegando a análisis: $photoPath")
        val intent = Intent(this, AnalysisResultActivity::class.java).apply {
            putExtra(MoleCaptureContract.EXTRA_PHOTO_PATH, photoPath)
            putExtra(
                MoleCaptureContract.EXTRA_IS_FRONT_CAMERA,
                isFrontCamera
            )
            putExtra(
                MoleCaptureContract.EXTRA_BODY_PART_COLOR,
                bodyPartColorCode
            )
            putExtra(
                MoleCaptureContract.EXTRA_PREPROCESSING_APPLIED,
                preprocessingApplied
            )
            putExtra(
                MoleCaptureContract.EXTRA_PROCESSING_METADATA,
                processingMetadata
            )
            putExtra(MoleCaptureContract.EXTRA_MOLE_ID, targetMoleId)
            putExtra(
                MoleCaptureContract.EXTRA_MOLE_TITLE,
                targetMoleTitle
            )
            putExtra(
                MoleCaptureContract.EXTRA_MOLE_DESCRIPTION,
                targetMoleDescription
            )
        }
        startActivity(intent)
        finish()
    }
    private fun discardPhoto() {
        try {
            photoFile?.let { file ->
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d(TAG, "Archivo eliminado: $deleted - ${file.absolutePath}")
                }
            }
            if (preprocessingApplied) {
                val originalPath = photoFile?.absolutePath?.replace("processed-", "original-")
                originalPath?.let { path ->
                    val originalFile = File(path)
                    if (originalFile.exists()) {
                        val deleted = originalFile.delete()
                        Log.d(TAG, "Archivo original eliminado: $deleted - $path")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando archivos", e)
        }
        setResult(RESULT_CANCELED)
        finish()
    }
    private fun handleImageError(message: String) {
        Log.e(TAG, "Error de imagen: $message")
        binding.previewImage.setImageResource(R.drawable.cat)
        UIUtils.showErrorToast(this, getString(R.string.ui_error))
        binding.confirmButton.isEnabled = false
    }
    override fun onDestroy() {
        super.onDestroy()
        binding.previewImage.setImageDrawable(null)
    }
}

package com.monsteraltech.skincare.camera
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.analysis.AnalysisResultActivity
import com.monsteraltech.skincare.databinding.ActivityPreviewBinding
import com.monsteraltech.skincare.utils.UIUtils
import java.io.File
@AndroidEntryPoint
class PreviewActivity : AppCompatActivity() {
    @Inject lateinit var drafts: CaptureDraftStore
    private var args: CaptureArgs? = null
    @Inject lateinit var photoDecoder: CapturedPhotoDecoder
    private var displayedBitmap: Bitmap? = null
    private var decodeJob: Job? = null
    companion object {
        private const val TAG = "PreviewActivity"
    }
    private lateinit var binding: ActivityPreviewBinding
    private var photoFile: File? = null
    private var isFrontCamera: Boolean = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.confirmButton.isEnabled = false
        try {
            args = CaptureArgs.read(intent, savedInstanceState, drafts)
            isFrontCamera = args!!.isFrontCamera
            photoFile = drafts.file(args!!.captureId)
            if (photoFile!!.isFile) displayImage(photoFile!!.absolutePath)
            else handleImageError("Imagen no encontrada")
        } catch (error: Exception) {
            handleImageError("Referencia de captura no válida")
        }
        setupButtons()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = discardPhoto()
        })
    }
    private fun displayImage(photoPath: String) {
        decodeJob = lifecycleScope.launch {
            try {
                val bitmap = photoDecoder.decode(photoPath, isFrontCamera)
                if (bitmap == null) {
                    handleImageError("Error decodificando imagen")
                    return@launch
                }
                displayedBitmap = bitmap
                binding.previewImage.setImageBitmap(bitmap)
                binding.confirmButton.isEnabled = true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Error mostrando imagen", error)
                handleImageError("Error procesando imagen: ${error.message}")
            }
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
        val intent = requireNotNull(args).toIntent(this, AnalysisResultActivity::class.java)
        binding.confirmButton.isEnabled = false
        startActivity(intent)
        finish()
    }
    private fun discardPhoto() {
        args?.let { drafts.discard(it.captureId) }
        setResult(RESULT_CANCELED)
        finish()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        args?.save(outState)
        super.onSaveInstanceState(outState)
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
        val release = {
            displayedBitmap?.recycle()
            displayedBitmap = null
        }
        decodeJob?.invokeOnCompletion { release() } ?: release()
    }
}

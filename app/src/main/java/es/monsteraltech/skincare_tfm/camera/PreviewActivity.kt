package es.monsteraltech.skincare_tfm.camera

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.analysis.AnalysisResultActivity
import es.monsteraltech.skincare_tfm.databinding.ActivityPreviewBinding
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Extraer datos del intent
        val photoPath = intent.getStringExtra("PHOTO_PATH")
        isFrontCamera = intent.getBooleanExtra("IS_FRONT_CAMERA", false)
        bodyPartColorCode = intent.getStringExtra("BODY_PART_COLOR")
        preprocessingApplied = intent.getBooleanExtra("PREPROCESSING_APPLIED", false)
        processingMetadata = intent.getStringExtra("PROCESSING_METADATA") ?: ""

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

    /**
     * Muestra la imagen en la vista previa
     */
    private fun displayImage(photoPath: String) {
        try {
            // Decodificar y mostrar la imagen
            var bitmap = BitmapFactory.decodeFile(photoPath)

            if (bitmap == null) {
                handleImageError("Error decodificando imagen")
                return
            }

            // Corregir la orientación si es necesario
            if (isFrontCamera) {
                val matrix = Matrix()
                matrix.preScale(-1.0f, 1.0f) // Voltea horizontalmente para cámara frontal
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }

            binding.previewImage.setImageBitmap(bitmap)
            Log.d(TAG, "Imagen mostrada exitosamente")

        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando imagen", e)
            handleImageError("Error procesando imagen: ${e.message}")
        }
    }

    /**
     * Configura la información de procesamiento si está disponible
     */
    private fun setupProcessingInfo() {
        if (preprocessingApplied && processingMetadata.isNotEmpty()) {
            // Si hay información de procesamiento, mostrarla al usuario
            val processingInfo = if (processingMetadata.contains("Error")) {
                "⚠️ $processingMetadata"
            } else {
                "✅ Imagen mejorada automáticamente\n$processingMetadata"
            }
            
            // Mostrar información como toast para no sobrecargar la UI
            Toast.makeText(this, "Imagen procesada exitosamente", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Info de procesamiento: $processingInfo")
        }
    }

    /**
     * Configura los botones de la interfaz
     */
    private fun setupButtons() {
        // Botón para confirmar la foto y continuar al análisis
        binding.confirmButton.setOnClickListener {
            confirmAndNavigateToAnalysis()
        }

        // Botón para descartar la foto
        binding.discardButton.setOnClickListener {
            discardPhoto()
        }
    }

    /**
     * Confirma la foto y navega a la actividad de análisis
     */
    private fun confirmAndNavigateToAnalysis() {
        val photoPath = photoFile?.absolutePath
        
        if (photoPath == null || !File(photoPath).exists()) {
            Toast.makeText(this, "Error: Imagen no disponible", Toast.LENGTH_LONG).show()
            return
        }

        Log.d(TAG, "Confirmando imagen y navegando a análisis: $photoPath")

        // Crear intent para AnalysisResultActivity manteniendo compatibilidad completa
        val intent = Intent(this, AnalysisResultActivity::class.java).apply {
            putExtra("PHOTO_PATH", photoPath)
            putExtra("IS_FRONT_CAMERA", isFrontCamera)
            putExtra("BODY_PART_COLOR", bodyPartColorCode)
            
            // Pasar información de preprocesado para análisis mejorado
            putExtra("PREPROCESSING_APPLIED", preprocessingApplied)
            putExtra("PROCESSING_METADATA", processingMetadata)
        }

        startActivity(intent)
        finish() // Terminamos esta actividad para no volver aquí al presionar atrás
    }

    /**
     * Descarta la foto y regresa a la cámara
     */
    private fun discardPhoto() {
        try {
            // Borrar archivo de imagen
            photoFile?.let { file ->
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d(TAG, "Archivo eliminado: $deleted - ${file.absolutePath}")
                }
            }

            // También intentar borrar archivo original si existe
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

    /**
     * Maneja errores de imagen
     */
    private fun handleImageError(message: String) {
        Log.e(TAG, "Error de imagen: $message")
        binding.previewImage.setImageResource(R.drawable.cat) // Placeholder para errores
        Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
        
        // Deshabilitar botón de confirmación si hay error
        binding.confirmButton.isEnabled = false
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar recursos si es necesario
        binding.previewImage.setImageDrawable(null)
    }
}
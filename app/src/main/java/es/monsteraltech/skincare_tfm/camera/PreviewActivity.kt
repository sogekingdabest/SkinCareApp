package es.monsteraltech.skincare_tfm.camera

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.analysis.AnalysisResultActivity
import es.monsteraltech.skincare_tfm.databinding.ActivityPreviewBinding
import java.io.File

class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private var photoFile: File? = null
    private var isFrontCamera: Boolean = false
    private var bodyPartColorCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val photoPath = intent.getStringExtra("PHOTO_PATH")
        isFrontCamera = intent.getBooleanExtra("IS_FRONT_CAMERA", false)
        bodyPartColorCode = intent.getStringExtra("BODY_PART_COLOR")

        if (photoPath != null) {
            photoFile = File(photoPath)
            if (photoFile!!.exists()) {
                // Decodificar y mostrar la imagen
                var bitmap = BitmapFactory.decodeFile(photoPath)

                // Corregir la orientación si es necesario
                if (isFrontCamera) {
                    val matrix = Matrix()
                    matrix.preScale(-1.0f, 1.0f) // Voltea horizontalmente para cámara frontal
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                }

                binding.previewImage.setImageBitmap(bitmap)
            } else {
                // Mostrar un error si el archivo no existe
                binding.previewImage.setImageResource(R.drawable.cat) // Placeholder para errores
                Toast.makeText(this, "Error: Imagen no encontrada", Toast.LENGTH_LONG).show()
            }
        } else {
            binding.previewImage.setImageResource(R.drawable.cat) // Placeholder para errores
            Toast.makeText(this, "Error: Ruta de imagen no válida", Toast.LENGTH_LONG).show()
        }

        // Botón para confirmar la foto
        binding.confirmButton.setOnClickListener {
            // En lugar de devolver un resultado, iniciamos la actividad de análisis
            val intent = Intent(this, AnalysisResultActivity::class.java)
            intent.putExtra("PHOTO_PATH", photoFile?.absolutePath)
            intent.putExtra("IS_FRONT_CAMERA", isFrontCamera)
            intent.putExtra("BODY_PART_COLOR", bodyPartColorCode)
            startActivity(intent)
            finish() // Terminamos esta actividad para no volver aquí al presionar atrás
        }

        // Botón para descartar la foto
        binding.discardButton.setOnClickListener {
            photoFile?.delete() // Borra la foto temporal
            setResult(RESULT_CANCELED)
            finish()
        }
    }
}
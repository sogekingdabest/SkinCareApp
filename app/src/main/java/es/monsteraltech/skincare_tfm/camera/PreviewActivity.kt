package es.monsteraltech.skincare_tfm.camera

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.databinding.ActivityPreviewBinding
import java.io.File

class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private var photoFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val photoPath = intent.getStringExtra("PHOTO_PATH")
        if (photoPath != null) {
            photoFile = File(photoPath)
            if (photoFile!!.exists()) {
                // Decodificar y mostrar la imagen
                val bitmap = BitmapFactory.decodeFile(photoPath)
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
            Toast.makeText(this, "HOLAAAAAAAAA", Toast.LENGTH_SHORT).show()
            val resultIntent = Intent()
            resultIntent.putExtra("CONFIRMED_PATH", photoFile?.absolutePath)
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        // Botón para descartar la foto
        binding.discardButton.setOnClickListener {
            Toast.makeText(this, "ADIOOOOOSS", Toast.LENGTH_SHORT).show()
            photoFile?.delete() // Borra la foto temporal
            setResult(RESULT_CANCELED)
            finish()
        }
    }
}
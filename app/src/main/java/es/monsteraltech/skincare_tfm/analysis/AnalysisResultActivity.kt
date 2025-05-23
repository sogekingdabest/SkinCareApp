package es.monsteraltech.skincare_tfm.analysis

import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.BodyPartActivity
import es.monsteraltech.skincare_tfm.body.mole.repository.MoleRepository
import es.monsteraltech.skincare_tfm.databinding.ActivityAnalysisResultBinding
import kotlinx.coroutines.launch
import java.io.File

class AnalysisResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisResultBinding
    private var photoFile: File? = null
    private var bodyPartColorCode: String? = null
    private var selectedBodyPart: String = "" // Para almacenar la parte del cuerpo seleccionada
    private var analysisResult: String = "" // Para almacenar el resultado del análisis IA

    private val moleRepository = MoleRepository()
    private val auth = FirebaseAuth.getInstance()

    // Mapeo entre nombres de partes del cuerpo y códigos de color
    private val bodyPartToColorMap = mapOf(
        "Cabeza" to "#FF000000",
        "Brazo derecho" to "#FFED1C24",
        "Torso" to "#FFFFC90E",
        "Brazo izquierdo" to "#FF22B14C",
        "Pierna derecha" to "#FF3F48CC",
        "Pierna izquierda" to "#FFED00FF"
    )

    // Y el mapeo inverso
    private val colorToBodyPartMap = bodyPartToColorMap.entries.associate { (k, v) -> v to k }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val photoPath = intent.getStringExtra("PHOTO_PATH")
        val isFrontCamera = intent.getBooleanExtra("IS_FRONT_CAMERA", false)
        bodyPartColorCode = intent.getStringExtra("BODY_PART_COLOR")

        // Configurar el spinner con las partes del cuerpo
        setupBodyPartSpinner()

        if (photoPath != null) {
            photoFile = File(photoPath)
            if (photoFile!!.exists()) {
                // Decodificar y mostrar la imagen con rotación correcta
                var bitmap = BitmapFactory.decodeFile(photoPath)

                // Corregir la orientación si es necesario
                if (isFrontCamera) {
                    val matrix = Matrix()
                    matrix.preScale(-1.0f, 1.0f) // Voltea horizontalmente para cámara frontal
                    bitmap =
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                }

                binding.resultImageView.setImageBitmap(bitmap)

                // Aquí simularíamos o llamaríamos a un análisis real de IA
                performAnalysis(bitmap)
            } else {
                binding.resultImageView.setImageResource(R.drawable.cat)
                Toast.makeText(this, "Error: Imagen no encontrada", Toast.LENGTH_LONG).show()
            }
        }

        binding.saveButton.setOnClickListener {
            saveImageWithDetails()
        }
    }

    private fun setupBodyPartSpinner() {
        // Crear el adaptador para el spinner con la lista de partes del cuerpo
        val bodyPartsList =
            arrayListOf("Seleccionar parte del cuerpo") + bodyPartToColorMap.keys.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, bodyPartsList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.bodyPartSpinner.adapter = adapter

        // Si ya tenemos un código de color (venimos de selección de parte del cuerpo)
        if (bodyPartColorCode != null && colorToBodyPartMap.containsKey(bodyPartColorCode)) {
            // Obtenemos el nombre de la parte del cuerpo
            val bodyPartName = colorToBodyPartMap[bodyPartColorCode]
            selectedBodyPart = bodyPartName ?: ""

            // Mostramos el TextView con la parte seleccionada
            binding.bodyPartTextView.text = selectedBodyPart
            binding.bodyPartTextView.visibility = View.VISIBLE
            binding.bodyPartSpinner.visibility = View.GONE
        } else {
            // Si no hay parte del cuerpo predefinida, mostramos el spinner
            binding.bodyPartSpinner.visibility = View.VISIBLE
            binding.bodyPartTextView.visibility = View.GONE

            // Escuchar cambios en el spinner
            binding.bodyPartSpinner.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        if (position > 0) { // Ignoramos la primera opción que es "Seleccionar parte del cuerpo"
                            selectedBodyPart = bodyPartsList[position]
                            bodyPartColorCode = bodyPartToColorMap[selectedBodyPart]
                        } else {
                            selectedBodyPart = ""
                            bodyPartColorCode = null
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        selectedBodyPart = ""
                        bodyPartColorCode = null
                    }
                }
        }
    }

    private fun performAnalysis(bitmap: Bitmap) {
        // Aquí implementarías la lógica real para analizar la imagen con IA
        // Este es solo un placeholder para simular un resultado

        // Simulamos un tiempo de procesamiento
        binding.analysisResultText.text = "Analizando imagen..."

        binding.root.postDelayed({
            // Resultado de ejemplo
            analysisResult = "Análisis completado. La imagen muestra características de un lunar " +
                    "de tipo benigno con bordes regulares y coloración uniforme. " +
                    "Recomendación: Seguir monitorizando regularmente."

            binding.analysisResultText.text = analysisResult
        }, 1500)
    }

    private fun saveImageWithDetails() {
        // Verificar que el usuario está autenticado
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Por favor, inicia sesión para guardar", Toast.LENGTH_SHORT).show()
            return
        }

        val title = binding.titleEditText.text.toString()
        val description = binding.descriptionEditText.text.toString()

        // Validar que hay un título
        if (title.isEmpty()) {
            Toast.makeText(this, "Por favor, añade un título", Toast.LENGTH_SHORT).show()
            return
        }

        // Validar la selección de parte del cuerpo solo si no venía predefinida
        // y el usuario necesita seleccionar una
        if (bodyPartColorCode == null && selectedBodyPart.isEmpty()) {
            Toast.makeText(this, "Por favor, selecciona una parte del cuerpo", Toast.LENGTH_SHORT)
                .show()
            return
        }

        // Verificar que tenemos un archivo de imagen
        if (photoFile == null || !photoFile!!.exists()) {
            Toast.makeText(this, "Error: No se encontró la imagen", Toast.LENGTH_SHORT).show()
            return
        }

        // Mostrar diálogo de progreso
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Guardando información...")
            setCancelable(false)
            show()
        }

        // Guardar en Firebase usando coroutines
        lifecycleScope.launch {
            try {
                val result = moleRepository.saveMole(
                    context = applicationContext,
                    imageFile = photoFile!!,
                    title = title,
                    description = description,
                    bodyPart = selectedBodyPart,
                    bodyPartColorCode = bodyPartColorCode ?: "",
                    aiResult = analysisResult
                )

                // Ocultar el diálogo de progreso
                progressDialog.dismiss()

                if (result.isSuccess) {
                    Toast.makeText(
                        this@AnalysisResultActivity,
                        "Imagen guardada correctamente",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Volvemos a la actividad de la parte del cuerpo correspondiente
                    if (bodyPartColorCode != null) {
                        val intent =
                            Intent(this@AnalysisResultActivity, BodyPartActivity::class.java)
                        intent.putExtra("COLOR_VALUE", bodyPartColorCode)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(intent)
                    } else {
                        // Si no hay parte del cuerpo, volvemos a la actividad principal
                        val intent = Intent(
                            this@AnalysisResultActivity,
                            es.monsteraltech.skincare_tfm.MainActivity::class.java
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(intent)
                    }
                    finish()
                } else {
                    // Mostrar error
                    Toast.makeText(
                        this@AnalysisResultActivity,
                        "Error al guardar: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                // Ocultar el diálogo de progreso
                progressDialog.dismiss()

                // Mostrar error
                Toast.makeText(
                    this@AnalysisResultActivity,
                    "Error al guardar: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
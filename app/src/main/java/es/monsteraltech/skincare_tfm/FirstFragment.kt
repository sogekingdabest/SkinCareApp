package es.monsteraltech.skincare_tfm

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import es.monsteraltech.skincare_tfm.camera.CameraActivity
import es.monsteraltech.skincare_tfm.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var selectedImages: ArrayList<Uri> = ArrayList()

    private val startForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            data?.let { intentData ->
                val images = intentData.getParcelableArrayListExtra<Uri>("selectedImages")
                if (images != null) {
                    selectedImages.addAll(images)
                }
            }
            Log.d("AddFragment", "Imagenes seleccionadas: ${selectedImages.size}")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializa los componentes de la UI
        val addImageButton = view.findViewById<Button>(R.id.button_open_camera)

        // Controlamos el boton de agregar imagenes
        addImageButton.setOnClickListener {
                val intent = Intent(requireContext(), CameraActivity::class.java)
                startForResult.launch(intent)

        }


        binding.buttonFirst.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
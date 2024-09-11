package es.monsteraltech.skincare_tfm.fragments

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import es.monsteraltech.skincare_tfm.R

class MyBodyFragment : Fragment() {

    private lateinit var frontBodyImageAreas: ImageView
    private lateinit var bitmap: Bitmap

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_body, container, false)

        frontBodyImageAreas = view.findViewById(R.id.frontBodyImageAreas)

        frontBodyImageAreas.post {
            val drawable = frontBodyImageAreas.drawable as BitmapDrawable
            bitmap = drawable.bitmap

            frontBodyImageAreas.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val imageViewWidth = frontBodyImageAreas.width
                    val imageViewHeight = frontBodyImageAreas.height

                    val x = (event.x * bitmap.width / imageViewWidth).toInt()
                    val y = (event.y * bitmap.height / imageViewHeight).toInt()

                    Log.d("MyBodyFragment", "Touch at: x=$x, y=$y")

                    if (x >= 0 && y >= 0 && x < bitmap.width && y < bitmap.height) {
                        val pixelColor = bitmap.getPixel(x, y)
                        //Log.d("MyBodyFragment", String.format("Pixel color: #%08X", pixelColor))
                        val hexColor = String.format("#%08X", pixelColor)
                        handleColorClick(hexColor)
                    }
                }
                true
            }
        }

        return view
    }

    private fun handleColorClick(color: String) {
        Log.d("MyBodyFragment", "Color clicado: $color")

        when (color) {
            "#FF000000" -> {
                Toast.makeText(context, "Área Negra", Toast.LENGTH_SHORT).show()
            }
            "#FFED1C24" -> {
                Toast.makeText(context, "Área Roja", Toast.LENGTH_SHORT).show()
            }
            "#FFFFC90E" -> {
                Toast.makeText(context, "Área Amarilla", Toast.LENGTH_SHORT).show()
            }
            "#FF22B14C" -> {
                Toast.makeText(context, "Área Verde", Toast.LENGTH_SHORT).show()
            }
            "#FF3F48CC" -> {
                Toast.makeText(context, "Área Azul", Toast.LENGTH_SHORT).show()
            }
            "#FFED00FF" -> {
                Toast.makeText(context, "Área Morada", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(context, "Área Desconocida", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

package es.monsteraltech.skincare_tfm.body.mole.ViewPageImages

import android.view.View
import androidx.viewpager2.widget.ViewPager2

class ZoomOutPageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(view: View, position: Float) {
        view.apply {
            val pageWidth = width
            val pageHeight = height
            when {
                position < -1 -> { // Página fuera de la izquierda
                    alpha = 0f
                }
                position <= 1 -> { // Página visible (posición entre -1 y 1)
                    // Desvanecer la página mientras desaparece
                    alpha = 1 - Math.abs(position)

                    // Escalar la página
                    val scaleFactor = Math.max(0.85f, 1 - Math.abs(position))
                    val verticalMargin = pageHeight * (1 - scaleFactor) / 2
                    val horizontalMargin = pageWidth * (1 - scaleFactor) / 2
                    translationX = if (position < 0) {
                        horizontalMargin - verticalMargin / 2
                    } else {
                        horizontalMargin + verticalMargin / 2
                    }

                    // Aplicar la escala
                    scaleX = scaleFactor
                    scaleY = scaleFactor
                }
                else -> { // Página fuera de la derecha
                    alpha = 0f
                }
            }
        }
    }
}

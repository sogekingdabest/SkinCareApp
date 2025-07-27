package es.monsteraltech.skincare_tfm.utils

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet

/**
 * Utilidades para layouts responsivos que se adaptan a diferentes tamaños de pantalla
 */
object ResponsiveLayoutUtils {

    /**
     * Determina si el dispositivo es una tablet
     */
    fun isTablet(context: Context): Boolean {
        val configuration = context.resources.configuration
        return (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    /**
     * Determina si la orientación es horizontal
     */
    fun isLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    /**
     * Obtiene el ancho de la pantalla en dp
     */
    fun getScreenWidthDp(context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        return (displayMetrics.widthPixels / displayMetrics.density).toInt()
    }

    /**
     * Obtiene el alto de la pantalla en dp
     */
    fun getScreenHeightDp(context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        return (displayMetrics.heightPixels / displayMetrics.density).toInt()
    }

    /**
     * Convierte dp a pixels
     */
    fun dpToPx(context: Context, dp: Float): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }

    /**
     * Convierte pixels a dp
     */
    fun pxToDp(context: Context, px: Float): Int {
        val density = context.resources.displayMetrics.density
        return (px / density + 0.5f).toInt()
    }

    /**
     * Ajusta el layout para diferentes tamaños de pantalla
     */
    fun adjustLayoutForScreenSize(context: Context, view: View) {
        val isTablet = isTablet(context)
        val isLandscape = isLandscape(context)

        when {
            isTablet && isLandscape -> {
                // Tablet en horizontal - usar layout de dos columnas
                adjustForTabletLandscape(view)
            }
            isTablet && !isLandscape -> {
                // Tablet en vertical - usar layout expandido
                adjustForTabletPortrait(view)
            }
            !isTablet && isLandscape -> {
                // Teléfono en horizontal - layout compacto horizontal
                adjustForPhoneLandscape(view)
            }
            else -> {
                // Teléfono en vertical - layout estándar
                adjustForPhonePortrait(view)
            }
        }
    }

    /**
     * Ajusta el layout para tablet en horizontal
     */
    private fun adjustForTabletLandscape(view: View) {
        // Implementar ajustes específicos para tablet horizontal
        val layoutParams = view.layoutParams
        if (layoutParams is ViewGroup.MarginLayoutParams) {
            val context = view.context
            layoutParams.setMargins(
                dpToPx(context, 32f),
                dpToPx(context, 24f),
                dpToPx(context, 32f),
                dpToPx(context, 24f)
            )
        }
    }

    /**
     * Ajusta el layout para tablet en vertical
     */
    private fun adjustForTabletPortrait(view: View) {
        // Implementar ajustes específicos para tablet vertical
        val layoutParams = view.layoutParams
        if (layoutParams is ViewGroup.MarginLayoutParams) {
            val context = view.context
            layoutParams.setMargins(
                dpToPx(context, 24f),
                dpToPx(context, 20f),
                dpToPx(context, 24f),
                dpToPx(context, 20f)
            )
        }
    }

    /**
     * Ajusta el layout para teléfono en horizontal
     */
    private fun adjustForPhoneLandscape(view: View) {
        // Implementar ajustes específicos para teléfono horizontal
        val layoutParams = view.layoutParams
        if (layoutParams is ViewGroup.MarginLayoutParams) {
            val context = view.context
            layoutParams.setMargins(
                dpToPx(context, 16f),
                dpToPx(context, 8f),
                dpToPx(context, 16f),
                dpToPx(context, 8f)
            )
        }
    }

    /**
     * Ajusta el layout para teléfono en vertical
     */
    private fun adjustForPhonePortrait(view: View) {
        // Implementar ajustes específicos para teléfono vertical
        val layoutParams = view.layoutParams
        if (layoutParams is ViewGroup.MarginLayoutParams) {
            val context = view.context
            layoutParams.setMargins(
                dpToPx(context, 16f),
                dpToPx(context, 16f),
                dpToPx(context, 16f),
                dpToPx(context, 16f)
            )
        }
    }

    /**
     * Ajusta el espaciado entre elementos basado en el tamaño de pantalla
     */
    fun adjustSpacing(context: Context, view: View, baseSpacing: Int): Int {
        return when {
            isTablet(context) -> (baseSpacing * 1.5f).toInt()
            isLandscape(context) -> (baseSpacing * 0.8f).toInt()
            else -> baseSpacing
        }
    }

    /**
     * Ajusta el tamaño de texto basado en el tamaño de pantalla
     */
    fun adjustTextSize(context: Context, baseTextSize: Float): Float {
        return when {
            isTablet(context) -> baseTextSize * 1.2f
            isLandscape(context) -> baseTextSize * 0.9f
            else -> baseTextSize
        }
    }

    /**
     * Configura un LinearLayout para ser responsivo
     */
    fun setupResponsiveLinearLayout(context: Context, linearLayout: LinearLayout) {
        val isTablet = isTablet(context)
        val isLandscape = isLandscape(context)

        when {
            isTablet && isLandscape -> {
                // En tablet horizontal, usar orientación horizontal para aprovechar el espacio
                linearLayout.orientation = LinearLayout.HORIZONTAL
            }
            isTablet || (!isTablet && !isLandscape) -> {
                // En tablet vertical o teléfono vertical, usar orientación vertical
                linearLayout.orientation = LinearLayout.VERTICAL
            }
            else -> {
                // En teléfono horizontal, mantener vertical pero con espaciado reducido
                linearLayout.orientation = LinearLayout.VERTICAL
            }
        }

        // Ajustar padding basado en el tipo de dispositivo
        val padding = when {
            isTablet -> dpToPx(context, 24f)
            isLandscape -> dpToPx(context, 12f)
            else -> dpToPx(context, 16f)
        }

        linearLayout.setPadding(padding, padding, padding, padding)
    }

    /**
     * Configura un ConstraintLayout para ser responsivo
     */
    fun setupResponsiveConstraintLayout(context: Context, constraintLayout: ConstraintLayout) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        val isTablet = isTablet(context)
        val isLandscape = isLandscape(context)

        // Ajustar márgenes basado en el tipo de dispositivo
        val margin = when {
            isTablet -> dpToPx(context, 32f)
            isLandscape -> dpToPx(context, 16f)
            else -> dpToPx(context, 20f)
        }

        // Aplicar los cambios
        constraintSet.applyTo(constraintLayout)

        // Ajustar padding
        val padding = when {
            isTablet -> dpToPx(context, 24f)
            isLandscape -> dpToPx(context, 12f)
            else -> dpToPx(context, 16f)
        }

        constraintLayout.setPadding(padding, padding, padding, padding)
    }

    /**
     * Determina el número óptimo de columnas para una grilla basado en el ancho de pantalla
     */
    fun getOptimalColumnCount(context: Context, itemMinWidth: Int): Int {
        val screenWidth = getScreenWidthDp(context)
        val availableWidth = screenWidth - 32 // Restar márgenes
        return maxOf(1, availableWidth / itemMinWidth)
    }

    /**
     * Ajusta el tamaño de los iconos basado en el tamaño de pantalla
     */
    fun getResponsiveIconSize(context: Context, baseSize: Int): Int {
        return when {
            isTablet(context) -> (baseSize * 1.3f).toInt()
            isLandscape(context) -> (baseSize * 0.9f).toInt()
            else -> baseSize
        }
    }

    /**
     * Obtiene el ancho máximo recomendado para contenido en tablets
     */
    fun getMaxContentWidth(context: Context): Int {
        return if (isTablet(context)) {
            dpToPx(context, 800f) // Máximo 800dp para tablets
        } else {
            ViewGroup.LayoutParams.MATCH_PARENT
        }
    }
}
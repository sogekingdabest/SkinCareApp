package es.monsteraltech.skincare_tfm.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import es.monsteraltech.skincare_tfm.R

/**
 * Utilidades para mejorar la experiencia de usuario con animaciones y feedback visual
 */
object UIUtils {

    /**
     * Aplica una animación de fade in a una vista
     */
    fun fadeIn(view: View, duration: Long = 300) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        
        ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * Aplica una animación de escala (bounce) para feedback de toque
     */
    fun bounceView(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f, 1f)
        
        scaleX.duration = 150
        scaleY.duration = 150
        
        scaleX.start()
        scaleY.start()
    }

    /**
     * Muestra un Snackbar con estilo personalizado para éxito
     */
    fun showSuccessSnackbar(view: View, message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        val snackbar = Snackbar.make(view, message, duration)
        snackbar.setBackgroundTint(ContextCompat.getColor(view.context, R.color.risk_very_low))
        snackbar.setTextColor(ContextCompat.getColor(view.context, R.color.white))
        snackbar.show()
    }

    /**
     * Muestra un Snackbar con estilo personalizado para error
     */
    fun showErrorSnackbar(view: View, message: String, duration: Int = Snackbar.LENGTH_LONG) {
        val snackbar = Snackbar.make(view, message, duration)
        snackbar.setBackgroundTint(ContextCompat.getColor(view.context, R.color.md_theme_error))
        snackbar.setTextColor(ContextCompat.getColor(view.context, R.color.white))
        snackbar.show()
    }

    /**
     * Aplica animación de shake para indicar error
     */
    fun shakeView(view: View) {
        val shake = ObjectAnimator.ofFloat(view, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f)
        shake.duration = 600
        shake.start()
    }

    /**
     * Aplica animación de pulso para llamar la atención
     */
    fun pulseView(view: View, repeat: Boolean = false) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.1f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.1f, 1f)
        
        scaleX.duration = 800
        scaleY.duration = 800
        
        if (repeat) {
            scaleX.repeatCount = ObjectAnimator.INFINITE
            scaleY.repeatCount = ObjectAnimator.INFINITE
        }
        
        scaleX.start()
        scaleY.start()
    }

    /**
     * Aplica animación de entrada para elementos de lista
     */
    fun animateListItem(view: View, position: Int) {
        view.alpha = 0f
        view.translationY = 50f
        
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setStartDelay((position * 50).toLong())
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }
}
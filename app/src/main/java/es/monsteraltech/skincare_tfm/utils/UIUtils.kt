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
     * Aplica una animación de fade out a una vista
     */
    fun fadeOut(view: View, duration: Long = 300, hideAfter: Boolean = true) {
        ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            
            if (hideAfter) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        view.visibility = View.GONE
                    }
                })
            }
            
            start()
        }
    }

    /**
     * Aplica una animación de slide in desde la derecha
     */
    fun slideInFromRight(view: View, duration: Long = 300) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.slide_in_right)
        animation.duration = duration
        view.startAnimation(animation)
        view.visibility = View.VISIBLE
    }

    /**
     * Aplica una animación de slide out hacia la izquierda
     */
    fun slideOutToLeft(view: View, duration: Long = 300, hideAfter: Boolean = true) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.slide_out_left)
        animation.duration = duration
        
        if (hideAfter) {
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    view.visibility = View.GONE
                }
            })
        }
        
        view.startAnimation(animation)
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
     * Muestra un Snackbar con estilo personalizado para información
     */
    fun showInfoSnackbar(view: View, message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        val snackbar = Snackbar.make(view, message, duration)
        snackbar.setBackgroundTint(ContextCompat.getColor(view.context, R.color.md_theme_primary))
        snackbar.setTextColor(ContextCompat.getColor(view.context, R.color.white))
        snackbar.show()
    }

    /**
     * Muestra un Snackbar con acción personalizada
     */
    fun showActionSnackbar(
        view: View, 
        message: String, 
        actionText: String, 
        action: () -> Unit,
        duration: Int = Snackbar.LENGTH_LONG
    ) {
        val snackbar = Snackbar.make(view, message, duration)
        snackbar.setAction(actionText) { action() }
        snackbar.setActionTextColor(ContextCompat.getColor(view.context, R.color.risk_moderate))
        snackbar.show()
    }

    /**
     * Aplica animación de transición entre estados de vista
     */
    fun crossFadeViews(viewOut: View, viewIn: View, duration: Long = 300) {
        // Fade out la vista actual
        fadeOut(viewOut, duration, true)
        
        // Fade in la nueva vista con un pequeño delay
        viewIn.postDelayed({
            fadeIn(viewIn, duration)
        }, duration / 2)
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
     * Muestra un Toast con duración personalizada y posición
     */
    fun showCustomToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
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

    /**
     * Aplica animación de carga con rotación
     */
    fun startLoadingAnimation(view: View) {
        val rotation = ObjectAnimator.ofFloat(view, "rotation", 0f, 360f)
        rotation.duration = 1000
        rotation.repeatCount = ObjectAnimator.INFINITE
        rotation.start()
        
        // Guardar la animación en el tag para poder detenerla después
        view.tag = rotation
    }

    /**
     * Detiene la animación de carga
     */
    fun stopLoadingAnimation(view: View) {
        val rotation = view.tag as? ObjectAnimator
        rotation?.cancel()
        view.rotation = 0f
        view.tag = null
    }
}
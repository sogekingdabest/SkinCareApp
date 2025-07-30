package es.monsteraltech.skincare_tfm.login

import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import es.monsteraltech.skincare_tfm.R


class InicioActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
/*        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )*/
        setContentView(R.layout.activity_inicio)

        // Agregar animaciones
        val animacion1: Animation = AnimationUtils.loadAnimation(this, R.anim.desplazamiento_arriba)
        val animacion2: Animation = AnimationUtils.loadAnimation(this, R.anim.desplazamiento_abajo)
        val deTextView: TextView = findViewById(R.id.deTextView)
        val skinCareTextView: TextView = findViewById(R.id.skinCareTextView)
        val logoImageView: ImageView = findViewById(R.id.logoImageView)
        deTextView.animation = animacion2
        skinCareTextView.animation = animacion2
        logoImageView.animation = animacion1

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this@InicioActivity, SessionCheckActivity::class.java)
            val pairs = arrayOf(
                android.util.Pair<View, String>(logoImageView, "logoImageView"),
                android.util.Pair<View, String>(skinCareTextView, "textTrans")
            )
            val options = ActivityOptions.makeSceneTransitionAnimation(this@InicioActivity, *pairs)
            startActivity(intent, options.toBundle())
            finish() // Finalizar InicioActivity para que no quede en el stack
        }, 4000)
    }
}
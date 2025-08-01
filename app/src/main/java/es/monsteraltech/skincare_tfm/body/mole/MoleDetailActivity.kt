package es.monsteraltech.skincare_tfm.body.mole

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Actividad de compatibilidad que redirige a MoleViewerActivity
 * Mantiene compatibilidad con código existente
 */
class MoleDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Redirigir a la nueva MoleViewerActivity con todos los datos
        val intent = Intent(this, MoleViewerActivity::class.java).apply {
            // Pasar todos los extras del intent original
            putExtras(getIntent().extras ?: Bundle())
        }
        
        startActivity(intent)
        finish() // Cerrar esta actividad para evitar duplicados en el stack
    }
}
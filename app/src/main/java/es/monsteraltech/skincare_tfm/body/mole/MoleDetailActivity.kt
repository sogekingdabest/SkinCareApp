package es.monsteraltech.skincare_tfm.body.mole
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
class MoleDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, MoleViewerActivity::class.java).apply {
            putExtras(intent.extras ?: Bundle())
        }
        startActivity(intent)
        finish()
    }
}
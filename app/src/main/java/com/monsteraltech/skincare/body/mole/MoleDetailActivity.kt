package com.monsteraltech.skincare.body.mole
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
class MoleDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = MoleViewerArgs.fromIntent(intent).toIntent(this)
        startActivity(intent)
        finish()
    }
}

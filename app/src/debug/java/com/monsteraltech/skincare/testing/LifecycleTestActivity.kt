package com.monsteraltech.skincare.testing

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.monsteraltech.skincare.R
import dagger.hilt.android.AndroidEntryPoint

/** Minimal debug-only host for testing actual fragment view lifecycles. */
@AndroidEntryPoint
class LifecycleTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id = R.id.fragment_container })
    }
}

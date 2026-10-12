package com.monsteraltech.skincare.testing

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.monsteraltech.skincare.analysis.AnalysisResultActivity
import com.monsteraltech.skincare.camera.CaptureArgs
import com.monsteraltech.skincare.camera.PreviewActivity
import dagger.hilt.android.EntryPointAccessors

/** Debug-only shell entry: production capture screens remain unexported in every build. */
@SuppressLint("CustomSplashScreen") // Test redirect, not an application launch screen.
class ProcessRestorationLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val destination = when (intent.getStringExtra("mode")) {
                "preview" -> PreviewActivity::class.java
                "analysis" -> AnalysisResultActivity::class.java
                else -> error("Unknown fixture destination")
            }
            val drafts = EntryPointAccessors.fromApplication(this, TestingDependencies::class.java).drafts()
            val args = CaptureArgs.read(intent, null, drafts)
            check(drafts.file(args.captureId).isFile)
            startActivity(args.toIntent(this, destination))
        } catch (error: Exception) {
            Log.e("ProcessRestoration", "Cannot open the process fixture", error)
        } finally { finish() }
    }
}

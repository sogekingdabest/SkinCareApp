package com.monsteraltech.skincare.testing

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import com.monsteraltech.skincare.utils.MedicalDisclaimerHelper
import dagger.hilt.android.EntryPointAccessors

/** Creates a synthetic private photo for host-driven process restoration checks. */
class ProcessRestorationProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            val dependencies = EntryPointAccessors.fromApplication(targetContext, TestingDependencies::class.java)
            dependencies.profile().activateLocalProfile()
            MedicalDisclaimerHelper(targetContext).markDisclaimerAsAccepted()
            val draft = dependencies.drafts().create()
            val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.RED)
                for (y in 0 until 48) for (x in 32 until 64) bitmap.setPixel(x, y, Color.BLUE)
                draft.file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            dependencies.drafts().markReady(draft.id)
            result.putString("captureId", draft.id)
            finish(Activity.RESULT_OK, result)
        } catch (error: Exception) {
            result.putString("error", error.toString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}

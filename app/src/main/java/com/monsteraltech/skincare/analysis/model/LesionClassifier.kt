package com.monsteraltech.skincare.analysis.model

import android.graphics.Bitmap
import java.io.Closeable

/** Raw model score and provenance, before the application's ABCDE decision policy. */
data class LesionPrediction(val score: Float, val model: ModelIdentity) {
    init { require(score.isFinite() && score in 0f..1f) { "Invalid classifier output" } }
}

interface LesionClassifier : Closeable {
    fun predict(bitmap: Bitmap, metadata: FloatArray? = null): LesionPrediction
}

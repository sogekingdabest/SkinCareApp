package com.monsteraltech.skincare.analysis

import android.graphics.Bitmap

interface ImageAnalysisRunner {
    suspend fun processImage(
        bitmap: Bitmap,
        previousBitmap: Bitmap? = null,
        pixelDensity: Float = 1f,
        config: AnalysisConfiguration = AnalysisConfiguration.default(),
        onProgress: (AnalysisProgress) -> Unit = {}
    ): AnalysisOutcome

    fun close()
}

/** Native operations are isolated so orchestration can be tested without loading OpenCV or LiteRT. */
interface AnalysisEngines {
    fun prepare(bitmap: Bitmap, config: AnalysisConfiguration): Bitmap
    suspend fun infer(bitmap: Bitmap): MelanomaAIDetector.AIInferenceResult
    suspend fun analyzeABCDE(bitmap: Bitmap, previousBitmap: Bitmap?, pixelDensity: Float): ABCDEAnalyzerOpenCV.ABCDEResult
    fun combine(ai: MelanomaAIDetector.AIInferenceResult, abcde: ABCDEAnalyzerOpenCV.ABCDEResult): MelanomaAIDetector.CombinedAnalysisResult
    fun close()
}

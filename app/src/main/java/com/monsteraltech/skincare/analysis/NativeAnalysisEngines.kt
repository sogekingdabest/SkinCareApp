package com.monsteraltech.skincare.analysis

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.sqrt

class NativeAnalysisEngines @Inject constructor(
    @ApplicationContext context: Context
) : AnalysisEngines {
    private val detector = lazy { MelanomaAIDetector(context) }
    private val analyzer = lazy { ABCDEAnalyzerOpenCV(context) }

    override fun prepare(bitmap: Bitmap, config: AnalysisConfiguration): Bitmap {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0 ||
            bitmap.width.toLong() * bitmap.height < 1000L
        ) {
            throw AnalysisError.InvalidImageError("Imagen vacía, reciclada o demasiado pequeña")
        }
        if (!config.enableAutoImageReduction) return bitmap
        val memoryCap = if (Runtime.getRuntime().maxMemory() < 512L * 1024 * 1024) 512 * 512 else 1024 * 1024
        val maxPixels = minOf(config.maxImageResolution, memoryCap)
        val pixels = bitmap.width.toLong() * bitmap.height
        val maxDimension = maxOf(bitmap.width, bitmap.height)
        if (pixels <= maxPixels && maxDimension <= 1024) return bitmap
        val scale = minOf(sqrt(maxPixels.toDouble() / pixels), 1024.0 / maxDimension)
        return bitmap.scale(maxOf(1, (bitmap.width * scale).toInt()), maxOf(1, (bitmap.height * scale).toInt()))
    }

    override suspend fun infer(bitmap: Bitmap) = detector.value.runInference(bitmap)

    override suspend fun analyzeABCDE(bitmap: Bitmap, previousBitmap: Bitmap?, pixelDensity: Float) =
        analyzer.value.analyzeMole(bitmap, previousBitmap, pixelDensity)

    override fun combine(ai: MelanomaAIDetector.AIInferenceResult, abcde: ABCDEAnalyzerOpenCV.ABCDEResult) =
        detector.value.combineResults(ai.probability, ai.riskLevel, ai.confidence, abcde)

    override fun close() {
        if (detector.isInitialized()) detector.value.close()
    }
}

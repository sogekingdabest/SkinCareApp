package com.monsteraltech.skincare.camera.guidance

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.min

/**
 * Analiza la calidad del frame para el guiado de captura: exposición
 * (brillo medio y ratio de píxeles saturados) y nitidez (varianza del
 * Laplaciano sobre la región central, donde está el lunar).
 */
class ImageQualityAnalyzer {

    companion object {
        private const val TAG = "ImageQualityAnalyzer"
        private const val MIN_BRIGHTNESS = 70f
        private const val MAX_BRIGHTNESS = 190f
        private const val OVEREXPOSURE_THRESHOLD = 240.0
        private const val UNDEREXPOSURE_THRESHOLD = 30.0
        private const val EXPOSURE_PIXEL_RATIO = 0.15
        // Varianza del Laplaciano por debajo de la cual el frame se considera borroso.
        private const val SHARPNESS_THRESHOLD = 60.0
        private const val CENTER_ROI_FRACTION = 0.5
    }

    data class QualityAnalysis(
        val brightness: Float,
        val isOptimalLighting: Boolean,
        val isOverexposed: Boolean,
        val isUnderexposed: Boolean,
        val sharpness: Double,
        val isBlurry: Boolean
    )

    suspend fun analyze(frame: Mat): QualityAnalysis = withContext(Dispatchers.Default) {
        val ownsGray = frame.channels() != 1
        val gray = if (ownsGray) {
            Mat().also { Imgproc.cvtColor(frame, it, Imgproc.COLOR_BGR2GRAY) }
        } else {
            frame
        }
        try {
            val brightness = calculateBrightness(gray)
            val isOverexposed = exceedsPixelRatio(gray, OVEREXPOSURE_THRESHOLD, Core.CMP_GT)
            val isUnderexposed = exceedsPixelRatio(gray, UNDEREXPOSURE_THRESHOLD, Core.CMP_LT)
            val sharpness = calculateCenterSharpness(gray)

            QualityAnalysis(
                brightness = brightness,
                isOptimalLighting = !isOverexposed && !isUnderexposed &&
                        brightness in MIN_BRIGHTNESS..MAX_BRIGHTNESS,
                isOverexposed = isOverexposed,
                isUnderexposed = isUnderexposed,
                sharpness = sharpness,
                isBlurry = sharpness < SHARPNESS_THRESHOLD
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Log.e(TAG, "Error analizando calidad del frame", e)
            // Peor caso conservador: tratar como subexpuesto para que el usuario reintente.
            QualityAnalysis(0f, false, false, true, 0.0, false)
        } finally {
            if (ownsGray) gray.release()
        }
    }

    private fun calculateBrightness(gray: Mat): Float {
        val mean = MatOfDouble()
        val std = MatOfDouble()
        try {
            Core.meanStdDev(gray, mean, std)
            return mean.get(0, 0)[0].toFloat()
        } finally {
            mean.release()
            std.release()
        }
    }

    private fun exceedsPixelRatio(gray: Mat, threshold: Double, compareOp: Int): Boolean {
        val mask = Mat()
        try {
            Core.compare(gray, Scalar(threshold), mask, compareOp)
            val matching = Core.countNonZero(mask)
            return matching.toDouble() / gray.total() > EXPOSURE_PIXEL_RATIO
        } finally {
            mask.release()
        }
    }

    /**
     * Nitidez como varianza del Laplaciano sobre la zona central del frame.
     * Valores bajos = imagen borrosa (desenfoque o movimiento).
     */
    private fun calculateCenterSharpness(gray: Mat): Double {
        val side = (min(gray.cols(), gray.rows()) * CENTER_ROI_FRACTION).toInt()
        if (side < 16) return SHARPNESS_THRESHOLD
        val roi = Mat(
            gray,
            Rect((gray.cols() - side) / 2, (gray.rows() - side) / 2, side, side)
        )
        val laplacian = Mat()
        val mean = MatOfDouble()
        val std = MatOfDouble()
        try {
            Imgproc.Laplacian(roi, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, mean, std)
            val sigma = std.get(0, 0)[0]
            return sigma * sigma
        } finally {
            laplacian.release()
            mean.release()
            std.release()
            roi.release()
        }
    }
}

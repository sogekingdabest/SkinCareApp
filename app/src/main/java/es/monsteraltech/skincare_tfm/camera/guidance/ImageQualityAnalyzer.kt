package es.monsteraltech.skincare_tfm.camera.guidance

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc


class ImageQualityAnalyzer {
    
    companion object {
        private const val TAG = "ImageQualityAnalyzer"
        private const val MIN_BRIGHTNESS = 80f
        private const val MAX_BRIGHTNESS = 180f
        private const val OVEREXPOSURE_THRESHOLD = 240.0
        private const val UNDEREXPOSURE_THRESHOLD = 30.0
        private const val EXPOSURE_PIXEL_RATIO = 0.15
    }
    data class LightingAnalysis(
        val brightness: Float,
        val isOptimalLighting: Boolean,
        val isOverexposed: Boolean,
        val isUnderexposed: Boolean
    ) {
        fun getFeedbackMessage(): String {
            return when {
                isUnderexposed -> "Necesitas más luz"
                isOverexposed -> "Demasiada luz - busca sombra"
                isOptimalLighting -> "Iluminación óptima"
                else -> "Ajusta la iluminación"
            }
        }
    }

    suspend fun analyzeLighting(frame: Mat): LightingAnalysis = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Analizando iluminación del frame ${frame.cols()}x${frame.rows()}")

            val grayFrame = Mat()
            if (frame.channels() > 1) {
                Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY)
            } else {
                frame.copyTo(grayFrame)
            }

            val brightness = calculateBrightness(grayFrame)

            val isOverexposed = detectOverexposure(grayFrame)
            val isUnderexposed = detectUnderexposure(grayFrame)

            val isOptimalLighting = !isOverexposed && !isUnderexposed && 
                                  brightness >= MIN_BRIGHTNESS && brightness <= MAX_BRIGHTNESS
            
            grayFrame.release()
            
            val result = LightingAnalysis(
                brightness = brightness,
                isOptimalLighting = isOptimalLighting,
                isOverexposed = isOverexposed,
                isUnderexposed = isUnderexposed
            )
            
            Log.d(TAG, "Análisis de iluminación: brillo=$brightness, óptima=$isOptimalLighting")
            return@withContext result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analizando iluminación", e)
            return@withContext LightingAnalysis(0f, false, false, true)
        }
    }


    private fun calculateBrightness(grayFrame: Mat): Float {
        val meanMat = MatOfDouble()
        try {
            Core.meanStdDev(grayFrame, meanMat, MatOfDouble())
            return meanMat.get(0, 0)[0].toFloat()
        } finally {
            meanMat.release()
        }
    }

    private fun detectOverexposure(grayFrame: Mat): Boolean {
        val mask = Mat()
        try {
            Core.compare(grayFrame, Scalar(OVEREXPOSURE_THRESHOLD), mask, Core.CMP_GT)
            val overexposedPixels = Core.countNonZero(mask)
            val totalPixels = grayFrame.total()
            val overexposedRatio = overexposedPixels.toDouble() / totalPixels
            return overexposedRatio > EXPOSURE_PIXEL_RATIO
        } finally {
            mask.release()
        }
    }
    

    private fun detectUnderexposure(grayFrame: Mat): Boolean {
        val mask = Mat()
        try {
            Core.compare(grayFrame, Scalar(UNDEREXPOSURE_THRESHOLD), mask, Core.CMP_LT)
            val underexposedPixels = Core.countNonZero(mask)
            val totalPixels = grayFrame.total()
            val underexposedRatio = underexposedPixels.toDouble() / totalPixels
            return underexposedRatio > EXPOSURE_PIXEL_RATIO
        } finally {
            mask.release()
        }
    }

}
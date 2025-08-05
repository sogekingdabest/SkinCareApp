package es.monsteraltech.skincare_tfm.camera.guidance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
class ImageQualityAnalyzer(
    private val performanceManager: PerformanceManager,
    private val thermalDetector: ThermalStateDetector
) {
    data class QualityMetrics(
        val sharpness: Float,
        val brightness: Float,
        val contrast: Float,
        val isBlurry: Boolean,
        val isOverexposed: Boolean,
        val isUnderexposed: Boolean
    ) {
        fun isGoodQuality(): Boolean {
            return !isBlurry && !isOverexposed && !isUnderexposed
        }
        fun getFeedbackMessage(): String {
            return when {
                isBlurry -> "Imagen borrosa - mantén firme la cámara"
                isUnderexposed -> "Necesitas más luz"
                isOverexposed -> "Demasiada luz - busca sombra"
                else -> "Calidad de imagen buena"
            }
        }
    }
    private val sharpnessThreshold = 0.3f
    private val minBrightness = 80f
    private val maxBrightness = 180f
    private val minContrast = 0.2f
    private val overexposureThreshold = 240.0
    private val underexposureThreshold = 30.0
    private val exposurePixelRatio = 0.1
    suspend fun analyzeQuality(frame: Mat): QualityMetrics = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val grayFrame = performanceManager.borrowMat()
        try {
            if (frame.channels() > 1) {
                Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY)
            } else {
                frame.copyTo(grayFrame)
            }
            val thermalAdjustments = thermalDetector.getCurrentAdjustments()
            val workingFrame = if (thermalAdjustments.imageResolutionScale < 1.0f) {
                val resized = performanceManager.borrowMat()
                val newSize = Size(
                    (grayFrame.cols() * thermalAdjustments.imageResolutionScale).toDouble(),
                    (grayFrame.rows() * thermalAdjustments.imageResolutionScale).toDouble()
                )
                Imgproc.resize(grayFrame, resized, newSize)
                resized
            } else {
                grayFrame
            }
            val sharpness = if (thermalAdjustments.enableAdvancedFilters) {
                calculateSharpnessAdvanced(workingFrame)
            } else {
                calculateSharpnessBasic(workingFrame)
            }
            val brightness = calculateBrightness(workingFrame)
            val contrast = if (thermalAdjustments.enableAdvancedFilters) {
                calculateContrast(workingFrame)
            } else {
                calculateContrastBasic(workingFrame)
            }
            val isOverexposed = detectOverexposure(workingFrame)
            val isUnderexposed = detectUnderexposure(workingFrame)
            val isBlurry = sharpness < sharpnessThreshold
            if (workingFrame != grayFrame) {
                performanceManager.returnMat(workingFrame)
            }
            QualityMetrics(
                sharpness = sharpness,
                brightness = brightness,
                contrast = contrast,
                isBlurry = isBlurry,
                isOverexposed = isOverexposed,
                isUnderexposed = isUnderexposed
            )
        } finally {
            performanceManager.returnMat(grayFrame)
            val processingTime = System.currentTimeMillis() - startTime
            performanceManager.recordFrameProcessingTime(processingTime)
        }
    }
    private fun calculateSharpnessAdvanced(grayFrame: Mat): Float {
        val laplacian = performanceManager.borrowMat()
        val mean = performanceManager.borrowMat()
        val stdDev = performanceManager.borrowMat()
        try {
            Imgproc.Laplacian(grayFrame, laplacian, CvType.CV_64F)
            val mean = MatOfDouble()
            val stdDev = MatOfDouble()
            Core.meanStdDev(laplacian, mean, stdDev)
            val variance = stdDev.get(0, 0)[0]
            return (variance * variance).toFloat()
        } finally {
            performanceManager.returnMat(laplacian)
            performanceManager.returnMat(mean)
            performanceManager.returnMat(stdDev)
        }
    }
    private fun calculateSharpnessBasic(grayFrame: Mat): Float {
        val sobelX = performanceManager.borrowMat()
        val sobelY = performanceManager.borrowMat()
        val magnitude = performanceManager.borrowMat()
        try {
            Imgproc.Sobel(grayFrame, sobelX, CvType.CV_32F, 1, 0, 3)
            Imgproc.Sobel(grayFrame, sobelY, CvType.CV_32F, 0, 1, 3)
            Core.magnitude(sobelX, sobelY, magnitude)
            val mean = Core.mean(magnitude)
            return mean.`val`[0].toFloat()
        } finally {
            performanceManager.returnMat(sobelX)
            performanceManager.returnMat(sobelY)
            performanceManager.returnMat(magnitude)
        }
    }
    private fun calculateBrightness(grayFrame: Mat): Float {
        val meanMat = MatOfDouble()
        val stdDevMat = MatOfDouble()
        try {
            Core.meanStdDev(grayFrame, meanMat, stdDevMat)
            return meanMat.get(0, 0)[0].toFloat()
        } finally {
            meanMat.release()
            stdDevMat.release()
        }
    }
    private fun calculateContrast(grayFrame: Mat): Float {
        val mean = MatOfDouble()
        val stdDev = MatOfDouble()
        try {
            Core.meanStdDev(grayFrame, mean, stdDev)
            return stdDev.get(0, 0)[0].toFloat()
        } finally {
            mean.release()
            stdDev.release()
        }
    }
    private fun calculateContrastBasic(grayFrame: Mat): Float {
        val minMaxLoc = Core.minMaxLoc(grayFrame)
        return (minMaxLoc.maxVal - minMaxLoc.minVal).toFloat()
    }
    private fun detectOverexposure(grayFrame: Mat): Boolean {
        val mask = performanceManager.borrowMat()
        try {
            Core.compare(grayFrame, Scalar(overexposureThreshold), mask, Core.CMP_GT)
            val overexposedPixels = Core.countNonZero(mask)
            val totalPixels = grayFrame.total()
            val overexposedRatio = overexposedPixels.toDouble() / totalPixels
            return overexposedRatio > exposurePixelRatio
        } finally {
            performanceManager.returnMat(mask)
        }
    }
    private fun detectUnderexposure(grayFrame: Mat): Boolean {
        val mask = performanceManager.borrowMat()
        try {
            Core.compare(grayFrame, Scalar(underexposureThreshold), mask, Core.CMP_LT)
            val underexposedPixels = Core.countNonZero(mask)
            val totalPixels = grayFrame.total()
            val underexposedRatio = underexposedPixels.toDouble() / totalPixels
            return underexposedRatio > exposurePixelRatio
        } finally {
            performanceManager.returnMat(mask)
        }
    }
    fun analyzeHistogram(frame: Mat): HistogramAnalysis {
        val hist = Mat()
        val grayFrame = Mat()
        try {
            if (frame.channels() > 1) {
                Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY)
            } else {
                frame.copyTo(grayFrame)
            }
            val histSize = MatOfInt(256)
            val ranges = MatOfFloat(0f, 256f)
            Imgproc.calcHist(
                listOf(grayFrame),
                MatOfInt(0),
                Mat(),
                hist,
                histSize,
                ranges
            )
            val histArray = FloatArray(256)
            hist.get(0, 0, histArray)
            return HistogramAnalysis(
                distribution = histArray,
                peak = findHistogramPeak(histArray),
                isWellDistributed = isHistogramWellDistributed(histArray)
            )
        } finally {
            hist.release()
            grayFrame.release()
        }
    }
    private fun findHistogramPeak(histogram: FloatArray): Int {
        var maxValue = 0f
        var peakIndex = 0
        for (i in histogram.indices) {
            if (histogram[i] > maxValue) {
                maxValue = histogram[i]
                peakIndex = i
            }
        }
        return peakIndex
    }
    private fun isHistogramWellDistributed(histogram: FloatArray): Boolean {
        val totalPixels = histogram.sum()
        val segments = 4
        val segmentSize = histogram.size / segments
        for (i in 0 until segments) {
            val start = i * segmentSize
            val end = minOf((i + 1) * segmentSize, histogram.size)
            val segmentSum = histogram.sliceArray(start until end).sum()
            val segmentRatio = segmentSum / totalPixels
            if (segmentRatio < 0.05f) {
                return false
            }
        }
        return true
    }
    data class HistogramAnalysis(
        val distribution: FloatArray,
        val peak: Int,
        val isWellDistributed: Boolean
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as HistogramAnalysis
            if (!distribution.contentEquals(other.distribution)) return false
            if (peak != other.peak) return false
            if (isWellDistributed != other.isWellDistributed) return false
            return true
        }
        override fun hashCode(): Int {
            var result = distribution.contentHashCode()
            result = 31 * result + peak
            result = 31 * result + isWellDistributed.hashCode()
            return result
        }
    }
}
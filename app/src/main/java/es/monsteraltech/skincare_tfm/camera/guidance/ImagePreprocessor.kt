package es.monsteraltech.skincare_tfm.camera.guidance
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.system.measureTimeMillis
class ImagePreprocessor {
    data class PreprocessingResult(
        val originalBitmap: Bitmap,
        val processedBitmap: Bitmap,
        val appliedFilters: List<String>,
        val processingTime: Long,
        val qualityImprovement: Float = 0f
    ) {
        fun isSuccessful(): Boolean = appliedFilters.isNotEmpty()
        fun getProcessingSummary(): String {
            return buildString {
                append("Procesamiento completado en ${processingTime}ms. ")
                append("Filtros aplicados: ${appliedFilters.joinToString(", ")}. ")
                if (qualityImprovement > 0) {
                    append("Mejora de calidad: ${String.format("%.1f", qualityImprovement * 100)}%")
                }
            }
        }
    }
    data class PreprocessingConfig(
        val enableIlluminationNormalization: Boolean = true,
        val enableContrastEnhancement: Boolean = true,
        val enableNoiseReduction: Boolean = true,
        val enableSharpening: Boolean = true,
        val claheClipLimit: Double = 2.0,
        val claheTileSize: Size = Size(8.0, 8.0),
        val gaussianKernelSize: Size = Size(3.0, 3.0),
        val sharpeningStrength: Double = 0.5
    )
    private val defaultConfig = PreprocessingConfig()
    suspend fun preprocessImage(
        bitmap: Bitmap,
        config: PreprocessingConfig = defaultConfig
    ): PreprocessingResult = withContext(Dispatchers.Default) {
        val appliedFilters = mutableListOf<String>()
        var processedBitmap = bitmap.copy(bitmap.config, false)
        val processingTime = measureTimeMillis {
            try {
                val originalMat = Mat()
                val processedMat = Mat()
                Utils.bitmapToMat(bitmap, originalMat)
                originalMat.copyTo(processedMat)
                if (config.enableIlluminationNormalization) {
                    normalizeIllumination(processedMat, config)
                    appliedFilters.add("Normalización de iluminación")
                }
                if (config.enableContrastEnhancement) {
                    enhanceContrast(processedMat)
                    appliedFilters.add("Mejora de contraste")
                }
                if (config.enableNoiseReduction) {
                    reduceNoise(processedMat)
                    appliedFilters.add("Reducción de ruido")
                }
                if (config.enableSharpening) {
                    sharpenImage(processedMat, config)
                    appliedFilters.add("Enfoque")
                }
                processedBitmap = Bitmap.createBitmap(
                    processedMat.cols(),
                    processedMat.rows(),
                    Bitmap.Config.ARGB_8888
                )
                Utils.matToBitmap(processedMat, processedBitmap)
                originalMat.release()
                processedMat.release()
            } catch (e: Exception) {
                appliedFilters.clear()
                appliedFilters.add("Error - usando imagen original")
            }
        }
        PreprocessingResult(
            originalBitmap = bitmap,
            processedBitmap = processedBitmap,
            appliedFilters = appliedFilters,
            processingTime = processingTime
        )
    }
    private fun normalizeIllumination(mat: Mat, config: PreprocessingConfig) {
        val labMat = Mat()
        Imgproc.cvtColor(mat, labMat, Imgproc.COLOR_BGR2Lab)
        val labChannels = mutableListOf<Mat>()
        Core.split(labMat, labChannels)
        val clahe = Imgproc.createCLAHE(config.claheClipLimit, config.claheTileSize)
        clahe.apply(labChannels[0], labChannels[0])
        Core.merge(labChannels, labMat)
        Imgproc.cvtColor(labMat, mat, Imgproc.COLOR_Lab2BGR)
        labMat.release()
        labChannels.forEach { it.release() }
    }
    private fun enhanceContrast(mat: Mat) {
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
        val mean = MatOfDouble()
        val stdDev = MatOfDouble()
        Core.meanStdDev(grayMat, mean, stdDev)
        val currentContrast = stdDev.get(0, 0)[0]
        val targetContrast = 50.0
        if (currentContrast < targetContrast) {
            val alpha = targetContrast / currentContrast.coerceAtLeast(1.0)
            val beta = 0.0
            mat.convertTo(mat, -1, alpha.coerceIn(1.0, 2.0), beta)
        }
        grayMat.release()
        mean.release()
        stdDev.release()
    }
    private fun reduceNoise(mat: Mat) {
        val denoised = Mat()
        Imgproc.bilateralFilter(
            mat, denoised,
            9,
            75.0,
            75.0
        )
        denoised.copyTo(mat)
        denoised.release()
    }
    private fun sharpenImage(mat: Mat, config: PreprocessingConfig) {
        val sharpeningKernel = Mat(3, 3, CvType.CV_32F)
        val kernelData = floatArrayOf(
            0f, -1f, 0f,
            -1f, 5f, -1f,
            0f, -1f, 0f
        )
        sharpeningKernel.put(0, 0, *kernelData.map { it.toDouble() }.toDoubleArray())
        val sharpened = Mat()
        Imgproc.filter2D(mat, sharpened, -1, sharpeningKernel)
        Core.addWeighted(
            mat, 1.0 - config.sharpeningStrength,
            sharpened, config.sharpeningStrength,
            0.0, mat
        )
        sharpeningKernel.release()
        sharpened.release()
    }
    suspend fun analyzeQualityImprovement(
        originalBitmap: Bitmap,
        processedBitmap: Bitmap
    ): Float = withContext(Dispatchers.Default) {
        try {
            val originalMat = Mat()
            val processedMat = Mat()
            Utils.bitmapToMat(originalBitmap, originalMat)
            Utils.bitmapToMat(processedBitmap, processedMat)
            val originalSharpness = calculateSharpness(originalMat)
            val processedSharpness = calculateSharpness(processedMat)
            val originalContrast = calculateContrast(originalMat)
            val processedContrast = calculateContrast(processedMat)
            val sharpnessImprovement = (processedSharpness - originalSharpness) / originalSharpness.coerceAtLeast(0.1f)
            val contrastImprovement = (processedContrast - originalContrast) / originalContrast.coerceAtLeast(0.1f)
            val overallImprovement = (sharpnessImprovement * 0.6f + contrastImprovement * 0.4f)
                .coerceIn(-1f, 1f)
            originalMat.release()
            processedMat.release()
            overallImprovement
        } catch (e: Exception) {
            0f
        }
    }
    private fun calculateSharpness(mat: Mat): Float {
        val grayMat = Mat()
        val laplacian = Mat()
        val mean = Mat()
        val stdDev = Mat()
        try {
            if (mat.channels() > 1) {
                Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
            } else {
                mat.copyTo(grayMat)
            }
            Imgproc.Laplacian(grayMat, laplacian, CvType.CV_64F)
            val mean = MatOfDouble()
            val stdDev = MatOfDouble()
            Core.meanStdDev(laplacian, mean, stdDev)
            val variance = stdDev.get(0, 0)[0]
            return (variance * variance).toFloat()
        } finally {
            grayMat.release()
            laplacian.release()
            mean.release()
            stdDev.release()
        }
    }
    private fun calculateContrast(mat: Mat): Float {
        val grayMat = Mat()
        val mean = Mat()
        val stdDev = Mat()
        try {
            if (mat.channels() > 1) {
                Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
            } else {
                mat.copyTo(grayMat)
            }
            val mean = MatOfDouble()
            val stdDev = MatOfDouble()
            Core.meanStdDev(grayMat, mean, stdDev)
            return stdDev.get(0, 0)[0].toFloat()
        } finally {
            grayMat.release()
            mean.release()
            stdDev.release()
        }
    }
    suspend fun preprocessForDermatologyAnalysis(bitmap: Bitmap): PreprocessingResult {
        val dermatologyConfig = PreprocessingConfig(
            enableIlluminationNormalization = true,
            enableContrastEnhancement = true,
            enableNoiseReduction = true,
            enableSharpening = true,
            claheClipLimit = 3.0,
            claheTileSize = Size(6.0, 6.0),
            sharpeningStrength = 0.3
        )
        return preprocessImage(bitmap, dermatologyConfig)
    }
    suspend fun preprocessForLowLight(bitmap: Bitmap): PreprocessingResult {
        val lowLightConfig = PreprocessingConfig(
            enableIlluminationNormalization = true,
            enableContrastEnhancement = true,
            enableNoiseReduction = true,
            enableSharpening = false,
            claheClipLimit = 4.0,
            claheTileSize = Size(4.0, 4.0),
            sharpeningStrength = 0.0
        )
        return preprocessImage(bitmap, lowLightConfig)
    }
    suspend fun preprocessForOverexposure(bitmap: Bitmap): PreprocessingResult {
        val overexposureConfig = PreprocessingConfig(
            enableIlluminationNormalization = true,
            enableContrastEnhancement = false,
            enableNoiseReduction = false,
            enableSharpening = true,
            claheClipLimit = 1.5,
            claheTileSize = Size(10.0, 10.0),
            sharpeningStrength = 0.4
        )
        return preprocessImage(bitmap, overexposureConfig)
    }
}
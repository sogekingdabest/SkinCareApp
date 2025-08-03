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

/**
 * Preprocesador de imágenes para mejora automática de calidad antes del análisis.
 * Aplica técnicas de procesamiento de imagen para optimizar la calidad de las capturas
 * de lunares, mejorando contraste, reduciendo ruido y normalizando la iluminación.
 */
class ImagePreprocessor {
    
    /**
     * Resultado del preprocesado de imagen
     */
    data class PreprocessingResult(
        val originalBitmap: Bitmap,
        val processedBitmap: Bitmap,
        val appliedFilters: List<String>,
        val processingTime: Long,
        val qualityImprovement: Float = 0f
    ) {
        /**
         * Indica si el preprocesado fue exitoso
         */
        fun isSuccessful(): Boolean = appliedFilters.isNotEmpty()
        
        /**
         * Obtiene un resumen del procesamiento aplicado
         */
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
    
    /**
     * Configuración de preprocesado
     */
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
    
    /**
     * Preprocesa una imagen aplicando mejoras automáticas de calidad
     */
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
                
                // Convertir bitmap a Mat
                Utils.bitmapToMat(bitmap, originalMat)
                originalMat.copyTo(processedMat)
                
                // Aplicar filtros según configuración
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
                
                // Convertir Mat procesado de vuelta a Bitmap
                processedBitmap = Bitmap.createBitmap(
                    processedMat.cols(), 
                    processedMat.rows(), 
                    Bitmap.Config.ARGB_8888
                )
                Utils.matToBitmap(processedMat, processedBitmap)
                
                // Limpiar recursos
                originalMat.release()
                processedMat.release()
                
            } catch (e: Exception) {
                // En caso de error, usar imagen original
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
    
    /**
     * Normaliza la iluminación usando CLAHE (Contrast Limited Adaptive Histogram Equalization)
     */
    private fun normalizeIllumination(mat: Mat, config: PreprocessingConfig) {
        // Convertir a espacio de color LAB para mejor procesamiento de luminancia
        val labMat = Mat()
        Imgproc.cvtColor(mat, labMat, Imgproc.COLOR_BGR2Lab)
        
        // Separar canales LAB
        val labChannels = mutableListOf<Mat>()
        Core.split(labMat, labChannels)
        
        // Aplicar CLAHE solo al canal L (luminancia)
        val clahe = Imgproc.createCLAHE(config.claheClipLimit, config.claheTileSize)
        clahe.apply(labChannels[0], labChannels[0])
        
        // Recombinar canales
        Core.merge(labChannels, labMat)
        
        // Convertir de vuelta a BGR
        Imgproc.cvtColor(labMat, mat, Imgproc.COLOR_Lab2BGR)
        
        // Limpiar recursos
        labMat.release()
        labChannels.forEach { it.release() }
    }
    
    /**
     * Mejora el contraste de la imagen
     */
    private fun enhanceContrast(mat: Mat) {
        // Convertir a escala de grises para análisis
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
        
        // Calcular estadísticas de la imagen
        val mean = MatOfDouble()
        val stdDev = MatOfDouble()
        Core.meanStdDev(grayMat, mean, stdDev)
        
        val currentContrast = stdDev.get(0, 0)[0]
        val targetContrast = 50.0 // Contraste objetivo
        
        // Solo aplicar mejora si el contraste actual es bajo
        if (currentContrast < targetContrast) {
            val alpha = targetContrast / currentContrast.coerceAtLeast(1.0)
            val beta = 0.0 // Sin cambio de brillo
            
            // Aplicar transformación lineal: new_pixel = alpha * old_pixel + beta
            mat.convertTo(mat, -1, alpha.coerceIn(1.0, 2.0), beta)
        }
        
        // Limpiar recursos
        grayMat.release()
        mean.release()
        stdDev.release()
    }
    
    /**
     * Reduce el ruido de la imagen manteniendo los detalles importantes
     */
    private fun reduceNoise(mat: Mat) {
        // Usar filtro bilateral para reducir ruido preservando bordes
        val denoised = Mat()
        Imgproc.bilateralFilter(
            mat, denoised,
            9,      // Diámetro del filtro
            75.0,   // Sigma color
            75.0    // Sigma space
        )
        
        // Copiar resultado de vuelta
        denoised.copyTo(mat)
        denoised.release()
    }
    
    /**
     * Aplica enfoque (sharpening) para mejorar la definición de la imagen
     */
    private fun sharpenImage(mat: Mat, config: PreprocessingConfig) {
        // Crear kernel de enfoque
        val sharpeningKernel = Mat(3, 3, CvType.CV_32F)
        val kernelData = floatArrayOf(
            0f, -1f, 0f,
            -1f, 5f, -1f,
            0f, -1f, 0f
        )
        sharpeningKernel.put(0, 0, *kernelData.map { it.toDouble() }.toDoubleArray())
        
        // Aplicar filtro con intensidad controlada
        val sharpened = Mat()
        Imgproc.filter2D(mat, sharpened, -1, sharpeningKernel)
        
        // Mezclar imagen original con la enfocada según la intensidad configurada
        Core.addWeighted(
            mat, 1.0 - config.sharpeningStrength,
            sharpened, config.sharpeningStrength,
            0.0, mat
        )
        
        // Limpiar recursos
        sharpeningKernel.release()
        sharpened.release()
    }
    
    /**
     * Analiza la calidad de la imagen antes y después del preprocesado
     */
    suspend fun analyzeQualityImprovement(
        originalBitmap: Bitmap,
        processedBitmap: Bitmap
    ): Float = withContext(Dispatchers.Default) {
        
        try {
            val originalMat = Mat()
            val processedMat = Mat()
            
            Utils.bitmapToMat(originalBitmap, originalMat)
            Utils.bitmapToMat(processedBitmap, processedMat)
            
            // Calcular métricas de calidad
            val originalSharpness = calculateSharpness(originalMat)
            val processedSharpness = calculateSharpness(processedMat)
            
            val originalContrast = calculateContrast(originalMat)
            val processedContrast = calculateContrast(processedMat)
            
            // Calcular mejora general (promedio ponderado)
            val sharpnessImprovement = (processedSharpness - originalSharpness) / originalSharpness.coerceAtLeast(0.1f)
            val contrastImprovement = (processedContrast - originalContrast) / originalContrast.coerceAtLeast(0.1f)
            
            val overallImprovement = (sharpnessImprovement * 0.6f + contrastImprovement * 0.4f)
                .coerceIn(-1f, 1f)
            
            // Limpiar recursos
            originalMat.release()
            processedMat.release()
            
            overallImprovement
            
        } catch (e: Exception) {
            0f // Sin mejora medible en caso de error
        }
    }
    
    /**
     * Calcula la nitidez de una imagen usando el filtro Laplaciano
     */
    private fun calculateSharpness(mat: Mat): Float {
        val grayMat = Mat()
        val laplacian = Mat()
        val mean = Mat()
        val stdDev = Mat()
        
        try {
            // Convertir a escala de grises
            if (mat.channels() > 1) {
                Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
            } else {
                mat.copyTo(grayMat)
            }
            
            // Aplicar filtro Laplaciano
            Imgproc.Laplacian(grayMat, laplacian, CvType.CV_64F)
            
            // Calcular varianza como medida de nitidez
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
    
    /**
     * Calcula el contraste de una imagen
     */
    private fun calculateContrast(mat: Mat): Float {
        val grayMat = Mat()
        val mean = Mat()
        val stdDev = Mat()
        
        try {
            // Convertir a escala de grises
            if (mat.channels() > 1) {
                Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
            } else {
                mat.copyTo(grayMat)
            }
            
            // Calcular desviación estándar como medida de contraste
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
    
    /**
     * Preprocesa una imagen con configuración optimizada para análisis dermatológico
     */
    suspend fun preprocessForDermatologyAnalysis(bitmap: Bitmap): PreprocessingResult {
        val dermatologyConfig = PreprocessingConfig(
            enableIlluminationNormalization = true,
            enableContrastEnhancement = true,
            enableNoiseReduction = true,
            enableSharpening = true,
            claheClipLimit = 3.0, // Más agresivo para imágenes médicas
            claheTileSize = Size(6.0, 6.0), // Tiles más pequeños para mejor adaptación local
            sharpeningStrength = 0.3 // Enfoque moderado para preservar textura natural
        )
        
        return preprocessImage(bitmap, dermatologyConfig)
    }
    
    /**
     * Preprocesa una imagen con configuración optimizada para condiciones de poca luz
     */
    suspend fun preprocessForLowLight(bitmap: Bitmap): PreprocessingResult {
        val lowLightConfig = PreprocessingConfig(
            enableIlluminationNormalization = true,
            enableContrastEnhancement = true,
            enableNoiseReduction = true,
            enableSharpening = false, // Evitar enfoque en condiciones de ruido alto
            claheClipLimit = 4.0, // Más agresivo para iluminación pobre
            claheTileSize = Size(4.0, 4.0), // Tiles más pequeños para mejor adaptación
            sharpeningStrength = 0.0
        )
        
        return preprocessImage(bitmap, lowLightConfig)
    }
    
    /**
     * Preprocesa una imagen con configuración optimizada para imágenes sobreexpuestas
     */
    suspend fun preprocessForOverexposure(bitmap: Bitmap): PreprocessingResult {
        val overexposureConfig = PreprocessingConfig(
            enableIlluminationNormalization = true,
            enableContrastEnhancement = false, // Evitar aumentar contraste en imágenes ya brillantes
            enableNoiseReduction = false,
            enableSharpening = true,
            claheClipLimit = 1.5, // Menos agresivo para evitar artefactos
            claheTileSize = Size(10.0, 10.0), // Tiles más grandes para suavizar
            sharpeningStrength = 0.4
        )
        
        return preprocessImage(bitmap, overexposureConfig)
    }
}
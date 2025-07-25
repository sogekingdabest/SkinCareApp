package es.monsteraltech.skincare_tfm.camera.guidance

import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Optimizador de Region of Interest (ROI) para mejorar el rendimiento
 * del procesamiento de imágenes enfocándose solo en áreas relevantes
 */
class ROIOptimizer {
    
    data class ROIConfig(
        val centerRatio: Float = 0.5f,      // Ratio del centro de la imagen
        val adaptiveROI: Boolean = true,     // ROI adaptativo basado en detecciones previas
        val minROISize: Float = 0.3f,       // Tamaño mínimo del ROI
        val maxROISize: Float = 0.8f,       // Tamaño máximo del ROI
        val expansionFactor: Float = 1.2f    // Factor de expansión alrededor de detecciones
    )
    
    data class ROIResult(
        val roi: Rect,
        val scaleFactor: Float,
        val offsetX: Int,
        val offsetY: Int
    )
    
    private var config = ROIConfig()
    private var lastDetectionCenter: org.opencv.core.Point? = null
    private var detectionHistory = mutableListOf<org.opencv.core.Point>()
    private val maxHistorySize = 10
    
    /**
     * Actualiza la configuración del ROI
     */
    fun updateConfig(newConfig: ROIConfig) {
        config = newConfig
    }
    
    /**
     * Calcula el ROI óptimo para una imagen
     */
    fun calculateROI(imageSize: Size, roiScale: Float = 0.7f): ROIResult {
        val width = imageSize.width.toInt()
        val height = imageSize.height.toInt()
        
        // Calcular tamaño del ROI
        val roiWidth = (width * roiScale).roundToInt()
        val roiHeight = (height * roiScale).roundToInt()
        
        // Determinar centro del ROI
        val centerX: Int
        val centerY: Int
        
        if (config.adaptiveROI && detectionHistory.isNotEmpty()) {
            // Usar promedio de detecciones recientes
            val avgCenter = calculateAverageDetectionCenter()
            centerX = avgCenter.x.roundToInt()
            centerY = avgCenter.y.roundToInt()
        } else {
            // Usar centro de la imagen
            centerX = width / 2
            centerY = height / 2
        }
        
        // Calcular coordenadas del ROI
        val roiX = max(0, centerX - roiWidth / 2)
        val roiY = max(0, centerY - roiHeight / 2)
        val adjustedWidth = min(roiWidth, width - roiX)
        val adjustedHeight = min(roiHeight, height - roiY)
        
        val roi = Rect(roiX, roiY, adjustedWidth, adjustedHeight)
        
        return ROIResult(
            roi = roi,
            scaleFactor = roiScale,
            offsetX = roiX,
            offsetY = roiY
        )
    }
    
    /**
     * Extrae la región de interés de una imagen
     */
    fun extractROI(image: Mat, roiResult: ROIResult): Mat {
        return Mat(image, roiResult.roi)
    }
    
    /**
     * Convierte coordenadas del ROI a coordenadas de imagen completa
     */
    fun roiToImageCoordinates(
        roiPoint: org.opencv.core.Point,
        roiResult: ROIResult
    ): org.opencv.core.Point {
        return org.opencv.core.Point(
            roiPoint.x + roiResult.offsetX,
            roiPoint.y + roiResult.offsetY
        )
    }
    
    /**
     * Convierte coordenadas de imagen completa a coordenadas del ROI
     */
    fun imageToROICoordinates(
        imagePoint: org.opencv.core.Point,
        roiResult: ROIResult
    ): org.opencv.core.Point {
        return org.opencv.core.Point(
            imagePoint.x - roiResult.offsetX,
            imagePoint.y - roiResult.offsetY
        )
    }
    
    /**
     * Actualiza el historial de detecciones para ROI adaptativo
     */
    fun updateDetectionHistory(detectionCenter: org.opencv.core.Point) {
        lastDetectionCenter = detectionCenter
        detectionHistory.add(detectionCenter)
        
        // Mantener solo las detecciones más recientes
        if (detectionHistory.size > maxHistorySize) {
            detectionHistory.removeAt(0)
        }
    }
    
    /**
     * Calcula el centro promedio de las detecciones recientes
     */
    private fun calculateAverageDetectionCenter(): org.opencv.core.Point {
        if (detectionHistory.isEmpty()) {
            return org.opencv.core.Point(0.0, 0.0)
        }
        
        val avgX = detectionHistory.map { it.x }.average()
        val avgY = detectionHistory.map { it.y }.average()
        
        return org.opencv.core.Point(avgX, avgY)
    }
    
    /**
     * Calcula ROI expandido alrededor de una detección
     */
    fun calculateExpandedROI(
        imageSize: Size,
        detectionRect: Rect,
        expansionFactor: Float = config.expansionFactor
    ): ROIResult {
        val width = imageSize.width.toInt()
        val height = imageSize.height.toInt()
        
        // Calcular centro de la detección
        val centerX = detectionRect.x + detectionRect.width / 2
        val centerY = detectionRect.y + detectionRect.height / 2
        
        // Calcular tamaño expandido
        val expandedWidth = (detectionRect.width * expansionFactor).roundToInt()
        val expandedHeight = (detectionRect.height * expansionFactor).roundToInt()
        
        // Asegurar que el ROI esté dentro de los límites de la imagen
        val roiX = max(0, centerX - expandedWidth / 2)
        val roiY = max(0, centerY - expandedHeight / 2)
        val adjustedWidth = min(expandedWidth, width - roiX)
        val adjustedHeight = min(expandedHeight, height - roiY)
        
        val roi = Rect(roiX, roiY, adjustedWidth, adjustedHeight)
        val scaleFactor = min(
            adjustedWidth.toFloat() / width,
            adjustedHeight.toFloat() / height
        )
        
        return ROIResult(
            roi = roi,
            scaleFactor = scaleFactor,
            offsetX = roiX,
            offsetY = roiY
        )
    }
    
    /**
     * Verifica si un punto está dentro del ROI
     */
    fun isPointInROI(point: org.opencv.core.Point, roiResult: ROIResult): Boolean {
        val roi = roiResult.roi
        return point.x >= roi.x && 
               point.x < roi.x + roi.width &&
               point.y >= roi.y && 
               point.y < roi.y + roi.height
    }
    
    /**
     * Calcula el área del ROI como porcentaje de la imagen total
     */
    fun calculateROIAreaRatio(imageSize: Size, roiResult: ROIResult): Float {
        val imageArea = imageSize.width * imageSize.height
        val roiArea = roiResult.roi.width * roiResult.roi.height
        return roiArea.toFloat() / imageArea.toFloat()
    }
    
    /**
     * Optimiza el ROI basado en el rendimiento actual
     */
    fun optimizeROIForPerformance(
        imageSize: Size,
        currentProcessingTime: Long,
        targetProcessingTime: Long
    ): ROIResult {
        val performanceRatio = currentProcessingTime.toFloat() / targetProcessingTime.toFloat()
        
        // Ajustar tamaño del ROI basado en rendimiento
        val adjustedScale = when {
            performanceRatio > 1.5f -> config.minROISize // Muy lento, ROI mínimo
            performanceRatio > 1.2f -> 0.5f              // Lento, ROI reducido
            performanceRatio < 0.8f -> config.maxROISize // Rápido, ROI máximo
            else -> 0.7f                                  // Normal, ROI estándar
        }
        
        return calculateROI(imageSize, adjustedScale)
    }
    
    /**
     * Limpia el historial de detecciones
     */
    fun clearHistory() {
        detectionHistory.clear()
        lastDetectionCenter = null
    }
    
    /**
     * Obtiene estadísticas del ROI
     */
    fun getROIStats(): Map<String, Any> {
        return mapOf(
            "historySize" to detectionHistory.size,
            "hasLastDetection" to (lastDetectionCenter != null),
            "adaptiveROI" to config.adaptiveROI,
            "minROISize" to config.minROISize,
            "maxROISize" to config.maxROISize,
            "expansionFactor" to config.expansionFactor
        )
    }
}
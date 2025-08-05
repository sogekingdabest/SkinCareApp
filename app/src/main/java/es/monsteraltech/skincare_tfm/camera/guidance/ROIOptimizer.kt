package es.monsteraltech.skincare_tfm.camera.guidance
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
class ROIOptimizer {
    data class ROIConfig(
        val centerRatio: Float = 0.5f,
        val adaptiveROI: Boolean = true,
        val minROISize: Float = 0.3f,
        val maxROISize: Float = 0.8f,
        val expansionFactor: Float = 1.2f
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
    fun updateConfig(newConfig: ROIConfig) {
        config = newConfig
    }
    fun calculateROI(imageSize: Size, roiScale: Float = 0.7f): ROIResult {
        val width = imageSize.width.toInt()
        val height = imageSize.height.toInt()
        val roiWidth = (width * roiScale).roundToInt()
        val roiHeight = (height * roiScale).roundToInt()
        val centerX: Int
        val centerY: Int
        if (config.adaptiveROI && detectionHistory.isNotEmpty()) {
            val avgCenter = calculateAverageDetectionCenter()
            centerX = avgCenter.x.roundToInt()
            centerY = avgCenter.y.roundToInt()
        } else {
            centerX = width / 2
            centerY = height / 2
        }
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
    fun extractROI(image: Mat, roiResult: ROIResult): Mat {
        return Mat(image, roiResult.roi)
    }
    fun roiToImageCoordinates(
        roiPoint: org.opencv.core.Point,
        roiResult: ROIResult
    ): org.opencv.core.Point {
        return org.opencv.core.Point(
            roiPoint.x + roiResult.offsetX,
            roiPoint.y + roiResult.offsetY
        )
    }
    fun imageToROICoordinates(
        imagePoint: org.opencv.core.Point,
        roiResult: ROIResult
    ): org.opencv.core.Point {
        return org.opencv.core.Point(
            imagePoint.x - roiResult.offsetX,
            imagePoint.y - roiResult.offsetY
        )
    }
    fun updateDetectionHistory(detectionCenter: org.opencv.core.Point) {
        lastDetectionCenter = detectionCenter
        detectionHistory.add(detectionCenter)
        if (detectionHistory.size > maxHistorySize) {
            detectionHistory.removeAt(0)
        }
    }
    private fun calculateAverageDetectionCenter(): org.opencv.core.Point {
        if (detectionHistory.isEmpty()) {
            return org.opencv.core.Point(0.0, 0.0)
        }
        val avgX = detectionHistory.map { it.x }.average()
        val avgY = detectionHistory.map { it.y }.average()
        return org.opencv.core.Point(avgX, avgY)
    }
    fun calculateExpandedROI(
        imageSize: Size,
        detectionRect: Rect,
        expansionFactor: Float = config.expansionFactor
    ): ROIResult {
        val width = imageSize.width.toInt()
        val height = imageSize.height.toInt()
        val centerX = detectionRect.x + detectionRect.width / 2
        val centerY = detectionRect.y + detectionRect.height / 2
        val expandedWidth = (detectionRect.width * expansionFactor).roundToInt()
        val expandedHeight = (detectionRect.height * expansionFactor).roundToInt()
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
    fun isPointInROI(point: org.opencv.core.Point, roiResult: ROIResult): Boolean {
        val roi = roiResult.roi
        return point.x >= roi.x &&
               point.x < roi.x + roi.width &&
               point.y >= roi.y &&
               point.y < roi.y + roi.height
    }
    fun calculateROIAreaRatio(imageSize: Size, roiResult: ROIResult): Float {
        val imageArea = imageSize.width * imageSize.height
        val roiArea = roiResult.roi.width * roiResult.roi.height
        return roiArea.toFloat() / imageArea.toFloat()
    }
    fun optimizeROIForPerformance(
        imageSize: Size,
        currentProcessingTime: Long,
        targetProcessingTime: Long
    ): ROIResult {
        val performanceRatio = currentProcessingTime.toFloat() / targetProcessingTime.toFloat()
        val adjustedScale = when {
            performanceRatio > 1.5f -> config.minROISize
            performanceRatio > 1.2f -> 0.5f
            performanceRatio < 0.8f -> config.maxROISize
            else -> 0.7f
        }
        return calculateROI(imageSize, adjustedScale)
    }
    fun clearHistory() {
        detectionHistory.clear()
        lastDetectionCenter = null
    }
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
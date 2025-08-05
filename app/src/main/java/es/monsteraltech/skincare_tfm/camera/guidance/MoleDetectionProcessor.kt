package es.monsteraltech.skincare_tfm.camera.guidance
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
class MoleDetectionProcessor(
    private val context: android.content.Context,
    private val performanceManager: PerformanceManager,
    private val roiOptimizer: ROIOptimizer,
    private val thermalDetector: ThermalStateDetector
) {
    companion object {
        private const val TAG = "MoleDetectionProcessor"
        private const val MIN_MOLE_SIZE = 50
        private const val MAX_MOLE_SIZE = 500
        private const val CONFIDENCE_THRESHOLD = 0.7f
        private const val BLUR_SIZE = 5
        private const val MORPH_SIZE = 3
        private const val CANNY_LOW = 50.0
        private const val CANNY_HIGH = 150.0
    }
    data class DetectionConfig(
        val minMoleSize: Int = MIN_MOLE_SIZE,
        val maxMoleSize: Int = MAX_MOLE_SIZE,
        val confidenceThreshold: Float = CONFIDENCE_THRESHOLD,
        val colorThreshold: Scalar = Scalar(20.0, 50.0, 50.0),
        val enableMultiMethod: Boolean = true,
        val enableColorFiltering: Boolean = true
    )
    data class MoleDetection(
        val boundingBox: Rect,
        val centerPoint: Point,
        val confidence: Float,
        val area: Double,
        val contour: MatOfPoint,
        val method: String
    )
    private val config = DetectionConfig()
    suspend fun detectMole(frame: Mat): MoleDetection? = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        try {
            Log.d(TAG, "Iniciando detección de lunar en frame ${frame.cols()}x${frame.rows()}")
            if (frame.empty() || frame.cols() < 100 || frame.rows() < 100) {
                Log.w(TAG, "Frame inválido o muy pequeño")
                return@withContext null
            }
            val thermalAdjustments = thermalDetector.getCurrentAdjustments()
            val workingFrame = if (thermalAdjustments.enableROI) {
                val imageSize = Size(frame.cols().toDouble(), frame.rows().toDouble())
                val roiResult = roiOptimizer.calculateROI(imageSize, thermalAdjustments.roiScale)
                roiOptimizer.extractROI(frame, roiResult)
            } else {
                frame
            }
            val processedFrame = preprocessFrameOptimized(workingFrame)
            var detection = detectMolePrimary(processedFrame)
            if (detection == null && config.enableMultiMethod && thermalAdjustments.enableAdvancedFilters) {
                Log.d(TAG, "Método principal falló, intentando método alternativo")
                detection = detectMoleAlternative(processedFrame)
            }
            if (thermalAdjustments.enableROI && detection != null && workingFrame != frame) {
                val imageSize = Size(frame.cols().toDouble(), frame.rows().toDouble())
                val roiResult = roiOptimizer.calculateROI(imageSize, thermalAdjustments.roiScale)
                detection = adjustDetectionForROI(detection, roiResult)
            }
            if (workingFrame != frame) {
                workingFrame.release()
            }
            processedFrame.release()
            detection?.let {
                Log.d(TAG, "Lunar detectado: confianza=${it.confidence}, área=${it.area}, método=${it.method}")
                roiOptimizer.updateDetectionHistory(it.centerPoint)
            } ?: Log.d(TAG, "No se detectó lunar en el frame")
            detection
        } catch (e: Exception) {
            Log.e(TAG, "Error durante detección de lunar", e)
            null
        } finally {
            val processingTime = System.currentTimeMillis() - startTime
            performanceManager.recordFrameProcessingTime(processingTime)
        }
    }
    private fun preprocessFrameOptimized(frame: Mat): Mat {
        val processed = performanceManager.borrowMat()
        frame.copyTo(processed)
        Log.d(TAG, "Preprocesado desactivado: frame original usado")
        return processed
    }
    private fun detectMolePrimary(frame: Mat): MoleDetection? {
        var mask: Mat? = null
        var contours: List<MatOfPoint>?
        try {
            val hsv = Mat()
            if (frame.channels() == 3) {
                Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV)
            } else if (frame.channels() == 1) {
                Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_GRAY2BGR)
                Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_BGR2HSV)
            } else {
                frame.copyTo(hsv)
            }
            mask = Mat()
            if (frame.channels() == 1) {
                Core.inRange(frame, Scalar(0.0), Scalar(90.0), mask)
            } else {
                val lowerBound = Scalar(0.0, 10.0, 0.0)
                val upperBound = Scalar(180.0, 255.0, 200.0)
                Core.inRange(hsv, lowerBound, upperBound, mask)
            }
            val kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE,
                Size(MORPH_SIZE.toDouble(), MORPH_SIZE.toDouble())
            )
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
            contours = findMoleContours(mask)
            val bestContour = contours.firstOrNull { validateMoleCandidate(it) }
            hsv.release()
            kernel.release()
            return bestContour?.let { createDetectionResult(it, "primary_color") }
        } catch (e: Exception) {
            Log.e(TAG, "Error en detección primaria", e)
            return null
        } finally {
            mask?.release()
        }
    }
    private fun detectMoleAlternative(frame: Mat): MoleDetection? {
        var gray: Mat? = null
        var binary: Mat? = null
        var markers: Mat? = null
        try {
            gray = Mat()
            if (frame.channels() == 3) {
                Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY)
            } else if (frame.channels() == 1) {
                frame.copyTo(gray)
            } else {
                frame.copyTo(gray)
            }
            binary = Mat()
            Imgproc.adaptiveThreshold(
                gray, binary, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                11, 2.0
            )
            val kernel = Mat.ones(3, 3, CvType.CV_8U)
            Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel)
            val distTransform = Mat()
            Imgproc.distanceTransform(binary, distTransform, Imgproc.DIST_L2, 5)
            val localMaxima = Mat()
            val maxVal = Core.minMaxLoc(distTransform).maxVal
            Imgproc.threshold(distTransform, localMaxima, 0.4 * maxVal, 255.0, Imgproc.THRESH_BINARY)
            localMaxima.convertTo(localMaxima, CvType.CV_8U)
            markers = Mat()
            Imgproc.connectedComponents(localMaxima, markers)
            Imgproc.watershed(frame, markers)
            val watershedMask = Mat()
            Core.compare(markers, Scalar(1.0), watershedMask, Core.CMP_GT)
            watershedMask.convertTo(watershedMask, CvType.CV_8U)
            val contours = findMoleContours(watershedMask)
            val bestContour = contours.firstOrNull { validateMoleCandidate(it) }
            kernel.release()
            distTransform.release()
            localMaxima.release()
            watershedMask.release()
            return bestContour?.let { createDetectionResult(it, "alternative_watershed") }
        } catch (e: Exception) {
            Log.e(TAG, "Error en detección alternativa", e)
            return null
        } finally {
            gray?.release()
            binary?.release()
            markers?.release()
        }
    }
    private fun findMoleContours(mask: Mat): List<MatOfPoint> {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        try {
            Imgproc.findContours(
                mask, contours, hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            contours.sortByDescending { Imgproc.contourArea(it) }
            Log.d(TAG, "Encontrados ${contours.size} contornos candidatos")
        } catch (e: Exception) {
            Log.e(TAG, "Error encontrando contornos", e)
        } finally {
            hierarchy.release()
        }
        return contours
    }
    private fun validateMoleCandidate(contour: MatOfPoint): Boolean {
        try {
            val area = Imgproc.contourArea(contour)
            if (area < 10 || area > 2000) {
                return false
            }
            val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            if (perimeter <= 0) return false
            val compactness = 4 * Math.PI * area / (perimeter * perimeter)
            if (compactness < 0.1) {
                return false
            }
            val hull = MatOfInt()
            Imgproc.convexHull(contour, hull)
            val hullPoints = hull.toArray().map { contour.toList()[it] }.toTypedArray()
            val hullArea = Imgproc.contourArea(MatOfPoint(*hullPoints))
            val solidity = if (hullArea > 0) area / hullArea else 0.0
            hull.release()
            if (solidity < 0.3) {
                return false
            }
            val boundingRect = Imgproc.boundingRect(contour)
            val aspectRatio = boundingRect.width.toDouble() / boundingRect.height
            if (aspectRatio > 3.0 || aspectRatio < 0.15) {
                return false
            }
            Log.d(TAG, "Contorno validado: área=$area, compacidad=$compactness, solidez=$solidity, aspecto=$aspectRatio (PERMISIVO)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error validando contorno", e)
            return false
        }
    }
    private fun createDetectionResult(contour: MatOfPoint, method: String): MoleDetection {
        val boundingBox = Imgproc.boundingRect(contour)
        val moments = Imgproc.moments(contour)
        val centerX = moments.m10 / moments.m00
        val centerY = moments.m01 / moments.m00
        val centerPoint = Point(centerX, centerY)
        val area = Imgproc.contourArea(contour)
        val confidence = calculateConfidence(contour, area)
        return MoleDetection(
            boundingBox = boundingBox,
            centerPoint = centerPoint,
            confidence = confidence,
            area = area,
            contour = contour,
            method = method
        )
    }
    private fun calculateConfidence(contour: MatOfPoint, area: Double): Float {
        try {
            var confidence = 0.5f
            val areaFactor = when {
                area in 100.0..300.0 -> 0.3f
                area in 80.0..400.0 -> 0.2f
                else -> 0.1f
            }
            confidence += areaFactor
            val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            if (perimeter > 0) {
                val compactness = (4 * Math.PI * area / (perimeter * perimeter)).toFloat()
                confidence += (compactness * 0.2f).coerceAtMost(0.2f)
            }
            val hull = MatOfInt()
            Imgproc.convexHull(contour, hull)
            val hullPoints = hull.toArray().map { contour.toList()[it] }.toTypedArray()
            val hullArea = Imgproc.contourArea(MatOfPoint(*hullPoints))
            if (hullArea > 0) {
                val solidity = (area / hullArea).toFloat()
                confidence += (solidity * 0.1f).coerceAtMost(0.1f)
            }
            hull.release()
            return confidence.coerceIn(0f, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando confianza", e)
            return 0.5f
        }
    }
    private fun adjustDetectionForROI(detection: MoleDetection, roiResult: ROIOptimizer.ROIResult): MoleDetection {
        val adjustedCenter = roiOptimizer.roiToImageCoordinates(detection.centerPoint, roiResult)
        val adjustedBoundingBox = Rect(
            detection.boundingBox.x + roiResult.offsetX,
            detection.boundingBox.y + roiResult.offsetY,
            detection.boundingBox.width,
            detection.boundingBox.height
        )
        return detection.copy(
            boundingBox = adjustedBoundingBox,
            centerPoint = adjustedCenter
        )
    }
}
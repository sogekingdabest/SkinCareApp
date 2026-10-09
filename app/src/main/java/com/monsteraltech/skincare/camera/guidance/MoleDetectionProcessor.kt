package com.monsteraltech.skincare.camera.guidance

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.min

/**
 * Detecta el lunar más plausible en la zona central del frame.
 *
 * En lugar de umbrales de gris fijos (que fallan según el tono de piel y la
 * iluminación), segmenta con umbral de Otsu sobre la región central y valida
 * cada candidato por contraste real con la piel circundante, circularidad y
 * cercanía al centro. La confianza devuelta es un 0..1 con significado, apto
 * para filtrar con [CaptureGuidanceConfig.minMoleConfidence].
 */
class MoleDetectionProcessor(
    private val centerToleranceFraction: Float = 0.15f
) {
    companion object {
        private const val TAG = "MoleDetectionProcessor"
        // Lado del ROI central analizado, como fracción del lado menor del frame.
        private const val ROI_FRACTION = 0.5
        // Área admisible del candidato, relativa al área del ROI.
        private const val MIN_AREA_FRACTION = 0.0005
        private const val MAX_AREA_FRACTION = 0.30
        // Contraste mínimo (niveles de gris) entre lunar y piel para aceptar el candidato.
        private const val MIN_CONTRAST = 10.0
        // Contraste a partir del cual la puntuación de contraste satura en 1.0.
        private const val CONTRAST_NORMALIZATION = 55.0
        private const val BLUR_KERNEL_SIZE = 5.0
        private const val MIN_FRAME_SIZE = 100
    }

    data class MoleDetection(
        /** Centro del lunar en coordenadas del frame completo. */
        val centerPoint: Point,
        val confidence: Float,
        val area: Double,
        val isInCenter: Boolean
    )

    suspend fun detectMoleInCenter(frame: Mat): MoleDetection? = withContext(Dispatchers.Default) {
        try {
            if (frame.empty() || frame.cols() < MIN_FRAME_SIZE || frame.rows() < MIN_FRAME_SIZE) {
                return@withContext null
            }
            val ownsGray = frame.channels() != 1
            val gray = if (ownsGray) {
                Mat().also { Imgproc.cvtColor(frame, it, Imgproc.COLOR_BGR2GRAY) }
            } else {
                frame
            }
            try {
                findBestCandidate(gray)
            } finally {
                if (ownsGray) gray.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error durante detección de lunar", e)
            null
        }
    }

    private fun findBestCandidate(gray: Mat): MoleDetection? {
        val minSide = min(gray.cols(), gray.rows())
        val roiSide = (minSide * ROI_FRACTION).toInt()
        val roiX = (gray.cols() - roiSide) / 2
        val roiY = (gray.rows() - roiSide) / 2
        val roi = Mat(gray, Rect(roiX, roiY, roiSide, roiSide))

        val blurred = Mat()
        val mask = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()
        try {
            Imgproc.GaussianBlur(roi, blurred, Size(BLUR_KERNEL_SIZE, BLUR_KERNEL_SIZE), 0.0)

            // Umbral adaptativo: Otsu corta según el histograma real del frame,
            // funcionando en distintos tonos de piel y condiciones de luz.
            Imgproc.threshold(blurred, mask, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)

            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            val roiArea = roiSide.toDouble() * roiSide
            val minArea = roiArea * MIN_AREA_FRACTION
            val maxArea = roiArea * MAX_AREA_FRACTION
            val roiCenter = Point(roiSide / 2.0, roiSide / 2.0)
            val centerTolerancePx = minSide * centerToleranceFraction

            var best: MoleDetection? = null
            for (contour in contours) {
                val candidate = evaluateCandidate(
                    contour, blurred, roiSide, roiCenter, minArea, maxArea, centerTolerancePx
                ) ?: continue
                if (best == null || candidate.confidence > best.confidence) {
                    // Trasladar del ROI a coordenadas del frame completo.
                    best = candidate.copy(
                        centerPoint = Point(candidate.centerPoint.x + roiX, candidate.centerPoint.y + roiY)
                    )
                }
            }
            return best
        } finally {
            blurred.release()
            mask.release()
            kernel.release()
            hierarchy.release()
            contours.forEach { it.release() }
            roi.release()
        }
    }

    private fun evaluateCandidate(
        contour: MatOfPoint,
        blurredRoi: Mat,
        roiSide: Int,
        roiCenter: Point,
        minArea: Double,
        maxArea: Double,
        centerTolerancePx: Float
    ): MoleDetection? {
        val area = Imgproc.contourArea(contour)
        if (area < minArea || area > maxArea) return null

        // Manchas pegadas al borde del ROI suelen ser sombras o bordes del encuadre.
        val bounds = Imgproc.boundingRect(contour)
        val touchesBorder = bounds.x <= 1 || bounds.y <= 1 ||
                bounds.x + bounds.width >= roiSide - 1 ||
                bounds.y + bounds.height >= roiSide - 1
        if (touchesBorder) return null

        val moments = Imgproc.moments(contour)
        if (moments.m00 == 0.0) return null
        val cx = moments.m10 / moments.m00
        val cy = moments.m01 / moments.m00
        val distanceToCenter = hypot(cx - roiCenter.x, cy - roiCenter.y)
        if (distanceToCenter > centerTolerancePx * 2) return null

        // Contraste real entre el interior del candidato y la piel circundante.
        val contrast = measureContrast(contour, blurredRoi, roiSide)
        if (contrast < MIN_CONTRAST) return null

        val circularity = calculateCircularity(contour, area)

        val contrastScore = (contrast / CONTRAST_NORMALIZATION).coerceIn(0.0, 1.0)
        val centerScore = (1.0 - distanceToCenter / (centerTolerancePx * 2)).coerceIn(0.0, 1.0)
        val confidence = (0.5 * contrastScore + 0.3 * circularity + 0.2 * centerScore).toFloat()

        return MoleDetection(
            centerPoint = Point(cx, cy),
            confidence = confidence,
            area = area,
            isInCenter = distanceToCenter <= centerTolerancePx
        )
    }

    private fun measureContrast(contour: MatOfPoint, blurredRoi: Mat, roiSide: Int): Double {
        val contourMask = Mat.zeros(roiSide, roiSide, CvType.CV_8UC1)
        val inverseMask = Mat()
        try {
            Imgproc.drawContours(contourMask, listOf(contour), -1, Scalar(255.0), Core.FILLED)
            Core.bitwise_not(contourMask, inverseMask)
            val insideMean = Core.mean(blurredRoi, contourMask).`val`[0]
            val outsideMean = Core.mean(blurredRoi, inverseMask).`val`[0]
            // El lunar debe ser más oscuro que la piel que lo rodea.
            return outsideMean - insideMean
        } finally {
            contourMask.release()
            inverseMask.release()
        }
    }

    private fun calculateCircularity(contour: MatOfPoint, area: Double): Double {
        val points = MatOfPoint2f(*contour.toArray())
        try {
            val perimeter = Imgproc.arcLength(points, true)
            if (perimeter <= 0.0) return 0.0
            return (4.0 * PI * area / (perimeter * perimeter)).coerceIn(0.0, 1.0)
        } finally {
            points.release()
        }
    }
}

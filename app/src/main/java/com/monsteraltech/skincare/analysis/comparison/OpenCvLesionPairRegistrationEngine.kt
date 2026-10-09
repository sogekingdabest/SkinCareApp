package com.monsteraltech.skincare.analysis.comparison

import android.graphics.Bitmap
import com.monsteraltech.skincare.body.mole.domain.model.LesionRegistrationMetrics
import com.monsteraltech.skincare.di.DefaultDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt
import javax.inject.Inject

/**
 * Segmenta cada captura y registra la máscara base sobre la actual mediante
 * una transformación de similitud (traslación, escala y rotación).
 */
class OpenCvLesionPairRegistrationEngine @Inject constructor(
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) : LesionPairRegistrationEngine {

    override suspend fun register(
        baselineBitmap: Bitmap,
        currentBitmap: Bitmap
    ): LesionPairRegistrationResult = withContext(dispatcher) {
        val baselineImage = toProcessingImage(baselineBitmap)
        val currentImage = toProcessingImage(currentBitmap)
        var baselineSegmentation: Segmentation? = null
        var currentSegmentation: Segmentation? = null
        try {
            baselineSegmentation = segment(baselineImage)
                ?: return@withContext LesionPairRegistrationResult.Failed(
                    LesionPairRegistrationResult.FailureStage.BASELINE_SEGMENTATION,
                    "No se encontró una lesión central válida"
                )
            currentSegmentation = segment(currentImage)
                ?: return@withContext LesionPairRegistrationResult.Failed(
                    LesionPairRegistrationResult.FailureStage.CURRENT_SEGMENTATION,
                    "No se encontró una lesión central válida"
                )

            registerSegmentations(
                baseline = baselineSegmentation,
                current = currentSegmentation
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LesionPairRegistrationResult.Failed(
                stage = LesionPairRegistrationResult.FailureStage.REGISTRATION,
                detail = error.message
            )
        } finally {
            baselineSegmentation?.mask?.release()
            currentSegmentation?.mask?.release()
            baselineImage.release()
            currentImage.release()
        }
    }

    private fun toProcessingImage(bitmap: Bitmap): Mat {
        val rgba = Mat()
        val bgr = Mat()
        val resized = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            Imgproc.resize(
                bgr,
                resized,
                Size(PROCESSING_SIDE.toDouble(), PROCESSING_SIDE.toDouble()),
                0.0,
                0.0,
                Imgproc.INTER_AREA
            )
            return resized
        } finally {
            rgba.release()
            bgr.release()
        }
    }

    private fun segment(image: Mat): Segmentation? {
        val otsuMask = createOtsuMask(image)
        try {
            segmentationFromMask(otsuMask)?.let { return it }
        } finally {
            otsuMask.release()
        }

        val grabCutMask = createGrabCutMask(image)
        return try {
            segmentationFromMask(grabCutMask)
        } finally {
            grabCutMask.release()
        }
    }

    private fun createOtsuMask(image: Mat): Mat {
        val gray = Mat()
        val blurred = Mat()
        val binary = Mat()
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE,
            Size(MORPHOLOGY_SIZE.toDouble(), MORPHOLOGY_SIZE.toDouble())
        )
        try {
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(7.0, 7.0), 0.0)
            Imgproc.threshold(
                blurred,
                binary,
                0.0,
                255.0,
                Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU
            )
            Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel)
            Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, kernel)
            return binary
        } finally {
            gray.release()
            blurred.release()
            kernel.release()
        }
    }

    private fun createGrabCutMask(image: Mat): Mat {
        val labels = Mat()
        val backgroundModel = Mat()
        val foregroundModel = Mat()
        val probableForeground = Mat()
        val foreground = Mat()
        val binary = Mat()
        try {
            val margin = (PROCESSING_SIDE * GRAB_CUT_MARGIN_FRACTION).toInt()
            Imgproc.grabCut(
                image,
                labels,
                Rect(
                    margin,
                    margin,
                    PROCESSING_SIDE - margin * 2,
                    PROCESSING_SIDE - margin * 2
                ),
                backgroundModel,
                foregroundModel,
                5,
                Imgproc.GC_INIT_WITH_RECT
            )
            Core.compare(
                labels,
                Scalar(Imgproc.GC_PR_FGD.toDouble()),
                probableForeground,
                Core.CMP_EQ
            )
            Core.compare(
                labels,
                Scalar(Imgproc.GC_FGD.toDouble()),
                foreground,
                Core.CMP_EQ
            )
            Core.bitwise_or(probableForeground, foreground, binary)
            return binary
        } finally {
            labels.release()
            backgroundModel.release()
            foregroundModel.release()
            probableForeground.release()
            foreground.release()
        }
    }

    private fun segmentationFromMask(sourceMask: Mat): Segmentation? {
        val contourInput = sourceMask.clone()
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        try {
            Imgproc.findContours(
                contourInput,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val candidate = contours
                .mapNotNull(::describeContour)
                .maxByOrNull { it.score }
                ?: return null

            val finalMask = Mat.zeros(
                sourceMask.size(),
                CvType.CV_8UC1
            )
            Imgproc.drawContours(
                finalMask,
                listOf(candidate.contour),
                -1,
                Scalar(255.0),
                Core.FILLED
            )
            return Segmentation(
                mask = finalMask,
                areaFraction = candidate.area / sourceMask.total(),
                centroid = candidate.centroid,
                orientationDegrees = candidate.orientationDegrees
            )
        } finally {
            contours.forEach(MatOfPoint::release)
            contourInput.release()
            hierarchy.release()
        }
    }

    private fun describeContour(contour: MatOfPoint): ContourCandidate? {
        if (contour.total() < MIN_CONTOUR_POINTS) return null
        val area = Imgproc.contourArea(contour)
        val areaFraction = area / (PROCESSING_SIDE * PROCESSING_SIDE)
        if (areaFraction !in MIN_SEGMENT_AREA..MAX_SEGMENT_AREA) return null

        val moments = Imgproc.moments(contour)
        if (moments.m00 <= 0.0) return null
        val centroid = Point(
            moments.m10 / moments.m00,
            moments.m01 / moments.m00
        )
        val centerDistance = hypot(
            centroid.x - PROCESSING_SIDE / 2.0,
            centroid.y - PROCESSING_SIDE / 2.0
        ) / PROCESSING_SIDE
        if (centerDistance > MAX_SEGMENT_CENTER_DISTANCE) return null

        val bounds = Imgproc.boundingRect(contour)
        if (
            bounds.x <= FRAME_MARGIN ||
            bounds.y <= FRAME_MARGIN ||
            bounds.x + bounds.width >= PROCESSING_SIDE - FRAME_MARGIN ||
            bounds.y + bounds.height >= PROCESSING_SIDE - FRAME_MARGIN
        ) {
            return null
        }

        val covarianceX = moments.mu20 / moments.m00
        val covarianceY = moments.mu02 / moments.m00
        val covarianceXY = moments.mu11 / moments.m00
        val anisotropy = sqrt(
            (covarianceX - covarianceY) * (covarianceX - covarianceY) +
                4.0 * covarianceXY * covarianceXY
        ) / (covarianceX + covarianceY).coerceAtLeast(1e-9)
        val orientation = if (anisotropy >= MIN_ORIENTATION_ANISOTROPY) {
            0.5 * atan2(
                2.0 * covarianceXY,
                covarianceX - covarianceY
            ) * 180.0 / PI
        } else {
            null
        }

        return ContourCandidate(
            contour = contour,
            area = area,
            centroid = centroid,
            orientationDegrees = orientation,
            score = area * (1.0 - centerDistance)
        )
    }

    private fun registerSegmentations(
        baseline: Segmentation,
        current: Segmentation
    ): LesionPairRegistrationResult {
        val appliedScale = sqrt(
            current.areaFraction / baseline.areaFraction
        )
        val rotation = normalizedAxisRotation(
            current.orientationDegrees,
            baseline.orientationDegrees
        )
        val transform = Imgproc.getRotationMatrix2D(
            baseline.centroid,
            rotation,
            appliedScale
        )
        val alignedBaselineMask = Mat()
        val intersection = Mat()
        val union = Mat()
        try {
            val translatedX =
                transform.get(0, 2)[0] +
                    current.centroid.x -
                    baseline.centroid.x
            val translatedY =
                transform.get(1, 2)[0] +
                    current.centroid.y -
                    baseline.centroid.y
            transform.put(0, 2, translatedX)
            transform.put(1, 2, translatedY)

            Imgproc.warpAffine(
                baseline.mask,
                alignedBaselineMask,
                transform,
                current.mask.size(),
                Imgproc.INTER_NEAREST,
                Core.BORDER_CONSTANT,
                Scalar(0.0)
            )
            Core.bitwise_and(
                alignedBaselineMask,
                current.mask,
                intersection
            )
            Core.bitwise_or(
                alignedBaselineMask,
                current.mask,
                union
            )
            val unionPixels = Core.countNonZero(union)
            if (unionPixels == 0) {
                return LesionPairRegistrationResult.Failed(
                    LesionPairRegistrationResult.FailureStage.REGISTRATION,
                    "La unión de máscaras está vacía"
                )
            }

            val centroidDistance = hypot(
                current.centroid.x - baseline.centroid.x,
                current.centroid.y - baseline.centroid.y
            ) / hypot(PROCESSING_SIDE.toDouble(), PROCESSING_SIDE.toDouble())
            val metrics = LesionRegistrationMetrics(
                algorithmVersion = ALGORITHM_VERSION,
                baselineAreaFraction = baseline.areaFraction,
                currentAreaFraction = current.areaFraction,
                originalCentroidDistance = centroidDistance,
                appliedScale = appliedScale,
                appliedRotationDegrees = rotation,
                alignedMaskIou =
                    Core.countNonZero(intersection).toDouble() / unionPixels
            )
            return LesionPairRegistrationResult.Registered(metrics)
        } finally {
            transform.release()
            alignedBaselineMask.release()
            intersection.release()
            union.release()
        }
    }

    private fun normalizedAxisRotation(
        current: Double?,
        baseline: Double?
    ): Double {
        if (current == null || baseline == null) return 0.0
        var difference = current - baseline
        while (difference > 90.0) difference -= 180.0
        while (difference < -90.0) difference += 180.0
        return difference
    }

    private data class Segmentation(
        val mask: Mat,
        val areaFraction: Double,
        val centroid: Point,
        val orientationDegrees: Double?
    )

    private data class ContourCandidate(
        val contour: MatOfPoint,
        val area: Double,
        val centroid: Point,
        val orientationDegrees: Double?,
        val score: Double
    )

    private companion object {
        const val ALGORITHM_VERSION = "opencv-similarity-v1"
        const val PROCESSING_SIDE = 512
        const val MORPHOLOGY_SIZE = 7
        const val MIN_CONTOUR_POINTS = 12L
        const val MIN_SEGMENT_AREA = 0.01
        const val MAX_SEGMENT_AREA = 0.45
        const val MAX_SEGMENT_CENTER_DISTANCE = 0.35
        const val MIN_ORIENTATION_ANISOTROPY = 0.15
        const val FRAME_MARGIN = 2
        const val GRAB_CUT_MARGIN_FRACTION = 0.10
    }
}

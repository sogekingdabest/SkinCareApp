package com.monsteraltech.skincare.analysis.comparison

import android.graphics.Bitmap
import com.monsteraltech.skincare.body.mole.domain.model.MoleObservation
import com.monsteraltech.skincare.body.mole.domain.model.ObservationComparability
import com.monsteraltech.skincare.body.mole.domain.model.ObservationImageMetrics
import com.monsteraltech.skincare.body.mole.domain.usecase.EvaluateLesionRegistrationUseCase
import com.monsteraltech.skincare.body.mole.domain.usecase.EvaluateObservationComparabilityUseCase
import com.monsteraltech.skincare.camera.guidance.ImageQualityAnalyzer
import kotlinx.coroutines.CancellationException
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import javax.inject.Inject

/**
 * Implementación para las referencias locales del esquema actual.
 *
 * La decodificación limita el lado mayor para evitar cargar una imagen
 * histórica a resolución completa en memoria. Los esquemas remotos se
 * rechazan explícitamente hasta disponer de un almacén de imágenes estable.
 */
class LocalObservationComparisonPreparer @Inject constructor(
    private val imageDecoder: LocalObservationImageDecoder,
    private val evaluator: EvaluateObservationComparabilityUseCase,
    private val registrationEngine: LesionPairRegistrationEngine,
    private val evaluateRegistration: EvaluateLesionRegistrationUseCase
) : ObservationComparisonPreparer {

    override suspend fun prepare(
        currentBitmap: Bitmap,
        baseline: MoleObservation
    ): PreparedObservationComparison {
        val baselineBitmap = try {
            imageDecoder.decode(baseline.imageReference)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return unreadable(baseline, error.message)
        } ?: return unreadable(baseline, "La referencia no contiene una imagen válida")

        return try {
            val baselineMetrics = analyze(baselineBitmap)
            val currentMetrics = analyze(currentBitmap)
            when (
                val assessment = evaluator(
                    baselineObservationId = baseline.id,
                    baseline = baselineMetrics,
                    current = currentMetrics
                )
            ) {
                is ObservationComparability.Comparable ->
                    prepareRegistration(
                        baseline = baseline,
                        baselineBitmap = baselineBitmap,
                        currentBitmap = currentBitmap,
                        qualityAssessment = assessment
                    )
                is ObservationComparability.NotComparable -> {
                    baselineBitmap.recycle()
                    PreparedObservationComparison(assessment)
                }
            }
        } catch (cancellation: CancellationException) {
            baselineBitmap.recycle()
            throw cancellation
        } catch (error: Exception) {
            baselineBitmap.recycle()
            unreadable(baseline, error.message)
        }
    }

    private suspend fun prepareRegistration(
        baseline: MoleObservation,
        baselineBitmap: Bitmap,
        currentBitmap: Bitmap,
        qualityAssessment: ObservationComparability.Comparable
    ): PreparedObservationComparison {
        return when (
            val registration = registrationEngine.register(
                baselineBitmap = baselineBitmap,
                currentBitmap = currentBitmap
            )
        ) {
            is LesionPairRegistrationResult.Registered -> {
                if (evaluateRegistration(registration.metrics)) {
                    PreparedObservationComparison(
                        assessment = qualityAssessment,
                        baselineBitmap = baselineBitmap,
                        registrationMetrics = registration.metrics
                    )
                } else {
                    baselineBitmap.recycle()
                    notComparable(
                        baseline = baseline,
                        reason = ObservationComparability.Reason
                            .REGISTRATION_UNRELIABLE,
                        detail = "iou=${registration.metrics.alignedMaskIou}, " +
                            "scale=${registration.metrics.appliedScale}, " +
                            "centroid=${registration.metrics.originalCentroidDistance}"
                    )
                }
            }
            is LesionPairRegistrationResult.Failed -> {
                baselineBitmap.recycle()
                val reason = when (registration.stage) {
                    LesionPairRegistrationResult.FailureStage
                        .BASELINE_SEGMENTATION ->
                        ObservationComparability.Reason
                            .BASELINE_SEGMENTATION_FAILED
                    LesionPairRegistrationResult.FailureStage
                        .CURRENT_SEGMENTATION ->
                        ObservationComparability.Reason
                            .CURRENT_SEGMENTATION_FAILED
                    LesionPairRegistrationResult.FailureStage.REGISTRATION ->
                        ObservationComparability.Reason
                            .REGISTRATION_UNRELIABLE
                }
                notComparable(baseline, reason, registration.detail)
            }
        }
    }

    private suspend fun analyze(bitmap: Bitmap): ObservationImageMetrics {
        val rgba = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            val quality = qualityAnalyzer.analyze(gray)
            ObservationImageMetrics(
                width = bitmap.width,
                height = bitmap.height,
                brightness = quality.brightness,
                isOptimalLighting = quality.isOptimalLighting,
                isOverexposed = quality.isOverexposed,
                isUnderexposed = quality.isUnderexposed,
                sharpness = quality.sharpness,
                isBlurry = quality.isBlurry
            )
        } finally {
            gray.release()
            rgba.release()
        }
    }

    private fun unreadable(
        baseline: MoleObservation,
        detail: String?
    ) = notComparable(
        baseline = baseline,
        reason = ObservationComparability.Reason.BASELINE_IMAGE_UNREADABLE,
        detail = detail
    )

    private fun notComparable(
        baseline: MoleObservation,
        reason: ObservationComparability.Reason,
        detail: String?
    ) = PreparedObservationComparison(
        assessment = ObservationComparability.NotComparable(
            reason = reason,
            baselineObservationId = baseline.id,
            detail = detail
        )
    )

    private companion object {
        val qualityAnalyzer = ImageQualityAnalyzer()
    }
}

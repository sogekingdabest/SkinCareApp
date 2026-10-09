package com.monsteraltech.skincare.analysis.comparison

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/**
 * Implementación para las referencias locales del esquema actual.
 *
 * La decodificación limita el lado mayor para evitar cargar una imagen
 * histórica a resolución completa en memoria. Los esquemas remotos se
 * rechazan explícitamente hasta disponer de un almacén de imágenes estable.
 */
class LocalObservationComparisonPreparer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val evaluator: EvaluateObservationComparabilityUseCase,
    private val registrationEngine: LesionPairRegistrationEngine,
    private val evaluateRegistration: EvaluateLesionRegistrationUseCase
) : ObservationComparisonPreparer {

    override suspend fun prepare(
        currentBitmap: Bitmap,
        baseline: MoleObservation
    ): PreparedObservationComparison {
        val baselineBitmap = try {
            decodeBaseline(baseline.imageReference)
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

    private fun decodeBaseline(reference: String): Bitmap? {
        if (reference.isBlank()) return null
        val uri = Uri.parse(reference)
        return when (uri.scheme?.lowercase()) {
            null, "" -> decodeFile(File(reference))
            "file" -> uri.path?.let(::File)?.let(::decodeFile)
            "content" -> decodeContent(uri)
            else -> null
        }
    }

    private fun decodeFile(file: File): Bitmap? {
        if (!file.isFile || !file.canRead()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (!bounds.hasValidBounds()) return null
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(bounds)
            }
        )
    }

    private fun decodeContent(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (!bounds.hasValidBounds()) return null
        return context.contentResolver.openInputStream(uri).use { stream ->
            decodeStream(stream, calculateSampleSize(bounds))
        }
    }

    private fun decodeStream(stream: InputStream?, sampleSize: Int): Bitmap? {
        if (stream == null) return null
        return BitmapFactory.decodeStream(
            stream,
            null,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        )
    }

    private fun BitmapFactory.Options.hasValidBounds() =
        outWidth > 0 && outHeight > 0

    private fun calculateSampleSize(bounds: BitmapFactory.Options): Int {
        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > MAX_DECODED_SIDE ||
            bounds.outHeight / sampleSize > MAX_DECODED_SIDE
        ) {
            sampleSize *= 2
        }
        return sampleSize
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
        const val MAX_DECODED_SIDE = 2048
        val qualityAnalyzer = ImageQualityAnalyzer()
    }
}

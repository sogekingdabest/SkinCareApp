package com.monsteraltech.skincare.body.mole.domain.usecase

import com.monsteraltech.skincare.body.mole.domain.model.LesionRegistrationMetrics
import javax.inject.Inject

/**
 * Umbrales conservadores para aceptar un registro antes de medir cambios.
 */
class EvaluateLesionRegistrationUseCase @Inject constructor() {

    operator fun invoke(metrics: LesionRegistrationMetrics): Boolean =
        metrics.baselineAreaFraction in MIN_AREA_FRACTION..MAX_AREA_FRACTION &&
            metrics.currentAreaFraction in MIN_AREA_FRACTION..MAX_AREA_FRACTION &&
            metrics.originalCentroidDistance <= MAX_CENTROID_DISTANCE &&
            metrics.appliedScale in MIN_SCALE..MAX_SCALE &&
            metrics.alignedMaskIou >= MIN_ALIGNED_MASK_IOU

    private companion object {
        const val MIN_AREA_FRACTION = 0.01
        const val MAX_AREA_FRACTION = 0.45
        const val MAX_CENTROID_DISTANCE = 0.45
        const val MIN_SCALE = 1.0 / 3.0
        const val MAX_SCALE = 3.0
        const val MIN_ALIGNED_MASK_IOU = 0.35
    }
}

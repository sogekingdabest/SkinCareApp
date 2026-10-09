package com.monsteraltech.skincare.analysis.comparison

import android.graphics.Bitmap
import com.monsteraltech.skincare.body.mole.domain.model.LesionRegistrationMetrics
import com.monsteraltech.skincare.body.mole.domain.model.MoleObservation
import com.monsteraltech.skincare.body.mole.domain.model.ObservationComparability

/**
 * Frontera Android que carga la captura base y obtiene sus métricas técnicas.
 */
interface ObservationComparisonPreparer {
    suspend fun prepare(
        currentBitmap: Bitmap,
        baseline: MoleObservation
    ): PreparedObservationComparison
}

data class PreparedObservationComparison(
    val assessment: ObservationComparability,
    val baselineBitmap: Bitmap? = null,
    val registrationMetrics: LesionRegistrationMetrics? = null
)

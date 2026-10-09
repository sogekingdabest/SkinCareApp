package com.monsteraltech.skincare.analysis.comparison

import android.graphics.Bitmap
import com.monsteraltech.skincare.body.mole.domain.model.LesionRegistrationMetrics

interface LesionPairRegistrationEngine {
    suspend fun register(
        baselineBitmap: Bitmap,
        currentBitmap: Bitmap
    ): LesionPairRegistrationResult
}

sealed interface LesionPairRegistrationResult {
    data class Registered(
        val metrics: LesionRegistrationMetrics
    ) : LesionPairRegistrationResult

    data class Failed(
        val stage: FailureStage,
        val detail: String? = null
    ) : LesionPairRegistrationResult

    enum class FailureStage {
        BASELINE_SEGMENTATION,
        CURRENT_SEGMENTATION,
        REGISTRATION
    }
}

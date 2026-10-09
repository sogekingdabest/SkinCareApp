package com.monsteraltech.skincare.body.mole.domain.usecase

import com.monsteraltech.skincare.body.mole.domain.model.MoleObservation
import com.monsteraltech.skincare.body.mole.domain.repository.MoleObservationRepository
import javax.inject.Inject

class GetBaselineObservationUseCase @Inject constructor(
    private val repository: MoleObservationRepository
) {
    suspend operator fun invoke(moleId: String?): Result<MoleObservation?> {
        if (moleId.isNullOrBlank()) {
            return Result.success(null)
        }
        return repository.getLatestObservation(moleId)
    }
}

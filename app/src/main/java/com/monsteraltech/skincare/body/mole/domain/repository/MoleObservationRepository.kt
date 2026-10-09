package com.monsteraltech.skincare.body.mole.domain.repository

import com.monsteraltech.skincare.body.mole.domain.model.MoleObservation

interface MoleObservationRepository {
    suspend fun getLatestObservation(moleId: String): Result<MoleObservation?>
}

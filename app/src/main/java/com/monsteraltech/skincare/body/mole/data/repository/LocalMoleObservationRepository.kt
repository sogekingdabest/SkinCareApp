package com.monsteraltech.skincare.body.mole.data.repository

import com.monsteraltech.skincare.body.mole.data.local.LocalObservationStore
import com.monsteraltech.skincare.body.mole.domain.model.MoleObservation
import com.monsteraltech.skincare.body.mole.domain.repository.MoleObservationRepository
import com.monsteraltech.skincare.data.identity.ActiveProfile
import com.monsteraltech.skincare.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LocalMoleObservationRepository @Inject constructor(
    private val observations: LocalObservationStore,
    private val activeProfile: ActiveProfile,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : MoleObservationRepository {
    override suspend fun getLatestObservation(moleId: String): Result<MoleObservation?> = withContext(dispatcher) {
        runCatching {
            val owner = activeProfile.requireOwnerId()
            observations.current(owner, moleId).analysis?.takeIf { it.imagePath.isNotBlank() }?.let {
                MoleObservation(it.id, it.moleId, it.imagePath, it.createdAtEpochMillis)
            }
        }.onFailure { if (it is CancellationException) throw it }
    }
}

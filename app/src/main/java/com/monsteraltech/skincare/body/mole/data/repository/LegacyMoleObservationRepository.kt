package com.monsteraltech.skincare.body.mole.data.repository

import com.monsteraltech.skincare.data.identity.ActiveProfile
import com.monsteraltech.skincare.body.mole.domain.model.MoleObservation
import com.monsteraltech.skincare.body.mole.domain.repository.MoleObservationRepository
import com.monsteraltech.skincare.body.mole.repository.MoleRepository
import javax.inject.Inject

/**
 * Adaptador de lectura para el esquema actual de Firestore.
 *
 * Permite introducir el dominio de observaciones sin migrar todavía los datos.
 * La imagen vigente de [MoleData] se interpreta como la última observación.
 */
class LegacyMoleObservationRepository @Inject constructor(
    private val moleRepository: MoleRepository,
    private val activeProfile: ActiveProfile
) : MoleObservationRepository {

    override suspend fun getLatestObservation(
        moleId: String
    ): Result<MoleObservation?> {
        val userId = activeProfile.ownerId
            ?: return Result.failure(SecurityException("Usuario no autenticado"))

        return moleRepository.getMoleById(userId, moleId).map { mole ->
            mole.imageUrl
                .takeIf(String::isNotBlank)
                ?.let { imageReference ->
                    MoleObservation(
                        id = "${mole.id}_current",
                        moleId = mole.id,
                        imageReference = imageReference,
                        capturedAtEpochMillis = (
                            mole.lastAnalysisDate ?: mole.updatedAt
                        ).toDate().time
                    )
                }
        }
    }
}

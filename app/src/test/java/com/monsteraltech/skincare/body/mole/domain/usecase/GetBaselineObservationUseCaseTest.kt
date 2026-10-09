package com.monsteraltech.skincare.body.mole.domain.usecase

import com.monsteraltech.skincare.body.mole.domain.model.MoleObservation
import com.monsteraltech.skincare.body.mole.domain.repository.MoleObservationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetBaselineObservationUseCaseTest {

    @Test
    fun `sin moleId devuelve ausencia sin consultar el repositorio`() =
        runTest {
            val repository = FakeMoleObservationRepository()
            val useCase = GetBaselineObservationUseCase(repository)

            assertEquals(null, useCase(null).getOrThrow())
            assertEquals(0, repository.requests)
        }

    @Test
    fun `con moleId devuelve la ultima observacion del repositorio`() =
        runTest {
            val observation =
                MoleObservation(
                    id = "obs-2",
                    moleId = "m-2",
                    imageReference = "imagenes/m-2.jpg",
                    capturedAtEpochMillis = 5678L
                )
            val repository =
                FakeMoleObservationRepository(observation)
            val useCase = GetBaselineObservationUseCase(repository)

            assertEquals(
                observation,
                useCase("m-2").getOrThrow()
            )
            assertEquals("m-2", repository.lastMoleId)
        }

    private class FakeMoleObservationRepository(
        private val observation: MoleObservation? = null
    ) : MoleObservationRepository {
        var requests: Int = 0
            private set
        var lastMoleId: String? = null
            private set

        override suspend fun getLatestObservation(
            moleId: String
        ): Result<MoleObservation?> {
            requests += 1
            lastMoleId = moleId
            return Result.success(observation)
        }
    }
}

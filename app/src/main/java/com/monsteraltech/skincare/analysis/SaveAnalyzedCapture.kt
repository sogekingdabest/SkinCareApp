package com.monsteraltech.skincare.analysis

import android.content.Context
import com.monsteraltech.skincare.body.mole.model.MoleData
import com.monsteraltech.skincare.body.mole.repository.MoleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** Adapts a complete capture to the existing local persistence contract. */
class SaveAnalyzedCapture @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MoleRepository,
    private val records: AnalysisRecordFactory
) {
    suspend fun findSaved(captureId: String) = repository.findSavedCapture(captureId)

    suspend fun getMole(moleId: String, owner: String) = repository.getMoleById(owner, moleId)

    suspend fun saveNew(
        image: File,
        complete: AnalysisOutcome.Complete,
        annotations: AnalysisAnnotations,
        captureId: String = UUID.randomUUID().toString()
    ): Result<MoleData> {
        val record = records.create(complete, captureId, annotations, observationId = captureId)
        return repository.saveMoleWithAnalysis(
            context, image, annotations.title, annotations.description, annotations.bodyPart,
            annotations.bodyPartColorCode.orEmpty(), record
        )
    }

    suspend fun append(
        moleId: String,
        image: File,
        complete: AnalysisOutcome.Complete,
        annotations: AnalysisAnnotations,
        captureId: String = UUID.randomUUID().toString()
    ): Result<Unit> = repository.saveAnalysisToMole(
        context, moleId, records.create(complete, moleId, annotations, observationId = captureId), image
    )
}

package com.monsteraltech.skincare.backup

import java.time.Instant
import com.monsteraltech.skincare.account.AccountSettings
import com.monsteraltech.skincare.body.mole.data.local.LocalMoleMapper
import com.monsteraltech.skincare.body.mole.data.local.MoleDao
import com.monsteraltech.skincare.body.mole.data.local.SkinCareDatabase
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.model.MoleData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.Date
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class ObservationBackupTest {
    private val mapper = LocalMoleMapper()
    private val dao = mockk<MoleDao>()
    private val database = mockk<SkinCareDatabase>()
    private val service: LocalBackupService
    private val mole = mapper.toEntity(MoleData(id = "m", userId = "old-owner", analysisCount = 1,
        aiResult = "current", imageUrl = "original.jpg", lastAnalysisDate = Instant.ofEpochMilli(2000)))
    private val analysis = mapper.toEntity(AnalysisData(id = "capture", moleId = "m", imageUrl = "original.jpg"), "old-owner")

    init {
        every { database.moleDao() } returns dao
        coEvery { dao.getMoleOwner(any()) } returns null
        coEvery { dao.getAnalysisOwner(any()) } returns null
        val images = mockk<com.monsteraltech.skincare.data.image.PrivateImageStore>()
        service = LocalBackupService(mockk(), database, mockk(), mockk(), images,
            BackupRestoreCoordinator(images, Dispatchers.IO), BackupRecordRestorer(database, images), Dispatchers.IO)
    }

    private fun payload(version: Int = 2) = BackupPayload(version, 1000,
        listOf(mole.copy(currentAnalysisId = if (version == 2) analysis.id else null)),
        if (version == 2) listOf(analysis) else emptyList(), emptyMap(), AccountSettings())

    @Test fun `legacy backup receives a stable current observation including its image`() {
        val normalized = service.normalizeLegacyPayload(payload(1))
        val repeated = service.normalizeLegacyPayload(payload(1))
        assertEquals(normalized, repeated)
        assertEquals(3, normalized.schemaVersion)
        assertEquals(1, normalized.analyses.size)
        assertEquals(normalized.analyses.single().id, normalized.moles.single().currentAnalysisId)
        assertEquals("original.jpg", normalized.analyses.single().imagePath)
        assertEquals(2000L, normalized.analyses.single().createdAtEpochMillis)
        service.validatePayload(normalized)
    }

    @Test fun `annotations do not change legacy capture identity`() {
        val original = service.normalizeLegacyPayload(payload(1)).analyses.single()
        val edited = service.normalizeLegacyPayload(payload(1).copy(moles = listOf(mole.copy(
            updatedAtEpochMillis = 5000, analysisMetadataJson = "{\"userTotal\":2}")))).analyses.single()
        assertEquals(original.id, edited.id)
    }

    @Test fun `same profile restore preserves IDs and remaps the current image`() = runTest {
        val restored = service.remapRecordsForCurrentUser("new-owner", payload(), mapOf("original.jpg" to "new.jpg"))
        assertEquals("capture", restored.analyses.single().id)
        assertEquals("capture", restored.moles.single().currentAnalysisId)
        assertEquals("new.jpg", restored.analyses.single().imagePath)
        assertEquals("new.jpg", restored.moles.single().imagePath)
        assertEquals("new-owner", restored.analyses.single().userId)
    }

    @Test fun `owner collisions remap both observation IDs and current pointer`() = runTest {
        coEvery { dao.getMoleOwner("m") } returns "another-owner"
        coEvery { dao.getAnalysisOwner("capture") } returns "another-owner"
        val restored = service.remapRecordsForCurrentUser("new-owner", payload(), emptyMap())
        assertNotEquals("m", restored.moles.single().id)
        assertNotEquals("capture", restored.analyses.single().id)
        assertEquals(restored.analyses.single().id, restored.moles.single().currentAnalysisId)
        assertEquals(restored.moles.single().id, restored.analyses.single().moleId)
    }

    @Test fun `same owner with a different parent does not overwrite another mole observation`() = runTest {
        coEvery { dao.getAnalysisOwner("capture") } returns "new-owner"
        coEvery { dao.getAnalysis("new-owner", "m", "capture") } returns null
        val restored = service.remapRecordsForCurrentUser("new-owner", payload(), emptyMap())
        assertNotEquals("capture", restored.analyses.single().id)
        assertEquals(restored.analyses.single().id, restored.moles.single().currentAnalysisId)
    }

    @Test fun `reimport retains scoped identities after original records disappear`() = runTest {
        coEvery { dao.getMoleOwner("m") } returns "another-owner"
        coEvery { dao.getAnalysisOwner("capture") } returns "another-owner"
        val first = service.remapRecordsForCurrentUser("new-owner", payload(), emptyMap())
        val importedMole = first.moles.single()
        val importedAnalysis = first.analyses.single()
        coEvery { dao.getMoleOwner("m") } returns null
        coEvery { dao.getAnalysisOwner("capture") } returns null
        coEvery { dao.getMoleOwner(importedMole.id) } returns "new-owner"
        coEvery { dao.getAnalysisOwner(importedAnalysis.id) } returns "new-owner"
        coEvery { dao.getAnalysis("new-owner", importedMole.id, importedAnalysis.id) } returns importedAnalysis
        assertEquals(first, service.remapRecordsForCurrentUser("new-owner", payload(), emptyMap()))
    }

    @Test fun `same owner and parent keep existing observation identity`() = runTest {
        coEvery { dao.getAnalysisOwner("capture") } returns "new-owner"
        coEvery { dao.getAnalysis("new-owner", "m", "capture") } returns analysis
        val restored = service.remapRecordsForCurrentUser("new-owner", payload(), emptyMap())
        assertEquals("capture", restored.analyses.single().id)
    }

    @Test fun `dangling current pointer and duplicate observation IDs are rejected`() {
        val cases = listOf(payload().copy(moles = listOf(mole.copy(currentAnalysisId = "missing"))),
            payload().copy(analyses = listOf(analysis, analysis)))
        for (invalid in cases) {
            try { service.validatePayload(invalid); fail("Expected invalid backup") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun `version two backup infers a region without changing observations or images`() {
        val old = payload().copy(moles = listOf(mole.copy(currentAnalysisId = analysis.id,
            bodyPart = "Brazo derecho", bodyPartColorCode = "#FFED1C24")))
        val migrated = service.normalizeLegacyPayload(old)
        assertEquals(3, migrated.schemaVersion)
        assertEquals("right_arm", migrated.moles.single().bodyRegionId)
        assertEquals(old.analyses, migrated.analyses)
        assertEquals(old.imageEntries, migrated.imageEntries)
        assertEquals(old.moles.single().currentAnalysisId, migrated.moles.single().currentAnalysisId)
        service.validatePayload(migrated)
    }

    @Test fun `modern backup IDs have authority over labels and palettes`() {
        val modern = payload().copy(schemaVersion = 3,
            moles = listOf(mole.copy(currentAnalysisId = analysis.id, bodyPart = "Changed",
                bodyPartColorCode = "#FF000000", bodyRegionId = "left_leg")))
        assertEquals(modern, service.normalizeLegacyPayload(modern))
        service.validatePayload(modern)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed modern region ID is rejected before restoring data`() {
        service.validatePayload(payload().copy(schemaVersion = 3,
            moles = listOf(mole.copy(currentAnalysisId = analysis.id, bodyRegionId = "not-an-id"))))
    }
}

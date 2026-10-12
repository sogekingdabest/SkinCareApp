package com.monsteraltech.skincare.camera

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsteraltech.skincare.body.domain.BodyRegionId
import com.monsteraltech.skincare.body.mole.data.local.*
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.model.MoleData
import com.monsteraltech.skincare.body.mole.repository.MoleRepository
import com.monsteraltech.skincare.data.identity.ActiveProfile
import com.monsteraltech.skincare.data.image.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureNavigationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private class Profile : ActiveProfile {
        override var ownerId: String? = "capture-fixture"
        override val isLocal = true
        override fun activateLocalProfile() = requireOwnerId()
        override fun activateAccountProfile() = Unit
        override fun clear() { ownerId = null }
    }

    @Test fun durableDraftRestoresByReferenceAndIsScopedToItsProfile() {
        val profile = Profile()
        val drafts = CaptureDraftStore(context, profile)
        val draft = drafts.create()
        try {
            draft.file.writeBytes(byteArrayOf(1, 2, 3))
            val args = CaptureArgs(draft.id, true, BodyRegionId.LEFT_ARM, "m")
            val intent = args.toIntent(context, PreviewActivity::class.java)
            assertFalse(intent.hasExtra("PHOTO_PATH"))
            val saved = Bundle().also(args::save)
            val fresh = CaptureDraftStore(context, profile)
            assertEquals(args, CaptureArgs.read(intent, saved, fresh))
            assertArrayEquals(byteArrayOf(1, 2, 3), fresh.file(draft.id).readBytes())
            assertTrue(draft.file.toPath().startsWith(context.noBackupFilesDir.toPath()))
            profile.ownerId = "another-profile"
            assertFalse(fresh.file(draft.id).exists())
            try { fresh.file("../escape"); fail("Expected invalid reference") } catch (_: IllegalArgumentException) { }
        } finally { draft.file.delete() }
    }

    @Test fun committedCaptureIsRecoveredAndRetriesDoNotWriteImagesOrDuplicateHistory() = runBlocking {
        val profile = Profile()
        val db = Room.inMemoryDatabaseBuilder(context, SkinCareDatabase::class.java).build()
        val images = privateImageStore(context)
        val persistence = PrivateImagePersistence(images, Dispatchers.IO)
        val store = LocalObservationStore(db)
        val mapper = LocalMoleMapper()
        val id = UUID.randomUUID().toString()
        val record = AnalysisData(id = id, moleId = "m", analysisResult = "fixture", riskLevel = "LOW", recommendation = "fixture")
        try {
            store.create(mapper.toEntity(MoleData(id = "m", title = "Canonical", userId = profile.requireOwnerId(),
                bodyRegionId = BodyRegionId.TORSO)), mapper.toEntity(record, profile.requireOwnerId()))
            // No file exists: a committed retry must succeed before compression/copying.
            val repository = MoleRepository(db, profile, images, persistence, store)
            val missing = File(context.cacheDir, "missing-$id.jpg")
            coroutineScope {
                (1..3).map { async { repository.saveAnalysisToMole(context, "m", record, missing).getOrThrow() } }.awaitAll()
            }
            val fresh = MoleRepository(db, profile, images, persistence, LocalObservationStore(db))
            val receipt = checkNotNull(fresh.findSavedCapture(id))
            assertEquals("Canonical", receipt.mole.title)
            assertEquals(id, receipt.analysis!!.id)
            assertEquals(1, db.moleDao().countAnalyses(profile.requireOwnerId(), "m"))
            assertTrue(fresh.saveMoleWithAnalysis(context, missing, "stale", "", "", "", record).isSuccess)
            assertEquals("Canonical", fresh.getMoleById(profile.requireOwnerId(), "m").getOrThrow().title)
            assertTrue(fresh.saveAnalysisToMole(context, "other-mole", record.copy(moleId = "other-mole"), missing).isFailure)
            profile.ownerId = "another-profile"
            assertNull(fresh.findSavedCapture(id))
        } finally { db.close() }
    }

    @Test fun historicalReadRequiresBothTheMoleAndItsOwnerAndUsesTheStoredObservation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SkinCareDatabase::class.java).build()
        val store = LocalObservationStore(db)
        val mapper = LocalMoleMapper()
        try {
            val mole = MoleData(id = "m", userId = "owner", title = "Canonical")
            store.create(mapper.toEntity(mole), mapper.toEntity(AnalysisData(id = "first", moleId = "m", aiProbability = 0.2f), "owner"))
            store.append("owner", "m", mapper.toEntity(AnalysisData(id = "current", moleId = "m", aiProbability = 0.8f), "owner"))
            val historical = store.current("owner", "m", "first")
            assertEquals("current", historical.mole.currentAnalysisId)
            assertEquals("first", historical.analysis!!.id)
            assertEquals(0.2f, historical.analysis!!.aiProbability)
            try { store.current("other-owner", "m", "first"); fail("Expected wrong owner") } catch (_: NoSuchElementException) { }
            try { store.current("owner", "other-mole", "first"); fail("Expected wrong mole") } catch (_: NoSuchElementException) { }
            try { store.current("owner", "m", "missing"); fail("Expected missing observation") } catch (_: NoSuchElementException) { }
        } finally { db.close() }
    }
}

package com.monsteraltech.skincare.backup

import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import com.google.gson.Gson
import com.monsteraltech.skincare.account.AccountSettings
import com.monsteraltech.skincare.body.mole.data.local.LocalMoleMapper
import com.monsteraltech.skincare.body.mole.data.local.LocalObservationStore
import com.monsteraltech.skincare.body.mole.data.local.SkinCareDatabase
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.model.MoleData
import com.monsteraltech.skincare.data.identity.ActiveProfile
import com.monsteraltech.skincare.data.image.FilePrivateImageStore
import com.monsteraltech.skincare.data.image.PrivateImageStore
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupConsistencyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val mapper = LocalMoleMapper()
    private lateinit var database: SkinCareDatabase
    private lateinit var observations: LocalObservationStore
    private lateinit var root: File
    private lateinit var images: FilePrivateImageStore
    private val profile = TestProfile()
    private val settings = TestSettings()

    @Before fun setup() {
        root = Files.createTempDirectory(context.cacheDir.toPath(), "backup_consistency_").toFile()
        images = FilePrivateImageStore(File(root, "private"), File(root, "legacy"), File(root, "cache"))
        database = Room.inMemoryDatabaseBuilder(context, SkinCareDatabase::class.java).build()
        observations = LocalObservationStore(database)
    }
    @After fun close() {
        database.close()
        check(root.canonicalFile.toPath().startsWith(context.cacheDir.canonicalFile.toPath()))
        root.deleteRecursively()
    }

    private fun service(store: PrivateImageStore = images) = LocalBackupService(context, database, profile, settings, store,
        BackupRestoreCoordinator(store, Dispatchers.IO), BackupRecordRestorer(database, store), Dispatchers.IO)

    private fun analysis(id: String, image: String, time: Long = 1000) = mapper.toEntity(AnalysisData(
        id = id, moleId = "m", description = id, analysisResult = "result-$id", imageUrl = image,
        riskLevel = "LOW", recommendation = "fixture", createdAt = Instant.ofEpochMilli(time)), profile.ownerId)

    private fun photo(name: String): String {
        val source = File(root, name).apply { writeText("bytes-$name") }
        return images.save(source, profile.ownerId)
    }
    private fun password() = "fixture-password".toCharArray()
    private fun imageCount() = File(root, "private").walkTopDown().count { it.isFile }

    @Test fun encryptedRoundTripAcrossProfilesAndRepeatedImportPreserveStableIDsAndFiles() = runBlocking {
        val first = analysis("first", photo("first-photo"))
        observations.create(mapper.toEntity(MoleData(id = "m", userId = profile.ownerId, title = "Source")), first)
        observations.append(profile.ownerId, "m", analysis("second", photo("second-photo"), 2000))
        val source = observations.current(profile.ownerId, "m")
        val backup = File(root, "copy.skb")
        assertEquals(2, service().exportTo(Uri.fromFile(backup), password()).getOrThrow().imageCount)
        profile.owner = "target"
        val imported = service().importFrom(Uri.fromFile(backup), password()).getOrThrow()
        assertEquals(BackupSummary(1, 2, 2), imported.summary)
        val targetMole = database.moleDao().getAllMoles("target").single()
        val targetBefore = observations.current("target", targetMole.id)
        assertNotEquals("m", targetMole.id)
        assertEquals("result-second", targetBefore.analysis!!.analysisResult)
        val count = imageCount()
        val repeated = service().importFrom(Uri.fromFile(backup), password()).getOrThrow()
        assertEquals(BackupSummary(0, 0, 0), repeated.summary)
        assertTrue(RestoreWarning.EXISTING_DATA_PRESERVED in repeated.warnings)
        assertEquals(targetBefore, observations.current("target", targetMole.id))
        assertEquals(count, imageCount())
        assertEquals(source, observations.current("source", "m"))
        assertEquals("target", settings.lastWrite!!.userId)
        assertFalse(settings.lastWrite!!.autoBackup)
        database.withTransaction {
            database.openHelper.writableDatabase.execSQL("DELETE FROM mole_analyses WHERE userId = ?", arrayOf("source"))
            database.openHelper.writableDatabase.execSQL("DELETE FROM moles WHERE userId = ?", arrayOf("source"))
        }
        val afterSourceDeletion = service().importFrom(Uri.fromFile(backup), password()).getOrThrow()
        assertEquals(BackupSummary(0, 0, 0), afterSourceDeletion.summary)
        assertEquals(listOf(targetMole.id), database.moleDao().getAllMoles("target").map { it.id })
        assertEquals(targetBefore, observations.current("target", targetMole.id))
        assertEquals(count, imageCount())
    }

    @Test fun restoringAnOlderCopyKeepsLocalCurrentObservationAndManualAnnotations() = runBlocking {
        observations.create(mapper.toEntity(MoleData(id = "m", userId = profile.ownerId)), analysis("first", photo("first-photo")))
        val backup = File(root, "old.skb")
        service().exportTo(Uri.fromFile(backup), password()).getOrThrow()
        observations.append(profile.ownerId, "m", analysis("latest", photo("latest-photo"), 3000))
        observations.annotate(profile.ownerId, "m", "first", mapOf("userTotal" to 9))
        val before = observations.current(profile.ownerId, "m")
        val fileCount = imageCount()
        service().importFrom(Uri.fromFile(backup), password()).getOrThrow()
        assertEquals(before, observations.current(profile.ownerId, "m"))
        assertEquals(9.0, mapper.toModel(database.moleDao().getAnalysis(profile.ownerId, "m", "first")!!).analysisMetadata["userTotal"])
        assertEquals(fileCount, imageCount())
    }

    @Test fun missingSourceImagePreventsExportFromTruncatingTheDestination() = runBlocking {
        val reference = photo("photo")
        observations.create(mapper.toEntity(MoleData(id = "m", userId = profile.ownerId)), analysis("first", reference))
        images.delete(reference)
        val destination = File(root, "existing.skb").apply { writeText("original destination") }
        val secret = password()
        assertTrue(service().exportTo(Uri.fromFile(destination), secret).isFailure)
        assertEquals("original destination", destination.readText())
        assertTrue(secret.all { it == '\u0000' })
    }

    @Test fun restoringRecoversMissingPhotoWithoutReplacingClinicalFieldsOrAnnotations() = runBlocking {
        val reference = photo("photo")
        observations.create(mapper.toEntity(MoleData(id = "m", userId = profile.ownerId)), analysis("first", reference))
        val backup = File(root, "repair.skb")
        service().exportTo(Uri.fromFile(backup), password()).getOrThrow()
        observations.annotate(profile.ownerId, "m", "first", mapOf("userTotal" to 4))
        val before = observations.current(profile.ownerId, "m").analysis!!
        images.delete(reference)
        val restored = service().importFrom(Uri.fromFile(backup), password()).getOrThrow()
        assertEquals(1, restored.summary.imageCount)
        val after = observations.current(profile.ownerId, "m").analysis!!
        assertTrue(images.resolve(after.imagePath)?.isFile == true)
        assertEquals(before, after.copy(imagePath = before.imagePath))
    }

    @Test fun settingsFailureAfterActualRoomCommitReportsWarningAndKeepsRecoveredPhoto() = runBlocking {
        val backup = rawBackup(includePhoto = true)
        settings.fail = true
        val report = service().importFrom(Uri.fromFile(backup), password()).getOrThrow()
        assertEquals(BackupSummary(1, 1, 1), report.summary)
        assertTrue(RestoreWarning.SETTINGS_NOT_RESTORED in report.warnings)
        val current = observations.current(profile.ownerId, "m")
        assertTrue(images.resolve(current.analysis!!.imagePath)?.isFile == true)
    }

    @Test fun oldArchiveOmittingPhotosIsAcceptedWithExplicitWarning() = runBlocking {
        val backup = rawBackup(includePhoto = false)
        val report = service().importFrom(Uri.fromFile(backup), password()).getOrThrow()
        assertTrue(RestoreWarning.MISSING_IMAGES in report.warnings)
        assertEquals("", observations.current(profile.ownerId, "m").analysis!!.imagePath)
    }

    @Test fun profileChangeDuringCopyRollsBackOwnedFilesAndNeverWritesAnotherProfile() = runBlocking {
        val backup = rawBackup(includePhoto = true)
        val switching = object : PrivateImageStore by images {
            override fun save(source: File, ownerId: String) = images.save(source, ownerId).also { profile.owner = "another" }
        }
        val secret = password()
        assertTrue(service(switching).importFrom(Uri.fromFile(backup), secret).isFailure)
        assertTrue(database.moleDao().getAllMoles("source").isEmpty())
        assertTrue(database.moleDao().getAllMoles("another").isEmpty())
        assertEquals(0, imageCount())
        assertNull(settings.lastWrite)
        assertTrue(secret.all { it == '\u0000' })
    }

    private fun rawBackup(includePhoto: Boolean): File {
        val entry = "images/${"a".repeat(64)}.bin"
        val savedAnalysis = analysis("capture", "original.jpg")
        val mole = mapper.toEntity(MoleData(id = "m", userId = profile.ownerId, analysisCount = 1,
            aiResult = "result", currentAnalysisId = "capture", imageUrl = "original.jpg"))
        val payload = BackupPayload(2, 1000, listOf(mole), listOf(savedAnalysis),
            if (includePhoto) mapOf("original.jpg" to entry) else emptyMap(), AccountSettings(autoBackup = true))
        return File(root, "raw.skb").also { file -> file.outputStream().use { output ->
            EncryptedBackupCodec().encrypt(output, password()) { plain ->
                val data = DataOutputStream(plain)
                val json = Gson().toJson(payload).toByteArray(Charsets.UTF_8)
                data.writeInt(json.size); data.write(json); data.writeInt(if (includePhoto) 1 else 0)
                if (includePhoto) { data.writeUTF(entry); data.writeLong(5); data.write("photo".toByteArray()) }
            }
        } }
    }

    private class TestProfile : ActiveProfile {
        var owner = "source"
        override val ownerId get() = owner
        override val isLocal = true
        override fun activateLocalProfile() = owner
        override fun activateAccountProfile() = Unit
        override fun clear() = Unit
    }
    private class TestSettings : BackupSettingsStore {
        var fail = false
        var lastWrite: AccountSettings? = null
        override suspend fun read(ownerId: String) = AccountSettings(userId = ownerId)
        override suspend fun write(settings: AccountSettings, ownerId: String): Boolean {
            lastWrite = settings
            return !fail
        }
    }
}

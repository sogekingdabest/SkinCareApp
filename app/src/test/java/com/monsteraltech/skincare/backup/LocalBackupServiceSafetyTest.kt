package com.monsteraltech.skincare.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.monsteraltech.skincare.body.mole.data.local.LocalMoleMapper
import com.monsteraltech.skincare.body.mole.data.local.SkinCareDatabase
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.model.MoleData
import com.monsteraltech.skincare.account.AccountSettings
import com.monsteraltech.skincare.data.identity.ActiveProfile
import com.monsteraltech.skincare.data.image.FilePrivateImageStore
import io.mockk.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LocalBackupServiceSafetyTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context = mockk<Context>()
    private val resolver = mockk<ContentResolver>()
    private val uri = mockk<Uri>()
    private val db = mockk<SkinCareDatabase>()
    private val profile = mockk<ActiveProfile>()
    private val settings = mockk<BackupSettingsStore>()
    private val records = mockk<BackupRecordRestorer>()
    private val mapper = LocalMoleMapper()

    init {
        every { db.moleDao() } returns mockk()
        every { context.contentResolver } returns resolver
        every { profile.ownerId } returns "owner"
        every { profile.requireOwnerId() } returns "owner"
    }

    private fun payload() = BackupPayload(2, 1000,
        listOf(mapper.toEntity(MoleData(id = "m", userId = "owner", analysisCount = 1, aiResult = "result",
            currentAnalysisId = "capture", imageUrl = "original.jpg"))),
        listOf(mapper.toEntity(AnalysisData(id = "capture", moleId = "m", imageUrl = "original.jpg"), "owner")),
        mapOf("original.jpg" to "images/${"a".repeat(64)}.bin"), AccountSettings())

    private fun encrypted(includeImage: Boolean): ByteArray {
        val output = ByteArrayOutputStream()
        EncryptedBackupCodec().encrypt(output, "fixture-password".toCharArray()) { plain ->
            val data = DataOutputStream(plain)
            val json = Gson().toJson(payload()).toByteArray(Charsets.UTF_8)
            data.writeInt(json.size); data.write(json); data.writeInt(if (includeImage) 1 else 0)
            if (includeImage) {
                data.writeUTF(payload().imageEntries.values.single())
                data.writeLong(5); data.write("photo".toByteArray())
            }
        }
        return output.toByteArray()
    }

    @Test fun `cancellation before IO dispatch wipes both import and export passwords`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler, "backup-io")
        val images = mockk<com.monsteraltech.skincare.data.image.PrivateImageStore>()
        val service = LocalBackupService(context, db, profile, settings, images,
            BackupRestoreCoordinator(images, dispatcher), records, dispatcher)
        val imported = "fixture-password".toCharArray()
        val importJob = async(start = CoroutineStart.UNDISPATCHED) { service.importFrom(uri, imported) }
        importJob.cancel(); runCurrent()
        assertTrue(importJob.isCancelled)
        assertTrue(imported.all { it == '\u0000' })
        val exported = "fixture-password".toCharArray()
        val exportJob = async(start = CoroutineStart.UNDISPATCHED) { service.exportTo(uri, exported) }
        exportJob.cancel(); runCurrent()
        assertTrue(exported.all { it == '\u0000' })
        coVerify(exactly = 0) { records.merge(any(), any(), any()) }
    }

    @Test fun `failure creating staging is reported and still wipes the password`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        every { context.cacheDir } returns temporary.newFile("not-a-directory")
        val images = mockk<com.monsteraltech.skincare.data.image.PrivateImageStore>()
        val service = LocalBackupService(context, db, profile, settings, images,
            BackupRestoreCoordinator(images, dispatcher), records, dispatcher)
        val password = "fixture-password".toCharArray()
        assertTrue(service.importFrom(uri, password).isFailure)
        assertTrue(password.all { it == '\u0000' })
        coVerify(exactly = 0) { records.merge(any(), any(), any()) }
    }

    @Test fun `missing declared image rejects the archive before mutation and cleans staging`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cache = temporary.newFolder("cache")
        every { context.cacheDir } returns cache
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(encrypted(false))
        val images = FilePrivateImageStore(temporary.newFolder("private"), temporary.newFolder("legacy"), cache)
        val service = LocalBackupService(context, db, profile, settings, images,
            BackupRestoreCoordinator(images, dispatcher), records, dispatcher)
        val password = "fixture-password".toCharArray()
        assertTrue(service.importFrom(uri, password).isFailure)
        assertTrue(password.all { it == '\u0000' })
        assertTrue(cache.listFiles()!!.isEmpty())
        coVerify(exactly = 0) { records.merge(any(), any(), any()) }
    }

    @Test fun `settings failure after a real archive import yields successful data with warning`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cache = temporary.newFolder("cache")
        every { context.cacheDir } returns cache
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(encrypted(true))
        val images = FilePrivateImageStore(temporary.newFolder("private"), temporary.newFolder("legacy"), cache)
        var saved = emptyMap<String, String>()
        coEvery { records.merge(any(), any(), any()) } coAnswers {
            saved = arg(2)
            CommittedRestore(BackupSummary(1, 1, 1), saved.values.toSet())
        }
        coEvery { settings.write(any(), "owner") } returns false
        val service = LocalBackupService(context, db, profile, settings, images,
            BackupRestoreCoordinator(images, dispatcher), records, dispatcher)
        val password = "fixture-password".toCharArray()
        val result = service.importFrom(uri, password).getOrThrow()
        assertTrue(RestoreWarning.SETTINGS_NOT_RESTORED in result.warnings)
        assertTrue(images.resolve(saved.values.single())?.isFile == true)
        assertTrue(cache.listFiles()!!.isEmpty())
        assertTrue(password.all { it == '\u0000' })
    }
}

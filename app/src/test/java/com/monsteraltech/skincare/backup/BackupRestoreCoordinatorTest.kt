package com.monsteraltech.skincare.backup

import com.monsteraltech.skincare.data.image.FilePrivateImageStore
import com.monsteraltech.skincare.data.image.PrivateImageStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
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
class BackupRestoreCoordinatorTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun images() = FilePrivateImageStore(temporary.newFolder("private"), temporary.newFolder("legacy"), temporary.newFolder("cache"))
    private fun source(name: String) = temporary.newFile(name).apply { writeText(name) }
    private fun committed(paths: Map<String, String>) = CommittedRestore(BackupSummary(1, 1, paths.size), paths.values.toSet())

    @Test fun `second copy failure removes only newly leased images`() = runTest {
        val real = images()
        val one = source("one")
        val existing = real.save(one, "owner")
        val leased = mutableListOf<String>()
        val failing = object : PrivateImageStore by real {
            override fun save(source: File, ownerId: String): String {
                if (source.name == "two") throw IOException("second copy")
                return real.save(source, ownerId).also(leased::add)
            }
        }
        val coordinator = BackupRestoreCoordinator(failing, StandardTestDispatcher(testScheduler))
        try {
            coordinator.restore("owner", mapOf("one" to one, "two" to source("two")), { fail("No commit"); committed(it) }, { true })
            fail("Expected copy error")
        } catch (_: IOException) { }
        assertTrue(real.resolve(existing)?.isFile == true)
        assertTrue(leased.all { real.resolve(it)?.isFile != true })
        assertTrue(one.isFile)
    }

    @Test fun `cancel immediately after save still registers and compensates the lease`() = runTest {
        val real = images()
        val leased = mutableListOf<String>()
        lateinit var job: Deferred<RestoreReport>
        val cancelling = object : PrivateImageStore by real {
            override fun save(source: File, ownerId: String) = real.save(source, ownerId).also {
                leased += it
                job.cancel()
            }
        }
        val coordinator = BackupRestoreCoordinator(cancelling, StandardTestDispatcher(testScheduler))
        job = async { coordinator.restore("owner", mapOf("one" to source("one")), { fail("No commit"); committed(it) }, { true }) }
        runCurrent()
        assertTrue(job.isCancelled)
        assertEquals(1, leased.size)
        assertFalse(real.resolve(leased.single())?.isFile == true)
    }

    @Test fun `database failure rolls back every newly copied image`() = runTest {
        val real = images()
        val leased = mutableListOf<String>()
        val tracking = object : PrivateImageStore by real {
            override fun save(source: File, ownerId: String) = real.save(source, ownerId).also(leased::add)
        }
        val coordinator = BackupRestoreCoordinator(tracking, StandardTestDispatcher(testScheduler))
        try {
            coordinator.restore("owner", mapOf("one" to source("one"), "two" to source("two")), { throw IOException("db failed") }, { true })
            fail("Expected database failure")
        } catch (_: IOException) { }
        assertEquals(2, leased.size)
        assertTrue(leased.all { real.resolve(it)?.isFile != true })
    }

    @Test fun `cancellation during commit retains referenced images and completes settings`() = runTest {
        val real = images()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var paths = emptyMap<String, String>()
        var settings = false
        val coordinator = BackupRestoreCoordinator(real, StandardTestDispatcher(testScheduler))
        val job = async { coordinator.restore("owner", mapOf("one" to source("one")), {
            paths = it
            started.complete(Unit)
            finish.await()
            committed(it)
        }, { settings = true; true }) }
        runCurrent()
        assertTrue(started.isCompleted)
        job.cancel()
        finish.complete(Unit)
        runCurrent()
        assertTrue(job.isCancelled)
        assertTrue(settings)
        assertTrue(real.resolve(paths.values.single())?.isFile == true)
    }

    @Test fun `settings returning false or throwing leaves restored data intact with a warning`() = runTest {
        val real = images()
        var references = emptyMap<String, String>()
        val coordinator = BackupRestoreCoordinator(real, StandardTestDispatcher(testScheduler))
        val falseResult = coordinator.restore("owner", mapOf("one" to source("one")), { references = it; committed(it) }, { false })
        assertTrue(RestoreWarning.SETTINGS_NOT_RESTORED in falseResult.warnings)
        assertTrue(real.resolve(references.values.single())?.isFile == true)
        val thrown = coordinator.restore("owner", emptyMap(), { committed(it) }, { throw IOException("prefs") })
        assertTrue(RestoreWarning.SETTINGS_NOT_RESTORED in thrown.warnings)
    }

    @Test fun `merge retaining existing files discards unused new copies`() = runTest {
        val real = images()
        var references = emptyMap<String, String>()
        val coordinator = BackupRestoreCoordinator(real, StandardTestDispatcher(testScheduler))
        val result = coordinator.restore("owner", mapOf("one" to source("one"), "two" to source("two")), {
            references = it
            CommittedRestore(BackupSummary(0, 1, 1), setOf(it.getValue("one")))
        }, { true })
        assertEquals(1, result.summary.imageCount)
        assertTrue(real.resolve(references.getValue("one"))?.isFile == true)
        assertFalse(real.resolve(references.getValue("two"))?.isFile == true)
    }

    @Test fun `failed rollback cleanup is suppressed on the original error`() = runTest {
        val real = images()
        var attempted = false
        val original = IOException("db")
        val failing = object : PrivateImageStore by real { override fun delete(reference: String) { attempted = true; throw IOException("cleanup") } }
        val coordinator = BackupRestoreCoordinator(failing, StandardTestDispatcher(testScheduler))
        try {
            coordinator.restore("owner", mapOf("one" to source("one")), { throw original }, { true })
            fail("Expected database failure")
        } catch (error: IOException) {
            assertEquals("db", error.message)
            assertTrue(attempted)
            val errors = generateSequence(error as Throwable?) { it.cause }.flatMap { it.suppressed.asSequence() }.toList()
            assertTrue("original=${original.suppressed.toList()}; caught=${error.suppressed.toList()}; cause=${error.cause}", errors.isNotEmpty())
            val cleanup = errors.first()
            assertEquals("cleanup", cleanup.message)
        }
    }

    @Test fun `failed postcommit cleanup is a warning rather than a failed restore`() = runTest {
        val real = images()
        val failing = object : PrivateImageStore by real { override fun delete(reference: String) { throw IOException("cleanup") } }
        val coordinator = BackupRestoreCoordinator(failing, StandardTestDispatcher(testScheduler))
        val result = coordinator.restore("owner", mapOf("one" to source("one")), {
            CommittedRestore(BackupSummary(0, 0, 0), emptySet())
        }, { true })
        assertTrue(RestoreWarning.TEMPORARY_FILES_REMAIN in result.warnings)
    }
}

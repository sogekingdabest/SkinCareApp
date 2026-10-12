package com.monsteraltech.skincare.data.image

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PrivateImagePersistenceTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun store() = FilePrivateImageStore(
        temporary.newFolder("private"), temporary.newFolder("legacy"), temporary.newFolder("cache")
    )
    private fun source() = temporary.newFile("capture.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test fun `guardar confirma el registro con una imagen que ya puede abrirse`() = runTest {
        val images = store()
        val writer = PrivateImagePersistence(images, UnconfinedTestDispatcher(testScheduler))
        val reference = writer.save(source(), "owner") { ref ->
            assertArrayEquals(byteArrayOf(1, 2, 3), images.open(ref).use { it.readBytes() })
            ref
        }
        assertTrue(requireNotNull(images.resolve(reference)).isFile)
    }

    @Test fun `un error en base de datos elimina solo la foto nueva`() = runTest {
        val images = store()
        val original = source()
        val previous = images.save(original, "owner")
        val writer = PrivateImagePersistence(images, UnconfinedTestDispatcher(testScheduler))
        var attempted: String? = null
        val error = runCatching {
            writer.save(original, "owner") { ref ->
                attempted = ref
                throw IOException("Database write failed")
            }
        }.exceptionOrNull()
        assertTrue(error is IOException)
        assertFalse(requireNotNull(images.resolve(requireNotNull(attempted))).exists())
        assertTrue(requireNotNull(images.resolve(previous)).isFile)
        assertTrue(original.isFile)
    }

    @Test fun `cancelar antes de guardar no crea una imagen ni llama al commit`() = runTest {
        val images = store()
        val writer = PrivateImagePersistence(images, UnconfinedTestDispatcher(testScheduler))
        var committed = false
        val job = launch(start = CoroutineStart.LAZY) {
            writer.save(source(), "owner") { committed = true }
        }
        job.cancel()
        job.start()
        job.join()
        assertFalse(committed)
    }

    @Test fun `cancelar durante el commit no elimina la imagen que referencia el registro`() = runTest {
        val images = store()
        val writer = PrivateImagePersistence(images, UnconfinedTestDispatcher(testScheduler))
        var committed: String? = null
        lateinit var job: Job
        job = launch(start = CoroutineStart.LAZY) {
            writer.save(source(), "owner") { ref ->
                job.cancel()
                committed = ref
            }
        }
        job.start()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(requireNotNull(images.resolve(requireNotNull(committed))).isFile)
    }
}

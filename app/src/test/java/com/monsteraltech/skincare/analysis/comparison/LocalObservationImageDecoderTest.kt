package com.monsteraltech.skincare.analysis.comparison

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.monsteraltech.skincare.body.mole.domain.model.MoleObservation
import com.monsteraltech.skincare.data.image.FilePrivateImageStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LocalObservationImageDecoderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bytes = byteArrayOf(1, 2, 3)
    private val readBytes = mutableListOf<ByteArray>()
    private val bitmap = mockk<Bitmap>()
    private var sampleSize = 0

    @Before fun setUp() {
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeStream(any(), isNull(), any()) } answers {
            val options = thirdArg<BitmapFactory.Options>()
            readBytes += firstArg<InputStream>().readBytes()
            if (options.inJustDecodeBounds) {
                options.outWidth = 8192
                options.outHeight = 4096
                null
            } else {
                sampleSize = options.inSampleSize
                bitmap
            }
        }
    }

    @After fun tearDown() { unmockkStatic(BitmapFactory::class) }

    @Test fun `foto guardada y referencia de observacion llegan al decodificador real`() = runTest {
        val privateRoot = temporary.newFolder("private")
        val legacyRoot = temporary.newFolder("legacy")
        val cacheRoot = temporary.newFolder("cache")
        val images = FilePrivateImageStore(privateRoot, legacyRoot, cacheRoot)
        val source = File(cacheRoot, "capture.jpg").apply { writeBytes(bytes) }
        val reference = images.save(source, "owner")
        val observation = MoleObservation("observation", "mole", reference, 1L)
        val decoder = LocalObservationImageDecoder(mockk<Context>(), images, UnconfinedTestDispatcher(testScheduler))
        assertSame(bitmap, decoder.decode(observation.imageReference))
        assertEquals(2, readBytes.size)
        readBytes.forEach { assertArrayEquals(bytes, it) }
        assertEquals(4, sampleSize)
    }

    @Test fun `decodificar una referencia heredada migra en IO y abre la copia privada`() = runTest {
        val privateRoot = temporary.newFolder("private")
        val legacyRoot = temporary.newFolder("legacy")
        val cacheRoot = temporary.newFolder("cache")
        val reference = "mole_images/owner/old.jpg"
        val old = File(legacyRoot, reference).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        val images = FilePrivateImageStore(privateRoot, legacyRoot, cacheRoot)
        val decoder = LocalObservationImageDecoder(mockk<Context>(), images, UnconfinedTestDispatcher(testScheduler))
        assertSame(bitmap, decoder.decode(reference))
        assertFalse(old.exists())
        assertTrue(File(privateRoot, reference).isFile)
        readBytes.forEach { assertArrayEquals(bytes, it) }
    }

    @Test fun `cancelar al entregar el bitmap libera la imagen decodificada`() = runTest {
        val privateRoot = temporary.newFolder("private")
        val legacyRoot = temporary.newFolder("legacy")
        val cacheRoot = temporary.newFolder("cache")
        val images = FilePrivateImageStore(privateRoot, legacyRoot, cacheRoot)
        val reference = images.save(File(cacheRoot, "capture.jpg").apply { writeBytes(bytes) }, "owner")
        val decoder = LocalObservationImageDecoder(mockk<Context>(), images, UnconfinedTestDispatcher(testScheduler))
        lateinit var job: Job
        every { bitmap.recycle() } returns Unit
        every { BitmapFactory.decodeStream(any(), isNull(), any()) } answers {
            val options = thirdArg<BitmapFactory.Options>()
            if (options.inJustDecodeBounds) {
                options.outWidth = 1024
                options.outHeight = 1024
                null
            } else {
                job.cancel()
                bitmap
            }
        }
        job = launch(start = CoroutineStart.LAZY) { decoder.decode(reference) }
        job.start()
        job.join()
        assertTrue(job.isCancelled)
        verify(exactly = 1) { bitmap.recycle() }
    }
}

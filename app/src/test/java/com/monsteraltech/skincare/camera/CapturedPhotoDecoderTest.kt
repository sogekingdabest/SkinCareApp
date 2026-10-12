package com.monsteraltech.skincare.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapturedPhotoDecoderTest {
    @Before fun setUp() { mockkStatic(BitmapFactory::class) }
    @After fun tearDown() { unmockkStatic(BitmapFactory::class) }

    @Test fun `an unreadable photo returns no bitmap`() = runTest {
        every { BitmapFactory.decodeFile("missing.jpg") } returns null
        val decoder = FileCapturedPhotoDecoder(UnconfinedTestDispatcher(testScheduler))
        assertNull(decoder.decode("missing.jpg", false))
    }

    @Test fun `the caller owns a delivered photo`() = runTest {
        val bitmap = mockk<Bitmap>(relaxed = true)
        every { BitmapFactory.decodeFile("photo.jpg") } returns bitmap
        val decoder = FileCapturedPhotoDecoder(UnconfinedTestDispatcher(testScheduler))
        assertSame(bitmap, decoder.decode("photo.jpg", false))
        verify(exactly = 0) { bitmap.recycle() }
    }

    @Test fun `cancellation while decoding releases the undelivered photo`() = runTest {
        lateinit var job: Job
        val bitmap = mockk<Bitmap>(relaxed = true)
        every { BitmapFactory.decodeFile("photo.jpg") } answers { job.cancel(); bitmap }
        val decoder = FileCapturedPhotoDecoder(UnconfinedTestDispatcher(testScheduler))
        job = launch(start = CoroutineStart.LAZY) { decoder.decode("photo.jpg", false) }
        job.start()
        job.join()
        assertTrue(job.isCancelled)
        verify(exactly = 1) { bitmap.recycle() }
    }
}

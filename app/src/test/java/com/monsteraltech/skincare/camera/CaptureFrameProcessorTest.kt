package com.monsteraltech.skincare.camera

import androidx.camera.core.ImageProxy
import com.monsteraltech.skincare.camera.guidance.SimplifiedGuidanceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureFrameProcessorTest {
    private val frame = mockk<DecodedCameraFrame>(relaxed = true) {
        every { isEmpty } returns false
        every { width } returns 64
        every { height } returns 32
    }
    private val result = mockk<SimplifiedGuidanceManager.GuidanceResult>()
    private var time = 1000L
    private var decoded = 0
    private var delivered = 0
    private var errors = 0
    private var resets = 0
    private var closed = 0
    private var includeColor = false
    private var analyze: suspend () -> SimplifiedGuidanceManager.GuidanceResult = { result }
    private var decode: () -> DecodedCameraFrame = { frame }
    private val engine = object : CaptureGuidanceEngine {
        override suspend fun analyze(frame: DecodedCameraFrame) = analyze()
        override fun reset() { resets++ }
        override fun close() { closed++ }
    }
    private val decoder = object : CameraFrameDecoder {
        override fun decode(image: ImageProxy, includeColor: Boolean): DecodedCameraFrame {
            decoded++
            this@CaptureFrameProcessorTest.includeColor = includeColor
            return decode()
        }
    }

    private fun processor(scope: CoroutineScope, dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        CaptureFrameProcessor(scope, decoder, engine, dispatcher, dispatcher, true, { time },
            { received, width, height ->
                assertSame(result, received)
                assertEquals(64, width)
                assertEquals(32, height)
                delivered++
            }, { errors++ })

    @Test fun `disabled frames close without decoding`() = runTest {
        val processor = processor(backgroundScope, StandardTestDispatcher(testScheduler))
        val proxy = mockk<ImageProxy>(relaxed = true)
        processor.submit(proxy)
        assertEquals(0, decoded)
        verify(exactly = 1) { proxy.close() }
        processor.close()
        processor.close()
        assertEquals(1, closed)
    }

    @Test fun `accepted frame delivers dimensions and releases both resources`() = runTest {
        val processor = processor(this, StandardTestDispatcher(testScheduler))
        val proxy = mockk<ImageProxy>(relaxed = true)
        processor.start()
        processor.submit(proxy)
        advanceUntilIdle()
        assertEquals(1, delivered)
        assertEquals(1, resets)
        assertTrue(includeColor)
        verify(exactly = 1) { frame.close() }
        verify(exactly = 1) { proxy.close() }
        processor.close()
    }

    @Test fun `backpressure and throttle close rejected frames`() = runTest {
        val processor = processor(this, StandardTestDispatcher(testScheduler))
        val first = mockk<ImageProxy>(relaxed = true)
        val busy = mockk<ImageProxy>(relaxed = true)
        val throttled = mockk<ImageProxy>(relaxed = true)
        processor.start()
        processor.submit(first)
        processor.submit(busy)
        advanceUntilIdle()
        time += 99
        processor.submit(throttled)
        assertEquals(1, decoded)
        time++
        processor.submit(mockk(relaxed = true))
        advanceUntilIdle()
        assertEquals(2, decoded)
        assertEquals(1, resets)
        verify(exactly = 1) { busy.close() }
        verify(exactly = 1) { throttled.close() }
        processor.close()
    }

    @Test fun `cancelled owner closes an accepted proxy even before body starts`() = runTest {
        val owner = Job()
        val processor = processor(CoroutineScope(owner + StandardTestDispatcher(testScheduler)),
            StandardTestDispatcher(testScheduler))
        val proxy = mockk<ImageProxy>(relaxed = true)
        processor.start()
        processor.submit(proxy)
        owner.cancel()
        advanceUntilIdle()
        assertEquals(0, decoded)
        verify(exactly = 1) { proxy.close() }
        processor.close()
        assertEquals(1, closed)
    }

    @Test fun `an already cancelled owner cannot leak an accepted frame`() = runTest {
        val owner = Job().apply { cancel() }
        val processor = processor(CoroutineScope(owner + StandardTestDispatcher(testScheduler)),
            StandardTestDispatcher(testScheduler))
        val proxy = mockk<ImageProxy>(relaxed = true)
        processor.start()
        processor.submit(proxy)
        advanceUntilIdle()
        assertEquals(0, decoded)
        verify(exactly = 1) { proxy.close() }
        processor.close()
    }

    @Test fun `pausing rejects a late result and resuming resets guidance`() = runTest {
        lateinit var processor: CaptureFrameProcessor
        processor = processor(this, StandardTestDispatcher(testScheduler))
        analyze = {
            processor.stop()
            processor.start()
            result
        }
        processor.start()
        processor.submit(mockk(relaxed = true))
        advanceUntilIdle()
        assertEquals(0, delivered)
        analyze = { result }
        processor.submit(mockk(relaxed = true))
        advanceUntilIdle()
        assertEquals(1, delivered)
        assertEquals(2, resets)
        processor.close()
    }

    @Test fun `closing waits for in flight work before releasing the engine`() = runTest {
        var stopped = false
        analyze = { try { awaitCancellation() } finally { stopped = true } }
        val processor = processor(this, StandardTestDispatcher(testScheduler))
        val proxy = mockk<ImageProxy>(relaxed = true)
        processor.start()
        processor.submit(proxy)
        runCurrent()
        processor.close()
        assertEquals(0, closed)
        advanceUntilIdle()
        assertTrue(stopped)
        assertEquals(1, closed)
        assertEquals(0, errors)
        verify(exactly = 1) { frame.close() }
        verify(exactly = 1) { proxy.close() }
        processor.submit(mockk(relaxed = true))
        processor.close()
        assertEquals(1, closed)
    }

    @Test fun `conversion failure closes proxy and allows a subsequent frame`() = runTest {
        val processor = processor(this, StandardTestDispatcher(testScheduler))
        val proxy = mockk<ImageProxy>(relaxed = true)
        decode = { error("broken plane") }
        processor.start()
        processor.submit(proxy)
        advanceUntilIdle()
        assertEquals(1, errors)
        verify(exactly = 1) { proxy.close() }
        time += 100
        decode = { frame }
        processor.submit(mockk(relaxed = true))
        advanceUntilIdle()
        assertEquals(1, delivered)
        processor.close()
    }

    @Test fun `guidance failure releases the decoded frame`() = runTest {
        analyze = { error("classifier failed") }
        val processor = processor(this, StandardTestDispatcher(testScheduler))
        processor.start()
        processor.submit(mockk(relaxed = true))
        advanceUntilIdle()
        assertEquals(1, errors)
        assertEquals(0, delivered)
        verify(exactly = 1) { frame.close() }
        processor.close()
    }
}

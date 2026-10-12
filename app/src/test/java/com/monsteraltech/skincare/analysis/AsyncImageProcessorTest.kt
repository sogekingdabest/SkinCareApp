package com.monsteraltech.skincare.analysis

import android.graphics.Bitmap
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AsyncImageProcessorTest {
    private val image = mockk<Bitmap>(relaxed = true)
    private val engines = mockk<AnalysisEngines>()
    private val ai = MelanomaAIDetector.AIInferenceResult(0.3f, MelanomaAIDetector.RiskLevel.LOW, 0.8f)
    private val abcde = mockk<ABCDEAnalyzerOpenCV.ABCDEResult>()
    private val combined = mockk<MelanomaAIDetector.CombinedAnalysisResult>()

    init {
        every { engines.prepare(image, any()) } returns image
        coEvery { engines.infer(any()) } coAnswers { delay(50); ai }
        coEvery { engines.analyzeABCDE(any(), any(), any()) } coAnswers { delay(100); abcde }
        every { engines.combine(ai, abcde) } returns combined
    }

    @Test fun `complete result combines two actual signals`() = runTest {
        val runner: ImageAnalysisRunner = AsyncImageProcessor(engines, StandardTestDispatcher(testScheduler))
        assertEquals(AnalysisOutcome.Complete(combined), runner.processImage(image))
        verify(exactly = 1) { engines.combine(ai, abcde) }
        verify(exactly = 0) { image.recycle() }
    }

    @Test fun `AI failure lets parallel ABCDE finish without combination`() = runTest {
        coEvery { engines.infer(any()) } throws AnalysisError.AIModelError("missing model")
        val runner: ImageAnalysisRunner = AsyncImageProcessor(engines, StandardTestDispatcher(testScheduler))
        val result = runner.processImage(image) as AnalysisOutcome.Partial
        assertEquals(AnalysisSignal.Reason.ERROR, (result.ai as AnalysisSignal.Unavailable).reason)
        assertEquals(AnalysisSignal.Available(abcde), result.abcde)
        verify(exactly = 0) { engines.combine(any(), any()) }
    }

    @Test fun `ABCDE failure preserves the actual AI signal`() = runTest {
        coEvery { engines.analyzeABCDE(any(), any(), any()) } throws AnalysisError.ABCDEAnalysisError("segmentation")
        val runner: ImageAnalysisRunner = AsyncImageProcessor(engines, StandardTestDispatcher(testScheduler))
        val result = runner.processImage(image) as AnalysisOutcome.Partial
        assertEquals(AnalysisSignal.Available(ai), result.ai)
        assertEquals(AnalysisSignal.Reason.ERROR, (result.abcde as AnalysisSignal.Unavailable).reason)
        verify(exactly = 0) { engines.combine(any(), any()) }
    }

    @Test fun `both engines failing yields failure without fabricated scores`() = runTest {
        coEvery { engines.infer(any()) } throws AnalysisError.AIModelError("missing")
        coEvery { engines.analyzeABCDE(any(), any(), any()) } throws AnalysisError.ABCDEAnalysisError("invalid")
        val runner: ImageAnalysisRunner = AsyncImageProcessor(engines, StandardTestDispatcher(testScheduler))
        assertTrue(runner.processImage(image) is AnalysisOutcome.Failed)
        verify(exactly = 0) { engines.combine(any(), any()) }
    }

    @Test fun `sequential execution also preserves independent failure`() = runTest {
        coEvery { engines.infer(any()) } throws AnalysisError.AIModelError("missing")
        val runner: ImageAnalysisRunner = AsyncImageProcessor(engines, StandardTestDispatcher(testScheduler))
        val result = runner.processImage(image, config = AnalysisConfiguration(enableParallelProcessing = false)) as AnalysisOutcome.Partial
        assertEquals(AnalysisSignal.Available(abcde), result.abcde)
    }

    @Test fun `disabled engine remains explicitly unavailable`() = runTest {
        val runner: ImageAnalysisRunner = AsyncImageProcessor(engines, StandardTestDispatcher(testScheduler))
        val result = runner.processImage(image, config = AnalysisConfiguration(enableAI = false)) as AnalysisOutcome.Partial
        assertEquals(AnalysisSignal.Reason.DISABLED, (result.ai as AnalysisSignal.Unavailable).reason)
        coVerify(exactly = 0) { engines.infer(any()) }
        verify(exactly = 0) { engines.combine(any(), any()) }
    }

    @Test fun `AI timeout does not cancel ABCDE`() = runTest {
        val runner: ImageAnalysisRunner = AsyncImageProcessor(engines, StandardTestDispatcher(testScheduler))
        val result = runner.processImage(image, config = AnalysisConfiguration(aiTimeoutMs = 10)) as AnalysisOutcome.Partial
        assertEquals(AnalysisSignal.Reason.TIMEOUT, (result.ai as AnalysisSignal.Unavailable).reason)
        assertEquals(AnalysisSignal.Available(abcde), result.abcde)
    }

    @Test fun `ABCDE timeout does not discard AI`() = runTest {
        val runner: ImageAnalysisRunner = AsyncImageProcessor(engines, StandardTestDispatcher(testScheduler))
        val result = runner.processImage(image, config = AnalysisConfiguration(abcdeTimeoutMs = 60)) as AnalysisOutcome.Partial
        assertEquals(AnalysisSignal.Available(ai), result.ai)
        assertEquals(AnalysisSignal.Reason.TIMEOUT, (result.abcde as AnalysisSignal.Unavailable).reason)
    }

    @Test fun `total deadline produces failure and cancels unfinished work`() = runTest {
        var stopped = false
        coEvery { engines.analyzeABCDE(any(), any(), any()) } coAnswers {
            try { awaitCancellation() } finally { stopped = true }
        }
        val runner: ImageAnalysisRunner = AsyncImageProcessor(engines, StandardTestDispatcher(testScheduler))
        assertEquals(AnalysisOutcome.Failed(AnalysisError.Timeout), runner.processImage(image, config = AnalysisConfiguration(timeoutMs = 70)))
        assertTrue(stopped)
        verify(exactly = 0) { engines.combine(any(), any()) }
    }

    @Test fun `parent deadline is propagated instead of converted to branch timeout`() = runTest {
        val runner: ImageAnalysisRunner = AsyncImageProcessor(engines, StandardTestDispatcher(testScheduler))
        assertNull(withTimeoutOrNull(10) { runner.processImage(image) })
        verify(exactly = 0) { engines.combine(any(), any()) }
    }

    @Test fun `caller cancellation stops both children and emits no terminal progress`() = runTest {
        var stopped = 0
        val progress = mutableListOf<AnalysisProgress>()
        coEvery { engines.infer(any()) } coAnswers { try { awaitCancellation() } finally { stopped++ } }
        coEvery { engines.analyzeABCDE(any(), any(), any()) } coAnswers { try { awaitCancellation() } finally { stopped++ } }
        val runner: ImageAnalysisRunner = AsyncImageProcessor(engines, StandardTestDispatcher(testScheduler))
        val job = async { runner.processImage(image, onProgress = progress::add) }
        runCurrent()
        job.cancel()
        runCurrent()
        assertTrue(job.isCancelled)
        assertEquals(2, stopped)
        assertFalse(progress.any { it.stage == ProcessingStage.FINALIZING })
        verify(exactly = 0) { engines.combine(any(), any()) }
    }

    @Test fun `engine cancellation is never treated as a technical failure`() = runTest {
        coEvery { engines.infer(any()) } throws CancellationException("cancelled")
        val runner: ImageAnalysisRunner = AsyncImageProcessor(engines, StandardTestDispatcher(testScheduler))
        try {
            runner.processImage(image)
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        verify(exactly = 0) { engines.combine(any(), any()) }
    }

    @Test fun `invalid configuration and image never reach native engines`() = runTest {
        val runner: ImageAnalysisRunner = AsyncImageProcessor(engines, StandardTestDispatcher(testScheduler))
        assertTrue(runner.processImage(image, config = AnalysisConfiguration(enableAI = false, enableABCDE = false)) is AnalysisOutcome.Failed)
        every { engines.prepare(image, any()) } throws AnalysisError.InvalidImageError("invalid")
        assertTrue(runner.processImage(image) is AnalysisOutcome.Failed)
        coVerify(exactly = 0) { engines.infer(any()) }
        coVerify(exactly = 0) { engines.analyzeABCDE(any(), any(), any()) }
    }

    @Test fun `temporary bitmap is released after children stop on cancellation`() = runTest {
        val temporary = mockk<Bitmap>(relaxed = true)
        every { engines.prepare(image, any()) } returns temporary
        var stopped = 0
        coEvery { engines.infer(any()) } coAnswers { try { awaitCancellation() } finally { stopped++ } }
        coEvery { engines.analyzeABCDE(any(), any(), any()) } coAnswers { try { awaitCancellation() } finally { stopped++ } }
        every { temporary.recycle() } answers { assertEquals(2, stopped) }
        val runner: ImageAnalysisRunner = AsyncImageProcessor(engines, StandardTestDispatcher(testScheduler))
        val job = async { runner.processImage(image) }
        runCurrent()
        job.cancel()
        runCurrent()
        verify(exactly = 1) { temporary.recycle() }
        verify(exactly = 0) { image.recycle() }
    }

    @Test fun `native library and memory errors produce unavailable signals`() = runTest {
        coEvery { engines.infer(any()) } throws UnsatisfiedLinkError("native")
        val runner: ImageAnalysisRunner = AsyncImageProcessor(engines, StandardTestDispatcher(testScheduler))
        assertTrue(runner.processImage(image) is AnalysisOutcome.Partial)
        coEvery { engines.infer(any()) } throws OutOfMemoryError("native")
        assertTrue(runner.processImage(image) is AnalysisOutcome.Partial)
        verify(exactly = 0) { engines.combine(any(), any()) }
    }
}

package com.monsteraltech.skincare.camera

import androidx.camera.core.ImageProxy
import com.monsteraltech.skincare.camera.guidance.SimplifiedGuidanceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface CaptureGuidanceEngine {
    suspend fun analyze(frame: DecodedCameraFrame): SimplifiedGuidanceManager.GuidanceResult
    fun reset()
    fun close()
}

/** One frame at a time, with session invalidation and resource ownership until job completion. */
class CaptureFrameProcessor(
    private val scope: CoroutineScope,
    private val decoder: CameraFrameDecoder,
    private val guidance: CaptureGuidanceEngine,
    private val workerDispatcher: CoroutineDispatcher,
    private val mainDispatcher: CoroutineDispatcher,
    private val includeColor: Boolean,
    private val nowMillis: () -> Long,
    private val onResult: (SimplifiedGuidanceManager.GuidanceResult, Int, Int) -> Unit,
    private val onError: (Exception) -> Unit,
    private val throttleMillis: Long = 100L
) {
    private val lock = Any()
    private var enabled = false
    private var closed = false
    private var engineClosed = false
    private var session = 0L
    private var lastResetSession = -1L
    private var lastAccepted: Long? = null
    private var activeJob: Job? = null

    fun start() = synchronized(lock) {
        if (!closed) {
            enabled = true
            session++
            lastAccepted = null
        }
    }

    fun stop() {
        val job = synchronized(lock) {
            enabled = false
            session++
            activeJob
        }
        job?.cancel()
    }

    fun submit(image: ImageProxy) {
        synchronized(lock) {
            val now = nowMillis()
            val last = lastAccepted
            if (!enabled || closed || activeJob != null ||
                (last != null && now - last < throttleMillis)) {
                image.close()
                return
            }
            val acceptedSession = session
            lastAccepted = now
            val job = scope.launch(workerDispatcher, start = CoroutineStart.LAZY) {
                try {
                    decoder.decode(image, includeColor).use { frame ->
                        currentCoroutineContext().ensureActive()
                        if (frame.isEmpty) return@use
                        if (lastResetSession != acceptedSession) {
                            guidance.reset()
                            lastResetSession = acceptedSession
                        }
                        val result = guidance.analyze(frame)
                        currentCoroutineContext().ensureActive()
                        withContext(mainDispatcher) {
                            synchronized(lock) {
                                if (enabled && !closed && session == acceptedSession) {
                                    onResult(result, frame.width, frame.height)
                                }
                            }
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    onError(error)
                }
            }
            activeJob = job
            // This runs even if the owner was cancelled before the coroutine body could start.
            job.invokeOnCompletion {
                try { image.close() } finally {
                    val releaseEngine = synchronized(lock) {
                        activeJob = null
                        if (closed && !engineClosed) {
                            engineClosed = true
                            true
                        } else false
                    }
                    if (releaseEngine) guidance.close()
                }
            }
            job.start()
        }
    }

    fun close() {
        val (job, releaseEngine) = synchronized(lock) {
            closed = true
            enabled = false
            session++
            val release = activeJob == null && !engineClosed
            if (release) engineClosed = true
            activeJob to release
        }
        job?.cancel()
        if (releaseEngine) guidance.close()
    }
}

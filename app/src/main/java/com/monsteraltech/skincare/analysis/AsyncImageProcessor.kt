package com.monsteraltech.skincare.analysis

import android.graphics.Bitmap
import com.monsteraltech.skincare.di.DefaultDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

class AsyncImageProcessor @Inject constructor(
    private val engines: AnalysisEngines,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) : ImageAnalysisRunner {
    private val processingMutex = Mutex()

    override suspend fun processImage(
        bitmap: Bitmap,
        previousBitmap: Bitmap?,
        pixelDensity: Float,
        config: AnalysisConfiguration,
        onProgress: (AnalysisProgress) -> Unit
    ): AnalysisOutcome = withContext(dispatcher) {
        processingMutex.withLock {
            if (!config.validate() || !pixelDensity.isFinite() || pixelDensity <= 0f) {
                return@withLock AnalysisOutcome.Failed(AnalysisError.ConfigurationError("Configuración inválida"))
            }
            var prepared: Bitmap? = null
            try {
                withTimeoutOrNull(config.timeoutMs) {
                    report(onProgress, ProcessingStage.INITIALIZING, 0, "Preparando imagen")
                    prepared = engines.prepare(bitmap, config)
                    currentCoroutineContext().ensureActive()
                    report(onProgress, ProcessingStage.PREPROCESSING, 100, "Imagen preparada")
                    val image = requireNotNull(prepared)
                    val baseline = previousBitmap.takeIf { config.enableEvolution }
                    val (ai, abcde) = if (config.enableParallelProcessing) {
                        coroutineScope {
                            // Recoverable failures become values inside each child, so a sibling keeps running.
                            val aiTask = async { aiSignal(image, config, onProgress) }
                            val abcdeTask = async { abcdeSignal(image, baseline, pixelDensity, config, onProgress) }
                            aiTask.await() to abcdeTask.await()
                        }
                    } else {
                        aiSignal(image, config, onProgress) to abcdeSignal(image, baseline, pixelDensity, config, onProgress)
                    }
                    currentCoroutineContext().ensureActive()
                    val outcome = when {
                        ai is AnalysisSignal.Available && abcde is AnalysisSignal.Available ->
                            AnalysisOutcome.Complete(engines.combine(ai.value, abcde.value))
                        ai is AnalysisSignal.Available || abcde is AnalysisSignal.Available ->
                            AnalysisOutcome.Partial(ai, abcde)
                        else -> AnalysisOutcome.Failed(AnalysisError.UnknownError("No se pudo completar IA ni ABCDE"))
                    }
                    currentCoroutineContext().ensureActive()
                    report(onProgress, ProcessingStage.FINALIZING, 100, "Procesamiento finalizado")
                    outcome
                } ?: AnalysisOutcome.Failed(AnalysisError.Timeout)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: AnalysisError) {
                AnalysisOutcome.Failed(error)
            } catch (error: OutOfMemoryError) {
                AnalysisOutcome.Failed(AnalysisError.OutOfMemory)
            } catch (error: LinkageError) {
                AnalysisOutcome.Failed(AnalysisError.ImageProcessingError("Motor nativo no disponible"))
            } catch (error: Exception) {
                AnalysisOutcome.Failed(AnalysisError.UnknownError(error.message ?: "Error inesperado", error))
            } finally {
                // Only the processor's temporary copy is owned here. Waited children no longer use it.
                prepared?.takeIf { it !== bitmap }?.recycle()
            }
        }
    }

    private suspend fun aiSignal(bitmap: Bitmap, config: AnalysisConfiguration, progress: (AnalysisProgress) -> Unit) =
        signal(config.enableAI, config.aiTimeoutMs, ProcessingStage.AI_ANALYSIS, "IA", progress) { engines.infer(bitmap) }

    private suspend fun abcdeSignal(
        bitmap: Bitmap, previousBitmap: Bitmap?, pixelDensity: Float,
        config: AnalysisConfiguration, progress: (AnalysisProgress) -> Unit
    ) = signal(config.enableABCDE, config.abcdeTimeoutMs, ProcessingStage.ABCDE_ANALYSIS, "ABCDE", progress) {
        engines.analyzeABCDE(bitmap, previousBitmap, pixelDensity)
    }

    private suspend fun <T : Any> signal(
        enabled: Boolean, timeout: Long, stage: ProcessingStage, name: String,
        progress: (AnalysisProgress) -> Unit, operation: suspend () -> T
    ): AnalysisSignal<T> {
        currentCoroutineContext().ensureActive()
        if (!enabled) return AnalysisSignal.Unavailable(AnalysisSignal.Reason.DISABLED, "$name desactivado")
        report(progress, stage, 0, "Analizando $name")
        val result = try {
            withTimeoutOrNull(timeout) {
                val value = operation()
                currentCoroutineContext().ensureActive()
                value
            }?.let { AnalysisSignal.Available(it) }
                ?: AnalysisSignal.Unavailable(AnalysisSignal.Reason.TIMEOUT, "$name no terminó a tiempo")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: OutOfMemoryError) {
            AnalysisSignal.Unavailable(AnalysisSignal.Reason.ERROR, "$name no disponible: memoria insuficiente")
        } catch (error: LinkageError) {
            AnalysisSignal.Unavailable(AnalysisSignal.Reason.ERROR, "Motor $name no disponible")
        } catch (error: Exception) {
            AnalysisSignal.Unavailable(AnalysisSignal.Reason.ERROR, "No se pudo completar $name")
        }
        currentCoroutineContext().ensureActive()
        report(progress, stage, 100, if (result is AnalysisSignal.Available) "$name completado" else "$name no disponible")
        return result
    }

    private fun report(callback: (AnalysisProgress) -> Unit, stage: ProcessingStage, progress: Int, message: String) {
        callback(AnalysisProgress(stage, stage.getProgressUpToStage() + progress * stage.weight / 100, message))
    }

    override fun close() = engines.close()
}

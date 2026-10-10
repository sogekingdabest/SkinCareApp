package com.monsteraltech.skincare.backup

import com.monsteraltech.skincare.data.image.PrivateImageStore
import com.monsteraltech.skincare.di.IoDispatcher
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Owns new image leases until Room commits. Cancellation never removes a committed image. */
class BackupRestoreCoordinator @Inject constructor(
    private val images: PrivateImageStore,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    internal suspend fun restore(
        ownerId: String,
        sources: Map<String, File>,
        commit: suspend (Map<String, String>) -> CommittedRestore,
        writeSettings: suspend () -> Boolean
    ): RestoreReport = withContext(dispatcher) {
        val owned = mutableListOf<String>()
        var committed = false
        var failure: Throwable? = null
        try {
            val references = sources.mapValues { (_, source) ->
                currentCoroutineContext().ensureActive()
                // Register in this context before another cancellation checkpoint or filesystem lookup.
                images.save(source, ownerId).also { owned += it }
            }
            currentCoroutineContext().ensureActive()
            // Transport exceptions as values across coroutine boundaries, retaining cleanup diagnostics.
            val result = withContext(NonCancellable) {
                runCatching {
                    val saved = commit(references)
                    committed = true
                    val warnings = saved.warnings.toMutableSet()
                    val unused = owned.filter { it !in saved.retainedImageReferences }
                    if (deleteImages(unused).isNotEmpty()) warnings += RestoreWarning.TEMPORARY_FILES_REMAIN
                    val settingsWritten = try { writeSettings() } catch (_: Exception) { false }
                    if (!settingsWritten) warnings += RestoreWarning.SETTINGS_NOT_RESTORED
                    RestoreReport(saved.summary, warnings)
                }
            }
            failure = result.exceptionOrNull()
            result
        } catch (error: Throwable) {
            failure = error
            Result.failure(error)
        } finally {
            if (!committed) withContext(NonCancellable) {
                deleteImages(owned).forEach { failure?.addSuppressed(it) }
            }
        }
    }.getOrThrow()

    private fun deleteImages(references: List<String>): List<Exception> = buildList {
        references.forEach { reference ->
            try { images.delete(reference) } catch (error: Exception) { add(error) }
        }
    }
}

package com.monsteraltech.skincare.data.image

import com.monsteraltech.skincare.di.IoDispatcher
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Compensa el archivo nuevo si falla la escritura. Una escritura iniciada termina antes de cancelar. */
class PrivateImagePersistence @Inject constructor(
    private val images: PrivateImageStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun <T> save(
        source: File,
        ownerId: String,
        commit: suspend (String) -> T
    ): T = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        val reference = images.save(source, ownerId)
        try {
            currentCoroutineContext().ensureActive()
            // Mantener archivo y registro coherentes aunque se cierre la pantalla durante el commit.
            withContext(NonCancellable) { commit(reference) }
        } catch (error: Exception) {
            try {
                images.delete(reference)
            } catch (cleanupError: Exception) {
                error.addSuppressed(cleanupError)
            }
            throw error
        }
    }
}

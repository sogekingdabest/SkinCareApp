package com.monsteraltech.skincare.analysis.comparison

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.monsteraltech.skincare.data.image.PrivateImageStore
import com.monsteraltech.skincare.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class LocalObservationImageDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val images: PrivateImageStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun decode(reference: String): Bitmap? {
        var decoded: Bitmap? = null
        try {
            return withContext(ioDispatcher) {
                if (reference.isBlank()) return@withContext null
                images.migrateLegacy(reference)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                open(reference).use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                currentCoroutineContext().ensureActive()
                var sampleSize = 1
                while (bounds.outWidth / sampleSize > MAX_SIDE || bounds.outHeight / sampleSize > MAX_SIDE) {
                    sampleSize *= 2
                }
                open(reference).use {
                    decoded = BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
                }
                decoded
            }
        } catch (error: Exception) {
            decoded?.recycle()
            throw error
        }
    }

    private fun open(reference: String): InputStream? =
        if (reference.startsWith("content:", ignoreCase = true)) {
            context.contentResolver.openInputStream(Uri.parse(reference))
        } else images.open(reference)

    private companion object { const val MAX_SIDE = 2048 }
}

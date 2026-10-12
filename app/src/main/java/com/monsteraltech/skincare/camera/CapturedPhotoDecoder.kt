package com.monsteraltech.skincare.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.monsteraltech.skincare.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** The caller owns the returned bitmap. Cancellation before delivery releases it here. */
interface CapturedPhotoDecoder {
    suspend fun decode(path: String, isFrontCamera: Boolean): Bitmap?
}

class FileCapturedPhotoDecoder @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CapturedPhotoDecoder {
    override suspend fun decode(path: String, isFrontCamera: Boolean): Bitmap? {
        var owned: Bitmap? = null
        try {
            return withContext(ioDispatcher) {
                val decoded = BitmapFactory.decodeFile(path) ?: return@withContext null
                owned = decoded
                currentCoroutineContext().ensureActive()
                if (isFrontCamera) {
                    val mirrored = Bitmap.createBitmap(
                        decoded, 0, 0, decoded.width, decoded.height,
                        Matrix().apply { preScale(-1f, 1f) }, true
                    )
                    owned = mirrored
                    if (mirrored !== decoded) decoded.recycle()
                }
                currentCoroutineContext().ensureActive()
                owned
            }
        } catch (error: Exception) {
            owned?.recycle()
            throw error
        }
    }
}

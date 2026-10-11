package com.monsteraltech.skincare.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.Closeable
import javax.inject.Inject
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat

class DecodedCameraFrame(val grayscale: Mat, val color: Bitmap?) : Closeable {
    val width: Int get() = grayscale.cols()
    val height: Int get() = grayscale.rows()
    val isEmpty: Boolean get() = grayscale.empty()

    override fun close() {
        try { color?.recycle() } finally { grayscale.release() }
    }
}

interface CameraFrameDecoder {
    /** Does not close the proxy. The caller owns both the proxy and decoded frame. */
    fun decode(image: ImageProxy, includeColor: Boolean): DecodedCameraFrame
}

class OpenCvCameraFrameDecoder @Inject constructor() : CameraFrameDecoder {
    override fun decode(image: ImageProxy, includeColor: Boolean): DecodedCameraFrame {
        var color: Bitmap? = null
        var grayscale: Mat? = null
        try {
            // RGB conversion failure keeps the existing semantic-gate unavailable behavior.
            if (includeColor) color = decodeColor(image)
            val plane = image.planes[0]
            val samples = LuminancePlaneReader.read(
                plane.buffer, image.width, image.height, plane.rowStride, plane.pixelStride
            )
            val source = Mat(image.height, image.width, CvType.CV_8UC1)
            grayscale = source
            source.put(0, 0, samples)
            val rotation = when (image.imageInfo.rotationDegrees) {
                90 -> Core.ROTATE_90_CLOCKWISE
                180 -> Core.ROTATE_180
                270 -> Core.ROTATE_90_COUNTERCLOCKWISE
                else -> null
            }
            if (rotation != null) {
                val rotated = Mat()
                try { Core.rotate(source, rotated, rotation) } catch (error: Exception) {
                    rotated.release()
                    throw error
                }
                grayscale = rotated
                source.release()
            }
            return DecodedCameraFrame(checkNotNull(grayscale), color)
        } catch (error: Exception) {
            color?.recycle()
            grayscale?.release()
            throw error
        }
    }

    private fun decodeColor(image: ImageProxy): Bitmap? {
        var source: Bitmap? = null
        return try {
            val decoded = image.toBitmap()
            source = decoded
            val rotation = image.imageInfo.rotationDegrees.toFloat()
            if (rotation == 0f) decoded else {
                val oriented = Bitmap.createBitmap(
                    decoded, 0, 0, decoded.width, decoded.height,
                    Matrix().apply { postRotate(rotation) }, true
                )
                if (oriented !== decoded) decoded.recycle()
                oriented
            }
        } catch (error: Exception) {
            source?.recycle()
            Log.e("CameraFrameDecoder", "Error converting camera frame to RGB", error)
            null
        }
    }
}

package com.monsteraltech.skincare.camera.guidance

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min
import kotlin.math.roundToInt

/** Runs the private V6 semantic capture gate entirely on-device. */
class CaptureValidityClassifier(context: Context) : Closeable {
    companion object {
        private const val TAG = "CaptureValidity"
    }

    private val applicationContext = context.applicationContext
    private val inferenceLock = Any()

    @Volatile
    private var interpreter: Interpreter? = loadInterpreter()

    val isAvailable: Boolean
        get() = interpreter != null

    /**
     * Crops the square enclosing the circular camera guide, resizes it to
     * 192x192 and supplies RGB float values in [0, 1]. The model embeds the
     * subsequent conversion to MobileNetV3's [-1, 1] range.
     */
    fun classifyGuideRoi(
        orientedFrame: Bitmap,
        roiSideFraction: Float
    ): CaptureValidityContract.Prediction {
        val activeInterpreter = interpreter
            ?: throw IllegalStateException("Capture-validity model is unavailable")
        require(roiSideFraction > 0f && roiSideFraction <= 1f) {
            "ROI side fraction must be in (0, 1]"
        }

        val minimumSide = min(orientedFrame.width, orientedFrame.height)
        val cropSide = (minimumSide * roiSideFraction)
            .roundToInt()
            .coerceIn(1, minimumSide)
        val left = (orientedFrame.width - cropSide) / 2
        val top = (orientedFrame.height - cropSide) / 2
        val crop = Bitmap.createBitmap(
            orientedFrame,
            left,
            top,
            cropSide,
            cropSide
        )
        val resized = Bitmap.createScaledBitmap(
            crop,
            CaptureValidityContract.IMAGE_SIZE,
            CaptureValidityContract.IMAGE_SIZE,
            true
        )

        try {
            val input = rgbFloatInput(resized)
            val output = Array(1) {
                FloatArray(CaptureValidityContract.OUTPUT_CLASSES)
            }
            synchronized(inferenceLock) {
                activeInterpreter.run(input, output)
            }
            return CaptureValidityContract.prediction(output[0])
        } finally {
            if (resized !== crop) resized.recycle()
            if (crop !== orientedFrame) crop.recycle()
        }
    }

    private fun rgbFloatInput(bitmap: Bitmap): ByteBuffer {
        val size = CaptureValidityContract.IMAGE_SIZE
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        return ByteBuffer.allocateDirect(4 * size * size * 3)
            .order(ByteOrder.nativeOrder())
            .apply {
                for (pixel in pixels) {
                    putFloat(((pixel shr 16) and 0xFF) / 255f)
                    putFloat(((pixel shr 8) and 0xFF) / 255f)
                    putFloat((pixel and 0xFF) / 255f)
                }
                rewind()
            }
    }

    private fun loadInterpreter(): Interpreter? = try {
        val loaded = Interpreter(
            loadMappedModel(),
            Interpreter.Options().apply {
                numThreads = 2
                useNNAPI = false
            }
        )
        try {
            validateModelContract(loaded)
            loaded
        } catch (error: Exception) {
            loaded.close()
            throw error
        }
    } catch (error: Exception) {
        Log.e(TAG, "Unable to load capture-validity V6", error)
        null
    }

    private fun validateModelContract(model: Interpreter) {
        val input = model.getInputTensor(0)
        val output = model.getOutputTensor(0)
        require(input.shape().contentEquals(intArrayOf(1, 192, 192, 3))) {
            "Unexpected capture-validity input shape: ${input.shape().contentToString()}"
        }
        require(input.dataType() == DataType.FLOAT32) {
            "Capture-validity input must be FLOAT32"
        }
        require(output.shape().contentEquals(intArrayOf(1, 3))) {
            "Unexpected capture-validity output shape: ${output.shape().contentToString()}"
        }
        require(output.dataType() == DataType.FLOAT32) {
            "Capture-validity output must be FLOAT32"
        }
    }

    private fun loadMappedModel(): MappedByteBuffer {
        return applicationContext.assets
            .openFd(CaptureValidityContract.MODEL_FILENAME)
            .use { asset ->
                FileInputStream(asset.fileDescriptor).channel.use { channel ->
                    channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        asset.startOffset,
                        asset.declaredLength
                    )
                }
            }
    }

    override fun close() {
        synchronized(inferenceLock) {
            interpreter?.close()
            interpreter = null
        }
    }
}

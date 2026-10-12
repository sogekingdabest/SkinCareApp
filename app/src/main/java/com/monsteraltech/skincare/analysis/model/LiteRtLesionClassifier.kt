package com.monsteraltech.skincare.analysis.model

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.google.gson.Gson
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Local-only interpreter with an explicit scalar-output contract and serialized lifecycle. */
class LiteRtLesionClassifier(
    assets: AssetManager,
    private val descriptor: LesionModelDescriptor,
    assetDirectory: String = ""
) : LesionClassifier {
    private val lock = Any()
    private var interpreter: Interpreter?

    init {
        descriptor.validate()
        require(assetDirectory.isEmpty() || assetDirectory.matches(Regex("[A-Za-z0-9_-]+")))
        val path = if (assetDirectory.isEmpty()) descriptor.filename else "$assetDirectory/${descriptor.filename}"
        val model = assets.openFd(path).use { asset ->
            FileInputStream(asset.fileDescriptor).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, asset.startOffset, asset.declaredLength)
            }
        }
        descriptor.verifyArtifact(model)
        val loaded = Interpreter(model, Interpreter.Options().apply { numThreads = 4; useNNAPI = false })
        try {
            require(loaded.inputTensorCount == descriptor.inputs.size)
            require(loaded.outputTensorCount == descriptor.outputs.size)
            descriptor.inputs.forEach { expected ->
                val actual = loaded.getInputTensor(expected.index)
                require(actual.name() == expected.name && actual.shape().toList() == expected.shape &&
                    actual.dataType().name == expected.dtype) { "Unexpected input tensor ${expected.index}" }
            }
            descriptor.outputs.forEach { expected ->
                val actual = loaded.getOutputTensor(expected.index)
                require(actual.name() == expected.name && actual.shape().toList() == expected.shape &&
                    actual.dataType().name == expected.dtype) { "Unexpected output tensor ${expected.index}" }
            }
            interpreter = loaded
        } catch (error: Exception) {
            loaded.close()
            throw error
        }
    }

    override fun predict(bitmap: Bitmap, metadata: FloatArray?): LesionPrediction = synchronized(lock) {
        val active = checkNotNull(interpreter) { "Classifier is closed" }
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0)
        require((descriptor.metadata != null) == (metadata != null)) { "Model metadata contract mismatch" }
        metadata?.let { require(it.size == 3 && it.all(Float::isFinite)) }
        val image = descriptor.image
        val resized = Bitmap.createScaledBitmap(bitmap, image.shape[2], image.shape[1], true)
        try {
            val pixels = IntArray(resized.width * resized.height)
            resized.getPixels(pixels, 0, resized.width, 0, 0, resized.width, resized.height)
            val inputs = arrayOfNulls<Any>(descriptor.inputs.size)
            inputs[image.index] = rgbInput(pixels, descriptor.imageNormalization)
            descriptor.metadata?.let { tensor ->
                inputs[tensor.index] = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder()).apply {
                    requireNotNull(metadata).forEach { putFloat(it) }
                    rewind()
                }
            }
            val output = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            active.runForMultipleInputsOutputs(inputs.requireNoNulls(), mapOf(0 to output))
            output.rewind()
            LesionPrediction(output.float, descriptor.identity())
        } finally {
            if (resized !== bitmap) resized.recycle()
        }
    }

    override fun close() = synchronized(lock) {
        interpreter?.close()
        interpreter = null
    }

    companion object {
        const val DEPLOYED_DESCRIPTOR = "lesion_classifier.json"

        fun readDescriptor(assets: AssetManager, filename: String): LesionModelDescriptor =
            assets.open(filename).bufferedReader().use { reader ->
                Gson().fromJson(reader, LesionModelDescriptor::class.java).also { it.validate() }
            }

        internal fun rgbInput(pixels: IntArray, normalization: ImageNormalization): ByteBuffer =
            ByteBuffer.allocateDirect(pixels.size * 3 * 4).order(ByteOrder.nativeOrder()).apply {
                for (pixel in pixels) {
                    putFloat(normalization.channel((pixel shr 16) and 0xff))
                    putFloat(normalization.channel((pixel shr 8) and 0xff))
                    putFloat(normalization.channel(pixel and 0xff))
                }
                rewind()
            }
    }
}

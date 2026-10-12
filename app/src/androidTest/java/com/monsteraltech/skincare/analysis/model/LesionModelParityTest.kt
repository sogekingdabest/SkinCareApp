package com.monsteraltech.skincare.analysis.model

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import org.tensorflow.lite.Interpreter
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LesionModelParityTest {
    @Test fun deployedModelPreservesPreviousAndroidInference() = verify("legacy")

    @Test fun privateCandidateMatchesPythonFixturesWhenStaged() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        assumeTrue("Stage the private candidate to run its parity test", assets.list("private-models")?.contains("candidate.json") == true)
        verify("candidate")
    }

    private fun verify(label: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testAssets = instrumentation.context.assets
        val modelAssets = if (label == "legacy") instrumentation.targetContext.assets else testAssets
        val descriptorFile = if (label == "legacy") LiteRtLesionClassifier.DEPLOYED_DESCRIPTOR else "private-models/candidate.json"
        val descriptor = LiteRtLesionClassifier.readDescriptor(modelAssets, descriptorFile)
        val fixtures = testAssets.open("model-parity/fixtures.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonArray("cases")
        }.filter { it.asJsonObject["model"].asString == label }
        assertEquals(3, fixtures.size)
        val classifier = LiteRtLesionClassifier(modelAssets, descriptor, if (label == "legacy") "" else "private-models")
        val timings = mutableListOf<Double>()
        val pythonErrors = mutableListOf<Double>()
        // The deployed dynamic export has cross-runtime drift. Preserve its original
        // Android invocation independently; keep the Python parity result visible.
        val legacyReference = if (label == "legacy") modelAssets.openFd(descriptor.filename).use { asset ->
            FileInputStream(asset.fileDescriptor).channel.use { channel ->
                Interpreter(channel.map(FileChannel.MapMode.READ_ONLY, asset.startOffset, asset.declaredLength),
                    Interpreter.Options().apply { numThreads = 4; useNNAPI = false })
            }
        } else null
        try {
            fixtures.forEach { value ->
                val fixture = value.asJsonObject
                assertEquals(descriptor.sha256, fixture["modelSha256"].asString)
                val bitmap = testAssets.open("model-parity/${fixture["image"].asString}").use { BitmapFactory.decodeStream(it) }
                requireNotNull(bitmap)
                try {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    val input = LiteRtLesionClassifier.rgbInput(pixels, descriptor.imageNormalization)
                    val hash = MessageDigest.getInstance("SHA-256").apply { update(input) }.digest()
                        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    assertEquals(fixture["imageInputSha256"].asString, hash)
                    val metadata = fixture["metadata"].takeUnless { it.isJsonNull }?.asJsonArray?.map { it.asFloat }?.toFloatArray()
                    val prediction = classifier.predict(bitmap, metadata)
                    val pythonError = kotlin.math.abs(fixture["expectedScore"].asDouble - prediction.score)
                    pythonErrors += pythonError
                    if (legacyReference == null) {
                        assertEquals(fixture["expectedScore"].asFloat, prediction.score, fixture["absoluteTolerance"].asFloat)
                    } else {
                        val legacyOutput = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
                        legacyReference.run(input.apply { rewind() }, legacyOutput)
                        legacyOutput.rewind()
                        assertEquals("Keep the previous Android result", legacyOutput.float, prediction.score, 0.000001f)
                    }
                    assertEquals(descriptor.identity(), prediction.model)
                    repeat(5) {
                        val started = System.nanoTime()
                        classifier.predict(bitmap, metadata)
                        timings += (System.nanoTime() - started) / 1_000_000.0
                    }
                } finally { bitmap.recycle() }
            }
            val ordered = timings.sorted()
            val report = """{"model":"$label","samples":${ordered.size},"medianMs":${ordered[ordered.size / 2]},"p95Ms":${ordered[(ordered.size * 0.95).toInt().coerceAtMost(ordered.lastIndex)]},"synthetic":true,"accuracyEvaluated":false,"pythonErrors":$pythonErrors,"pythonParityPassed":${pythonErrors.all { it <= 0.002 }},"legacyAndroidCompatibilityPassed":${legacyReference != null}}"""
            File(instrumentation.targetContext.getExternalFilesDir(null), "${label}_model_parity.json").writeText(report)
        } finally { classifier.close(); legacyReference?.close() }
    }
}

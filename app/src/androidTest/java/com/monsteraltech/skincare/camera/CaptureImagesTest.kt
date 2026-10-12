package com.monsteraltech.skincare.camera

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.analysis.AnalysisResultActivity
import java.io.File
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import dagger.hilt.android.EntryPointAccessors
import com.monsteraltech.skincare.testing.TestingDependencies
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

@RunWith(AndroidJUnit4::class)
class CaptureImagesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun prepareProfile() {
        EntryPointAccessors.fromApplication(context, TestingDependencies::class.java)
            .profile().activateLocalProfile()
    }

    @Test fun luminanceRotationUsesRealOpenCvAndBothSensorStrides() {
        assertTrue(OpenCVLoader.initLocal())
        val expected = mapOf(
            0 to byteArrayOf(1, 2, 3, 4, 5, 6),
            90 to byteArrayOf(4, 1, 5, 2, 6, 3),
            180 to byteArrayOf(6, 5, 4, 3, 2, 1),
            270 to byteArrayOf(3, 6, 2, 5, 1, 4)
        )
        for ((rotation, pixels) in expected) {
            val image = image(rotation)
            val frame = OpenCvCameraFrameDecoder().decode(image, false)
            try {
                assertEquals(if (rotation % 180 == 0) 3 else 2, frame.width)
                assertEquals(if (rotation % 180 == 0) 2 else 3, frame.height)
                val actual = ByteArray(6)
                frame.grayscale.get(0, 0, actual)
                assertArrayEquals(pixels, actual)
                assertNull(frame.color)
            } finally { frame.close(); image.close() }
            assertTrue(frame.grayscale.empty())
        }
    }

    @Test fun incompleteSensorPlaneCannotBecomeAnAnalyzableImage() {
        assertTrue(OpenCVLoader.initLocal())
        val image = image(90, byteArrayOf(1, 2))
        try {
            try {
                OpenCvCameraFrameDecoder().decode(image, false)
                fail("Expected an invalid luminance plane")
            } catch (_: IllegalArgumentException) { }
        } finally { image.close() }
    }

    @Test fun photoDecoderPreservesBackCameraAndMirrorsFrontCamera() = runBlocking {
        val file = fixture()
        val decoder = FileCapturedPhotoDecoder(Dispatchers.IO)
        try {
            val back = checkNotNull(decoder.decode(file.absolutePath, false))
            val front = checkNotNull(decoder.decode(file.absolutePath, true))
            try {
                assertEquals(Color.RED, back.getPixel(0, 0))
                assertEquals(Color.BLUE, back.getPixel(3, 0))
                assertEquals(Color.BLUE, front.getPixel(0, 0))
                assertEquals(Color.RED, front.getPixel(3, 0))
                assertEquals(4, front.width)
                assertEquals(2, front.height)
            } finally { back.recycle(); front.recycle() }
        } finally { file.delete() }
    }

    @Test fun missingPhotoReturnsNullOnTheAndroidDecoder() = runBlocking {
        assertNull(FileCapturedPhotoDecoder(Dispatchers.IO).decode(File(context.cacheDir, "missing-a5.png").path, true))
    }

    @Test fun previewReloadsItsMirroredPhotoAndReleasesItOnRecreation() {
        val file = fixture()
        val intent = Intent(context, PreviewActivity::class.java).apply {
            putExtra(MoleCaptureContract.EXTRA_PHOTO_PATH, file.absolutePath)
            putExtra(MoleCaptureContract.EXTRA_IS_FRONT_CAMERA, true)
        }
        try {
            ActivityScenario.launch<PreviewActivity>(intent).use { scenario ->
                val first = awaitPreview(scenario)
                scenario.recreate()
                val second = awaitPreview(scenario)
                assertTrue(first.isRecycled)
                assertFalse(second.isRecycled)
            }
        } finally { file.delete() }
    }

    @Test fun analysisRetainsItsPhotoOnRecreationAndReleasesItOnFinalDestruction() {
        val file = fixture()
        val intent = Intent(context, AnalysisResultActivity::class.java).apply {
            putExtra(MoleCaptureContract.EXTRA_PHOTO_PATH, file.absolutePath)
        }
        var retained: Bitmap? = null
        try {
            ActivityScenario.launch<AnalysisResultActivity>(intent).use { scenario ->
                retained = awaitResultImage(scenario)
                scenario.recreate()
                assertSame(retained, awaitResultImage(scenario))
                assertFalse(retained!!.isRecycled)
            }
            assertTrue(retained!!.isRecycled)
        } finally { file.delete() }
    }

    private fun awaitResultImage(scenario: ActivityScenario<AnalysisResultActivity>): Bitmap {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            var bitmap: Bitmap? = null
            scenario.onActivity { activity ->
                bitmap = (activity.findViewById<ImageView>(R.id.resultImageView).drawable as? BitmapDrawable)?.bitmap
            }
            bitmap?.let { return it }
            Thread.sleep(25)
        }
        error("Analysis did not display the decoded fixture")
    }

    private fun awaitPreview(scenario: ActivityScenario<PreviewActivity>): Bitmap {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            var bitmap: Bitmap? = null
            scenario.onActivity { activity ->
                if (activity.findViewById<android.view.View>(R.id.confirm_button).isEnabled) {
                    bitmap = (activity.findViewById<ImageView>(R.id.preview_image).drawable as BitmapDrawable).bitmap
                    assertEquals(Color.BLUE, bitmap!!.getPixel(0, 0))
                }
            }
            bitmap?.let { return it }
            Thread.sleep(25)
        }
        error("Preview did not display the decoded fixture")
    }

    private fun fixture(): File {
        val bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        for (y in 0..1) for (x in 2..3) bitmap.setPixel(x, y, Color.BLUE)
        val file = File.createTempFile("capture-a5-", ".png", context.cacheDir)
        try { file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        return file
    }

    private fun image(rotation: Int, bytes: ByteArray = byteArrayOf(1, 90, 2, 90, 3, 99, 4, 90, 5, 90, 6)): ImageProxy {
        val plane = proxy<ImageProxy.PlaneProxy> { method -> when (method) {
            "getBuffer" -> ByteBuffer.wrap(bytes)
            "getRowStride" -> 6
            "getPixelStride" -> 2
            else -> error(method)
        } }
        val info = proxy<ImageInfo> { method -> when (method) {
            "getRotationDegrees" -> rotation
            else -> error(method)
        } }
        return proxy { method -> when (method) {
            "getWidth" -> 3
            "getHeight" -> 2
            "getPlanes" -> arrayOf(plane)
            "getImageInfo" -> info
            "getFormat" -> ImageFormat.YUV_420_888
            "getCropRect" -> Rect(0, 0, 3, 2)
            "close" -> null
            else -> error(method)
        } }
    }

    private inline fun <reified T> proxy(crossinline invoke: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            invoke(method.name)
        } as T
}

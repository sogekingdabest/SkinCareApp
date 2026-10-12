package com.monsteraltech.skincare.data.image

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsteraltech.skincare.analysis.comparison.LocalObservationImageDecoder
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivateImageRoundTripTest {
    @Test fun savedReferenceDecodesThroughTheApplicationImageStore() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val images = privateImageStore(context)
        val original = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        val color = Color.rgb(40, 100, 180)
        original.eraseColor(color)
        val capture = File.createTempFile("private_image_test_", ".jpg", context.cacheDir)
        var reference: String? = null
        var decoded: Bitmap? = null
        try {
            capture.outputStream().use { assertTrue(original.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
            val savedReference = images.save(capture, "image-test-${UUID.randomUUID()}")
            reference = savedReference
            assertTrue(requireNotNull(images.resolve(savedReference)).toPath().startsWith(context.noBackupFilesDir.canonicalFile.toPath()))
            decoded = LocalObservationImageDecoder(context, images, Dispatchers.IO).decode(savedReference)
            val image = requireNotNull(decoded)
            assertEquals(64, image.width)
            assertEquals(48, image.height)
            val pixel = image.getPixel(32, 24)
            assertTrue(abs(Color.red(pixel) - Color.red(color)) <= 4)
            assertTrue(abs(Color.green(pixel) - Color.green(color)) <= 4)
            assertTrue(abs(Color.blue(pixel) - Color.blue(color)) <= 4)
            assertTrue(capture.isFile)
        } finally {
            reference?.let(images::delete)
            capture.delete()
            decoded?.recycle()
            original.recycle()
        }
    }
}

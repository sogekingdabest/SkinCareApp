package com.monsteraltech.skincare.analysis

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

@RunWith(AndroidJUnit4::class)
class AnalysisFailureTest {
    @Test fun preprocessingKeepsTheExistingDimensionLimitAndOriginalImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val engines = NativeAnalysisEngines(context)
        val bitmap = Bitmap.createBitmap(1536, 128, Bitmap.Config.ARGB_8888)
        var prepared: Bitmap? = null
        try {
            prepared = engines.prepare(bitmap, AnalysisConfiguration.default())
            assertEquals(1024, prepared.width)
            assertEquals(85, prepared.height)
            assertFalse(bitmap.isRecycled)
            assertSame(bitmap, engines.prepare(bitmap, AnalysisConfiguration(enableAutoImageReduction = false)))
        } finally {
            prepared?.takeIf { it !== bitmap }?.recycle()
            bitmap.recycle()
            engines.close()
        }
    }

    @Test fun unsegmentableImageIsAFailureInsteadOfSyntheticLowRisk() = runBlocking {
        assertTrue(OpenCVLoader.initLocal())
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(190, 155, 135))
        val runner: ImageAnalysisRunner = AsyncImageProcessor(NativeAnalysisEngines(context), Dispatchers.Default)
        try {
            val result = runner.processImage(bitmap, config = AnalysisConfiguration(enableAI = false))
            assertTrue("Expected failure for an image without a lesion, got $result", result is AnalysisOutcome.Failed)
            assertFalse(bitmap.isRecycled)
        } finally {
            runner.close()
            bitmap.recycle()
        }
    }

    @Test fun invalidBitmapCannotBecomeACombinedResult() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val runner: ImageAnalysisRunner = AsyncImageProcessor(NativeAnalysisEngines(context), Dispatchers.Default)
        try {
            val result = runner.processImage(bitmap)
            assertTrue(result is AnalysisOutcome.Failed && result.error is AnalysisError.InvalidImageError)
        } finally {
            runner.close()
            bitmap.recycle()
        }
    }
}

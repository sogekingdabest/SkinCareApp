package com.monsteraltech.skincare.camera

import androidx.lifecycle.SavedStateHandle
import com.monsteraltech.skincare.body.domain.BodyRegionId
import com.monsteraltech.skincare.data.identity.ActiveProfile
import io.mockk.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class CameraCaptureViewModelTest {
    private val id = "82574c2e-703a-4545-91af-a709a8fda2b9"
    private val drafts = mockk<CaptureDraftStore>(relaxed = true)
    private val profile = mockk<ActiveProfile> {
        every { ownerId } returns "fixture"
        every { requireOwnerId() } returns "fixture"
    }

    @Test fun `a completed photo waits for the screen and retains capture time arguments`() {
        every { drafts.create() } returns CaptureDraftStore.Draft(id, File("fixture.jpg"))
        val model = CameraCaptureViewModel(drafts, profile, SavedStateHandle())
        model.prepare(true, BodyRegionId.LEFT_ARM, "m")
        assertTrue(model.isCapturing.value)
        model.completed(id)
        assertEquals(CaptureArgs(id, true, BodyRegionId.LEFT_ARM, "m"), model.preview.value)
        verify(exactly = 1) { drafts.markReady(id) }
        model.previewOpened("wrong")
        assertNotNull(model.preview.value)
        model.previewOpened(id)
        assertNull(model.preview.value)
    }

    @Test fun `process restoration recovers a photo completed after state was saved`() {
        every { drafts.isReady(id) } returns true
        val saved = SavedStateHandle(mapOf("pendingCapture" to id, "captureOwner" to "fixture",
            "captureFront" to true, "captureRegion" to "torso", "captureMole" to "m"))
        val restored = CameraCaptureViewModel(drafts, profile, saved)
        assertEquals(CaptureArgs(id, true, BodyRegionId.TORSO, "m"), restored.preview.value)
        assertFalse(restored.isCapturing.value)
        verify(exactly = 0) { drafts.create() }
    }

    @Test fun `an unfinished camera write is discarded instead of treated as a completed photo`() {
        every { drafts.isReady(id) } returns false
        val restored = CameraCaptureViewModel(drafts, profile,
            SavedStateHandle(mapOf("pendingCapture" to id, "captureOwner" to "fixture")))
        assertNull(restored.preview.value)
        assertFalse(restored.isCapturing.value)
        verify(exactly = 1) { drafts.discard(id) }
    }
}

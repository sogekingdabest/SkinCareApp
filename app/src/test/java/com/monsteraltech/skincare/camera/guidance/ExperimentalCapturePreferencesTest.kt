package com.monsteraltech.skincare.camera.guidance

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalCapturePreferencesTest {
    @Test
    fun `enabled by default and saves user choice`() {
        val sharedPreferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every {
            sharedPreferences.getBoolean(
                "capture_validity_enabled",
                ExperimentalCapturePreferences.DEFAULT_CAPTURE_VALIDITY_ENABLED
            )
        } returns true
        every { sharedPreferences.edit() } returns editor
        every { editor.putBoolean("capture_validity_enabled", false) } returns editor
        val preferences = ExperimentalCapturePreferences(sharedPreferences)

        assertTrue(preferences.captureValidityEnabled)

        preferences.captureValidityEnabled = false

        verify(exactly = 1) { editor.putBoolean("capture_validity_enabled", false) }
        verify(exactly = 1) { editor.apply() }
    }

    @Test
    fun `feature remains disabled when model is unavailable`() {
        assertFalse(
            ExperimentalCapturePreferences.isCaptureValidityEffective(
                modelAvailable = false,
                userEnabled = true
            )
        )
    }

    @Test
    fun `feature requires both model and user preference`() {
        assertTrue(
            ExperimentalCapturePreferences.isCaptureValidityEffective(
                modelAvailable = true,
                userEnabled = true
            )
        )
        assertFalse(
            ExperimentalCapturePreferences.isCaptureValidityEffective(
                modelAvailable = true,
                userEnabled = false
            )
        )
    }
}

package com.monsteraltech.skincare.analysis.model

import com.monsteraltech.skincare.account.AnalysisProfile
import com.monsteraltech.skincare.account.AccountSettings
import com.monsteraltech.skincare.account.AccountSettingsPatch
import com.monsteraltech.skincare.body.domain.BodyRegionId
import org.junit.Assert.*
import org.junit.Test

class LesionMetadataTest {
    @Test fun `encoding matches source fields and preserves all missing sentinels`() {
        assertArrayEquals(floatArrayOf(-0.01f, -1f, -1f / 7f), LesionMetadata().encode(), 0f)
        assertArrayEquals(floatArrayOf(0.45f, 0f, 3f / 7f),
            LesionMetadata(45, AnalysisSex.MALE, AnatomicalSite.TORSO).encode(), 0f)
        assertEquals(1f, LesionMetadata(sex = AnalysisSex.FEMALE).encode()[1])
        assertThrows(IllegalArgumentException::class.java) { LesionMetadata(ageYears = 121) }
    }

    @Test fun `body regions suggest but do not silently confirm special anatomical sites`() {
        assertEquals(AnatomicalSite.UPPER_EXTREMITY, AnatomicalSite.suggestion(BodyRegionId.RIGHT_ARM))
        assertNotEquals(AnatomicalSite.PALMS_SOLES, AnatomicalSite.suggestion(BodyRegionId.RIGHT_ARM))
        assertNull(AnatomicalSite.suggestion(BodyRegionId.UNKNOWN))
        assertNull(AnalysisProfile().forCapture(2026).site)
    }

    @Test fun `stored ages need review in a later year and capture overrides do not edit profile`() {
        val profile = AnalysisProfile(45, 2026, AnalysisSex.MALE)
        assertEquals(45, profile.forCapture(2026).ageYears)
        assertNull(profile.forCapture(2027).ageYears)
        assertEquals(AnalysisSex.MALE, profile.forCapture(2027).sex)
        assertEquals(46, profile.forCapture(2026).copy(ageYears = 46).ageYears)
        assertEquals(45, profile.ageYears)
    }

    @Test fun `unrelated settings patches preserve demographics and explicit empty profile clears them`() {
        val settings = AccountSettings(analysisProfile = AnalysisProfile(45, 2026, AnalysisSex.FEMALE))
        assertEquals(settings.analysisProfile, AccountSettingsPatch(darkModeEnabled = true).applyTo(settings).analysisProfile)
        assertEquals(AnalysisProfile(), AccountSettingsPatch(analysisProfile = AnalysisProfile()).applyTo(settings).analysisProfile)
    }
}

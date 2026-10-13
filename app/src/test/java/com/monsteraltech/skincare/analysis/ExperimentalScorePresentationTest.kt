package com.monsteraltech.skincare.analysis

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ExperimentalScorePresentationTest {
    @Test fun `raw score is neither a calibrated probability nor a confidence claim`() {
        val text = ExperimentalScorePresentation.ai(0.42f, Locale.US)
        assertTrue(text.contains("42.0/100"))
        assertFalse(text.contains("%"))
        assertFalse(text.contains("Confianza"))
        assertTrue(ExperimentalScorePresentation.ai(Float.NaN).contains("no disponible"))
        assertTrue(ExperimentalScorePresentation.ai(0f).contains("0"))
    }
}

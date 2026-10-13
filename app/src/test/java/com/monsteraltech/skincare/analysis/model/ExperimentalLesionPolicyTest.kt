package com.monsteraltech.skincare.analysis.model

import org.junit.Assert.*
import org.junit.Test

class ExperimentalLesionPolicyTest {
    private val hash = "a".repeat(64)
    private val point = ExperimentalOperatingPoint(hash, MetadataScenario.NO_CONTEXT, 0.2f, 0.9f, "b".repeat(64))

    @Test fun `a threshold is tied to its model and exact studied metadata scenario`() {
        assertEquals(ExperimentalThresholdResult.ABOVE_THRESHOLD, ExperimentalLesionPolicy.evaluate(0.2f, hash, LesionMetadata(), point))
        assertEquals(ExperimentalThresholdResult.BELOW_THRESHOLD, ExperimentalLesionPolicy.evaluate(0.1f, hash, LesionMetadata(), point))
        assertEquals(ExperimentalThresholdResult.NOT_EVALUATED, ExperimentalLesionPolicy.evaluate(0.3f, "c".repeat(64), LesionMetadata(), point))
        assertEquals(ExperimentalThresholdResult.NOT_EVALUATED, ExperimentalLesionPolicy.evaluate(0.3f, hash, LesionMetadata(site = AnatomicalSite.TORSO), point))
    }
    @Test fun `unstudied partial inputs and invalid scores do not become negative decisions`() {
        assertNull(ExperimentalLesionPolicy.scenario(LesionMetadata(ageYears = 45)))
        assertEquals(ExperimentalThresholdResult.NOT_EVALUATED, ExperimentalLesionPolicy.evaluate(0.1f, hash, LesionMetadata(sex = AnalysisSex.FEMALE), point))
        assertEquals(ExperimentalThresholdResult.NOT_EVALUATED, ExperimentalLesionPolicy.evaluate(Float.NaN, hash, LesionMetadata(), point))
    }
}

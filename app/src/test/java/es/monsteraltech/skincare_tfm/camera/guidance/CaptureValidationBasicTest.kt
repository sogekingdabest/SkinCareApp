package es.monsteraltech.skincare_tfm.camera.guidance

import org.junit.Test
import org.junit.Assert.*

/**
 * Test básico para verificar que CaptureValidationManager se puede instanciar
 * y sus métodos básicos funcionan correctamente
 */
class CaptureValidationBasicTest {

    @Test
    fun `CaptureValidationManager should be instantiable`() {
        // Act
        val validationManager = CaptureValidationManager()
        
        // Assert
        assertNotNull("CaptureValidationManager should be created", validationManager)
    }

    @Test
    fun `ValidationConfig should have correct default values`() {
        // Arrange
        val validationManager = CaptureValidationManager()
        
        // Act
        val config = validationManager.getConfig()
        
        // Assert
        assertEquals(50f, config.centeringTolerance, 0.01f)
        assertEquals(0.15f, config.minMoleAreaRatio, 0.01f)
        assertEquals(0.80f, config.maxMoleAreaRatio, 0.01f)
        assertEquals(0.3f, config.minSharpness, 0.01f)
        assertEquals(80f, config.minBrightness, 0.01f)
        assertEquals(180f, config.maxBrightness, 0.01f)
        assertEquals(0.6f, config.minConfidence, 0.01f)
    }

    @Test
    fun `GuideState enum should have all expected values`() {
        // Act & Assert
        val states = CaptureValidationManager.GuideState.values()
        
        assertEquals(7, states.size)
        assertTrue(states.contains(CaptureValidationManager.GuideState.SEARCHING))
        assertTrue(states.contains(CaptureValidationManager.GuideState.CENTERING))
        assertTrue(states.contains(CaptureValidationManager.GuideState.TOO_FAR))
        assertTrue(states.contains(CaptureValidationManager.GuideState.TOO_CLOSE))
        assertTrue(states.contains(CaptureValidationManager.GuideState.POOR_LIGHTING))
        assertTrue(states.contains(CaptureValidationManager.GuideState.BLURRY))
        assertTrue(states.contains(CaptureValidationManager.GuideState.READY))
    }

    @Test
    fun `ValidationFailureReason enum should have all expected values`() {
        // Act & Assert
        val reasons = CaptureValidationManager.ValidationFailureReason.values()
        
        assertEquals(7, reasons.size)
        assertTrue(reasons.contains(CaptureValidationManager.ValidationFailureReason.NOT_CENTERED))
        assertTrue(reasons.contains(CaptureValidationManager.ValidationFailureReason.TOO_FAR))
        assertTrue(reasons.contains(CaptureValidationManager.ValidationFailureReason.TOO_CLOSE))
        assertTrue(reasons.contains(CaptureValidationManager.ValidationFailureReason.BLURRY))
        assertTrue(reasons.contains(CaptureValidationManager.ValidationFailureReason.POOR_LIGHTING))
        assertTrue(reasons.contains(CaptureValidationManager.ValidationFailureReason.NO_MOLE_DETECTED))
        assertTrue(reasons.contains(CaptureValidationManager.ValidationFailureReason.LOW_CONFIDENCE))
    }

    @Test
    fun `ValidationResult should be constructible with basic parameters`() {
        // Act
        val result = CaptureValidationManager.ValidationResult(
            canCapture = true,
            guideState = CaptureValidationManager.GuideState.READY,
            message = "Test message",
            confidence = 0.8f
        )
        
        // Assert
        assertTrue(result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.READY, result.guideState)
        assertEquals("Test message", result.message)
        assertEquals(0.8f, result.confidence, 0.01f)
        assertNull(result.failureReason)
        assertEquals(0f, result.distanceFromCenter, 0.01f)
        assertEquals(0f, result.moleAreaRatio, 0.01f)
    }

    @Test
    fun `ValidationConfig should be constructible with custom values`() {
        // Act
        val config = CaptureValidationManager.ValidationConfig(
            centeringTolerance = 75f,
            minMoleAreaRatio = 0.20f,
            maxMoleAreaRatio = 0.70f,
            minSharpness = 0.4f,
            minBrightness = 90f,
            maxBrightness = 170f,
            minConfidence = 0.7f
        )
        
        // Assert
        assertEquals(75f, config.centeringTolerance, 0.01f)
        assertEquals(0.20f, config.minMoleAreaRatio, 0.01f)
        assertEquals(0.70f, config.maxMoleAreaRatio, 0.01f)
        assertEquals(0.4f, config.minSharpness, 0.01f)
        assertEquals(90f, config.minBrightness, 0.01f)
        assertEquals(170f, config.maxBrightness, 0.01f)
        assertEquals(0.7f, config.minConfidence, 0.01f)
    }

    @Test
    fun `updateConfig should not throw exception`() {
        // Arrange
        val validationManager = CaptureValidationManager()
        val newConfig = CaptureValidationManager.ValidationConfig(
            centeringTolerance = 100f,
            minConfidence = 0.8f
        )
        
        // Act & Assert - Should not throw
        validationManager.updateConfig(newConfig)
    }
}
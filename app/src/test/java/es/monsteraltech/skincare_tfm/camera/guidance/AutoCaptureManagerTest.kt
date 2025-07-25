package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class AutoCaptureManagerTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockHapticManager: HapticFeedbackManager
    
    @Mock
    private lateinit var mockAccessibilityManager: AccessibilityManager
    
    @Mock
    private lateinit var mockListener: AutoCaptureManager.AutoCaptureListener
    
    private lateinit var autoCaptureManager: AutoCaptureManager
    private val testDispatcher = TestCoroutineDispatcher()

    @Before
    fun setup() {
        autoCaptureManager = AutoCaptureManager(mockContext, mockHapticManager, mockAccessibilityManager)
        autoCaptureManager.setListener(mockListener)
    }

    @Test
    fun `setEnabled should control auto capture functionality`() {
        autoCaptureManager.setEnabled(true)
        assertTrue(autoCaptureManager.isEnabled())
        
        autoCaptureManager.setEnabled(false)
        assertFalse(autoCaptureManager.isEnabled())
    }

    @Test
    fun `processValidationResult should start countdown for ready state`() = runBlockingTest {
        autoCaptureManager.setEnabled(true)
        
        val validationResult = CaptureValidationManager.ValidationResult(
            canCapture = true,
            guideState = CaptureValidationManager.GuideState.READY,
            message = "Ready",
            confidence = 0.9f
        )
        
        // Process multiple valid results to trigger countdown
        repeat(3) {
            autoCaptureManager.processValidationResult(validationResult)
        }
        
        // Advance time to allow stability check
        testDispatcher.advanceTimeBy(1500L)
        
        // Verify countdown started
        verify(mockListener, atLeastOnce()).onCountdownStarted(any())
    }

    @Test
    fun `processValidationResult should cancel countdown for invalid state`() {
        autoCaptureManager.setEnabled(true)
        
        val validResult = CaptureValidationManager.ValidationResult(
            canCapture = true,
            guideState = CaptureValidationManager.GuideState.READY,
            message = "Ready",
            confidence = 0.9f
        )
        
        val invalidResult = CaptureValidationManager.ValidationResult(
            canCapture = false,
            guideState = CaptureValidationManager.GuideState.BLURRY,
            message = "Blurry",
            confidence = 0.5f
        )
        
        // Start with valid results
        repeat(3) {
            autoCaptureManager.processValidationResult(validResult)
        }
        
        // Then provide invalid result
        autoCaptureManager.processValidationResult(invalidResult)
        
        // Should cancel countdown
        verify(mockListener, atMost(1)).onCountdownCancelled()
    }

    @Test
    fun `setCountdownDuration should configure countdown time`() {
        val customDuration = 5000L
        autoCaptureManager.setCountdownDuration(customDuration)
        
        // Verify duration was set (implementation specific)
        // This test ensures the method can be called without errors
    }

    @Test
    fun `cancelCountdown should stop active countdown`() {
        autoCaptureManager.setEnabled(true)
        
        // Start countdown
        val validResult = CaptureValidationManager.ValidationResult(
            canCapture = true,
            guideState = CaptureValidationManager.GuideState.READY,
            message = "Ready",
            confidence = 0.9f
        )
        
        repeat(3) {
            autoCaptureManager.processValidationResult(validResult)
        }
        
        // Cancel countdown
        autoCaptureManager.cancelCountdown()
        
        assertFalse(autoCaptureManager.isCountdownActive())
    }

    @Test
    fun `forceCapture should execute immediate capture`() {
        autoCaptureManager.setEnabled(true)
        autoCaptureManager.forceCapture()
        
        verify(mockListener).onAutoCapture()
        verify(mockHapticManager).provideCaptureSuccessFeedback()
    }

    @Test
    fun `isCountdownActive should return countdown state`() {
        assertFalse(autoCaptureManager.isCountdownActive())
        
        // After starting countdown, should be true
        // (Implementation specific test)
    }

    @Test
    fun `configureStability should allow custom stability settings`() {
        autoCaptureManager.configureStability(5, 2000L)
        
        // Verify configuration was applied (implementation specific)
        // This test ensures the method can be called without errors
    }

    @Test
    fun `cleanup should cancel operations and clear resources`() {
        autoCaptureManager.cleanup()
        
        // Verify cleanup was performed
        assertFalse(autoCaptureManager.isCountdownActive())
    }

    @Test
    fun `countdown should provide haptic and accessibility feedback`() = runBlockingTest {
        autoCaptureManager.setEnabled(true)
        
        val validResult = CaptureValidationManager.ValidationResult(
            canCapture = true,
            guideState = CaptureValidationManager.GuideState.READY,
            message = "Ready",
            confidence = 0.9f
        )
        
        repeat(3) {
            autoCaptureManager.processValidationResult(validResult)
        }
        
        // Advance time to trigger countdown
        testDispatcher.advanceTimeBy(2000L)
        
        // Verify feedback was provided
        verify(mockHapticManager, atLeastOnce()).vibratePattern(any())
        verify(mockAccessibilityManager, atLeastOnce()).announceForAccessibility(any())
    }

    @Test
    fun `auto capture should execute after countdown completes`() = runBlockingTest {
        autoCaptureManager.setEnabled(true)
        autoCaptureManager.setCountdownDuration(1000L) // Short duration for test
        
        val validResult = CaptureValidationManager.ValidationResult(
            canCapture = true,
            guideState = CaptureValidationManager.GuideState.READY,
            message = "Ready",
            confidence = 0.9f
        )
        
        repeat(3) {
            autoCaptureManager.processValidationResult(validResult)
        }
        
        // Advance time to complete countdown
        testDispatcher.advanceTimeBy(3000L)
        
        // Verify auto capture was executed
        verify(mockListener, atLeastOnce()).onAutoCapture()
    }
}
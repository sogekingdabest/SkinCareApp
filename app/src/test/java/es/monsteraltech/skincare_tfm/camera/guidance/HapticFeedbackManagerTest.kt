package es.monsteraltech.skincare_tfm.camera.guidance
import android.content.Context
import android.os.Vibrator
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue
@RunWith(MockitoJUnitRunner::class)
class HapticFeedbackManagerTest {
    @Mock
    private lateinit var mockContext: Context
    @Mock
    private lateinit var mockVibrator: Vibrator
    private lateinit var hapticManager: HapticFeedbackManager
    @Before
    fun setup() {
        `when`(mockContext.getSystemService(Context.VIBRATOR_SERVICE)).thenReturn(mockVibrator)
        `when`(mockVibrator.hasVibrator()).thenReturn(true)
        hapticManager = HapticFeedbackManager(mockContext)
    }
    @Test
    fun `provideFeedbackForState should vibrate for different states`() {
        hapticManager.provideFeedbackForState(CaptureValidationManager.GuideState.SEARCHING)
        hapticManager.provideFeedbackForState(CaptureValidationManager.GuideState.READY)
        hapticManager.provideFeedbackForState(CaptureValidationManager.GuideState.BLURRY)
        verify(mockVibrator, atLeastOnce()).hasVibrator()
    }
    @Test
    fun `setEnabled should control feedback provision`() {
        hapticManager.setEnabled(false)
        assertFalse(hapticManager.isEnabled())
        hapticManager.setEnabled(true)
        assertTrue(hapticManager.isEnabled())
    }
    @Test
    fun `provideCaptureSuccessFeedback should provide specific pattern`() {
        hapticManager.provideCaptureSuccessFeedback()
        verify(mockVibrator).hasVibrator()
    }
    @Test
    fun `provideSimpleFeedback should provide short vibration`() {
        hapticManager.provideSimpleFeedback()
        verify(mockVibrator).hasVibrator()
    }
    @Test
    fun `provideErrorFeedback should provide error pattern`() {
        hapticManager.provideErrorFeedback()
        verify(mockVibrator).hasVibrator()
    }
    @Test
    fun `hasVibrator should return vibrator capability`() {
        `when`(mockVibrator.hasVibrator()).thenReturn(true)
        assertTrue(hapticManager.hasVibrator())
        `when`(mockVibrator.hasVibrator()).thenReturn(false)
        assertFalse(hapticManager.hasVibrator())
    }
    @Test
    fun `cancelVibration should cancel ongoing vibration`() {
        hapticManager.cancelVibration()
        verify(mockVibrator).cancel()
    }
    @Test
    fun `feedback should be throttled to prevent excessive vibration`() {
        hapticManager.provideFeedbackForState(CaptureValidationManager.GuideState.SEARCHING)
        hapticManager.provideFeedbackForState(CaptureValidationManager.GuideState.CENTERING)
        hapticManager.provideFeedbackForState(CaptureValidationManager.GuideState.READY)
        verify(mockVibrator, atMost(2)).hasVibrator()
    }
}
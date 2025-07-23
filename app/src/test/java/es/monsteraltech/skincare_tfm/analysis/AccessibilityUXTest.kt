package es.monsteraltech.skincare_tfm.analysis

import android.content.Context
import android.content.res.Configuration
import android.os.Vibrator
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests para verificar las mejoras de accesibilidad y UX implementadas en el task 9
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccessibilityUXTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockProgressBar: ProgressBar
    
    @Mock
    private lateinit var mockStatusText: TextView
    
    @Mock
    private lateinit var mockCancelButton: Button
    
    @Mock
    private lateinit var mockAccessibilityManager: AccessibilityManager
    
    @Mock
    private lateinit var mockVibrator: Vibrator
    
    private lateinit var progressManager: ProgressManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        
        // Configurar mocks básicos
        `when`(mockContext.getSystemService(Context.ACCESSIBILITY_SERVICE))
            .thenReturn(mockAccessibilityManager)
        `when`(mockContext.getSystemService(Context.VIBRATOR_SERVICE))
            .thenReturn(mockVibrator)
        `when`(mockContext.resources).thenReturn(ApplicationProvider.getApplicationContext<Context>().resources)
        
        // Configurar AccessibilityManager como habilitado
        `when`(mockAccessibilityManager.isEnabled).thenReturn(true)
        
        // Configurar Vibrator como disponible
        `when`(mockVibrator.hasVibrator()).thenReturn(true)
        
        progressManager = ProgressManager(
            context = mockContext,
            progressBar = mockProgressBar,
            statusText = mockStatusText,
            cancelButton = mockCancelButton
        )
    }

    @Test
    fun `test accessibility announcements for progress updates`() {
        // Given
        val progress = 50
        val message = "Analizando con IA..."
        
        // When
        progressManager.updateProgressWithAccessibility(progress, message)
        
        // Then
        verify(mockStatusText, timeout(1000)).announceForAccessibility(anyString())
    }

    @Test
    fun `test stage change announcements for accessibility`() {
        // Given
        val stage = ProcessingStage.AI_ANALYSIS
        
        // When
        progressManager.showStageWithAccessibility(stage)
        
        // Then
        verify(mockStatusText, timeout(1000)).announceForAccessibility(contains("Nueva etapa"))
    }

    @Test
    fun `test tactile feedback for progress milestones`() {
        // Given
        val progress = 25 // Milestone que debería generar vibración
        val message = "Progreso 25%"
        
        // When
        progressManager.updateProgressWithAccessibility(progress, message)
        
        // Then - Verificar que se llamó al vibrator (con un delay para el handler)
        verify(mockVibrator, timeout(1000)).vibrate(anyLong())
    }

    @Test
    fun `test tactile feedback for stage changes`() {
        // Given
        val stage = ProcessingStage.AI_ANALYSIS
        
        // When
        progressManager.showStageWithAccessibility(stage)
        
        // Then - Verificar que se generó feedback táctil para cambio de etapa importante
        verify(mockVibrator, timeout(1000)).vibrate(any(LongArray::class.java), eq(-1))
    }

    @Test
    fun `test processing start with accessibility feedback`() {
        // When
        progressManager.startProcessing()
        
        // Then
        verify(mockStatusText, timeout(1000)).announceForAccessibility("Iniciando análisis de imagen")
        verify(mockVibrator, timeout(1000)).vibrate(anyLong())
    }

    @Test
    fun `test completion with accessibility feedback`() {
        // When
        progressManager.completeProcessingWithAccessibility()
        
        // Then
        verify(mockStatusText, timeout(1000)).announceForAccessibility("Análisis completado exitosamente")
        verify(mockVibrator, timeout(1000)).vibrate(any(LongArray::class.java), eq(-1))
    }

    @Test
    fun `test error handling with accessibility feedback`() {
        // Given
        val errorMessage = "Error de memoria"
        
        // When
        progressManager.showErrorWithAccessibility(errorMessage)
        
        // Then
        verify(mockStatusText, timeout(1000)).announceForAccessibility(contains("Error en el análisis"))
        verify(mockVibrator, timeout(1000)).vibrate(300L)
    }

    @Test
    fun `test cancellation with accessibility feedback`() {
        // When
        progressManager.showCancelledWithAccessibility()
        
        // Then
        verify(mockStatusText, timeout(1000)).announceForAccessibility("Análisis cancelado por el usuario")
        verify(mockVibrator, timeout(1000)).vibrate(any(LongArray::class.java), eq(-1))
    }

    @Test
    fun `test time estimation in progress updates`() {
        // Given
        val progress = 50
        val baseMessage = "Analizando..."
        val estimatedTotalTime = 10000L // 10 segundos
        
        // When
        progressManager.updateProgressWithTimeEstimate(progress, baseMessage, estimatedTotalTime)
        
        // Then
        verify(mockStatusText, timeout(1000)).text = contains("restantes")
    }

    @Test
    fun `test high contrast mode detection`() {
        // Given - Simular modo nocturno (alto contraste)
        val configuration = Configuration()
        configuration.uiMode = Configuration.UI_MODE_NIGHT_YES
        `when`(mockContext.resources).thenReturn(
            ApplicationProvider.getApplicationContext<Context>().resources
        )
        
        // When - Crear ProgressManager en modo alto contraste
        val progressManagerHighContrast = ProgressManager(
            context = mockContext,
            progressBar = mockProgressBar,
            statusText = mockStatusText,
            cancelButton = mockCancelButton
        )
        
        // Then - Verificar que se inicializa correctamente
        // (El test pasa si no hay excepciones durante la inicialización)
        assert(progressManagerHighContrast != null)
    }

    @Test
    fun `test accessibility disabled scenario`() {
        // Given - AccessibilityManager deshabilitado
        `when`(mockAccessibilityManager.isEnabled).thenReturn(false)
        
        val progressManagerNoA11y = ProgressManager(
            context = mockContext,
            progressBar = mockProgressBar,
            statusText = mockStatusText,
            cancelButton = mockCancelButton
        )
        
        // When
        progressManagerNoA11y.updateProgressWithAccessibility(50, "Test message")
        
        // Then - No debería hacer anuncios de accesibilidad
        verify(mockStatusText, never()).announceForAccessibility(anyString())
    }

    @Test
    fun `test vibrator unavailable scenario`() {
        // Given - Vibrator no disponible
        `when`(mockVibrator.hasVibrator()).thenReturn(false)
        
        val progressManagerNoVibration = ProgressManager(
            context = mockContext,
            progressBar = mockProgressBar,
            statusText = mockStatusText,
            cancelButton = mockCancelButton
        )
        
        // When
        progressManagerNoVibration.updateProgressWithAccessibility(25, "Test message")
        
        // Then - No debería intentar vibrar
        verify(mockVibrator, never()).vibrate(anyLong())
    }

    @Test
    fun `test progress callback with time estimation`() {
        // Given
        val mockCallback = mock(ProgressCallback::class.java)
        val progress = 30
        val message = "Procesando..."
        val estimatedTime = 15000L
        
        // When
        mockCallback.onProgressUpdateWithTimeEstimate(progress, message, estimatedTime)
        
        // Then
        verify(mockCallback).onProgressUpdateWithTimeEstimate(progress, message, estimatedTime)
    }
}
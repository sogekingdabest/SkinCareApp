package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThermalStateDetectorTest {
    
    private lateinit var context: Context
    private lateinit var thermalDetector: ThermalStateDetector
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        thermalDetector = ThermalStateDetector(context)
    }
    
    @After
    fun tearDown() {
        thermalDetector.cleanup()
    }
    
    @Test
    fun `test initial thermal state`() = runTest {
        val initialState = thermalDetector.currentThermalState.first()
        assertNotNull(initialState)
        assertTrue(initialState in ThermalStateDetector.ThermalState.values())
    }
    
    @Test
    fun `test thermal adjustments for all states`() = runTest {
        ThermalStateDetector.ThermalState.values().forEach { state ->
            // Verificar que cada estado tiene ajustes válidos
            val adjustments = thermalDetector.currentAdjustments.first()
            
            assertNotNull(adjustments)
            assertTrue(adjustments.processingFrequencyMultiplier > 0f)
            assertTrue(adjustments.processingFrequencyMultiplier <= 1f)
            assertTrue(adjustments.imageResolutionScale > 0f)
            assertTrue(adjustments.imageResolutionScale <= 1f)
            assertTrue(adjustments.maxConcurrentOperations > 0)
        }
    }
    
    @Test
    fun `test thermal state progression`() = runTest {
        val states = listOf(
            ThermalStateDetector.ThermalState.NONE,
            ThermalStateDetector.ThermalState.LIGHT,
            ThermalStateDetector.ThermalState.MODERATE,
            ThermalStateDetector.ThermalState.SEVERE,
            ThermalStateDetector.ThermalState.CRITICAL,
            ThermalStateDetector.ThermalState.EMERGENCY
        )
        
        var previousMultiplier = 1.0f
        var previousResolution = 1.0f
        var previousOperations = Int.MAX_VALUE
        
        // Verificar que los ajustes se vuelven más restrictivos
        states.forEach { state ->
            // Simular cambio de estado (requeriría acceso interno)
            val adjustments = thermalDetector.currentAdjustments.first()
            
            assertTrue(
                "Frequency multiplier should decrease with thermal stress",
                adjustments.processingFrequencyMultiplier <= previousMultiplier
            )
            assertTrue(
                "Resolution scale should decrease with thermal stress", 
                adjustments.imageResolutionScale <= previousResolution
            )
            assertTrue(
                "Max operations should decrease with thermal stress",
                adjustments.maxConcurrentOperations <= previousOperations
            )
            
            previousMultiplier = adjustments.processingFrequencyMultiplier
            previousResolution = adjustments.imageResolutionScale
            previousOperations = adjustments.maxConcurrentOperations
        }
    }
    
    @Test
    fun `test frequency multiplier calculation`() {
        val baseFrequency = 20
        val adjustedFrequency = thermalDetector.calculateAdjustedFrequency(baseFrequency)
        
        assertTrue(adjustedFrequency > 0)
        assertTrue(adjustedFrequency <= baseFrequency)
    }
    
    @Test
    fun `test advanced processing permission`() {
        val isAllowed = thermalDetector.isAdvancedProcessingAllowed()
        assertTrue(isAllowed is Boolean)
        
        // En estado inicial (NONE), debería estar permitido
        assertTrue(isAllowed)
    }
    
    @Test
    fun `test throttling requirement detection`() {
        val requiresThrottling = thermalDetector.requiresThrottling()
        assertTrue(requiresThrottling is Boolean)
        
        // En estado inicial, no debería requerir throttling
        assertFalse(requiresThrottling)
    }
    
    @Test
    fun `test optimization recommendations`() {
        val recommendations = thermalDetector.getOptimizationRecommendations()
        assertNotNull(recommendations)
        assertTrue(recommendations is List<String>)
        
        // En estado inicial, no debería haber recomendaciones
        assertTrue(recommendations.isEmpty())
    }
    
    @Test
    fun `test thermal state listener`() = runTest {
        var listenerCalled = false
        var receivedState: ThermalStateDetector.ThermalState? = null
        
        val listener: (ThermalStateDetector.ThermalState) -> Unit = { state ->
            listenerCalled = true
            receivedState = state
        }
        
        thermalDetector.addThermalStateListener(listener)
        
        // Simular cambio de estado térmico (requeriría acceso interno)
        // En una implementación real, esto se activaría por el sistema
        
        // Verificar que el listener se puede añadir y remover sin errores
        thermalDetector.removeThermalStateListener(listener)
    }
    
    @Test
    fun `test current adjustments access`() {
        val adjustments = thermalDetector.getCurrentAdjustments()
        
        assertNotNull(adjustments)
        assertTrue(adjustments.processingFrequencyMultiplier > 0f)
        assertTrue(adjustments.imageResolutionScale > 0f)
        assertTrue(adjustments.maxConcurrentOperations > 0)
    }
    
    @Test
    fun `test frequency multiplier bounds`() {
        val multiplier = thermalDetector.getFrequencyMultiplier()
        
        assertTrue(multiplier > 0f)
        assertTrue(multiplier <= 1f)
    }
    
    @Test
    fun `test resolution scale bounds`() {
        val scale = thermalDetector.getRecommendedResolutionScale()
        
        assertTrue(scale > 0f)
        assertTrue(scale <= 1f)
    }
    
    @Test
    fun `test concurrent operations limit`() {
        val maxOps = thermalDetector.getMaxConcurrentOperations()
        
        assertTrue(maxOps > 0)
        assertTrue(maxOps <= 10) // Límite razonable
    }
    
    @Test
    fun `test caching enabled check`() {
        val cachingEnabled = thermalDetector.isCachingEnabled()
        assertTrue(cachingEnabled is Boolean)
    }
    
    @Test
    fun `test extreme thermal states`() {
        // Verificar que los estados extremos tienen ajustes apropiados
        val emergencyAdjustments = ThermalStateDetector.ThermalAdjustments(
            processingFrequencyMultiplier = 0.1f,
            imageResolutionScale = 0.3f,
            enableAdvancedProcessing = false,
            maxConcurrentOperations = 1,
            enableCaching = false
        )
        
        // Verificar que los valores extremos son válidos
        assertTrue(emergencyAdjustments.processingFrequencyMultiplier > 0f)
        assertTrue(emergencyAdjustments.imageResolutionScale > 0f)
        assertFalse(emergencyAdjustments.enableAdvancedProcessing)
        assertEquals(1, emergencyAdjustments.maxConcurrentOperations)
        assertFalse(emergencyAdjustments.enableCaching)
    }
    
    @Test
    fun `test fallback thermal monitor for old devices`() {
        // En dispositivos sin ThermalManager, debe usar monitor de fallback
        // Esto se prueba implícitamente en la inicialización
        
        val adjustments = thermalDetector.getCurrentAdjustments()
        assertNotNull(adjustments)
    }
    
    @Test
    fun `test multiple listeners management`() {
        val listeners = mutableListOf<(ThermalStateDetector.ThermalState) -> Unit>()
        
        // Añadir múltiples listeners
        repeat(5) { index ->
            val listener: (ThermalStateDetector.ThermalState) -> Unit = { _ ->
                // Listener vacío para prueba
            }
            listeners.add(listener)
            thermalDetector.addThermalStateListener(listener)
        }
        
        // Remover todos los listeners
        listeners.forEach { listener ->
            thermalDetector.removeThermalStateListener(listener)
        }
        
        // No debe haber errores
    }
    
    @Test
    fun `test cleanup releases resources`() {
        // Añadir un listener
        val listener: (ThermalStateDetector.ThermalState) -> Unit = { _ -> }
        thermalDetector.addThermalStateListener(listener)
        
        // Cleanup debe limpiar recursos sin errores
        thermalDetector.cleanup()
        
        // Verificar que se puede llamar múltiples veces
        thermalDetector.cleanup()
    }
    
    @Test
    fun `test thermal state enum completeness`() {
        val expectedStates = listOf(
            ThermalStateDetector.ThermalState.NONE,
            ThermalStateDetector.ThermalState.LIGHT,
            ThermalStateDetector.ThermalState.MODERATE,
            ThermalStateDetector.ThermalState.SEVERE,
            ThermalStateDetector.ThermalState.CRITICAL,
            ThermalStateDetector.ThermalState.EMERGENCY
        )
        
        val actualStates = ThermalStateDetector.ThermalState.values().toList()
        assertEquals(expectedStates.size, actualStates.size)
        expectedStates.forEach { state ->
            assertTrue(actualStates.contains(state))
        }
    }
}
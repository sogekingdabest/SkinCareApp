package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detector de estado térmico del dispositivo para ajustar el rendimiento
 * del procesamiento de imágenes según las condiciones térmicas
 * 
 * Implementación sin imports de ThermalManager para evitar problemas de compilación
 */
class ThermalStateDetector(private val context: Context) {

    enum class ThermalState {
        NONE,        // Sin restricciones térmicas
        LIGHT,       // Restricciones ligeras
        MODERATE,    // Restricciones moderadas
        SEVERE,      // Restricciones severas
        CRITICAL,    // Restricciones críticas
        EMERGENCY    // Estado de emergencia térmica
    }

    data class ThermalAdjustments(
        val processingQuality: Float,
        val frameSkipRate: Int,
        val imageResolutionScale: Float,
        val enableAdvancedProcessing: Boolean,
        val maxConcurrentOperations: Int,
        val enableCaching: Boolean,
        val processingFrequencyMultiplier: Float,
        val enableAdvancedFilters: Boolean,
        val enableROI: Boolean,
        val roiScale: Float
    )

    // Constantes para estados térmicos (equivalentes a ThermalManager)
    companion object {
        private const val THERMAL_STATUS_NONE = 0
        private const val THERMAL_STATUS_LIGHT = 1
        private const val THERMAL_STATUS_MODERATE = 2
        private const val THERMAL_STATUS_SEVERE = 3
        private const val THERMAL_STATUS_CRITICAL = 4
        private const val THERMAL_STATUS_EMERGENCY = 5
    }

    // Configuraciones de ajuste por estado térmico
    private val thermalAdjustments = mapOf(
        ThermalState.NONE to ThermalAdjustments(
            processingQuality = 1.0f,
            frameSkipRate = 0,
            imageResolutionScale = 1.0f,
            enableAdvancedProcessing = true,
            maxConcurrentOperations = 4,
            enableCaching = true,
            processingFrequencyMultiplier = 1.0f,
            enableAdvancedFilters = true,
            enableROI = true,
            roiScale = 1.0f
        ),
        ThermalState.LIGHT to ThermalAdjustments(
            processingQuality = 0.9f,
            frameSkipRate = 1,
            imageResolutionScale = 0.9f,
            enableAdvancedProcessing = true,
            maxConcurrentOperations = 3,
            enableCaching = true,
            processingFrequencyMultiplier = 0.9f,
            enableAdvancedFilters = true,
            enableROI = true,
            roiScale = 0.9f
        ),
        ThermalState.MODERATE to ThermalAdjustments(
            processingQuality = 0.7f,
            frameSkipRate = 2,
            imageResolutionScale = 0.8f,
            enableAdvancedProcessing = true,
            maxConcurrentOperations = 2,
            enableCaching = true,
            processingFrequencyMultiplier = 0.7f,
            enableAdvancedFilters = true,
            enableROI = true,
            roiScale = 0.8f
        ),
        ThermalState.SEVERE to ThermalAdjustments(
            processingQuality = 0.5f,
            frameSkipRate = 3,
            imageResolutionScale = 0.6f,
            enableAdvancedProcessing = false,
            maxConcurrentOperations = 2,
            enableCaching = true,
            processingFrequencyMultiplier = 0.5f,
            enableAdvancedFilters = false,
            enableROI = true,
            roiScale = 0.6f
        ),
        ThermalState.CRITICAL to ThermalAdjustments(
            processingQuality = 0.4f,
            frameSkipRate = 4,
            imageResolutionScale = 0.5f,
            enableAdvancedProcessing = false,
            maxConcurrentOperations = 1,
            enableCaching = false,
            processingFrequencyMultiplier = 0.4f,
            enableAdvancedFilters = false,
            enableROI = false,
            roiScale = 0.5f
        ),
        ThermalState.EMERGENCY to ThermalAdjustments(
            processingQuality = 0.3f,
            frameSkipRate = 5,
            imageResolutionScale = 0.3f,
            enableAdvancedProcessing = false,
            maxConcurrentOperations = 1,
            enableCaching = false,
            processingFrequencyMultiplier = 0.3f,
            enableAdvancedFilters = false,
            enableROI = false,
            roiScale = 0.3f
        )
    )

    // Estado actual
    private val _currentThermalState = MutableStateFlow(ThermalState.NONE)
    val currentThermalState: StateFlow<ThermalState> = _currentThermalState.asStateFlow()

    private val _currentAdjustments = MutableStateFlow(thermalAdjustments[ThermalState.NONE]!!)
    val currentAdjustments: StateFlow<ThermalAdjustments> = _currentAdjustments.asStateFlow()

    // Manager térmico usando reflexión para evitar problemas de import
    private val thermalManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            context.getSystemService("thermal")
        } catch (e: Exception) {
            null
        }
    } else null

    // Monitor de fallback para dispositivos sin ThermalManager
    private var fallbackMonitor: FallbackThermalMonitor? = null

    init {
        initializeThermalMonitoring()
    }

    /**
     * Inicializa el monitoreo térmico según la versión de Android
     */
    private fun initializeThermalMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalManager != null) {
            // Usar ThermalManager oficial en Android Q+ con reflexión
            setupThermalManagerListener()
        } else {
            // Usar monitor de fallback para versiones anteriores
            fallbackMonitor = FallbackThermalMonitor()
            fallbackMonitor?.startMonitoring()
        }
    }

    /**
     * Configura el listener del ThermalManager usando reflexión para Android Q+
     */
    private fun setupThermalManagerListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalManager != null) {
            try {
                // Usar reflexión para evitar imports problemáticos
                val addListenerMethod = thermalManager.javaClass.getMethod(
                    "addThermalStatusListener",
                    java.util.concurrent.Executor::class.java,
                    Class.forName("android.os.ThermalManager\$OnThermalStatusChangedListener")
                )
                
                // Crear listener usando proxy
                val listenerClass = Class.forName("android.os.ThermalManager\$OnThermalStatusChangedListener")
                val listener = java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.classLoader,
                    arrayOf(listenerClass)
                ) { _, method, args ->
                    if (method.name == "onThermalStatusChanged" && args != null && args.isNotEmpty()) {
                        val status = args[0] as Int
                        val thermalState = mapSystemThermalStatus(status)
                        updateThermalState(thermalState)
                    }
                    null
                }
                
                addListenerMethod.invoke(thermalManager, context.mainExecutor, listener)
                
                // Obtener estado inicial
                val getCurrentStatusMethod = thermalManager.javaClass.getMethod("getCurrentThermalStatus")
                val initialStatus = getCurrentStatusMethod.invoke(thermalManager) as Int
                updateThermalState(mapSystemThermalStatus(initialStatus))
                
            } catch (e: Exception) {
                // Si falla la reflexión, usar monitor de fallback
                fallbackMonitor = FallbackThermalMonitor()
                fallbackMonitor?.startMonitoring()
            }
        }
    }

    /**
     * Mapea el estado térmico del sistema a nuestro enum
     */
    private fun mapSystemThermalStatus(status: Int): ThermalState {
        return when (status) {
            THERMAL_STATUS_NONE -> ThermalState.NONE
            THERMAL_STATUS_LIGHT -> ThermalState.LIGHT
            THERMAL_STATUS_MODERATE -> ThermalState.MODERATE
            THERMAL_STATUS_SEVERE -> ThermalState.SEVERE
            THERMAL_STATUS_CRITICAL -> ThermalState.CRITICAL
            THERMAL_STATUS_EMERGENCY -> ThermalState.EMERGENCY
            else -> ThermalState.NONE
        }
    }

    /**
     * Actualiza el estado térmico y los ajustes correspondientes
     */
    private fun updateThermalState(newState: ThermalState) {
        if (_currentThermalState.value != newState) {
            _currentThermalState.value = newState
            _currentAdjustments.value = thermalAdjustments[newState]!!
        }
    }

    /**
     * Monitor de fallback para dispositivos sin ThermalManager
     */
    private inner class FallbackThermalMonitor {
        private var isMonitoring = false

        fun startMonitoring() {
            if (!isMonitoring) {
                isMonitoring = true
                // Implementación básica que mantiene estado NONE
                // En una implementación real, podrías monitorear CPU, batería, etc.
                updateThermalState(ThermalState.NONE)
            }
        }

        fun stopMonitoring() {
            isMonitoring = false
        }
    }

    /**
     * Limpia los recursos del detector
     */
    fun cleanup() {
        fallbackMonitor?.stopMonitoring()
    }

    /**
     * Obtiene el estado térmico actual
     */
    fun getCurrentState(): ThermalState = _currentThermalState.value

    /**
     * Obtiene los ajustes actuales
     */
    fun getCurrentAdjustments(): ThermalAdjustments = _currentAdjustments.value


    /**
     * Calcula la frecuencia ajustada basada en el multiplicador térmico
     */
    fun calculateAdjustedFrequency(baseFrequency: Int): Int {
        return (baseFrequency * _currentAdjustments.value.processingFrequencyMultiplier).toInt()
    }

    /**
     * Verifica si el procesamiento avanzado está permitido
     */
    fun isAdvancedProcessingAllowed(): Boolean {
        return _currentAdjustments.value.enableAdvancedProcessing
    }

    /**
     * Verifica si se requiere throttling
     */
    fun requiresThrottling(): Boolean {
        return _currentThermalState.value != ThermalState.NONE
    }

    /**
     * Obtiene recomendaciones de optimización
     */
    fun getOptimizationRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        val state = _currentThermalState.value
        
        when (state) {
            ThermalState.LIGHT -> recommendations.add("Reducir calidad de procesamiento ligeramente")
            ThermalState.MODERATE -> recommendations.add("Saltar algunos frames para reducir carga")
            ThermalState.SEVERE -> recommendations.add("Deshabilitar procesamiento avanzado")
            ThermalState.CRITICAL -> recommendations.add("Reducir operaciones concurrentes")
            ThermalState.EMERGENCY -> recommendations.add("Procesamiento mínimo solamente")
            else -> recommendations.add("Rendimiento óptimo disponible")
        }
        
        return recommendations
    }
    /**
     * Obtiene el multiplicador de frecuencia actual
     */
    fun getFrequencyMultiplier(): Float {
        return _currentAdjustments.value.processingFrequencyMultiplier
    }

    /**
     * Obtiene la escala de resolución recomendada
     */
    fun getRecommendedResolutionScale(): Float {
        return _currentAdjustments.value.imageResolutionScale
    }

    /**
     * Obtiene el número máximo de operaciones concurrentes
     */
    fun getMaxConcurrentOperations(): Int {
        return _currentAdjustments.value.maxConcurrentOperations
    }

    /**
     * Verifica si el caching está habilitado
     */
    fun isCachingEnabled(): Boolean {
        return _currentAdjustments.value.enableCaching
    }
}
package es.monsteraltech.skincare_tfm.camera.guidance
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
class ThermalStateDetector(private val context: Context) {
    enum class ThermalState {
        NONE,
        LIGHT,
        MODERATE,
        SEVERE,
        CRITICAL,
        EMERGENCY
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
    companion object {
        private const val THERMAL_STATUS_NONE = 0
        private const val THERMAL_STATUS_LIGHT = 1
        private const val THERMAL_STATUS_MODERATE = 2
        private const val THERMAL_STATUS_SEVERE = 3
        private const val THERMAL_STATUS_CRITICAL = 4
        private const val THERMAL_STATUS_EMERGENCY = 5
    }
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
    private val _currentThermalState = MutableStateFlow(ThermalState.NONE)
    val currentThermalState: StateFlow<ThermalState> = _currentThermalState.asStateFlow()
    private val _currentAdjustments = MutableStateFlow(thermalAdjustments[ThermalState.NONE]!!)
    val currentAdjustments: StateFlow<ThermalAdjustments> = _currentAdjustments.asStateFlow()
    private val thermalManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            context.getSystemService("thermal")
        } catch (e: Exception) {
            null
        }
    } else null
    private var fallbackMonitor: FallbackThermalMonitor? = null
    init {
        initializeThermalMonitoring()
    }
    private fun initializeThermalMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalManager != null) {
            setupThermalManagerListener()
        } else {
            fallbackMonitor = FallbackThermalMonitor()
            fallbackMonitor?.startMonitoring()
        }
    }
    private fun setupThermalManagerListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalManager != null) {
            try {
                val addListenerMethod = thermalManager.javaClass.getMethod(
                    "addThermalStatusListener",
                    java.util.concurrent.Executor::class.java,
                    Class.forName("android.os.ThermalManager\$OnThermalStatusChangedListener")
                )
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
                val getCurrentStatusMethod = thermalManager.javaClass.getMethod("getCurrentThermalStatus")
                val initialStatus = getCurrentStatusMethod.invoke(thermalManager) as Int
                updateThermalState(mapSystemThermalStatus(initialStatus))
            } catch (e: Exception) {
                fallbackMonitor = FallbackThermalMonitor()
                fallbackMonitor?.startMonitoring()
            }
        }
    }
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
    private fun updateThermalState(newState: ThermalState) {
        if (_currentThermalState.value != newState) {
            _currentThermalState.value = newState
            _currentAdjustments.value = thermalAdjustments[newState]!!
        }
    }
    private inner class FallbackThermalMonitor {
        private var isMonitoring = false
        fun startMonitoring() {
            if (!isMonitoring) {
                isMonitoring = true
                updateThermalState(ThermalState.NONE)
            }
        }
        fun stopMonitoring() {
            isMonitoring = false
        }
    }
    fun cleanup() {
        fallbackMonitor?.stopMonitoring()
    }
    fun getCurrentState(): ThermalState = _currentThermalState.value
    fun getCurrentAdjustments(): ThermalAdjustments = _currentAdjustments.value
    fun calculateAdjustedFrequency(baseFrequency: Int): Int {
        return (baseFrequency * _currentAdjustments.value.processingFrequencyMultiplier).toInt()
    }
    fun isAdvancedProcessingAllowed(): Boolean {
        return _currentAdjustments.value.enableAdvancedProcessing
    }
    fun requiresThrottling(): Boolean {
        return _currentThermalState.value != ThermalState.NONE
    }
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
    fun getFrequencyMultiplier(): Float {
        return _currentAdjustments.value.processingFrequencyMultiplier
    }
    fun getRecommendedResolutionScale(): Float {
        return _currentAdjustments.value.imageResolutionScale
    }
    fun getMaxConcurrentOperations(): Int {
        return _currentAdjustments.value.maxConcurrentOperations
    }
    fun isCachingEnabled(): Boolean {
        return _currentAdjustments.value.enableCaching
    }
}
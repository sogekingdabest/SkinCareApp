package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opencv.core.Mat
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlin.math.min

/**
 * Gestor de rendimiento que ajusta el procesamiento según las condiciones del dispositivo
 * Implementación sin imports de ThermalManager para evitar problemas de compilación
 */
class PerformanceManager(private val context: Context) {

    // Estados de rendimiento
    enum class PerformanceLevel {
        HIGH,       // Rendimiento alto
        MEDIUM,     // Rendimiento medio
        LOW,        // Rendimiento bajo
        MINIMAL     // Rendimiento mínimo
    }

    // Estados térmicos (copiados para evitar dependencias)
    enum class ThermalState {
        NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY
    }

    // Configuración de rendimiento
    data class PerformanceConfig(
        val maxConcurrentOperations: Int,
        val processingQuality: Float,
        val enableAdvancedFeatures: Boolean,
        val memoryThreshold: Float,
        val frameSkipRate: Int,
        val processingFrequency: Int,
        val maxPoolSize: Int
    )

    // Constantes para estados térmicos
    companion object {
        private const val THERMAL_STATUS_NONE = 0
        private const val THERMAL_STATUS_LIGHT = 1
        private const val THERMAL_STATUS_MODERATE = 2
        private const val THERMAL_STATUS_SEVERE = 3
        private const val THERMAL_STATUS_CRITICAL = 4
        private const val THERMAL_STATUS_EMERGENCY = 5
    }

    // Configuraciones por nivel de rendimiento
    private val performanceConfigs = mapOf(
        PerformanceLevel.HIGH to PerformanceConfig(
            maxConcurrentOperations = 4,
            processingQuality = 1.0f,
            enableAdvancedFeatures = true,
            memoryThreshold = 0.8f,
            frameSkipRate = 0,
            processingFrequency = 60,
            maxPoolSize = 10
        ),
        PerformanceLevel.MEDIUM to PerformanceConfig(
            maxConcurrentOperations = 3,
            processingQuality = 0.8f,
            enableAdvancedFeatures = true,
            memoryThreshold = 0.7f,
            frameSkipRate = 1,
            processingFrequency = 30,
            maxPoolSize = 8
        ),
        PerformanceLevel.LOW to PerformanceConfig(
            maxConcurrentOperations = 2,
            processingQuality = 0.6f,
            enableAdvancedFeatures = false,
            memoryThreshold = 0.6f,
            frameSkipRate = 2,
            processingFrequency = 20,
            maxPoolSize = 6
        ),
        PerformanceLevel.MINIMAL to PerformanceConfig(
            maxConcurrentOperations = 1,
            processingQuality = 0.4f,
            enableAdvancedFeatures = false,
            memoryThreshold = 0.5f,
            frameSkipRate = 3,
            processingFrequency = 10,
            maxPoolSize = 4
        )
    )

    // Estados
    private val _currentPerformanceLevel = MutableStateFlow(PerformanceLevel.HIGH)
    val currentPerformanceLevel: StateFlow<PerformanceLevel> = _currentPerformanceLevel.asStateFlow()

    private val _currentConfig = MutableStateFlow(performanceConfigs[PerformanceLevel.HIGH]!!)
    val currentConfig: StateFlow<PerformanceConfig> = _currentConfig.asStateFlow()

    // Managers del sistema
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    // Manager térmico usando reflexión
    private val thermalManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            context.getSystemService("thermal")
        } catch (e: Exception) {
            null
        }
    } else null

    // Métricas de rendimiento
    private val processingTimes = ConcurrentLinkedQueue<Long>()
    private val memoryUsageHistory = ConcurrentLinkedQueue<Float>()
    
    // Pool de matrices para reutilización
    private val matPool = ConcurrentLinkedQueue<Mat>()
    private var matPoolSize = 0

    init {
        setupThermalListener()
        updatePerformanceLevel()
    }

    /**
     * Configura el listener térmico usando reflexión
     */
    private fun setupThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalManager != null) {
            try {
                // Usar reflexión para evitar imports problemáticos
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
                        adjustForThermalState(thermalState)
                    }
                    null
                }
                
                addListenerMethod.invoke(thermalManager, context.mainExecutor, listener)
            } catch (e: Exception) {
                // Si falla la reflexión, continuar sin listener térmico
            }
        }
    }

    /**
     * Mapea el estado térmico del sistema
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
     * Ajusta el rendimiento según el estado térmico
     */
    private fun adjustForThermalState(thermalState: ThermalState) {
        val newLevel = when (thermalState) {
            ThermalState.NONE, ThermalState.LIGHT -> PerformanceLevel.HIGH
            ThermalState.MODERATE -> PerformanceLevel.MEDIUM
            ThermalState.SEVERE -> PerformanceLevel.LOW
            ThermalState.CRITICAL, ThermalState.EMERGENCY -> PerformanceLevel.MINIMAL
        }
        
        if (_currentPerformanceLevel.value != newLevel) {
            _currentPerformanceLevel.value = newLevel
            _currentConfig.value = performanceConfigs[newLevel]!!
        }
    }

    /**
     * Actualiza el nivel de rendimiento basado en métricas
     */
    private fun updatePerformanceLevel() {
        val memoryPressure = getCurrentMemoryPressure()
        val avgProcessingTime = getAverageProcessingTime()
        val isBatteryLow = powerManager.isPowerSaveMode

        val newLevel = when {
            isBatteryLow || memoryPressure > 0.8f -> PerformanceLevel.MINIMAL
            memoryPressure > 0.7f || avgProcessingTime > 500 -> PerformanceLevel.LOW
            memoryPressure > 0.6f || avgProcessingTime > 300 -> PerformanceLevel.MEDIUM
            else -> PerformanceLevel.HIGH
        }

        if (_currentPerformanceLevel.value != newLevel) {
            _currentPerformanceLevel.value = newLevel
            _currentConfig.value = performanceConfigs[newLevel]!!
        }
    }

    /**
     * Registra el tiempo de procesamiento de una operación
     */
    fun recordProcessingTime(timeMs: Long) {
        processingTimes.offer(timeMs)
        if (processingTimes.size > 10) {
            processingTimes.poll()
        }
        updatePerformanceLevel()
    }

    /**
     * Registra el uso de memoria actual
     */
    fun recordMemoryUsage(usagePercent: Float) {
        memoryUsageHistory.offer(usagePercent)
        if (memoryUsageHistory.size > 10) {
            memoryUsageHistory.poll()
        }
        updatePerformanceLevel()
    }

    /**
     * Obtiene la presión de memoria actual
     */
    private fun getCurrentMemoryPressure(): Float {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        return (usedMemory.toFloat() / maxMemory.toFloat())
    }

    /**
     * Obtiene el tiempo promedio de procesamiento
     */
    private fun getAverageProcessingTime(): Long {
        return if (processingTimes.isEmpty()) {
            0L
        } else {
            processingTimes.sum() / processingTimes.size
        }
    }

    /**
     * Verifica si se debe saltar el frame actual
     */
    fun shouldSkipFrame(frameIndex: Int): Boolean {
        val skipRate = _currentConfig.value.frameSkipRate
        return skipRate > 0 && frameIndex % (skipRate + 1) != 0
    }

    /**
     * Obtiene el factor de calidad de procesamiento
     */
    fun getProcessingQuality(): Float {
        return _currentConfig.value.processingQuality
    }

    /**
     * Verifica si las características avanzadas están habilitadas
     */
    fun areAdvancedFeaturesEnabled(): Boolean {
        return _currentConfig.value.enableAdvancedFeatures
    }

    /**
     * Obtiene el número máximo de operaciones concurrentes
     */
    fun getMaxConcurrentOperations(): Int {
        return _currentConfig.value.maxConcurrentOperations
    }

    /**
     * Optimiza una matriz OpenCV según la configuración actual
     */
    fun optimizeMatForProcessing(mat: Mat): Mat {
        val quality = getProcessingQuality()
        if (quality < 1.0f) {
            val newSize = org.opencv.core.Size(
                (mat.width() * quality).toInt().toDouble(),
                (mat.height() * quality).toInt().toDouble()
            )
            val resizedMat = Mat()
            org.opencv.imgproc.Imgproc.resize(mat, resizedMat, newSize)
            return resizedMat
        }
        return mat
    }

    /**
     * Obtiene una matriz del pool para reutilización
     */
    fun borrowMat(): Mat {
        val pooledMat = matPool.poll()
        return if (pooledMat != null) {
            pooledMat
        } else {
            Mat()
        }
    }

    /**
     * Devuelve una matriz al pool para reutilización
     */
    fun returnMat(mat: Mat) {
        if (matPoolSize < _currentConfig.value.maxPoolSize) {
            matPool.offer(mat)
            matPoolSize++
        } else {
            mat.release()
        }
    }

    /**
     * Registra el tiempo de procesamiento de un frame
     */
    fun recordFrameProcessingTime(timeMs: Long) {
        recordProcessingTime(timeMs)
    }

    /**
     * Registra el uso de memoria actual
     */
    fun recordMemoryUsage() {
        val memoryUsage = getCurrentMemoryPressure()
        recordMemoryUsage(memoryUsage)
    }

    /**
     * Obtiene estadísticas de rendimiento
     */
    fun getPerformanceStats(): Map<String, Any> {
        return mapOf(
            "currentLevel" to _currentPerformanceLevel.value.name,
            "thermalState" to "NONE", // Placeholder
            "avgProcessingTime" to getAverageProcessingTime(),
            "processingFrequency" to _currentConfig.value.processingFrequency,
            "memoryPressure" to getCurrentMemoryPressure(),
            "matPoolSize" to matPoolSize,
            "maxPoolSize" to _currentConfig.value.maxPoolSize,
            "processingQuality" to _currentConfig.value.processingQuality
        )
    }

    /**
     * Limpia los recursos del manager
     */
    fun cleanup() {
        processingTimes.clear()
        memoryUsageHistory.clear()
        
        // Limpiar pool de matrices
        while (matPool.isNotEmpty()) {
            matPool.poll()?.release()
        }
        matPoolSize = 0
    }
}
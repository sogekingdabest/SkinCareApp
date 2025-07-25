package es.monsteraltech.skincare_tfm.camera.guidance

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import kotlinx.coroutines.*

/**
 * Integración completa del sistema de métricas con los componentes de guías de captura
 */
class MetricsIntegration(
    private val context: Context,
    private val config: MetricsConfig = MetricsConfig()
) {
    companion object {
        private const val TAG = "MetricsIntegration"
        private const val MEMORY_MONITORING_INTERVAL = 2000L // 2 segundos
    }
    
    private val metrics = CaptureMetrics.getInstance()
    private val logger = MetricsLogger(context, config)
    private val dashboard = MetricsDashboard(context, metrics)
    
    private var isRunning = false
    private var monitoringJob: Job? = null
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    /**
     * Inicia el sistema completo de métricas
     */
    fun start() {
        if (isRunning) return
        
        isRunning = true
        
        // Iniciar componentes
        logger.start()
        if (config.enablePerformanceTracking) {
            dashboard.start()
            startMemoryMonitoring()
        }
        
        Log.i(TAG, "Metrics integration started")
    }
    
    /**
     * Detiene el sistema de métricas
     */
    fun stop() {
        if (!isRunning) return
        
        isRunning = false
        
        // Detener componentes
        logger.stop()
        dashboard.stop()
        monitoringJob?.cancel()
        
        Log.i(TAG, "Metrics integration stopped")
    }
    
    /**
     * Integración con MoleDetectionProcessor
     */
    fun integrateWithMoleDetection(processor: MoleDetectionProcessor) {
        // Wrapper para detectMole que incluye métricas
        val originalDetectMole = processor::class.java.getDeclaredMethod("detectMole", org.opencv.core.Mat::class.java)
        
        // En una implementación real, usaríamos AOP o proxy pattern
        // Por simplicidad, proporcionamos métodos helper
    }
    
    /**
     * Registra métricas de detección de lunar
     */
    fun logMoleDetection(
        success: Boolean,
        processingTime: Long,
        confidence: Float? = null
    ) {
        // Registrar en métricas principales
        metrics.logDetectionAttempt(success, processingTime)
        
        // Registrar en logger estructurado
        logger.logDetectionEvent(success, processingTime, confidence)
    }
    
    /**
     * Registra métricas de análisis de calidad
     */
    suspend fun logQualityAnalysis(
        qualityMetrics: ImageQualityAnalyzer.QualityMetrics,
        processingTime: Long
    ) {
        // Registrar en métricas principales
        metrics.logQualityAnalysis(qualityMetrics, processingTime)
        
        // Registrar en logger estructurado
        logger.logQualityEvent(
            qualityMetrics.sharpness,
            qualityMetrics.brightness,
            qualityMetrics.contrast,
            processingTime
        )
    }
    
    /**
     * Registra métricas de validación de captura
     */
    fun logCaptureValidation(
        validationResult: CaptureValidationManager.ValidationResult,
        processingTime: Long
    ) {
        // Registrar en métricas principales
        metrics.logCaptureValidation(validationResult, processingTime)
        
        // Registrar en logger estructurado
        logger.logValidationEvent(
            validationResult.guideState.name,
            validationResult.canCapture,
            processingTime
        )
    }
    
    /**
     * Registra métricas de preprocesado de imagen
     */
    fun logImagePreprocessing(
        processingTime: Long,
        filtersApplied: List<String>
    ) {
        // Registrar en métricas principales
        metrics.logPreprocessingPerformance(processingTime, filtersApplied)
        
        // Registrar en logger estructurado
        logger.logPreprocessingEvent(processingTime, filtersApplied)
    }
    
    /**
     * Registra métricas de procesamiento de frame
     */
    suspend fun logFrameProcessing(
        processingTime: Long,
        frameWidth: Int,
        frameHeight: Int
    ) {
        val frameSize = "${frameWidth}x${frameHeight}"
        
        // Registrar en métricas principales
        metrics.logFrameProcessingTime(processingTime)
        
        // Registrar en logger estructurado
        logger.logFrameEvent(processingTime, frameSize)
    }
    
    /**
     * Inicia monitoreo automático de memoria
     */
    private fun startMemoryMonitoring() {
        monitoringJob = CoroutineScope(Dispatchers.Default).launch {
            while (isRunning) {
                try {
                    monitorMemoryUsage()
                    delay(MEMORY_MONITORING_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring memory", e)
                }
            }
        }
    }
    
    /**
     * Monitorea uso de memoria
     */
    private suspend fun monitorMemoryUsage() {
        try {
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            val totalMemory = memoryInfo.totalMem
            val availableMemory = memoryInfo.availMem
            val usedMemory = totalMemory - availableMemory
            
            // Registrar en métricas principales
            metrics.logMemoryUsage(usedMemory, totalMemory)
            
            // Registrar en logger estructurado
            logger.logMemoryEvent(usedMemory, totalMemory)
            
            // Verificar alertas de memoria
            val usagePercentage = (usedMemory.toFloat() / totalMemory * 100).toInt()
            if (usagePercentage > 85) {
                logger.logPerformanceAlert(
                    "HIGH_MEMORY_USAGE",
                    usagePercentage.toFloat(),
                    85f
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting memory info", e)
        }
    }
    
    /**
     * Obtiene resumen actual de métricas
     */
    suspend fun getCurrentMetrics(): MetricsSummary {
        return metrics.getMetricsSummary()
    }
    
    /**
     * Genera reporte completo para debugging
     */
    suspend fun generateDebugReport(): String {
        return dashboard.generateDetailedReport()
    }
    
    /**
     * Exporta métricas en formato JSON
     */
    suspend fun exportMetricsAsJson(): String {
        return dashboard.exportToJson()
    }
    
    /**
     * Añade listener para actualizaciones del dashboard
     */
    fun addDashboardListener(listener: MetricsDashboard.DashboardListener) {
        dashboard.addListener(listener)
    }
    
    /**
     * Remueve listener del dashboard
     */
    fun removeDashboardListener(listener: MetricsDashboard.DashboardListener) {
        dashboard.removeListener(listener)
    }
    
    /**
     * Reinicia todas las métricas
     */
    suspend fun resetMetrics() {
        metrics.reset()
        Log.i(TAG, "All metrics reset")
    }
    
    /**
     * Obtiene ruta del archivo de log
     */
    fun getLogFilePath(): String? {
        return logger.getLogFilePath()
    }
    
    /**
     * Limpia archivos de log
     */
    fun clearLogs() {
        logger.clearLogs()
    }
    
    /**
     * Wrapper para medir tiempo de ejecución de operaciones
     */
    suspend fun <T> measureOperation(
        operationName: String,
        operation: suspend () -> T
    ): T {
        val startTime = System.currentTimeMillis()
        return try {
            val result = operation()
            val processingTime = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "$operationName completed in ${processingTime}ms")
            
            // Registrar tiempo de operación genérica
            logger.logFrameEvent(processingTime, operationName)
            
            result
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "$operationName failed after ${processingTime}ms", e)
            
            // Registrar operación fallida
            logger.logPerformanceAlert(
                "OPERATION_FAILED",
                processingTime.toFloat(),
                0f
            )
            
            throw e
        }
    }
    
    /**
     * Configuración de alertas personalizadas
     */
    fun configureAlerts(
        memoryThreshold: Int = 85,
        processingTimeThreshold: Long = 200,
        successRateThreshold: Int = 70
    ) {
        // En una implementación completa, esto configuraría umbrales dinámicos
        Log.i(TAG, "Alert thresholds configured: memory=$memoryThreshold%, " +
                "processing=${processingTimeThreshold}ms, successRate=$successRateThreshold%")
    }
    
    /**
     * Obtiene estadísticas de rendimiento en tiempo real
     */
    suspend fun getRealtimePerformance(): PerformanceMetrics {
        val frameHistory = metrics.getFrameProcessingHistory()
        val memoryHistory = metrics.getMemoryUsageHistory()
        
        val avgFrameTime = frameHistory.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L
        val fps = if (avgFrameTime > 0) 1000f / avgFrameTime else 0f
        
        val currentMemory = memoryHistory.lastOrNull()
        val memoryUsage = currentMemory?.usagePercentage ?: 0
        
        return PerformanceMetrics(
            fps = fps,
            frameProcessingTime = avgFrameTime,
            memoryUsage = memoryUsage,
            cpuUsage = 0f, // Requeriría implementación adicional
            thermalState = "NORMAL" // Requeriría integración con ThermalStateDetector
        )
    }
}
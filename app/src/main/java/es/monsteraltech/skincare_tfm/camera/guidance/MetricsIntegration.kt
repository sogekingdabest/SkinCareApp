package es.monsteraltech.skincare_tfm.camera.guidance
import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
class MetricsIntegration(
    private val context: Context,
    private val config: MetricsConfig = MetricsConfig()
) {
    companion object {
        private const val TAG = "MetricsIntegration"
        private const val MEMORY_MONITORING_INTERVAL = 2000L
    }
    private val metrics = CaptureMetrics.getInstance()
    private val logger = MetricsLogger(context, config)
    private val dashboard = MetricsDashboard(context, metrics)
    private var isRunning = false
    private var monitoringJob: Job? = null
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    fun start() {
        if (isRunning) return
        isRunning = true
        logger.start()
        if (config.enablePerformanceTracking) {
            dashboard.start()
            startMemoryMonitoring()
        }
        Log.i(TAG, "Metrics integration started")
    }
    fun stop() {
        if (!isRunning) return
        isRunning = false
        logger.stop()
        dashboard.stop()
        monitoringJob?.cancel()
        Log.i(TAG, "Metrics integration stopped")
    }
    fun logMoleDetection(
        success: Boolean,
        processingTime: Long,
        confidence: Float? = null
    ) {
        metrics.logDetectionAttempt(success, processingTime)
        logger.logDetectionEvent(success, processingTime, confidence)
    }
    suspend fun logQualityAnalysis(
        qualityMetrics: ImageQualityAnalyzer.QualityMetrics,
        processingTime: Long
    ) {
        metrics.logQualityAnalysis(qualityMetrics, processingTime)
        logger.logQualityEvent(
            qualityMetrics.sharpness,
            qualityMetrics.brightness,
            qualityMetrics.contrast,
            processingTime
        )
    }
    fun logCaptureValidation(
        validationResult: CaptureValidationManager.ValidationResult,
        processingTime: Long
    ) {
        metrics.logCaptureValidation(validationResult, processingTime)
        logger.logValidationEvent(
            validationResult.guideState.name,
            validationResult.canCapture,
            processingTime
        )
    }
    fun logImagePreprocessing(
        processingTime: Long,
        filtersApplied: List<String>
    ) {
        metrics.logPreprocessingPerformance(processingTime, filtersApplied)
        logger.logPreprocessingEvent(processingTime, filtersApplied)
    }
    suspend fun logFrameProcessing(
        processingTime: Long,
        frameWidth: Int,
        frameHeight: Int
    ) {
        val frameSize = "${frameWidth}x${frameHeight}"
        metrics.logFrameProcessingTime(processingTime)
        logger.logFrameEvent(processingTime, frameSize)
    }
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
    private suspend fun monitorMemoryUsage() {
        try {
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val totalMemory = memoryInfo.totalMem
            val availableMemory = memoryInfo.availMem
            val usedMemory = totalMemory - availableMemory
            metrics.logMemoryUsage(usedMemory, totalMemory)
            logger.logMemoryEvent(usedMemory, totalMemory)
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
    suspend fun getCurrentMetrics(): MetricsSummary {
        return metrics.getMetricsSummary()
    }
    suspend fun generateDebugReport(): String {
        return dashboard.generateDetailedReport()
    }
    suspend fun exportMetricsAsJson(): String {
        return dashboard.exportToJson()
    }
    fun addDashboardListener(listener: MetricsDashboard.DashboardListener) {
        dashboard.addListener(listener)
    }
    fun removeDashboardListener(listener: MetricsDashboard.DashboardListener) {
        dashboard.removeListener(listener)
    }
    suspend fun resetMetrics() {
        metrics.reset()
        Log.i(TAG, "All metrics reset")
    }
    fun getLogFilePath(): String? {
        return logger.getLogFilePath()
    }
    fun clearLogs() {
        logger.clearLogs()
    }
    suspend fun <T> measureOperation(
        operationName: String,
        operation: suspend () -> T
    ): T {
        val startTime = System.currentTimeMillis()
        return try {
            val result = operation()
            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "$operationName completed in ${processingTime}ms")
            logger.logFrameEvent(processingTime, operationName)
            result
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "$operationName failed after ${processingTime}ms", e)
            logger.logPerformanceAlert(
                "OPERATION_FAILED",
                processingTime.toFloat(),
                0f
            )
            throw e
        }
    }
    fun configureAlerts(
        memoryThreshold: Int = 85,
        processingTimeThreshold: Long = 200,
        successRateThreshold: Int = 70
    ) {
        Log.i(TAG, "Alert thresholds configured: memory=$memoryThreshold%, " +
                "processing=${processingTimeThreshold}ms, successRate=$successRateThreshold%")
    }
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
            cpuUsage = 0f,
            thermalState = "NORMAL"
        )
    }
}
package es.monsteraltech.skincare_tfm.camera.guidance
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
class MetricsDashboard(
    private val context: Context,
    private val metrics: CaptureMetrics = CaptureMetrics.getInstance()
) {
    companion object {
        private const val TAG = "MetricsDashboard"
        private const val UPDATE_INTERVAL_MS = 1000L
    }
    private var isRunning = false
    private var dashboardJob: Job? = null
    private val dateFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val listeners = mutableListOf<DashboardListener>()
    interface DashboardListener {
        fun onMetricsUpdated(stats: DashboardStats)
        fun onPerformanceAlert(alert: PerformanceAlert)
    }
    data class PerformanceAlert(
        val type: AlertType,
        val message: String,
        val severity: Severity,
        val timestamp: Long = System.currentTimeMillis()
    )
    enum class AlertType {
        HIGH_MEMORY_USAGE,
        SLOW_PROCESSING,
        LOW_SUCCESS_RATE,
        THERMAL_THROTTLING
    }
    enum class Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    fun start() {
        if (isRunning) return
        isRunning = true
        dashboardJob = CoroutineScope(Dispatchers.Default).launch {
            while (isRunning) {
                try {
                    updateDashboard()
                    delay(UPDATE_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating dashboard", e)
                }
            }
        }
        Log.i(TAG, "Metrics dashboard started")
    }
    fun stop() {
        isRunning = false
        dashboardJob?.cancel()
        Log.i(TAG, "Metrics dashboard stopped")
    }
    fun addListener(listener: DashboardListener) {
        listeners.add(listener)
    }
    fun removeListener(listener: DashboardListener) {
        listeners.remove(listener)
    }
    private suspend fun updateDashboard() {
        val summary = metrics.getMetricsSummary()
        val qualityHistory = metrics.getQualityMetricsHistory()
        val frameHistory = metrics.getFrameProcessingHistory()
        val stats = calculateDashboardStats(summary, qualityHistory, frameHistory)
        withContext(Dispatchers.Main) {
            listeners.forEach { it.onMetricsUpdated(stats) }
        }
        checkPerformanceAlerts(summary)
        if (summary.detectionAttempts > 0) {
            logDashboardSummary(stats)
        }
    }
    private fun calculateDashboardStats(
        summary: MetricsSummary,
        qualityHistory: List<QualityMetricsSnapshot>,
        frameHistory: List<Long>
    ): DashboardStats {
        val avgQualityScore = if (qualityHistory.isNotEmpty()) {
            qualityHistory.map { calculateQualityScore(it) }.average().toFloat()
        } else 0f
        val mostCommonState = summary.validationStates.maxByOrNull { it.value }?.key ?: "UNKNOWN"
        val performanceScore = calculatePerformanceScore(summary, avgQualityScore, frameHistory)
        return DashboardStats(
            totalSessions = 1,
            avgSessionDuration = summary.sessionDuration,
            totalDetectionAttempts = summary.detectionAttempts,
            overallSuccessRate = summary.detectionSuccessRate.toFloat(),
            avgDetectionTime = summary.avgDetectionTime.toFloat(),
            avgQualityScore = avgQualityScore,
            mostCommonValidationState = mostCommonState,
            performanceScore = performanceScore
        )
    }
    private fun calculateQualityScore(snapshot: QualityMetricsSnapshot): Float {
        var score = 0f
        score += (snapshot.sharpness * 25).coerceAtMost(25f)
        val brightnessScore = when {
            snapshot.brightness in 80f..180f -> 25f
            snapshot.brightness in 60f..220f -> 15f
            else -> 5f
        }
        score += brightnessScore
        score += (snapshot.contrast * 25).coerceAtMost(25f)
        if (snapshot.isBlurry) score -= 15f
        if (snapshot.isOverexposed || snapshot.isUnderexposed) score -= 10f
        return score.coerceIn(0f, 100f)
    }
    private fun calculatePerformanceScore(
        summary: MetricsSummary,
        avgQualityScore: Float,
        frameHistory: List<Long>
    ): Float {
        var score = 100f
        if (summary.detectionSuccessRate < 70) {
            score -= (70 - summary.detectionSuccessRate) * 0.5f
        }
        if (summary.avgDetectionTime > 100) {
            score -= ((summary.avgDetectionTime - 100) / 10f).coerceAtMost(20f)
        }
        if (avgQualityScore < 60) {
            score -= (60 - avgQualityScore) * 0.3f
        }
        val avgFrameTime = frameHistory.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        if (avgFrameTime > 50) {
            score -= ((avgFrameTime - 50) / 5).toFloat().coerceAtMost(15f)
        }
        return score.coerceIn(0f, 100f)
    }
    private suspend fun checkPerformanceAlerts(summary: MetricsSummary) {
        val alerts = mutableListOf<PerformanceAlert>()
        summary.currentMemoryUsage?.let { memory ->
            when {
                memory.usagePercentage > 90 -> alerts.add(
                    PerformanceAlert(
                        AlertType.HIGH_MEMORY_USAGE,
                        "Uso crítico de memoria: ${memory.usagePercentage}%",
                        Severity.CRITICAL
                    )
                )
                memory.usagePercentage > 75 -> alerts.add(
                    PerformanceAlert(
                        AlertType.HIGH_MEMORY_USAGE,
                        "Uso alto de memoria: ${memory.usagePercentage}%",
                        Severity.HIGH
                    )
                )
                else -> {
                }
            }
        }
        if (summary.avgDetectionTime > 200) {
            alerts.add(
                PerformanceAlert(
                    AlertType.SLOW_PROCESSING,
                    "Detección lenta: ${summary.avgDetectionTime}ms promedio",
                    if (summary.avgDetectionTime > 500) Severity.HIGH else Severity.MEDIUM
                )
            )
        }
        if (summary.detectionAttempts > 10 && summary.detectionSuccessRate < 50) {
            alerts.add(
                PerformanceAlert(
                    AlertType.LOW_SUCCESS_RATE,
                    "Baja tasa de éxito: ${summary.detectionSuccessRate}%",
                    Severity.HIGH
                )
            )
        }
        withContext(Dispatchers.Main) {
            alerts.forEach { alert ->
                listeners.forEach { listener -> listener.onPerformanceAlert(alert) }
            }
        }
    }
    private fun logDashboardSummary(stats: DashboardStats) {
        val timestamp = dateFormatter.format(Date())
        Log.i(TAG, "[$timestamp] Dashboard Summary:")
        Log.i(TAG, "  Detection Success Rate: ${stats.overallSuccessRate.roundToInt()}%")
        Log.i(TAG, "  Avg Detection Time: ${stats.avgDetectionTime.roundToInt()}ms")
        Log.i(TAG, "  Avg Quality Score: ${stats.avgQualityScore.roundToInt()}/100")
        Log.i(TAG, "  Performance Score: ${stats.performanceScore.roundToInt()}/100")
        Log.i(TAG, "  Most Common State: ${stats.mostCommonValidationState}")
    }
    suspend fun generateDetailedReport(): String {
        val summary = metrics.getMetricsSummary()
        val qualityHistory = metrics.getQualityMetricsHistory()
        val frameHistory = metrics.getFrameProcessingHistory()
        val memoryHistory = metrics.getMemoryUsageHistory()
        return buildString {
            appendLine("=== DETAILED METRICS REPORT ===")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine()
            appendLine("GENERAL SUMMARY:")
            appendLine("Session Duration: ${summary.sessionDuration / 1000}s")
            appendLine("Detection Attempts: ${summary.detectionAttempts}")
            appendLine("Success Rate: ${summary.detectionSuccessRate}%")
            appendLine("Avg Detection Time: ${summary.avgDetectionTime}ms")
            appendLine()
            if (qualityHistory.isNotEmpty()) {
                appendLine("QUALITY METRICS (last 10):")
                qualityHistory.takeLast(10).forEach { snapshot ->
                    val time = dateFormatter.format(Date(snapshot.timestamp))
                    appendLine("  [$time] Sharpness: ${String.format("%.2f", snapshot.sharpness)}, " +
                            "Brightness: ${snapshot.brightness.roundToInt()}, " +
                            "Quality Score: ${calculateQualityScore(snapshot).roundToInt()}")
                }
                appendLine()
            }
            if (frameHistory.isNotEmpty()) {
                val avgFrameTime = frameHistory.average()
                val minFrameTime = frameHistory.minOrNull() ?: 0L
                val maxFrameTime = frameHistory.maxOrNull() ?: 0L
                appendLine("FRAME PROCESSING:")
                appendLine("  Average: ${avgFrameTime.roundToInt()}ms")
                appendLine("  Min: ${minFrameTime}ms")
                appendLine("  Max: ${maxFrameTime}ms")
                appendLine("  Samples: ${frameHistory.size}")
                appendLine()
            }
            if (memoryHistory.isNotEmpty()) {
                appendLine("MEMORY USAGE (last 5):")
                memoryHistory.takeLast(5).forEach { snapshot ->
                    val time = dateFormatter.format(Date(snapshot.timestamp))
                    appendLine("  [$time] ${snapshot.usagePercentage}% " +
                            "(${snapshot.usedMemory / 1024 / 1024}MB / ${snapshot.totalMemory / 1024 / 1024}MB)")
                }
                appendLine()
            }
            appendLine("VALIDATION STATES:")
            summary.validationStates.forEach { (state, count) ->
                val percentage = if (summary.detectionAttempts > 0) {
                    (count.toFloat() / summary.detectionAttempts * 100).roundToInt()
                } else 0
                appendLine("  $state: $count ($percentage%)")
            }
        }
    }
    suspend fun exportToJson(): String {
        val summary = metrics.getMetricsSummary()
        val qualityHistory = metrics.getQualityMetricsHistory()
        return buildString {
            appendLine("{")
            appendLine("  \"timestamp\": ${System.currentTimeMillis()},")
            appendLine("  \"sessionDuration\": ${summary.sessionDuration},")
            appendLine("  \"detectionAttempts\": ${summary.detectionAttempts},")
            appendLine("  \"successRate\": ${summary.detectionSuccessRate},")
            appendLine("  \"avgDetectionTime\": ${summary.avgDetectionTime},")
            appendLine("  \"avgQualityAnalysisTime\": ${summary.avgQualityAnalysisTime},")
            appendLine("  \"qualityHistory\": [")
            qualityHistory.forEachIndexed { index, snapshot ->
                appendLine("    {")
                appendLine("      \"timestamp\": ${snapshot.timestamp},")
                appendLine("      \"sharpness\": ${snapshot.sharpness},")
                appendLine("      \"brightness\": ${snapshot.brightness},")
                appendLine("      \"contrast\": ${snapshot.contrast},")
                appendLine("      \"isBlurry\": ${snapshot.isBlurry},")
                appendLine("      \"processingTime\": ${snapshot.processingTime}")
                append("    }")
                if (index < qualityHistory.size - 1) appendLine(",")
            }
            appendLine("  ]")
            appendLine("}")
        }
    }
}
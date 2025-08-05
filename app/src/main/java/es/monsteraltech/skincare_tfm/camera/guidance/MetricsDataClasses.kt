package es.monsteraltech.skincare_tfm.camera.guidance
data class QualityMetricsSnapshot(
    val timestamp: Long,
    val sharpness: Float,
    val brightness: Float,
    val contrast: Float,
    val isBlurry: Boolean,
    val isOverexposed: Boolean,
    val isUnderexposed: Boolean,
    val processingTime: Long
)
data class MemorySnapshot(
    val timestamp: Long,
    val usedMemory: Long,
    val totalMemory: Long,
    val usagePercentage: Int
)
data class MetricsSummary(
    val sessionDuration: Long,
    val detectionAttempts: Int,
    val successfulDetections: Int,
    val detectionSuccessRate: Int,
    val avgDetectionTime: Long,
    val avgQualityAnalysisTime: Long,
    val avgValidationTime: Long,
    val avgPreprocessingTime: Long,
    val avgFrameProcessingTime: Long,
    val validationStates: Map<String, Int>,
    val currentMemoryUsage: MemorySnapshot?
)
data class PerformanceMetrics(
    val fps: Float,
    val frameProcessingTime: Long,
    val memoryUsage: Int,
    val cpuUsage: Float,
    val thermalState: String
)
sealed class MetricEvent {
    abstract val timestamp: Long
    abstract val sessionId: String
    data class DetectionAttempt(
        override val timestamp: Long,
        override val sessionId: String,
        val success: Boolean,
        val processingTime: Long,
        val confidence: Float?
    ) : MetricEvent()
    data class QualityAnalysis(
        override val timestamp: Long,
        override val sessionId: String,
        val sharpness: Float,
        val brightness: Float,
        val contrast: Float,
        val processingTime: Long
    ) : MetricEvent()
    data class ValidationResult(
        override val timestamp: Long,
        override val sessionId: String,
        val state: String,
        val canCapture: Boolean,
        val processingTime: Long
    ) : MetricEvent()
    data class PreprocessingComplete(
        override val timestamp: Long,
        override val sessionId: String,
        val processingTime: Long,
        val filtersApplied: List<String>
    ) : MetricEvent()
    data class FrameProcessed(
        override val timestamp: Long,
        override val sessionId: String,
        val processingTime: Long,
        val frameSize: String
    ) : MetricEvent()
    data class MemorySnapshot(
        override val timestamp: Long,
        override val sessionId: String,
        val usedMemory: Long,
        val totalMemory: Long,
        val usagePercentage: Int
    ) : MetricEvent()
    data class PerformanceAlert(
        override val timestamp: Long,
        override val sessionId: String,
        val alertType: String,
        val value: Float,
        val threshold: Float
    ) : MetricEvent()
}
data class MetricsConfig(
    val enableLogging: Boolean = true,
    val enablePerformanceTracking: Boolean = true,
    val maxHistorySize: Int = 100,
    val logLevel: LogLevel = LogLevel.INFO,
    val exportFormat: ExportFormat = ExportFormat.TEXT
)
enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}
enum class ExportFormat {
    TEXT, JSON, CSV
}
data class DashboardStats(
    val totalSessions: Int,
    val avgSessionDuration: Long,
    val totalDetectionAttempts: Int,
    val overallSuccessRate: Float,
    val avgDetectionTime: Float,
    val avgQualityScore: Float,
    val mostCommonValidationState: String,
    val performanceScore: Float
)
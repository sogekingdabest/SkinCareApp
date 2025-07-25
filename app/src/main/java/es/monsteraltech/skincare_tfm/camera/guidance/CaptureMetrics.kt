package es.monsteraltech.skincare_tfm.camera.guidance

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Sistema de métricas para tracking de rendimiento del sistema de guías de captura
 */
class CaptureMetrics {
    companion object {
        private const val TAG = "CaptureMetrics"
        
        @Volatile
        private var INSTANCE: CaptureMetrics? = null
        
        fun getInstance(): CaptureMetrics {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CaptureMetrics().also { INSTANCE = it }
            }
        }
    }
    
    private val mutex = Mutex()
    private val sessionStartTime = System.currentTimeMillis()
    
    // Contadores de detección
    private val detectionAttempts = AtomicInteger(0)
    private val successfulDetections = AtomicInteger(0)
    private val failedDetections = AtomicInteger(0)
    
    // Métricas de tiempo
    private val totalDetectionTime = AtomicLong(0)
    private val totalQualityAnalysisTime = AtomicLong(0)
    private val totalValidationTime = AtomicLong(0)
    private val totalPreprocessingTime = AtomicLong(0)
    
    // Métricas de calidad
    private val qualityMetricsHistory = mutableListOf<QualityMetricsSnapshot>()
    private val validationResults = ConcurrentHashMap<String, AtomicInteger>()
    
    // Métricas de rendimiento
    private val frameProcessingTimes = mutableListOf<Long>()
    private val memoryUsageSnapshots = mutableListOf<MemorySnapshot>()
    
    /**
     * Registra un intento de detección de lunar
     */
    fun logDetectionAttempt(success: Boolean, processingTime: Long) {
        detectionAttempts.incrementAndGet()
        totalDetectionTime.addAndGet(processingTime)
        
        if (success) {
            successfulDetections.incrementAndGet()
        } else {
            failedDetections.incrementAndGet()
        }
        
        Log.d(TAG, "Detection attempt: success=$success, time=${processingTime}ms")
    }
    
    /**
     * Registra métricas de análisis de calidad
     */
    suspend fun logQualityAnalysis(metrics: ImageQualityAnalyzer.QualityMetrics, processingTime: Long) {
        mutex.withLock {
            totalQualityAnalysisTime.addAndGet(processingTime)
            
            val snapshot = QualityMetricsSnapshot(
                timestamp = System.currentTimeMillis(),
                sharpness = metrics.sharpness,
                brightness = metrics.brightness,
                contrast = metrics.contrast,
                isBlurry = metrics.isBlurry,
                isOverexposed = metrics.isOverexposed,
                isUnderexposed = metrics.isUnderexposed,
                processingTime = processingTime
            )
            
            qualityMetricsHistory.add(snapshot)
            
            // Mantener solo los últimos 100 registros
            if (qualityMetricsHistory.size > 100) {
                qualityMetricsHistory.removeAt(0)
            }
        }
        
        Log.d(TAG, "Quality analysis: sharpness=${metrics.sharpness}, brightness=${metrics.brightness}, time=${processingTime}ms")
    }
    
    /**
     * Registra resultado de validación de captura
     */
    fun logCaptureValidation(result: CaptureValidationManager.ValidationResult, processingTime: Long) {
        totalValidationTime.addAndGet(processingTime)
        
        val stateKey = result.guideState.name
        validationResults.computeIfAbsent(stateKey) { AtomicInteger(0) }.incrementAndGet()
        
        Log.d(TAG, "Validation: state=${result.guideState}, canCapture=${result.canCapture}, time=${processingTime}ms")
    }
    
    /**
     * Registra rendimiento de preprocesado
     */
    fun logPreprocessingPerformance(time: Long, filters: List<String>) {
        totalPreprocessingTime.addAndGet(time)
        
        Log.d(TAG, "Preprocessing: time=${time}ms, filters=${filters.joinToString(",")}")
    }
    
    /**
     * Registra tiempo de procesamiento de frame
     */
    suspend fun logFrameProcessingTime(time: Long) {
        mutex.withLock {
            frameProcessingTimes.add(time)
            
            // Mantener solo los últimos 50 registros
            if (frameProcessingTimes.size > 50) {
                frameProcessingTimes.removeAt(0)
            }
        }
    }
    
    /**
     * Registra snapshot de uso de memoria
     */
    suspend fun logMemoryUsage(usedMemory: Long, totalMemory: Long) {
        mutex.withLock {
            val snapshot = MemorySnapshot(
                timestamp = System.currentTimeMillis(),
                usedMemory = usedMemory,
                totalMemory = totalMemory,
                usagePercentage = (usedMemory.toFloat() / totalMemory * 100).toInt()
            )
            
            memoryUsageSnapshots.add(snapshot)
            
            // Mantener solo los últimos 20 registros
            if (memoryUsageSnapshots.size > 20) {
                memoryUsageSnapshots.removeAt(0)
            }
        }
    }
    
    /**
     * Obtiene resumen de métricas actuales
     */
    suspend fun getMetricsSummary(): MetricsSummary {
        return mutex.withLock {
            val sessionDuration = System.currentTimeMillis() - sessionStartTime
            val detectionSuccessRate = if (detectionAttempts.get() > 0) {
                (successfulDetections.get().toFloat() / detectionAttempts.get() * 100).toInt()
            } else 0
            
            val avgDetectionTime = if (detectionAttempts.get() > 0) {
                totalDetectionTime.get() / detectionAttempts.get()
            } else 0L
            
            val avgQualityAnalysisTime = if (qualityMetricsHistory.isNotEmpty()) {
                totalQualityAnalysisTime.get() / qualityMetricsHistory.size
            } else 0L
            
            val avgFrameProcessingTime = if (frameProcessingTimes.isNotEmpty()) {
                frameProcessingTimes.average().toLong()
            } else 0L
            
            MetricsSummary(
                sessionDuration = sessionDuration,
                detectionAttempts = detectionAttempts.get(),
                successfulDetections = successfulDetections.get(),
                detectionSuccessRate = detectionSuccessRate,
                avgDetectionTime = avgDetectionTime,
                avgQualityAnalysisTime = avgQualityAnalysisTime,
                avgValidationTime = if (validationResults.values.sumOf { it.get() } > 0) {
                    totalValidationTime.get() / validationResults.values.sumOf { it.get() }
                } else 0L,
                avgPreprocessingTime = if (totalPreprocessingTime.get() > 0) totalPreprocessingTime.get() else 0L,
                avgFrameProcessingTime = avgFrameProcessingTime,
                validationStates = validationResults.mapValues { it.value.get() },
                currentMemoryUsage = memoryUsageSnapshots.lastOrNull()
            )
        }
    }
    
    /**
     * Obtiene historial de métricas de calidad
     */
    suspend fun getQualityMetricsHistory(): List<QualityMetricsSnapshot> {
        return mutex.withLock {
            qualityMetricsHistory.toList()
        }
    }
    
    /**
     * Obtiene historial de tiempos de procesamiento de frames
     */
    suspend fun getFrameProcessingHistory(): List<Long> {
        return mutex.withLock {
            frameProcessingTimes.toList()
        }
    }
    
    /**
     * Obtiene historial de uso de memoria
     */
    suspend fun getMemoryUsageHistory(): List<MemorySnapshot> {
        return mutex.withLock {
            memoryUsageSnapshots.toList()
        }
    }
    
    /**
     * Reinicia todas las métricas
     */
    suspend fun reset() {
        mutex.withLock {
            detectionAttempts.set(0)
            successfulDetections.set(0)
            failedDetections.set(0)
            totalDetectionTime.set(0)
            totalQualityAnalysisTime.set(0)
            totalValidationTime.set(0)
            totalPreprocessingTime.set(0)
            qualityMetricsHistory.clear()
            validationResults.clear()
            frameProcessingTimes.clear()
            memoryUsageSnapshots.clear()
        }
        
        Log.i(TAG, "Metrics reset")
    }
    
    /**
     * Exporta métricas a formato de texto para debugging
     */
    suspend fun exportMetrics(): String {
        val summary = getMetricsSummary()
        return buildString {
            appendLine("=== CAPTURE GUIDANCE METRICS ===")
            appendLine("Session Duration: ${summary.sessionDuration / 1000}s")
            appendLine("Detection Attempts: ${summary.detectionAttempts}")
            appendLine("Successful Detections: ${summary.successfulDetections}")
            appendLine("Success Rate: ${summary.detectionSuccessRate}%")
            appendLine("Avg Detection Time: ${summary.avgDetectionTime}ms")
            appendLine("Avg Quality Analysis Time: ${summary.avgQualityAnalysisTime}ms")
            appendLine("Avg Validation Time: ${summary.avgValidationTime}ms")
            appendLine("Avg Frame Processing Time: ${summary.avgFrameProcessingTime}ms")
            appendLine()
            appendLine("Validation States:")
            summary.validationStates.forEach { (state, count) ->
                appendLine("  $state: $count")
            }
            appendLine()
            summary.currentMemoryUsage?.let { memory ->
                appendLine("Current Memory Usage: ${memory.usagePercentage}% (${memory.usedMemory / 1024 / 1024}MB / ${memory.totalMemory / 1024 / 1024}MB)")
            }
        }
    }
}
package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Logger especializado para métricas del sistema de guías de captura
 */
class MetricsLogger(
    private val context: Context,
    private val config: MetricsConfig = MetricsConfig()
) {
    companion object {
        private const val TAG = "MetricsLogger"
        private const val LOG_FILE_NAME = "capture_metrics.log"
        private const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024 // 5MB
        private const val FLUSH_INTERVAL_MS = 5000L // 5 segundos
    }
    
    private val sessionId = UUID.randomUUID().toString().take(8)
    private val eventQueue = ConcurrentLinkedQueue<MetricEvent>()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    private var isRunning = false
    private var loggerJob: Job? = null
    private var logFile: File? = null
    
    init {
        setupLogFile()
    }
    
    /**
     * Inicia el logger
     */
    fun start() {
        if (isRunning) return
        
        isRunning = true
        loggerJob = CoroutineScope(Dispatchers.IO).launch {
            while (isRunning) {
                try {
                    flushEvents()
                    delay(FLUSH_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error flushing log events", e)
                }
            }
        }
        
        logEvent(createSessionStartEvent())
        Log.i(TAG, "Metrics logger started with session ID: $sessionId")
    }
    
    /**
     * Detiene el logger
     */
    fun stop() {
        if (!isRunning) return
        
        logEvent(createSessionEndEvent())
        isRunning = false
        
        // Flush final de eventos pendientes
        runBlocking {
            flushEvents()
        }
        
        loggerJob?.cancel()
        Log.i(TAG, "Metrics logger stopped")
    }
    
    /**
     * Registra evento de detección
     */
    fun logDetectionEvent(success: Boolean, processingTime: Long, confidence: Float? = null) {
        if (!config.enableLogging) return
        
        val event = MetricEvent.DetectionAttempt(
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            success = success,
            processingTime = processingTime,
            confidence = confidence
        )
        
        eventQueue.offer(event)
        
        if (config.logLevel <= LogLevel.DEBUG) {
            Log.d(TAG, "Detection: success=$success, time=${processingTime}ms, confidence=$confidence")
        }
    }
    
    /**
     * Registra evento de análisis de calidad
     */
    fun logQualityEvent(
        sharpness: Float,
        brightness: Float,
        contrast: Float,
        processingTime: Long
    ) {
        if (!config.enableLogging) return
        
        val event = MetricEvent.QualityAnalysis(
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            sharpness = sharpness,
            brightness = brightness,
            contrast = contrast,
            processingTime = processingTime
        )
        
        eventQueue.offer(event)
        
        if (config.logLevel <= LogLevel.DEBUG) {
            Log.d(TAG, "Quality: sharpness=$sharpness, brightness=$brightness, time=${processingTime}ms")
        }
    }
    
    /**
     * Registra evento de validación
     */
    fun logValidationEvent(state: String, canCapture: Boolean, processingTime: Long) {
        if (!config.enableLogging) return
        
        val event = MetricEvent.ValidationResult(
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            state = state,
            canCapture = canCapture,
            processingTime = processingTime
        )
        
        eventQueue.offer(event)
        
        if (config.logLevel <= LogLevel.INFO) {
            Log.i(TAG, "Validation: state=$state, canCapture=$canCapture, time=${processingTime}ms")
        }
    }
    
    /**
     * Registra evento de preprocesado
     */
    fun logPreprocessingEvent(processingTime: Long, filtersApplied: List<String>) {
        if (!config.enableLogging) return
        
        val event = MetricEvent.PreprocessingComplete(
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            processingTime = processingTime,
            filtersApplied = filtersApplied
        )
        
        eventQueue.offer(event)
        
        if (config.logLevel <= LogLevel.INFO) {
            Log.i(TAG, "Preprocessing: time=${processingTime}ms, filters=${filtersApplied.joinToString(",")}")
        }
    }
    
    /**
     * Registra evento de procesamiento de frame
     */
    fun logFrameEvent(processingTime: Long, frameSize: String) {
        if (!config.enableLogging) return
        
        val event = MetricEvent.FrameProcessed(
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            processingTime = processingTime,
            frameSize = frameSize
        )
        
        eventQueue.offer(event)
        
        if (config.logLevel <= LogLevel.DEBUG) {
            Log.d(TAG, "Frame: time=${processingTime}ms, size=$frameSize")
        }
    }
    
    /**
     * Registra snapshot de memoria
     */
    fun logMemoryEvent(usedMemory: Long, totalMemory: Long) {
        if (!config.enableLogging) return
        
        val usagePercentage = (usedMemory.toFloat() / totalMemory * 100).toInt()
        
        val event = MetricEvent.MemorySnapshot(
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            usedMemory = usedMemory,
            totalMemory = totalMemory,
            usagePercentage = usagePercentage
        )
        
        eventQueue.offer(event)
        
        if (config.logLevel <= LogLevel.DEBUG) {
            Log.d(TAG, "Memory: ${usagePercentage}% (${usedMemory / 1024 / 1024}MB / ${totalMemory / 1024 / 1024}MB)")
        }
    }
    
    /**
     * Registra alerta de rendimiento
     */
    fun logPerformanceAlert(alertType: String, value: Float, threshold: Float) {
        val event = MetricEvent.PerformanceAlert(
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            alertType = alertType,
            value = value,
            threshold = threshold
        )
        
        eventQueue.offer(event)
        
        Log.w(TAG, "Performance Alert: $alertType - value=$value, threshold=$threshold")
    }
    
    /**
     * Registra un evento genérico
     */
    private fun logEvent(event: MetricEvent) {
        if (!config.enableLogging) return
        
        eventQueue.offer(event)
        
        if (config.logLevel <= LogLevel.DEBUG) {
            Log.d(TAG, "Event logged: ${event::class.simpleName}")
        }
    }
    
    /**
     * Configura archivo de log
     */
    private fun setupLogFile() {
        try {
            val logsDir = File(context.filesDir, "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
            
            logFile = File(logsDir, LOG_FILE_NAME)
            
            // Rotar archivo si es muy grande
            if (logFile?.exists() == true && (logFile?.length() ?: 0) > MAX_LOG_FILE_SIZE) {
                val backupFile = File(logsDir, "${LOG_FILE_NAME}.backup")
                logFile?.renameTo(backupFile)
                logFile = File(logsDir, LOG_FILE_NAME)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up log file", e)
        }
    }
    
    /**
     * Flush eventos pendientes al archivo
     */
    private suspend fun flushEvents() {
        if (eventQueue.isEmpty() || logFile == null) return
        
        withContext(Dispatchers.IO) {
            try {
                FileWriter(logFile, true).use { writer ->
                    while (eventQueue.isNotEmpty()) {
                        val event = eventQueue.poll() ?: break
                        val logLine = formatEvent(event)
                        writer.appendLine(logLine)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to log file", e)
            }
        }
    }
    
    /**
     * Formatea evento para logging
     */
    private fun formatEvent(event: MetricEvent): String {
        val timestamp = dateFormatter.format(Date(event.timestamp))
        
        return when (config.exportFormat) {
            ExportFormat.JSON -> formatEventAsJson(event, timestamp)
            ExportFormat.CSV -> formatEventAsCsv(event, timestamp)
            ExportFormat.TEXT -> formatEventAsText(event, timestamp)
        }
    }
    
    /**
     * Formatea evento como texto legible
     */
    private fun formatEventAsText(event: MetricEvent, timestamp: String): String {
        return when (event) {
            is MetricEvent.DetectionAttempt -> 
                "[$timestamp] DETECTION: success=${event.success}, time=${event.processingTime}ms, confidence=${event.confidence}"
            
            is MetricEvent.QualityAnalysis -> 
                "[$timestamp] QUALITY: sharpness=${event.sharpness}, brightness=${event.brightness}, contrast=${event.contrast}, time=${event.processingTime}ms"
            
            is MetricEvent.ValidationResult -> 
                "[$timestamp] VALIDATION: state=${event.state}, canCapture=${event.canCapture}, time=${event.processingTime}ms"
            
            is MetricEvent.PreprocessingComplete -> 
                "[$timestamp] PREPROCESSING: time=${event.processingTime}ms, filters=${event.filtersApplied.joinToString(",")}"
            
            is MetricEvent.FrameProcessed -> 
                "[$timestamp] FRAME: time=${event.processingTime}ms, size=${event.frameSize}"
            
            is MetricEvent.MemorySnapshot -> 
                "[$timestamp] MEMORY: usage=${event.usagePercentage}%, used=${event.usedMemory / 1024 / 1024}MB, total=${event.totalMemory / 1024 / 1024}MB"
            
            is MetricEvent.PerformanceAlert -> 
                "[$timestamp] ALERT: type=${event.alertType}, value=${event.value}, threshold=${event.threshold}"
        }
    }
    
    /**
     * Formatea evento como JSON
     */
    private fun formatEventAsJson(event: MetricEvent, timestamp: String): String {
        return when (event) {
            is MetricEvent.DetectionAttempt -> 
                """{"timestamp":"$timestamp","type":"detection","sessionId":"${event.sessionId}","success":${event.success},"processingTime":${event.processingTime},"confidence":${event.confidence}}"""
            
            is MetricEvent.QualityAnalysis -> 
                """{"timestamp":"$timestamp","type":"quality","sessionId":"${event.sessionId}","sharpness":${event.sharpness},"brightness":${event.brightness},"contrast":${event.contrast},"processingTime":${event.processingTime}}"""
            
            is MetricEvent.ValidationResult -> 
                """{"timestamp":"$timestamp","type":"validation","sessionId":"${event.sessionId}","state":"${event.state}","canCapture":${event.canCapture},"processingTime":${event.processingTime}}"""
            
            is MetricEvent.PreprocessingComplete -> 
                """{"timestamp":"$timestamp","type":"preprocessing","sessionId":"${event.sessionId}","processingTime":${event.processingTime},"filters":["${event.filtersApplied.joinToString("\",\"")}"]}"""
            
            is MetricEvent.FrameProcessed -> 
                """{"timestamp":"$timestamp","type":"frame","sessionId":"${event.sessionId}","processingTime":${event.processingTime},"frameSize":"${event.frameSize}"}"""
            
            is MetricEvent.MemorySnapshot -> 
                """{"timestamp":"$timestamp","type":"memory","sessionId":"${event.sessionId}","usedMemory":${event.usedMemory},"totalMemory":${event.totalMemory},"usagePercentage":${event.usagePercentage}}"""
            
            is MetricEvent.PerformanceAlert -> 
                """{"timestamp":"$timestamp","type":"alert","sessionId":"${event.sessionId}","alertType":"${event.alertType}","value":${event.value},"threshold":${event.threshold}}"""
        }
    }
    
    /**
     * Formatea evento como CSV
     */
    private fun formatEventAsCsv(event: MetricEvent, timestamp: String): String {
        return when (event) {
            is MetricEvent.DetectionAttempt -> 
                "$timestamp,detection,${event.sessionId},${event.success},${event.processingTime},${event.confidence ?: ""}"
            
            is MetricEvent.QualityAnalysis -> 
                "$timestamp,quality,${event.sessionId},${event.sharpness},${event.brightness},${event.contrast},${event.processingTime}"
            
            is MetricEvent.ValidationResult -> 
                "$timestamp,validation,${event.sessionId},${event.state},${event.canCapture},${event.processingTime}"
            
            is MetricEvent.PreprocessingComplete -> 
                "$timestamp,preprocessing,${event.sessionId},${event.processingTime},\"${event.filtersApplied.joinToString(",")}\""
            
            is MetricEvent.FrameProcessed -> 
                "$timestamp,frame,${event.sessionId},${event.processingTime},${event.frameSize}"
            
            is MetricEvent.MemorySnapshot -> 
                "$timestamp,memory,${event.sessionId},${event.usedMemory},${event.totalMemory},${event.usagePercentage}"
            
            is MetricEvent.PerformanceAlert -> 
                "$timestamp,alert,${event.sessionId},${event.alertType},${event.value},${event.threshold}"
        }
    }
    
    /**
     * Crea evento de inicio de sesión
     */
    private fun createSessionStartEvent(): MetricEvent.FrameProcessed {
        return MetricEvent.FrameProcessed(
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            processingTime = 0,
            frameSize = "SESSION_START"
        )
    }
    
    /**
     * Crea evento de fin de sesión
     */
    private fun createSessionEndEvent(): MetricEvent.FrameProcessed {
        return MetricEvent.FrameProcessed(
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            processingTime = 0,
            frameSize = "SESSION_END"
        )
    }
    
    /**
     * Obtiene ruta del archivo de log
     */
    fun getLogFilePath(): String? {
        return logFile?.absolutePath
    }
    
    /**
     * Limpia archivos de log antiguos
     */
    fun clearLogs() {
        try {
            val logsDir = File(context.filesDir, "logs")
            logsDir.listFiles()?.forEach { file ->
                if (file.name.contains("capture_metrics")) {
                    file.delete()
                }
            }
            Log.i(TAG, "Log files cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing log files", e)
        }
    }
}
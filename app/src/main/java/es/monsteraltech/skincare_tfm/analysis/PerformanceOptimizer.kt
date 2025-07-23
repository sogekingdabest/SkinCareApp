package es.monsteraltech.skincare_tfm.analysis

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import kotlin.system.measureTimeMillis

/**
 * Optimizador de rendimiento para el procesamiento de imágenes
 */
class PerformanceOptimizer {
    companion object {
        private const val TAG = "PerformanceOptimizer"
        
        // Configuración de hilos
        private const val MIN_THREADS = 2
        private const val MAX_THREADS = 4
        
        // Umbrales de rendimiento
        private const val SLOW_PROCESSING_THRESHOLD_MS = 5000L
        private const val VERY_SLOW_PROCESSING_THRESHOLD_MS = 10000L
    }
    
    // Pool de hilos optimizado para análisis de imágenes
    private val analysisThreadPool = Executors.newFixedThreadPool(
        minOf(MAX_THREADS, maxOf(MIN_THREADS, Runtime.getRuntime().availableProcessors()))
    ) as ThreadPoolExecutor
    
    // Dispatcher personalizado para análisis
    val analysisDispatcher = analysisThreadPool.asCoroutineDispatcher()
    
    // Métricas de rendimiento
    private var lastProcessingTime = 0L
    private var averageProcessingTime = 0L
    private var processedImages = 0
    
    /**
     * Información de rendimiento del dispositivo
     */
    data class DevicePerformance(
        val cpuCores: Int,
        val availableMemory: Long,
        val isLowEndDevice: Boolean,
        val recommendedThreads: Int,
        val recommendedImageSize: Int
    )
    
    /**
     * Métricas de procesamiento
     */
    data class ProcessingMetrics(
        val processingTimeMs: Long,
        val averageTimeMs: Long,
        val isSlowProcessing: Boolean,
        val memoryUsedMB: Long,
        val threadsUsed: Int
    )
    
    /**
     * Analiza el rendimiento del dispositivo
     */
    fun analyzeDevicePerformance(): DevicePerformance {
        val runtime = Runtime.getRuntime()
        val cpuCores = runtime.availableProcessors()
        val availableMemory = runtime.maxMemory()
        
        // Determinar si es un dispositivo de gama baja
        val isLowEndDevice = cpuCores <= 2 || availableMemory < 512 * 1024 * 1024 // < 512MB
        
        val recommendedThreads = when {
            isLowEndDevice -> 1
            cpuCores <= 4 -> 2
            else -> minOf(4, cpuCores - 1) // Dejar un core libre para UI
        }
        
        val recommendedImageSize = when {
            isLowEndDevice -> 512 * 512
            availableMemory < 1024 * 1024 * 1024 -> 1024 * 1024 // < 1GB RAM
            else -> 2048 * 2048
        }
        
        return DevicePerformance(
            cpuCores = cpuCores,
            availableMemory = availableMemory,
            isLowEndDevice = isLowEndDevice,
            recommendedThreads = recommendedThreads,
            recommendedImageSize = recommendedImageSize
        )
    }
    
    /**
     * Optimiza la configuración según el rendimiento del dispositivo
     */
    fun optimizeConfiguration(
        baseConfig: AnalysisConfiguration,
        devicePerformance: DevicePerformance
    ): AnalysisConfiguration {
        
        return when {
            devicePerformance.isLowEndDevice -> {
                Log.i(TAG, "Optimizando para dispositivo de gama baja")
                baseConfig.copy(
                    enableParallelProcessing = false,
                    maxImageResolution = devicePerformance.recommendedImageSize,
                    compressionQuality = 70,
                    timeoutMs = baseConfig.timeoutMs + 20000L, // Más tiempo
                    aiTimeoutMs = baseConfig.aiTimeoutMs + 10000L,
                    abcdeTimeoutMs = baseConfig.abcdeTimeoutMs + 5000L
                )
            }
            devicePerformance.cpuCores >= 6 -> {
                Log.i(TAG, "Optimizando para dispositivo de alto rendimiento")
                baseConfig.copy(
                    enableParallelProcessing = true,
                    maxImageResolution = devicePerformance.recommendedImageSize,
                    compressionQuality = 90,
                    timeoutMs = maxOf(15000L, baseConfig.timeoutMs - 5000L), // Menos tiempo
                    aiTimeoutMs = maxOf(8000L, baseConfig.aiTimeoutMs - 3000L),
                    abcdeTimeoutMs = maxOf(5000L, baseConfig.abcdeTimeoutMs - 2000L)
                )
            }
            else -> {
                Log.i(TAG, "Usando configuración balanceada")
                baseConfig.copy(
                    maxImageResolution = devicePerformance.recommendedImageSize
                )
            }
        }
    }
    
    /**
     * Ejecuta una tarea con medición de rendimiento
     */
    suspend fun <T> measurePerformance(
        taskName: String,
        task: suspend () -> T
    ): Pair<T, ProcessingMetrics> {
        
        val memoryBefore = getUsedMemoryMB()
        val threadsUsed = analysisThreadPool.activeCount
        
        val result: T
        val processingTime = measureTimeMillis {
            result = task()
        }
        
        val memoryAfter = getUsedMemoryMB()
        val memoryUsed = memoryAfter - memoryBefore
        
        // Actualizar métricas
        updateProcessingMetrics(processingTime)
        
        val metrics = ProcessingMetrics(
            processingTimeMs = processingTime,
            averageTimeMs = averageProcessingTime,
            isSlowProcessing = processingTime > SLOW_PROCESSING_THRESHOLD_MS,
            memoryUsedMB = memoryUsed,
            threadsUsed = threadsUsed
        )
        
        Log.d(TAG, "Tarea '$taskName' completada en ${processingTime}ms (promedio: ${averageProcessingTime}ms)")
        
        if (metrics.isSlowProcessing) {
            Log.w(TAG, "Procesamiento lento detectado para '$taskName': ${processingTime}ms")
        }
        
        return Pair(result, metrics)
    }
    
    /**
     * Optimiza un bitmap para procesamiento rápido
     */
    fun optimizeBitmapForProcessing(
        bitmap: Bitmap,
        targetSize: Int = 1024 * 1024
    ): Bitmap {
        val currentPixels = bitmap.width * bitmap.height
        
        if (currentPixels <= targetSize) {
            return bitmap
        }
        
        // Calcular nueva resolución
        val ratio = kotlin.math.sqrt(targetSize.toDouble() / currentPixels)
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        
        Log.d(TAG, "Optimizando bitmap para rendimiento: ${bitmap.width}x${bitmap.height} -> ${newWidth}x${newHeight}")
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Ejecuta análisis en paralelo de forma optimizada
     */
    suspend fun <T, U> executeParallelOptimized(
        task1: suspend () -> T,
        task2: suspend () -> U,
        devicePerformance: DevicePerformance
    ): Pair<T, U> = coroutineScope {
        
        if (devicePerformance.isLowEndDevice) {
            // En dispositivos de gama baja, ejecutar secuencialmente
            Log.d(TAG, "Ejecutando tareas secuencialmente (dispositivo de gama baja)")
            val result1 = task1()
            val result2 = task2()
            Pair(result1, result2)
        } else {
            // En dispositivos potentes, ejecutar en paralelo
            Log.d(TAG, "Ejecutando tareas en paralelo")
            val deferred1 = async(analysisDispatcher) { task1() }
            val deferred2 = async(analysisDispatcher) { task2() }
            
            Pair(deferred1.await(), deferred2.await())
        }
    }
    
    /**
     * Adapta timeouts según el rendimiento histórico
     */
    fun getAdaptiveTimeouts(baseConfig: AnalysisConfiguration): AnalysisConfiguration {
        if (processedImages < 3) {
            // No hay suficientes datos históricos
            return baseConfig
        }
        
        val multiplier = when {
            averageProcessingTime > VERY_SLOW_PROCESSING_THRESHOLD_MS -> 2.0f
            averageProcessingTime > SLOW_PROCESSING_THRESHOLD_MS -> 1.5f
            averageProcessingTime < 2000L -> 0.8f // Dispositivo rápido
            else -> 1.0f
        }
        
        Log.d(TAG, "Adaptando timeouts con multiplicador: $multiplier (promedio: ${averageProcessingTime}ms)")
        
        return baseConfig.copy(
            timeoutMs = (baseConfig.timeoutMs * multiplier).toLong(),
            aiTimeoutMs = (baseConfig.aiTimeoutMs * multiplier).toLong(),
            abcdeTimeoutMs = (baseConfig.abcdeTimeoutMs * multiplier).toLong()
        )
    }
    
    /**
     * Verifica si el procesamiento debe usar modo de ahorro de energía
     */
    fun shouldUsePowerSaveMode(): Boolean {
        // En una implementación real, esto verificaría el estado de la batería
        // Por ahora, basarse en el rendimiento histórico
        return averageProcessingTime > VERY_SLOW_PROCESSING_THRESHOLD_MS
    }
    
    /**
     * Obtiene configuración optimizada para ahorro de energía
     */
    fun getPowerSaveConfiguration(baseConfig: AnalysisConfiguration): AnalysisConfiguration {
        Log.i(TAG, "Aplicando configuración de ahorro de energía")
        
        return baseConfig.copy(
            enableParallelProcessing = false,
            maxImageResolution = 512 * 512,
            compressionQuality = 60,
            timeoutMs = baseConfig.timeoutMs + 30000L, // Más tiempo pero menos CPU
            aiTimeoutMs = baseConfig.aiTimeoutMs + 15000L,
            abcdeTimeoutMs = baseConfig.abcdeTimeoutMs + 10000L
        )
    }
    
    /**
     * Actualiza las métricas de procesamiento
     */
    private fun updateProcessingMetrics(processingTime: Long) {
        lastProcessingTime = processingTime
        processedImages++
        
        // Calcular promedio móvil
        averageProcessingTime = if (processedImages == 1) {
            processingTime
        } else {
            ((averageProcessingTime * (processedImages - 1)) + processingTime) / processedImages
        }
    }
    
    /**
     * Obtiene la memoria usada en MB
     */
    private fun getUsedMemoryMB(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }
    
    /**
     * Obtiene estadísticas de rendimiento
     */
    fun getPerformanceStats(): Map<String, Any> {
        return mapOf(
            "processedImages" to processedImages,
            "lastProcessingTimeMs" to lastProcessingTime,
            "averageProcessingTimeMs" to averageProcessingTime,
            "activeThreads" to analysisThreadPool.activeCount,
            "queuedTasks" to analysisThreadPool.queue.size,
            "usedMemoryMB" to getUsedMemoryMB()
        )
    }
    
    /**
     * Limpia recursos del optimizador
     */
    fun cleanup() {
        Log.d(TAG, "Limpiando recursos del optimizador de rendimiento")
        analysisDispatcher.close()
        analysisThreadPool.shutdown()
    }
}
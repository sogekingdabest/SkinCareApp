package es.monsteraltech.skincare_tfm.analysis
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import kotlin.system.measureTimeMillis

class PerformanceOptimizer {
    companion object {
        private const val TAG = "PerformanceOptimizer"
        private const val MIN_THREADS = 2
        private const val MAX_THREADS = 4
        private const val SLOW_PROCESSING_THRESHOLD_MS = 5000L
        private const val VERY_SLOW_PROCESSING_THRESHOLD_MS = 10000L
    }
    private val analysisThreadPool = Executors.newFixedThreadPool(
        minOf(MAX_THREADS, maxOf(MIN_THREADS, Runtime.getRuntime().availableProcessors()))
    ) as ThreadPoolExecutor
    val analysisDispatcher = analysisThreadPool.asCoroutineDispatcher()
    private var lastProcessingTime = 0L
    private var averageProcessingTime = 0L
    private var processedImages = 0
    data class DevicePerformance(
        val cpuCores: Int,
        val availableMemory: Long,
        val isLowEndDevice: Boolean,
        val recommendedThreads: Int,
        val recommendedImageSize: Int
    )
    data class ProcessingMetrics(
        val processingTimeMs: Long,
        val averageTimeMs: Long,
        val isSlowProcessing: Boolean,
        val memoryUsedMB: Long,
        val threadsUsed: Int
    )
    fun analyzeDevicePerformance(): DevicePerformance {
        val runtime = Runtime.getRuntime()
        val cpuCores = runtime.availableProcessors()
        val availableMemory = runtime.maxMemory()
        val isLowEndDevice = cpuCores <= 2 || availableMemory < 512 * 1024 * 1024
        val recommendedThreads = when {
            isLowEndDevice -> 1
            cpuCores <= 4 -> 2
            else -> minOf(4, cpuCores - 1)
        }
        val recommendedImageSize = when {
            isLowEndDevice -> 512 * 512
            availableMemory < 1024 * 1024 * 1024 -> 1024 * 1024
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
                    timeoutMs = baseConfig.timeoutMs + 20000L,
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
                    timeoutMs = maxOf(15000L, baseConfig.timeoutMs - 5000L),
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
    fun optimizeBitmapForProcessing(
        bitmap: Bitmap,
        targetSize: Int = 1024 * 1024
    ): Bitmap {
        val currentPixels = bitmap.width * bitmap.height
        if (currentPixels <= targetSize) {
            return bitmap
        }
        val ratio = kotlin.math.sqrt(targetSize.toDouble() / currentPixels)
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        Log.d(TAG, "Optimizando bitmap para rendimiento: ${bitmap.width}x${bitmap.height} -> ${newWidth}x${newHeight}")
        return bitmap.scale(newWidth, newHeight)
    }
    suspend fun <T, U> executeParallelOptimized(
        task1: suspend () -> T,
        task2: suspend () -> U,
        devicePerformance: DevicePerformance
    ): Pair<T, U> = coroutineScope {
        if (devicePerformance.isLowEndDevice) {
            Log.d(TAG, "Ejecutando tareas secuencialmente (dispositivo de gama baja)")
            val result1 = task1()
            val result2 = task2()
            Pair(result1, result2)
        } else {
            Log.d(TAG, "Ejecutando tareas en paralelo")
            val deferred1 = async(analysisDispatcher) { task1() }
            val deferred2 = async(analysisDispatcher) { task2() }
            Pair(deferred1.await(), deferred2.await())
        }
    }
    fun getAdaptiveTimeouts(baseConfig: AnalysisConfiguration): AnalysisConfiguration {
        if (processedImages < 3) {
            return baseConfig
        }
        val multiplier = when {
            averageProcessingTime > VERY_SLOW_PROCESSING_THRESHOLD_MS -> 2.0f
            averageProcessingTime > SLOW_PROCESSING_THRESHOLD_MS -> 1.5f
            averageProcessingTime < 2000L -> 0.8f
            else -> 1.0f
        }
        Log.d(TAG, "Adaptando timeouts con multiplicador: $multiplier (promedio: ${averageProcessingTime}ms)")
        return baseConfig.copy(
            timeoutMs = (baseConfig.timeoutMs * multiplier).toLong(),
            aiTimeoutMs = (baseConfig.aiTimeoutMs * multiplier).toLong(),
            abcdeTimeoutMs = (baseConfig.abcdeTimeoutMs * multiplier).toLong()
        )
    }
    fun shouldUsePowerSaveMode(): Boolean {
        return averageProcessingTime > VERY_SLOW_PROCESSING_THRESHOLD_MS
    }
    fun getPowerSaveConfiguration(baseConfig: AnalysisConfiguration): AnalysisConfiguration {
        Log.i(TAG, "Aplicando configuración de ahorro de energía")
        return baseConfig.copy(
            enableParallelProcessing = false,
            maxImageResolution = 512 * 512,
            compressionQuality = 60,
            timeoutMs = baseConfig.timeoutMs + 30000L,
            aiTimeoutMs = baseConfig.aiTimeoutMs + 15000L,
            abcdeTimeoutMs = baseConfig.abcdeTimeoutMs + 10000L
        )
    }
    private fun updateProcessingMetrics(processingTime: Long) {
        lastProcessingTime = processingTime
        processedImages++
        averageProcessingTime = if (processedImages == 1) {
            processingTime
        } else {
            ((averageProcessingTime * (processedImages - 1)) + processingTime) / processedImages
        }
    }
    private fun getUsedMemoryMB(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }
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
    fun cleanup() {
        Log.d(TAG, "Limpiando recursos del optimizador de rendimiento")
        analysisDispatcher.close()
        analysisThreadPool.shutdown()
    }
}
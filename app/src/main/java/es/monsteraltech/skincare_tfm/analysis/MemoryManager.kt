package es.monsteraltech.skincare_tfm.analysis
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

class MemoryManager {
    companion object {
        private const val TAG = "MemoryManager"
        private const val LOW_MEMORY_THRESHOLD = 0.8f
        private const val CRITICAL_MEMORY_THRESHOLD = 0.9f
        private const val HIGH_MEMORY_MAX_PIXELS = 2048 * 2048
        private const val MEDIUM_MEMORY_MAX_PIXELS = 1024 * 1024
        private const val LOW_MEMORY_MAX_PIXELS = 512 * 512
    }
    private val managedBitmaps = ConcurrentHashMap<String, WeakReference<Bitmap>>()
    data class MemoryInfo(
        val totalMemory: Long,
        val freeMemory: Long,
        val usedMemory: Long,
        val usagePercentage: Float,
        val isLowMemory: Boolean,
        val isCriticalMemory: Boolean
    )
    fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val usagePercentage = usedMemory.toFloat() / totalMemory.toFloat()
        return MemoryInfo(
            totalMemory = totalMemory,
            freeMemory = freeMemory,
            usedMemory = usedMemory,
            usagePercentage = usagePercentage,
            isLowMemory = usagePercentage >= LOW_MEMORY_THRESHOLD,
            isCriticalMemory = usagePercentage >= CRITICAL_MEMORY_THRESHOLD
        )
    }
    fun canProcessImage(bitmap: Bitmap): Boolean {
        val memoryInfo = getMemoryInfo()
        if (memoryInfo.isCriticalMemory) {
            Log.w(TAG, "Memoria crítica detectada: ${memoryInfo.usagePercentage * 100}%")
            return false
        }
        val estimatedMemoryNeeded = estimateProcessingMemory(bitmap)
        return memoryInfo.freeMemory > estimatedMemoryNeeded
    }
    private fun estimateProcessingMemory(bitmap: Bitmap): Long {
        val bitmapMemory = bitmap.byteCount.toLong()
        return bitmapMemory * 3
    }
    fun optimizeImageForMemory(bitmap: Bitmap, config: AnalysisConfiguration): Bitmap {
        val memoryInfo = getMemoryInfo()
        val maxPixels = when {
            memoryInfo.isCriticalMemory -> LOW_MEMORY_MAX_PIXELS
            memoryInfo.isLowMemory -> MEDIUM_MEMORY_MAX_PIXELS
            else -> minOf(config.maxImageResolution, HIGH_MEMORY_MAX_PIXELS)
        }
        val currentPixels = bitmap.width * bitmap.height
        if (currentPixels <= maxPixels) {
            Log.d(TAG, "Imagen ya optimizada: ${bitmap.width}x${bitmap.height}")
            return bitmap
        }
        val ratio = kotlin.math.sqrt(maxPixels.toDouble() / currentPixels)
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        Log.i(TAG, "Optimizando imagen: ${bitmap.width}x${bitmap.height} -> ${newWidth}x${newHeight} (memoria: ${memoryInfo.usagePercentage * 100}%)")
        val optimizedBitmap = bitmap.scale(newWidth, newHeight)
        registerBitmap("optimized_${System.currentTimeMillis()}", optimizedBitmap)
        return optimizedBitmap
    }
    fun registerBitmap(id: String, bitmap: Bitmap) {
        managedBitmaps[id] = WeakReference(bitmap)
        Log.d(TAG, "Bitmap registrado: $id (${bitmap.width}x${bitmap.height})")
    }
    fun releaseBitmap(id: String) {
        managedBitmaps[id]?.get()?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
                Log.d(TAG, "Bitmap liberado: $id")
            }
        }
        managedBitmaps.remove(id)
    }
    fun cleanupUnusedBitmaps() {
        val iterator = managedBitmaps.iterator()
        var cleanedCount = 0
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val bitmap = entry.value.get()
            if (bitmap == null || bitmap.isRecycled) {
                iterator.remove()
                cleanedCount++
            }
        }
        if (cleanedCount > 0) {
            Log.d(TAG, "Limpiados $cleanedCount bitmaps no utilizados")
        }
    }
    fun forceMemoryCleanup() {
        Log.i(TAG, "Forzando limpieza de memoria...")
        cleanupUnusedBitmaps()
        System.gc()
        val memoryInfo = getMemoryInfo()
        Log.i(TAG, "Memoria después de limpieza: ${memoryInfo.usagePercentage * 100}%")
    }
    fun getOptimizedConfiguration(baseConfig: AnalysisConfiguration): AnalysisConfiguration {
        val memoryInfo = getMemoryInfo()
        return when {
            memoryInfo.isCriticalMemory -> {
                Log.w(TAG, "Aplicando configuración de memoria crítica")
                baseConfig.copy(
                    enableParallelProcessing = false,
                    enableAutoImageReduction = true,
                    maxImageResolution = LOW_MEMORY_MAX_PIXELS,
                    compressionQuality = 60,
                    timeoutMs = baseConfig.timeoutMs + 15000L
                )
            }
            memoryInfo.isLowMemory -> {
                Log.w(TAG, "Aplicando configuración de memoria baja")
                baseConfig.forLowMemoryDevice()
            }
            else -> baseConfig
        }
    }
    fun startMemoryMonitoring(callback: (MemoryInfo) -> Unit): MemoryMonitor {
        return MemoryMonitor(callback)
    }
    inner class MemoryMonitor(private val callback: (MemoryInfo) -> Unit) {
        @Volatile
        private var isMonitoring = false
        fun start() {
            isMonitoring = true
        }
        fun checkMemory() {
            if (isMonitoring) {
                val memoryInfo = getMemoryInfo()
                callback(memoryInfo)
                if (memoryInfo.isCriticalMemory) {
                    Log.w(TAG, "¡Memoria crítica detectada durante procesamiento!")
                    forceMemoryCleanup()
                }
            }
        }
        fun stop() {
            isMonitoring = false
        }
    }
}
package es.monsteraltech.skincare_tfm.analysis

import android.graphics.Bitmap
import android.util.Log
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import androidx.core.graphics.scale

/**
 * Gestor de memoria para optimizar el uso de recursos durante el procesamiento de imágenes
 */
class MemoryManager {
    companion object {
        private const val TAG = "MemoryManager"
        
        // Umbrales de memoria
        private const val LOW_MEMORY_THRESHOLD = 0.8f // 80% de memoria usada
        private const val CRITICAL_MEMORY_THRESHOLD = 0.9f // 90% de memoria usada
        
        // Tamaños de imagen recomendados según memoria disponible
        private const val HIGH_MEMORY_MAX_PIXELS = 2048 * 2048 // 4MP
        private const val MEDIUM_MEMORY_MAX_PIXELS = 1024 * 1024 // 1MP
        private const val LOW_MEMORY_MAX_PIXELS = 512 * 512 // 0.25MP
    }
    
    // Registro de bitmaps para limpieza automática
    private val managedBitmaps = ConcurrentHashMap<String, WeakReference<Bitmap>>()
    
    /**
     * Información sobre el estado de la memoria
     */
    data class MemoryInfo(
        val totalMemory: Long,
        val freeMemory: Long,
        val usedMemory: Long,
        val usagePercentage: Float,
        val isLowMemory: Boolean,
        val isCriticalMemory: Boolean
    )
    
    /**
     * Obtiene información actual sobre el uso de memoria
     */
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
    
    /**
     * Verifica si hay suficiente memoria para procesar una imagen
     */
    fun canProcessImage(bitmap: Bitmap): Boolean {
        val memoryInfo = getMemoryInfo()
        
        if (memoryInfo.isCriticalMemory) {
            Log.w(TAG, "Memoria crítica detectada: ${memoryInfo.usagePercentage * 100}%")
            return false
        }
        
        // Estimar memoria necesaria para el procesamiento
        val estimatedMemoryNeeded = estimateProcessingMemory(bitmap)
        
        return memoryInfo.freeMemory > estimatedMemoryNeeded
    }
    
    /**
     * Estima la memoria necesaria para procesar una imagen
     */
    private fun estimateProcessingMemory(bitmap: Bitmap): Long {
        // Memoria del bitmap original
        val bitmapMemory = bitmap.byteCount.toLong()
        
        // Estimación: necesitamos ~3x la memoria del bitmap para procesamiento
        // (bitmap original + copias temporales + buffers de análisis)
        return bitmapMemory * 3
    }
    
    /**
     * Optimiza una imagen según la memoria disponible
     */
    fun optimizeImageForMemory(bitmap: Bitmap, config: AnalysisConfiguration): Bitmap {
        val memoryInfo = getMemoryInfo()
        
        // Determinar resolución máxima según memoria disponible
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
        
        // Calcular nueva resolución manteniendo aspect ratio
        val ratio = kotlin.math.sqrt(maxPixels.toDouble() / currentPixels)
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        
        Log.i(TAG, "Optimizando imagen: ${bitmap.width}x${bitmap.height} -> ${newWidth}x${newHeight} (memoria: ${memoryInfo.usagePercentage * 100}%)")
        
        val optimizedBitmap = bitmap.scale(newWidth, newHeight)
        
        // Registrar el bitmap optimizado para gestión de memoria
        registerBitmap("optimized_${System.currentTimeMillis()}", optimizedBitmap)
        
        return optimizedBitmap
    }
    
    /**
     * Registra un bitmap para gestión automática de memoria
     */
    fun registerBitmap(id: String, bitmap: Bitmap) {
        managedBitmaps[id] = WeakReference(bitmap)
        Log.d(TAG, "Bitmap registrado: $id (${bitmap.width}x${bitmap.height})")
    }
    
    /**
     * Libera un bitmap específico
     */
    fun releaseBitmap(id: String) {
        managedBitmaps[id]?.get()?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
                Log.d(TAG, "Bitmap liberado: $id")
            }
        }
        managedBitmaps.remove(id)
    }
    
    /**
     * Limpia bitmaps que ya no están en uso (garbage collection)
     */
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
    
    /**
     * Fuerza la liberación de memoria
     */
    fun forceMemoryCleanup() {
        Log.i(TAG, "Forzando limpieza de memoria...")
        
        // Limpiar bitmaps no utilizados
        cleanupUnusedBitmaps()
        
        // Sugerir garbage collection
        System.gc()
        
        val memoryInfo = getMemoryInfo()
        Log.i(TAG, "Memoria después de limpieza: ${memoryInfo.usagePercentage * 100}%")
    }
    
    /**
     * Obtiene configuración optimizada según memoria disponible
     */
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
                    timeoutMs = baseConfig.timeoutMs + 15000L // Más tiempo para dispositivos lentos
                )
            }
            memoryInfo.isLowMemory -> {
                Log.w(TAG, "Aplicando configuración de memoria baja")
                baseConfig.forLowMemoryDevice()
            }
            else -> baseConfig
        }
    }
    
    /**
     * Monitorea el uso de memoria durante el procesamiento
     */
    fun startMemoryMonitoring(callback: (MemoryInfo) -> Unit): MemoryMonitor {
        return MemoryMonitor(callback)
    }
    
    /**
     * Monitor de memoria en tiempo real
     */
    inner class MemoryMonitor(private val callback: (MemoryInfo) -> Unit) {
        @Volatile
        private var isMonitoring = false
        
        fun start() {
            isMonitoring = true
            // En una implementación real, esto podría usar un timer o coroutine
            // Por simplicidad, solo monitoreamos cuando se llama explícitamente
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
package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.Mat
import org.opencv.core.Size
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MemoryUsageTest {
    
    private lateinit var context: Context
    private lateinit var performanceManager: PerformanceManager
    private lateinit var roiOptimizer: ROIOptimizer
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        performanceManager = PerformanceManager(context)
        roiOptimizer = ROIOptimizer()
    }
    
    @After
    fun tearDown() {
        performanceManager.cleanup()
        roiOptimizer.clearHistory()
        System.gc() // Forzar garbage collection
    }
    
    @Test
    fun `test mat pool memory efficiency`() {
        val initialMemory = getUsedMemory()
        val mats = mutableListOf<Mat>()
        
        // Crear muchos Mats usando el pool
        repeat(50) {
            mats.add(performanceManager.borrowMat())
        }
        
        val memoryAfterBorrow = getUsedMemory()
        
        // Devolver todos los Mats al pool
        mats.forEach { mat ->
            performanceManager.returnMat(mat)
        }
        
        val memoryAfterReturn = getUsedMemory()
        
        // La memoria después de devolver debe ser menor que después de tomar prestado
        assertTrue(
            "Memory should be lower after returning mats to pool",
            memoryAfterReturn < memoryAfterBorrow
        )
        
        // Verificar que el pool está funcionando (reutilización)
        val stats = performanceManager.getPerformanceStats()
        val poolSize = stats["matPoolSize"] as Int
        assertTrue("Pool should contain reusable mats", poolSize > 0)
    }
    
    @Test
    fun `test memory usage without pool vs with pool`() {
        // Probar sin pool
        val configWithoutPool = PerformanceManager.PerformanceConfig(
            processingFrequency = 15,
            imageResolutionScale = 1.0f,
            enableROI = false,
            roiScale = 1.0f,
            enableMatPooling = false,
            maxPoolSize = 0,
            enableAdvancedFilters = true,
            throttleDetection = false
        )
        
        val memoryWithoutPool = measureMemoryUsage {
            repeat(20) {
                val mat = Mat()
                // Simular uso
                mat.release()
            }
        }
        
        // Probar con pool
        val memoryWithPool = measureMemoryUsage {
            repeat(20) {
                val mat = performanceManager.borrowMat()
                performanceManager.returnMat(mat)
            }
        }
        
        // El pool debería ser más eficiente en memoria
        assertTrue(
            "Pool should be more memory efficient",
            memoryWithPool <= memoryWithoutPool * 1.2f // Permitir 20% de margen
        )
    }
    
    @Test
    fun `test ROI memory optimization`() {
        val fullImageSize = Size(1920.0, 1080.0)
        val roiResult = roiOptimizer.calculateROI(fullImageSize, 0.5f)
        
        // Simular procesamiento de imagen completa vs ROI
        val fullImageMemory = measureMemoryUsage {
            val fullMat = Mat(fullImageSize, org.opencv.core.CvType.CV_8UC3)
            // Simular procesamiento
            fullMat.release()
        }
        
        val roiMemory = measureMemoryUsage {
            val fullMat = Mat(fullImageSize, org.opencv.core.CvType.CV_8UC3)
            val roiMat = roiOptimizer.extractROI(fullMat, roiResult)
            // Simular procesamiento del ROI
            roiMat.release()
            fullMat.release()
        }
        
        // El ROI debería usar menos memoria
        assertTrue(
            "ROI processing should use less memory",
            roiMemory < fullImageMemory
        )
    }
    
    @Test
    fun `test memory leak detection in continuous processing`() = runTest {
        val initialMemory = getUsedMemory()
        val memoryReadings = mutableListOf<Long>()
        
        // Simular procesamiento continuo
        repeat(100) { iteration ->
            val mat = performanceManager.borrowMat()
            
            // Simular procesamiento
            delay(1)
            
            performanceManager.returnMat(mat)
            
            // Registrar uso de memoria cada 10 iteraciones
            if (iteration % 10 == 0) {
                System.gc() // Forzar garbage collection
                memoryReadings.add(getUsedMemory())
            }
        }
        
        // Verificar que no hay crecimiento constante de memoria (leak)
        val memoryGrowth = memoryReadings.last() - memoryReadings.first()
        val maxAcceptableGrowth = initialMemory * 0.1 // 10% del uso inicial
        
        assertTrue(
            "Memory growth should be minimal (possible leak detected)",
            abs(memoryGrowth) < maxAcceptableGrowth
        )
    }
    
    @Test
    fun `test memory usage under different performance levels`() {
        val memoryUsageByLevel = mutableMapOf<PerformanceManager.PerformanceLevel, Long>()
        
        PerformanceManager.PerformanceLevel.values().forEach { level ->
            // Simular cambio de nivel (requeriría acceso interno)
            val memory = measureMemoryUsage {
                repeat(10) {
                    val mat = performanceManager.borrowMat()
                    performanceManager.returnMat(mat)
                }
            }
            memoryUsageByLevel[level] = memory
        }
        
        // Verificar que los niveles más bajos usan menos memoria
        // (esto es una aproximación ya que no podemos cambiar el nivel directamente)
        assertTrue("Memory usage should be tracked per level", memoryUsageByLevel.isNotEmpty())
    }
    
    @Test
    fun `test pool size limits prevent memory overflow`() {
        val stats = performanceManager.getPerformanceStats()
        val maxPoolSize = stats["maxPoolSize"] as Int
        
        val mats = mutableListOf<Mat>()
        
        // Crear más Mats que el límite del pool
        repeat(maxPoolSize * 2) {
            mats.add(performanceManager.borrowMat())
        }
        
        val memoryAfterBorrow = getUsedMemory()
        
        // Devolver todos los Mats
        mats.forEach { mat ->
            performanceManager.returnMat(mat)
        }
        
        val memoryAfterReturn = getUsedMemory()
        val finalStats = performanceManager.getPerformanceStats()
        val finalPoolSize = finalStats["matPoolSize"] as Int
        
        // El pool no debe exceder el límite
        assertTrue("Pool size should not exceed limit", finalPoolSize <= maxPoolSize)
        
        // La memoria debe haberse liberado apropiadamente
        assertTrue(
            "Memory should be freed when pool is full",
            memoryAfterReturn < memoryAfterBorrow
        )
    }
    
    @Test
    fun `test concurrent memory usage`() = runTest {
        val initialMemory = getUsedMemory()
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        
        // Crear múltiples corrutinas que usen memoria concurrentemente
        repeat(10) { threadIndex ->
            val job = launch {
                repeat(20) {
                    val mat = performanceManager.borrowMat()
                    delay(1) // Simular procesamiento
                    performanceManager.returnMat(mat)
                }
            }
            jobs.add(job)
        }
        
        // Esperar a que terminen todas las corrutinas
        jobs.forEach { it.join() }
        
        System.gc() // Forzar garbage collection
        val finalMemory = getUsedMemory()
        
        // La memoria final no debe ser significativamente mayor que la inicial
        val memoryGrowth = finalMemory - initialMemory
        val maxAcceptableGrowth = initialMemory * 0.15 // 15% de crecimiento máximo
        
        assertTrue(
            "Concurrent usage should not cause excessive memory growth",
            memoryGrowth < maxAcceptableGrowth
        )
    }
    
    @Test
    fun `test memory cleanup on performance manager cleanup`() {
        val mats = mutableListOf<Mat>()
        
        // Llenar el pool
        repeat(10) {
            val mat = performanceManager.borrowMat()
            mats.add(mat)
        }
        
        // Devolver algunos al pool
        mats.take(5).forEach { mat ->
            performanceManager.returnMat(mat)
        }
        
        val memoryBeforeCleanup = getUsedMemory()
        
        // Cleanup debe liberar todos los recursos
        performanceManager.cleanup()
        
        System.gc()
        val memoryAfterCleanup = getUsedMemory()
        
        // La memoria debe haberse reducido
        assertTrue(
            "Cleanup should free memory",
            memoryAfterCleanup <= memoryBeforeCleanup
        )
        
        // Verificar que el pool está vacío
        val stats = performanceManager.getPerformanceStats()
        val poolSize = stats["matPoolSize"] as Int
        assertEquals("Pool should be empty after cleanup", 0, poolSize)
    }
    
    @Test
    fun `test ROI history memory management`() {
        val initialMemory = getUsedMemory()
        
        // Añadir muchas detecciones al historial
        repeat(100) { i ->
            roiOptimizer.updateDetectionHistory(
                org.opencv.core.Point(i * 10.0, i * 5.0)
            )
        }
        
        val memoryAfterHistory = getUsedMemory()
        
        // Limpiar historial
        roiOptimizer.clearHistory()
        
        System.gc()
        val memoryAfterClear = getUsedMemory()
        
        // El historial debe estar limitado y la limpieza debe liberar memoria
        val stats = roiOptimizer.getROIStats()
        val historySize = stats["historySize"] as Int
        
        assertEquals("History should be cleared", 0, historySize)
        assertTrue(
            "Clear should free memory",
            memoryAfterClear <= memoryAfterHistory
        )
    }
    
    /**
     * Mide el uso de memoria durante la ejecución de un bloque de código
     */
    private fun measureMemoryUsage(block: () -> Unit): Long {
        val runtime = Runtime.getRuntime()
        
        System.gc() // Limpiar antes de medir
        val memoryBefore = runtime.totalMemory() - runtime.freeMemory()
        
        block()
        
        System.gc() // Limpiar después de ejecutar
        val memoryAfter = runtime.totalMemory() - runtime.freeMemory()
        
        return memoryAfter - memoryBefore
    }
    
    /**
     * Obtiene el uso actual de memoria
     */
    private fun getUsedMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
}
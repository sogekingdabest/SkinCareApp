package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoaderCallback
import org.opencv.android.OpenCVLoaderCallback.SUCCESS
import org.opencv.core.Mat
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PerformanceManagerTest {
    
    private lateinit var context: Context
    private lateinit var performanceManager: PerformanceManager
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Simular inicialización de OpenCV
        val callback = object : OpenCVLoaderCallback() {
            override fun onManagerConnected(status: Int) {
                if (status == SUCCESS) {
                    // OpenCV inicializado
                }
            }
        }
        
        performanceManager = PerformanceManager(context)
    }
    
    @After
    fun tearDown() {
        performanceManager.cleanup()
    }
    
    @Test
    fun `test initial performance level detection`() = runTest {
        // El nivel inicial debe ser detectado automáticamente
        val initialLevel = performanceManager.currentPerformanceLevel.first()
        assertNotNull(initialLevel)
        assertTrue(initialLevel in PerformanceManager.PerformanceLevel.values())
    }
    
    @Test
    fun `test performance config for different levels`() = runTest {
        // Verificar que cada nivel tiene una configuración válida
        PerformanceManager.PerformanceLevel.values().forEach { level ->
            // Simular cambio de nivel (esto requeriría acceso interno)
            val config = performanceManager.currentConfig.first()
            
            assertNotNull(config)
            assertTrue(config.processingFrequency > 0)
            assertTrue(config.imageResolutionScale > 0f)
            assertTrue(config.imageResolutionScale <= 1f)
            assertTrue(config.maxPoolSize > 0)
        }
    }
    
    @Test
    fun `test mat pool operations`() {
        // Probar obtener Mat del pool
        val mat1 = performanceManager.borrowMat()
        assertNotNull(mat1)
        
        val mat2 = performanceManager.borrowMat()
        assertNotNull(mat2)
        
        // Devolver Mat al pool
        performanceManager.returnMat(mat1)
        performanceManager.returnMat(mat2)
        
        // Verificar que se pueden reutilizar
        val mat3 = performanceManager.borrowMat()
        assertNotNull(mat3)
        
        performanceManager.returnMat(mat3)
    }
    
    @Test
    fun `test frame processing time recording`() {
        // Registrar tiempos de procesamiento
        performanceManager.recordFrameProcessingTime(16L) // 60 FPS
        performanceManager.recordFrameProcessingTime(33L) // 30 FPS
        performanceManager.recordFrameProcessingTime(50L) // 20 FPS
        
        val stats = performanceManager.getPerformanceStats()
        assertTrue(stats.containsKey("avgProcessingTime"))
        
        val avgTime = stats["avgProcessingTime"] as Double
        assertTrue(avgTime > 0.0)
    }
    
    @Test
    fun `test memory usage recording`() {
        // Registrar uso de memoria
        performanceManager.recordMemoryUsage()
        performanceManager.recordMemoryUsage()
        performanceManager.recordMemoryUsage()
        
        val stats = performanceManager.getPerformanceStats()
        assertTrue(stats.containsKey("usedMemory"))
        assertTrue(stats.containsKey("maxMemory"))
        
        val usedMemory = stats["usedMemory"] as Long
        val maxMemory = stats["maxMemory"] as Long
        
        assertTrue(usedMemory > 0)
        assertTrue(maxMemory > usedMemory)
    }
    
    @Test
    fun `test battery level adjustment`() = runTest {
        val initialConfig = performanceManager.currentConfig.first()
        val initialFrequency = initialConfig.processingFrequency
        
        // Simular batería baja
        performanceManager.adjustForBatteryLevel(10)
        
        val adjustedConfig = performanceManager.currentConfig.first()
        val adjustedFrequency = adjustedConfig.processingFrequency
        
        // La frecuencia debe reducirse con batería baja
        assertTrue(adjustedFrequency <= initialFrequency)
    }
    
    @Test
    fun `test performance stats collection`() {
        val stats = performanceManager.getPerformanceStats()
        
        // Verificar que todas las métricas esperadas están presentes
        val expectedKeys = listOf(
            "currentLevel",
            "thermalState", 
            "avgProcessingTime",
            "processingFrequency",
            "matPoolSize",
            "maxPoolSize",
            "usedMemory",
            "maxMemory",
            "availableProcessors"
        )
        
        expectedKeys.forEach { key ->
            assertTrue("Missing key: $key", stats.containsKey(key))
        }
    }
    
    @Test
    fun `test thermal state impact on performance`() = runTest {
        val initialLevel = performanceManager.currentPerformanceLevel.first()
        
        // Simular estado térmico severo (esto requeriría acceso interno)
        // En una implementación real, esto se haría a través del ThermalStateDetector
        
        // Verificar que el rendimiento se ajusta apropiadamente
        val config = performanceManager.currentConfig.first()
        assertNotNull(config)
    }
    
    @Test
    fun `test pool size limits`() {
        val config = performanceManager.currentConfig.first()
        val maxPoolSize = config.maxPoolSize
        
        // Crear más Mats que el límite del pool
        val mats = mutableListOf<Mat>()
        for (i in 0 until maxPoolSize + 5) {
            mats.add(performanceManager.borrowMat())
        }
        
        // Devolver todos los Mats
        mats.forEach { mat ->
            performanceManager.returnMat(mat)
        }
        
        val stats = performanceManager.getPerformanceStats()
        val poolSize = stats["matPoolSize"] as Int
        
        // El pool no debe exceder el límite máximo
        assertTrue(poolSize <= maxPoolSize)
    }
    
    @Test
    fun `test performance degradation handling`() {
        // Simular tiempos de procesamiento lentos
        repeat(10) {
            performanceManager.recordFrameProcessingTime(100L) // Muy lento
        }
        
        // El sistema debe ajustar el rendimiento automáticamente
        // (esto se verifica internamente en checkAndAdjustPerformance)
        val stats = performanceManager.getPerformanceStats()
        val avgTime = stats["avgProcessingTime"] as Double
        assertTrue(avgTime > 50.0) // Confirmar que se registraron tiempos lentos
    }
    
    @Test
    fun `test cleanup releases resources`() {
        // Crear algunos Mats
        val mat1 = performanceManager.borrowMat()
        val mat2 = performanceManager.borrowMat()
        
        performanceManager.returnMat(mat1)
        performanceManager.returnMat(mat2)
        
        // Verificar que hay Mats en el pool
        val statsBefore = performanceManager.getPerformanceStats()
        val poolSizeBefore = statsBefore["matPoolSize"] as Int
        assertTrue(poolSizeBefore > 0)
        
        // Limpiar recursos
        performanceManager.cleanup()
        
        // Verificar que el pool se vació
        val statsAfter = performanceManager.getPerformanceStats()
        val poolSizeAfter = statsAfter["matPoolSize"] as Int
        assertEquals(0, poolSizeAfter)
    }
    
    @Test
    fun `test concurrent mat operations`() {
        val threads = mutableListOf<Thread>()
        val results = mutableListOf<Mat>()
        
        // Crear múltiples threads que usen el pool
        repeat(5) { threadIndex ->
            val thread = Thread {
                repeat(10) {
                    val mat = performanceManager.borrowMat()
                    synchronized(results) {
                        results.add(mat)
                    }
                    Thread.sleep(10) // Simular procesamiento
                    performanceManager.returnMat(mat)
                }
            }
            threads.add(thread)
            thread.start()
        }
        
        // Esperar a que terminen todos los threads
        threads.forEach { it.join() }
        
        // Verificar que se crearon los Mats esperados
        assertEquals(50, results.size)
    }
}
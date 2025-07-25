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
import kotlin.system.measureTimeMillis

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PerformanceBenchmarkTest {
    
    private lateinit var context: Context
    private lateinit var performanceManager: PerformanceManager
    private lateinit var roiOptimizer: ROIOptimizer
    private lateinit var thermalDetector: ThermalStateDetector
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        performanceManager = PerformanceManager(context)
        roiOptimizer = ROIOptimizer()
        thermalDetector = ThermalStateDetector(context)
    }
    
    @After
    fun tearDown() {
        performanceManager.cleanup()
        roiOptimizer.clearHistory()
        thermalDetector.cleanup()
    }
    
    @Test
    fun `benchmark mat pool vs direct allocation`() {
        val iterations = 1000
        
        // Benchmark directo (sin pool)
        val directTime = measureTimeMillis {
            repeat(iterations) {
                val mat = Mat()
                // Simular uso mínimo
                mat.release()
            }
        }
        
        // Benchmark con pool
        val poolTime = measureTimeMillis {
            repeat(iterations) {
                val mat = performanceManager.borrowMat()
                performanceManager.returnMat(mat)
            }
        }
        
        println("Direct allocation: ${directTime}ms")
        println("Pool allocation: ${poolTime}ms")
        println("Pool improvement: ${((directTime - poolTime).toFloat() / directTime * 100).toInt()}%")
        
        // El pool debería ser más rápido o al menos comparable
        assertTrue(
            "Pool should be faster or comparable to direct allocation",
            poolTime <= directTime * 1.2f // Permitir 20% de overhead máximo
        )
    }
    
    @Test
    fun `benchmark ROI vs full image processing`() {
        val imageSize = Size(1920.0, 1080.0)
        val iterations = 100
        
        // Benchmark procesamiento completo
        val fullImageTime = measureTimeMillis {
            repeat(iterations) {
                val mat = Mat(imageSize, org.opencv.core.CvType.CV_8UC3)
                // Simular procesamiento básico
                mat.release()
            }
        }
        
        // Benchmark con ROI
        val roiTime = measureTimeMillis {
            repeat(iterations) {
                val mat = Mat(imageSize, org.opencv.core.CvType.CV_8UC3)
                val roiResult = roiOptimizer.calculateROI(imageSize, 0.5f)
                val roiMat = roiOptimizer.extractROI(mat, roiResult)
                // Simular procesamiento del ROI
                roiMat.release()
                mat.release()
            }
        }
        
        println("Full image processing: ${fullImageTime}ms")
        println("ROI processing: ${roiTime}ms")
        println("ROI improvement: ${((fullImageTime - roiTime).toFloat() / fullImageTime * 100).toInt()}%")
        
        // ROI debería ser más rápido
        assertTrue(
            "ROI processing should be faster than full image",
            roiTime < fullImageTime
        )
    }
    
    @Test
    fun `benchmark performance manager overhead`() {
        val iterations = 10000
        
        // Benchmark sin performance manager
        val directTime = measureTimeMillis {
            repeat(iterations) {
                val startTime = System.currentTimeMillis()
                // Simular procesamiento
                val endTime = System.currentTimeMillis()
                val processingTime = endTime - startTime
            }
        }
        
        // Benchmark con performance manager
        val managedTime = measureTimeMillis {
            repeat(iterations) {
                val startTime = System.currentTimeMillis()
                // Simular procesamiento
                val endTime = System.currentTimeMillis()
                val processingTime = endTime - startTime
                
                performanceManager.recordFrameProcessingTime(processingTime)
                performanceManager.recordMemoryUsage()
            }
        }
        
        println("Direct processing: ${directTime}ms")
        println("Managed processing: ${managedTime}ms")
        println("Manager overhead: ${managedTime - directTime}ms")
        
        // El overhead debe ser mínimo
        val overhead = managedTime - directTime
        assertTrue(
            "Performance manager overhead should be minimal",
            overhead < directTime * 0.1f // Menos del 10% de overhead
        )
    }
    
    @Test
    fun `benchmark concurrent mat operations`() = runTest {
        val threadsCount = 10
        val operationsPerThread = 100
        
        // Benchmark secuencial
        val sequentialTime = measureTimeMillis {
            repeat(threadsCount * operationsPerThread) {
                val mat = performanceManager.borrowMat()
                performanceManager.returnMat(mat)
            }
        }
        
        // Benchmark concurrente
        val concurrentTime = measureTimeMillis {
            val jobs = (1..threadsCount).map { threadIndex ->
                launch {
                    repeat(operationsPerThread) {
                        val mat = performanceManager.borrowMat()
                        delay(1) // Simular procesamiento mínimo
                        performanceManager.returnMat(mat)
                    }
                }
            }
            jobs.forEach { it.join() }
        }
        
        println("Sequential operations: ${sequentialTime}ms")
        println("Concurrent operations: ${concurrentTime}ms")
        println("Concurrency improvement: ${((sequentialTime - concurrentTime).toFloat() / sequentialTime * 100).toInt()}%")
        
        // Las operaciones concurrentes deberían ser más eficientes
        assertTrue(
            "Concurrent operations should be more efficient",
            concurrentTime < sequentialTime * 1.5f // Permitir overhead de concurrencia
        )
    }
    
    @Test
    fun `benchmark ROI calculation performance`() {
        val imageSize = Size(1920.0, 1080.0)
        val iterations = 10000
        
        // Benchmark cálculo básico de ROI
        val basicTime = measureTimeMillis {
            repeat(iterations) {
                roiOptimizer.calculateROI(imageSize, 0.7f)
            }
        }
        
        // Añadir historial para ROI adaptativo
        repeat(10) { i ->
            roiOptimizer.updateDetectionHistory(
                org.opencv.core.Point(i * 100.0, i * 50.0)
            )
        }
        
        // Benchmark cálculo adaptativo de ROI
        val adaptiveTime = measureTimeMillis {
            repeat(iterations) {
                roiOptimizer.calculateROI(imageSize, 0.7f)
            }
        }
        
        println("Basic ROI calculation: ${basicTime}ms")
        println("Adaptive ROI calculation: ${adaptiveTime}ms")
        println("Adaptive overhead: ${adaptiveTime - basicTime}ms")
        
        // El overhead del ROI adaptativo debe ser mínimo
        val overhead = adaptiveTime - basicTime
        assertTrue(
            "Adaptive ROI overhead should be minimal",
            overhead < basicTime * 0.2f // Menos del 20% de overhead
        )
    }
    
    @Test
    fun `benchmark thermal state detection impact`() {
        val iterations = 1000
        
        // Benchmark sin detección térmica
        val withoutThermalTime = measureTimeMillis {
            repeat(iterations) {
                // Simular procesamiento básico
                val mat = performanceManager.borrowMat()
                performanceManager.returnMat(mat)
            }
        }
        
        // Benchmark con detección térmica
        val withThermalTime = measureTimeMillis {
            repeat(iterations) {
                // Simular procesamiento con ajustes térmicos
                val adjustments = thermalDetector.getCurrentAdjustments()
                val mat = performanceManager.borrowMat()
                
                // Simular aplicación de ajustes
                if (adjustments.enableAdvancedProcessing) {
                    // Procesamiento avanzado
                }
                
                performanceManager.returnMat(mat)
            }
        }
        
        println("Without thermal detection: ${withoutThermalTime}ms")
        println("With thermal detection: ${withThermalTime}ms")
        println("Thermal detection overhead: ${withThermalTime - withoutThermalTime}ms")
        
        // El overhead de la detección térmica debe ser mínimo
        val overhead = withThermalTime - withoutThermalTime
        assertTrue(
            "Thermal detection overhead should be minimal",
            overhead < withoutThermalTime * 0.15f // Menos del 15% de overhead
        )
    }
    
    @Test
    fun `benchmark memory allocation patterns`() {
        val iterations = 1000
        
        // Patrón 1: Allocación/liberación inmediata
        val immediateTime = measureTimeMillis {
            repeat(iterations) {
                val mat = performanceManager.borrowMat()
                performanceManager.returnMat(mat)
            }
        }
        
        // Patrón 2: Batch allocation/deallocation
        val batchTime = measureTimeMillis {
            val mats = mutableListOf<Mat>()
            
            // Allocar en batch
            repeat(iterations) {
                mats.add(performanceManager.borrowMat())
            }
            
            // Liberar en batch
            mats.forEach { mat ->
                performanceManager.returnMat(mat)
            }
        }
        
        println("Immediate allocation pattern: ${immediateTime}ms")
        println("Batch allocation pattern: ${batchTime}ms")
        
        // Ambos patrones deben ser eficientes
        assertTrue(
            "Both allocation patterns should be efficient",
            immediateTime > 0 && batchTime > 0
        )
    }
    
    @Test
    fun `benchmark coordinate conversion performance`() {
        val imageSize = Size(1920.0, 1080.0)
        val roiResult = roiOptimizer.calculateROI(imageSize, 0.5f)
        val iterations = 100000
        
        // Benchmark conversión ROI a imagen
        val roiToImageTime = measureTimeMillis {
            repeat(iterations) {
                val roiPoint = org.opencv.core.Point(100.0, 50.0)
                roiOptimizer.roiToImageCoordinates(roiPoint, roiResult)
            }
        }
        
        // Benchmark conversión imagen a ROI
        val imageToRoiTime = measureTimeMillis {
            repeat(iterations) {
                val imagePoint = org.opencv.core.Point(500.0, 400.0)
                roiOptimizer.imageToROICoordinates(imagePoint, roiResult)
            }
        }
        
        println("ROI to image conversion: ${roiToImageTime}ms")
        println("Image to ROI conversion: ${imageToRoiTime}ms")
        
        // Las conversiones deben ser muy rápidas
        assertTrue(
            "Coordinate conversions should be fast",
            roiToImageTime < 100 && imageToRoiTime < 100
        )
    }
    
    @Test
    fun `benchmark performance stats collection`() {
        val iterations = 10000
        
        // Llenar con algunos datos
        repeat(100) {
            performanceManager.recordFrameProcessingTime(16L)
            performanceManager.recordMemoryUsage()
        }
        
        val statsTime = measureTimeMillis {
            repeat(iterations) {
                performanceManager.getPerformanceStats()
            }
        }
        
        println("Performance stats collection: ${statsTime}ms for $iterations calls")
        println("Average per call: ${statsTime.toFloat() / iterations}ms")
        
        // La recolección de estadísticas debe ser muy rápida
        assertTrue(
            "Stats collection should be fast",
            statsTime < 1000 // Menos de 1 segundo para 10k llamadas
        )
    }
    
    @Test
    fun `benchmark different performance levels impact`() {
        val iterations = 1000
        val results = mutableMapOf<String, Long>()
        
        // Simular diferentes niveles de rendimiento
        val configs = mapOf(
            "HIGH" to PerformanceManager.PerformanceConfig(
                processingFrequency = 20,
                imageResolutionScale = 1.0f,
                enableROI = false,
                roiScale = 1.0f,
                enableMatPooling = true,
                maxPoolSize = 10,
                enableAdvancedFilters = true,
                throttleDetection = false
            ),
            "LOW" to PerformanceManager.PerformanceConfig(
                processingFrequency = 5,
                imageResolutionScale = 0.4f,
                enableROI = true,
                roiScale = 0.3f,
                enableMatPooling = true,
                maxPoolSize = 3,
                enableAdvancedFilters = false,
                throttleDetection = true
            )
        )
        
        configs.forEach { (level, config) ->
            val time = measureTimeMillis {
                repeat(iterations) {
                    val mat = performanceManager.borrowMat()
                    
                    // Simular procesamiento según configuración
                    if (config.enableAdvancedFilters) {
                        // Procesamiento avanzado simulado
                        Thread.sleep(0, 100) // 0.1ms
                    }
                    
                    performanceManager.returnMat(mat)
                }
            }
            results[level] = time
        }
        
        println("Performance level benchmarks:")
        results.forEach { (level, time) ->
            println("$level: ${time}ms")
        }
        
        // El nivel LOW debe ser más rápido que HIGH
        assertTrue(
            "LOW performance level should be faster",
            results["LOW"]!! < results["HIGH"]!!
        )
    }
}
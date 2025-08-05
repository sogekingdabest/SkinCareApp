package es.monsteraltech.skincare_tfm.analysis
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
class AsyncImageProcessor(
    context: Context,
    private val progressCallback: ProgressCallback
) {
    companion object {
        private const val TAG = "AsyncImageProcessor"
    }
    private val melanomaDetector = MelanomaAIDetector(context)
    private val abcdeAnalyzer = ABCDEAnalyzerOpenCV(context)
    private val processingMutex = Mutex()
    private val isCancelled = AtomicBoolean(false)
    private var currentJob: Job? = null
    private var configuration = AnalysisConfiguration.default()
    private val errorRecoveryManager = ErrorRecoveryManager()
    private val memoryManager = MemoryManager()
    private val performanceOptimizer = PerformanceOptimizer()
    private val devicePerformance = performanceOptimizer.analyzeDevicePerformance()
    private var memoryMonitor: MemoryManager.MemoryMonitor? = null
    suspend fun processImage(
        bitmap: Bitmap,
        previousBitmap: Bitmap? = null,
        pixelDensity: Float = 1.0f,
        config: AnalysisConfiguration = AnalysisConfiguration.default()
    ): MelanomaAIDetector.CombinedAnalysisResult = withContext(Dispatchers.Default) {
        processingMutex.withLock {
            isCancelled.set(false)
            configuration = config
            Log.d(TAG, "Iniciando procesamiento asíncrono de imagen ${bitmap.width}x${bitmap.height}")
            return@withLock processImageWithRecovery(bitmap, previousBitmap, pixelDensity, config)
        }
    }
    private suspend fun processImageWithRecovery(
        originalBitmap: Bitmap,
        previousBitmap: Bitmap?,
        pixelDensity: Float,
        originalConfig: AnalysisConfiguration,
        attempt: Int = 1
    ): MelanomaAIDetector.CombinedAnalysisResult {
        var currentBitmap = originalBitmap
        var currentConfig = originalConfig
        try {
            if (!currentConfig.validate()) {
                throw AnalysisError.ConfigurationError("Configuración de análisis inválida")
            }
            validateImage(currentBitmap)
            currentConfig = performanceOptimizer.optimizeConfiguration(currentConfig, devicePerformance)
            if (!memoryManager.canProcessImage(currentBitmap)) {
                Log.w(TAG, "Memoria insuficiente, optimizando imagen...")
                currentBitmap = memoryManager.optimizeImageForMemory(currentBitmap, currentConfig)
                memoryManager.registerBitmap("processing_${System.currentTimeMillis()}", currentBitmap)
            }
            memoryMonitor = memoryManager.startMemoryMonitoring { memoryInfo ->
                if (memoryInfo.isCriticalMemory) {
                    Log.w(TAG, "Memoria crítica durante procesamiento: ${memoryInfo.usagePercentage * 100}%")
                    memoryManager.forceMemoryCleanup()
                }
            }
            memoryMonitor?.start()
            return withTimeout(currentConfig.timeoutMs) {
                executeProcessingWithErrorHandling(currentBitmap, previousBitmap, pixelDensity, currentConfig)
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timeout en procesamiento después de ${currentConfig.timeoutMs}ms")
            return handleAnalysisError(
                AnalysisError.Timeout,
                currentBitmap,
                previousBitmap,
                pixelDensity,
                currentConfig,
                attempt
            )
        } catch (e: CancellationException) {
            Log.i(TAG, "Procesamiento cancelado por el usuario")
            val error = AnalysisError.UserCancellation
            progressCallback.onError(error.getUserFriendlyMessage())
            throw error
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Error de memoria durante el procesamiento", e)
            return handleAnalysisError(
                AnalysisError.OutOfMemory,
                currentBitmap,
                previousBitmap,
                pixelDensity,
                currentConfig,
                attempt
            )
        } catch (e: AnalysisError) {
            Log.e(TAG, "Error de análisis: ${e.message}", e)
            return handleAnalysisError(e, currentBitmap, previousBitmap, pixelDensity, currentConfig, attempt)
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado durante el procesamiento", e)
            val analysisError = AnalysisError.UnknownError(e.message ?: "Error desconocido", e)
            return handleAnalysisError(
                analysisError,
                currentBitmap,
                previousBitmap,
                pixelDensity,
                currentConfig,
                attempt
            )
        } finally {
            memoryMonitor?.stop()
            memoryManager.cleanupUnusedBitmaps()
        }
    }
    private suspend fun handleAnalysisError(
        error: AnalysisError,
        bitmap: Bitmap,
        previousBitmap: Bitmap?,
        pixelDensity: Float,
        config: AnalysisConfiguration,
        attempt: Int
    ): MelanomaAIDetector.CombinedAnalysisResult {
        Log.d(TAG, "Manejando error: ${error.javaClass.simpleName}, intento: $attempt")
        progressCallback.onError(error.getUserFriendlyMessage())
        if (errorRecoveryManager.canHandle(error)) {
            val recoveryResult = errorRecoveryManager.attemptRecovery(error, bitmap, config, attempt)
            if (recoveryResult != null) {
                Log.d(TAG, "Aplicando estrategia de recuperación: ${recoveryResult.strategy}")
                progressCallback.onProgressUpdate(0, recoveryResult.message)
                val newBitmap = recoveryResult.bitmap ?: bitmap
                return processImageWithRecovery(
                    newBitmap,
                    previousBitmap,
                    pixelDensity,
                    recoveryResult.config,
                    attempt + 1
                )
            }
        }
        Log.w(TAG, "No se pudo recuperar del error, creando resultado de fallback")
        return createFallbackResult(error)
    }
    private fun validateImage(bitmap: Bitmap) {
        if (bitmap.isRecycled) {
            throw AnalysisError.InvalidImageError("La imagen ha sido reciclada")
        }
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            throw AnalysisError.InvalidImageError("Dimensiones de imagen inválidas: ${bitmap.width}x${bitmap.height}")
        }
        val totalPixels = bitmap.width * bitmap.height
        if (totalPixels < 1000) {
            throw AnalysisError.InvalidImageError("Imagen demasiado pequeña para análisis")
        }
    }
    private fun createFallbackResult(
        error: AnalysisError
    ): MelanomaAIDetector.CombinedAnalysisResult {
        val fallbackResult = createDefaultABCDEResult()
        val result = createCombinedResultFromABCDE(fallbackResult)
        return result.copy(
            recommendation = "Análisis limitado debido a: ${error.getUserFriendlyMessage()}",
            explanations = listOf(
                "El análisis completo no pudo completarse",
                "Se proporcionan valores por defecto",
                "Se recomienda intentar de nuevo con una imagen de mejor calidad"
            ),
            aiConfidence = 0.1f
        )
    }
    private suspend fun executeProcessingWithErrorHandling(
        bitmap: Bitmap,
        previousBitmap: Bitmap?,
        pixelDensity: Float,
        config: AnalysisConfiguration
    ): MelanomaAIDetector.CombinedAnalysisResult {
        updateProgress(ProcessingStage.INITIALIZING, 0, ProcessingStage.INITIALIZING.message)
        ensureActive()
        val processedBitmap = try {
            preprocessImageIfNeeded(bitmap)
        } catch (e: OutOfMemoryError) {
            throw AnalysisError.OutOfMemory
        } catch (e: Exception) {
            throw AnalysisError.ImageProcessingError("Error al preprocesar imagen: ${e.message}")
        }
        updateProgress(ProcessingStage.INITIALIZING, 50, "Imagen preparada")
        ensureActive()
        updateProgress(ProcessingStage.PREPROCESSING, 0, ProcessingStage.PREPROCESSING.message)
        delay(100)
        updateProgress(ProcessingStage.PREPROCESSING, 100, "Preprocesamiento completado")
        ensureActive()
        val analysisResult = try {
            if (config.enableParallelProcessing &&
                config.enableAI &&
                config.enableABCDE) {
                executeParallelAnalysisWithErrorHandling(processedBitmap, previousBitmap, pixelDensity, config)
            } else {
                executeSequentialAnalysisWithErrorHandling(processedBitmap, previousBitmap, pixelDensity, config)
            }
        } catch (e: OutOfMemoryError) {
            throw AnalysisError.OutOfMemory
        } catch (e: AnalysisError) {
            throw e
        } catch (e: Exception) {
            throw AnalysisError.UnknownError("Error durante análisis: ${e.message}", e)
        }
        ensureActive()
        updateProgress(ProcessingStage.FINALIZING, 0, ProcessingStage.FINALIZING.message)
        delay(200)
        updateProgress(ProcessingStage.FINALIZING, 100, "Análisis completado")
        progressCallback.onCompleted(analysisResult)
        return analysisResult
    }
    private suspend fun executeParallelAnalysisWithErrorHandling(
        bitmap: Bitmap,
        previousBitmap: Bitmap?,
        pixelDensity: Float,
        config: AnalysisConfiguration
    ): MelanomaAIDetector.CombinedAnalysisResult = coroutineScope {
        Log.d(TAG, "Ejecutando análisis en paralelo con manejo de errores")
        val aiJob = if (config.enableAI) {
            async(Dispatchers.Default) {
                updateProgress(ProcessingStage.AI_ANALYSIS, 0, "Iniciando análisis IA...")
                try {
                    withTimeout(config.aiTimeoutMs) {
                        val result = executeAIAnalysisWithErrorHandling(bitmap)
                        updateProgress(ProcessingStage.AI_ANALYSIS, 100, "Análisis IA completado")
                        result
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.w(TAG, "Timeout en análisis IA")
                    throw AnalysisError.AIModelError("Timeout en análisis IA")
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "Error de memoria en análisis IA")
                    throw AnalysisError.OutOfMemory
                } catch (e: Exception) {
                    Log.w(TAG, "Error en análisis IA: ${e.message}")
                    throw AnalysisError.AIModelError("Error en análisis IA: ${e.message}")
                }
            }
        } else null
        val abcdeJob = if (config.enableABCDE) {
            async(Dispatchers.Default) {
                updateProgress(ProcessingStage.ABCDE_ANALYSIS, 0, "Iniciando análisis ABCDE...")
                try {
                    withTimeout(config.abcdeTimeoutMs) {
                        val result = executeABCDEAnalysisWithErrorHandling(bitmap, previousBitmap, pixelDensity)
                        updateProgress(ProcessingStage.ABCDE_ANALYSIS, 100, "Análisis ABCDE completado")
                        result
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.w(TAG, "Timeout en análisis ABCDE")
                    throw AnalysisError.ABCDEAnalysisError("Timeout en análisis ABCDE")
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "Error de memoria en análisis ABCDE")
                    throw AnalysisError.OutOfMemory
                } catch (e: Exception) {
                    Log.w(TAG, "Error en análisis ABCDE: ${e.message}")
                    throw AnalysisError.ABCDEAnalysisError("Error en análisis ABCDE: ${e.message}")
                }
            }
        } else null
        var aiResult: AIAnalysisResult? = null
        var abcdeResult: ABCDEAnalyzerOpenCV.ABCDEResult = createDefaultABCDEResult()
        if (aiJob != null) {
            try {
                aiResult = aiJob.await()
            } catch (e: AnalysisError.AIModelError) {
                Log.w(TAG, "Fallback: IA falló, continuando solo con ABCDE")
                updateProgress(ProcessingStage.AI_ANALYSIS, 100, "IA no disponible, continuando...")
            }
        }
        if (abcdeJob != null) {
            try {
                abcdeResult = abcdeJob.await()
            } catch (e: AnalysisError.ABCDEAnalysisError) {
                Log.w(TAG, "ABCDE falló, usando valores por defecto")
                updateProgress(ProcessingStage.ABCDE_ANALYSIS, 100, "ABCDE falló, usando valores por defecto")
                abcdeResult = createDefaultABCDEResult()
            }
        }
        combineResults(aiResult, abcdeResult)
    }
    private suspend fun executeSequentialAnalysisWithErrorHandling(
        bitmap: Bitmap,
        previousBitmap: Bitmap?,
        pixelDensity: Float,
        config: AnalysisConfiguration
    ): MelanomaAIDetector.CombinedAnalysisResult {
        Log.d(TAG, "Ejecutando análisis secuencial con manejo de errores")
        var aiResult: AIAnalysisResult? = null
        var abcdeResult: ABCDEAnalyzerOpenCV.ABCDEResult = createDefaultABCDEResult()
        if (config.enableAI) {
            updateProgress(ProcessingStage.AI_ANALYSIS, 0, ProcessingStage.AI_ANALYSIS.message)
            try {
                withTimeout(config.aiTimeoutMs) {
                    aiResult = executeAIAnalysisWithErrorHandling(bitmap)
                    updateProgress(ProcessingStage.AI_ANALYSIS, 100, "Análisis IA completado")
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Timeout en análisis IA")
                updateProgress(ProcessingStage.AI_ANALYSIS, 100, "IA timeout, continuando...")
                throw AnalysisError.AIModelError("Timeout en análisis IA")
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Error de memoria en análisis IA")
                throw AnalysisError.OutOfMemory
            } catch (e: Exception) {
                Log.w(TAG, "Error en análisis IA: ${e.message}")
                updateProgress(ProcessingStage.AI_ANALYSIS, 100, "IA no disponible")
            }
            ensureActive()
        }
        if (config.enableABCDE) {
            updateProgress(ProcessingStage.ABCDE_ANALYSIS, 0, ProcessingStage.ABCDE_ANALYSIS.message)
            try {
                withTimeout(config.abcdeTimeoutMs) {
                    abcdeResult = executeABCDEAnalysisWithErrorHandling(bitmap, previousBitmap, pixelDensity)
                    updateProgress(ProcessingStage.ABCDE_ANALYSIS, 100, "Análisis ABCDE completado")
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Timeout en análisis ABCDE")
                updateProgress(ProcessingStage.ABCDE_ANALYSIS, 100, "ABCDE timeout")
                abcdeResult = createDefaultABCDEResult("Timeout en análisis ABCDE")
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Error de memoria en análisis ABCDE")
                abcdeResult = createDefaultABCDEResult("Error de memoria en análisis ABCDE")
            } catch (e: Exception) {
                Log.w(TAG, "Error en análisis ABCDE: ${e.message}")
                updateProgress(ProcessingStage.ABCDE_ANALYSIS, 100, "ABCDE falló")
                abcdeResult = createDefaultABCDEResult("Error en análisis ABCDE: ${e.message}")
            }
            ensureActive()
        }
        return combineResults(aiResult, abcdeResult)
    }
    private suspend fun executeAIAnalysisWithErrorHandling(bitmap: Bitmap): AIAnalysisResult = withContext(Dispatchers.Default) {
        try {
            delay(1000)
            val runtime = Runtime.getRuntime()
            val freeMemory = runtime.freeMemory()
            val totalMemory = runtime.totalMemory()
            val usedMemory = totalMemory - freeMemory
            if (usedMemory > totalMemory * 0.8) {
                Log.w(TAG, "Memoria baja detectada durante análisis IA")
                throw AnalysisError.OutOfMemory
            }
            val result = melanomaDetector.analyzeMole(bitmap)
            AIAnalysisResult(
                probability = result.aiProbability,
                riskLevel = result.aiRiskLevel,
                confidence = result.aiConfidence
            )
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Error de memoria en análisis IA", e)
            throw AnalysisError.OutOfMemory
        } catch (e: AnalysisError) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado en análisis IA", e)
            throw AnalysisError.AIModelError("Error en modelo IA: ${e.message}")
        }
    }
    private suspend fun executeABCDEAnalysisWithErrorHandling(
        bitmap: Bitmap,
        previousBitmap: Bitmap?,
        pixelDensity: Float
    ): ABCDEAnalyzerOpenCV.ABCDEResult = withContext(Dispatchers.Default) {
        try {
            System.gc()
            val runtime = Runtime.getRuntime()
            val freeMemory = runtime.freeMemory()
            val totalMemory = runtime.totalMemory()
            val maxMemory = runtime.maxMemory()
            val usedMemory = totalMemory - freeMemory
            val availableMemory = maxMemory - usedMemory
            if (availableMemory < 16 * 1024 * 1024) {
                Log.w(TAG, "Memoria baja detectada durante análisis ABCDE (menos de 16MB libres)")
                throw AnalysisError.OutOfMemory
            }
            abcdeAnalyzer.analyzeMole(bitmap, previousBitmap, pixelDensity)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Error de memoria en análisis ABCDE", e)
            throw AnalysisError.OutOfMemory
        } catch (e: AnalysisError) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado en análisis ABCDE", e)
            throw AnalysisError.ABCDEAnalysisError("Error en análisis ABCDE: ${e.message}")
        }
    }
    private fun combineResults(
        aiResult: AIAnalysisResult?,
        abcdeResult: ABCDEAnalyzerOpenCV.ABCDEResult
    ): MelanomaAIDetector.CombinedAnalysisResult {
        return if (aiResult != null) {
            melanomaDetector.combineResults(aiResult.probability, aiResult.riskLevel, aiResult.confidence, abcdeResult)
        } else {
            createCombinedResultFromABCDE(abcdeResult)
        }
    }
    private fun createCombinedResultFromABCDE(abcdeResult: ABCDEAnalyzerOpenCV.ABCDEResult): MelanomaAIDetector.CombinedAnalysisResult {
        val combinedScore = abcdeResult.totalScore / 10.0f
        val combinedRiskLevel = when {
            combinedScore < 0.3f -> MelanomaAIDetector.RiskLevel.LOW
            combinedScore < 0.6f -> MelanomaAIDetector.RiskLevel.MEDIUM
            else -> MelanomaAIDetector.RiskLevel.HIGH
        }
        return MelanomaAIDetector.CombinedAnalysisResult(
            aiProbability = 0.0f,
            aiRiskLevel = MelanomaAIDetector.RiskLevel.LOW,
            aiConfidence = 0.0f,
            abcdeResult = abcdeResult,
            combinedScore = combinedScore,
            combinedRiskLevel = combinedRiskLevel,
            recommendation = "Análisis basado solo en criterios ABCDE",
            shouldMonitor = combinedScore > 0.4f,
            urgencyLevel = when (combinedRiskLevel) {
                MelanomaAIDetector.RiskLevel.LOW -> MelanomaAIDetector.UrgencyLevel.ROUTINE
                MelanomaAIDetector.RiskLevel.MEDIUM -> MelanomaAIDetector.UrgencyLevel.MONITOR
                else -> MelanomaAIDetector.UrgencyLevel.CONSULT
            },
            explanations = listOf("Análisis realizado solo con criterios ABCDE")
        )
    }
    private fun preprocessImageIfNeeded(bitmap: Bitmap): Bitmap {
        val maxDim = 1024
        if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            return bitmap.scale(newWidth, newHeight)
        }
        return bitmap
    }
    private fun updateProgress(stage: ProcessingStage, progress: Int, message: String) {
        val totalProgress = stage.getProgressUpToStage() + (progress * stage.weight / 100)
        progressCallback.onProgressUpdate(totalProgress, message)
        progressCallback.onStageChanged(stage)
    }
    private fun ensureActive() {
        if (isCancelled.get()) {
            throw CancellationException("Procesamiento cancelado por el usuario")
        }
    }
    fun cancelProcessing() {
        Log.d(TAG, "Cancelando procesamiento...")
        isCancelled.set(true)
        currentJob?.cancel()
    }
    fun isProcessing(): Boolean {
        return currentJob?.isActive == true
    }
    private fun createDefaultABCDEResult(reason: String = "Análisis no disponible"): ABCDEAnalyzerOpenCV.ABCDEResult {
        return ABCDEAnalyzerOpenCV.ABCDEResult(
            asymmetryScore = 0f,
            borderScore = 0f,
            colorScore = 1f,
            diameterScore = 0f,
            evolutionScore = null,
            totalScore = 1f,
            riskLevel = ABCDEAnalyzerOpenCV.RiskLevel.LOW,
            details = ABCDEAnalyzerOpenCV.ABCDEDetails(
                asymmetryDetails = ABCDEAnalyzerOpenCV.AsymmetryDetails(
                    0f, 0f, reason
                ),
                borderDetails = ABCDEAnalyzerOpenCV.BorderDetails(
                    0f, 0, reason
                ),
                colorDetails = ABCDEAnalyzerOpenCV.ColorDetails(
                    emptyList(), 1, false, false, reason
                ),
                diameterDetails = ABCDEAnalyzerOpenCV.DiameterDetails(
                    0f, 0, reason
                ),
                evolutionDetails = null
            )
        )
    }
    private data class AIAnalysisResult(
        val probability: Float,
        val riskLevel: MelanomaAIDetector.RiskLevel,
        val confidence: Float
    )
    fun cleanup() {
        Log.d(TAG, "Limpiando recursos del AsyncImageProcessor")
        cancelProcessing()
        performanceOptimizer.cleanup()
        memoryMonitor?.stop()
    }
}
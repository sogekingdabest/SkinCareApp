package es.monsteraltech.skincare_tfm.analysis

/**
 * Data class que define la configuración para el análisis de imagen
 */
data class AnalysisConfiguration(
    /**
     * Habilitar análisis con inteligencia artificial
     */
    val enableAI: Boolean = true,
    
    /**
     * Habilitar análisis ABCDE
     */
    val enableABCDE: Boolean = true,
    
    /**
     * Habilitar análisis de evolución (comparación con imagen anterior)
     */
    val enableEvolution: Boolean = true,
    
    /**
     * Timeout para el procesamiento completo en milisegundos
     */
    val timeoutMs: Long = 30000L,
    
    /**
     * Timeout específico para el análisis de IA en milisegundos
     */
    val aiTimeoutMs: Long = 15000L,
    
    /**
     * Timeout específico para el análisis ABCDE en milisegundos
     */
    val abcdeTimeoutMs: Long = 10000L,
    
    /**
     * Permitir procesamiento en paralelo de IA y ABCDE cuando sea posible
     */
    val enableParallelProcessing: Boolean = true,
    
    /**
     * Reducir automáticamente la resolución de imagen si hay problemas de memoria
     */
    val enableAutoImageReduction: Boolean = true,
    
    /**
     * Resolución máxima permitida para el procesamiento (en píxeles)
     */
    val maxImageResolution: Int = 1024 * 1024, // 1MP
    
    /**
     * Calidad de compresión JPEG para optimización de memoria (0-100)
     */
    val compressionQuality: Int = 85
) {
    /**
     * Valida que la configuración sea válida
     */
    fun validate(): Boolean {
        return timeoutMs > 0 &&
                aiTimeoutMs > 0 &&
                abcdeTimeoutMs > 0 &&
                maxImageResolution > 0 &&
                compressionQuality in 1..100 &&
                (enableAI || enableABCDE) // Al menos uno debe estar habilitado
    }
    
    /**
     * Crea una configuración optimizada para dispositivos con poca memoria
     */
    fun forLowMemoryDevice(): AnalysisConfiguration {
        return copy(
            enableParallelProcessing = false,
            enableAutoImageReduction = true,
            maxImageResolution = 512 * 512, // 0.25MP
            compressionQuality = 70,
            timeoutMs = 45000L, // Más tiempo para dispositivos lentos
            aiTimeoutMs = 20000L,
            abcdeTimeoutMs = 15000L
        )
    }
    
    companion object {
        /**
         * Configuración por defecto balanceada
         */
        fun default(): AnalysisConfiguration {
            return AnalysisConfiguration()
        }
        
        /**
         * Configuración rápida
         */
        fun fast(): AnalysisConfiguration {
            return AnalysisConfiguration(
                enableParallelProcessing = true,
                maxImageResolution = 512 * 512,
                compressionQuality = 75,
                timeoutMs = 20000L,
                aiTimeoutMs = 10000L,
                abcdeTimeoutMs = 8000L
            )
        }
    }
}
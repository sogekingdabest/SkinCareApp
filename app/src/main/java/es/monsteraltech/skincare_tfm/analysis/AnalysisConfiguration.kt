package es.monsteraltech.skincare_tfm.analysis
data class AnalysisConfiguration(
    val enableAI: Boolean = true,
    val enableABCDE: Boolean = true,
    val enableEvolution: Boolean = true,
    val timeoutMs: Long = 30000L,
    val aiTimeoutMs: Long = 15000L,
    val abcdeTimeoutMs: Long = 10000L,
    val enableParallelProcessing: Boolean = true,
    val enableAutoImageReduction: Boolean = true,
    val maxImageResolution: Int = 1024 * 1024,
    val compressionQuality: Int = 85
) {
    fun validate(): Boolean {
        return timeoutMs > 0 &&
                aiTimeoutMs > 0 &&
                abcdeTimeoutMs > 0 &&
                maxImageResolution > 0 &&
                compressionQuality in 1..100 &&
                (enableAI || enableABCDE)
    }
    fun forLowMemoryDevice(): AnalysisConfiguration {
        return copy(
            enableParallelProcessing = false,
            enableAutoImageReduction = true,
            maxImageResolution = 512 * 512,
            compressionQuality = 70,
            timeoutMs = 45000L,
            aiTimeoutMs = 20000L,
            abcdeTimeoutMs = 15000L
        )
    }
    companion object {
        fun default(): AnalysisConfiguration {
            return AnalysisConfiguration()
        }
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
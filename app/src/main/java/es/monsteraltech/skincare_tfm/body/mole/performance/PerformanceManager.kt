package es.monsteraltech.skincare_tfm.body.mole.performance
import android.content.Context
import android.util.Log
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData

class PerformanceManager private constructor(private val context: Context) {
    private val lazyImageLoader = LazyImageLoader(context)
    private val localCacheManager = LocalCacheManager.getInstance(context)
    private val optimizedQueries = OptimizedFirebaseQueries()
    companion object {
        @Volatile
        private var INSTANCE: PerformanceManager? = null
        fun getInstance(context: Context): PerformanceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PerformanceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    data class PerformanceConfig(
        val enableImageLazyLoading: Boolean = true,
        val enablePagination: Boolean = true,
        val enableLocalCache: Boolean = true,
        val enableOptimizedQueries: Boolean = true,
        val preloadDistance: Int = 3,
        val pageSize: Int = 20,
        val cacheExpirationMs: Long = 5 * 60 * 1000L
    )
    suspend fun getCachedAnalysis(
        analysisId: String,
        config: PerformanceConfig = PerformanceConfig()
    ): AnalysisData? {
        if (!config.enableLocalCache) return null
        return localCacheManager.getCachedAnalysis(
            analysisId,
            LocalCacheManager.CacheConfig(cacheExpirationMs = config.cacheExpirationMs)
        )
    }
    suspend fun cacheAnalysis(
        analysisId: String,
        analysis: AnalysisData,
        config: PerformanceConfig = PerformanceConfig()
    ) {
        if (!config.enableLocalCache) return
        localCacheManager.cacheAnalysis(
            analysisId,
            analysis,
            LocalCacheManager.CacheConfig(cacheExpirationMs = config.cacheExpirationMs)
        )
    }
    suspend fun getCachedMole(
        moleId: String,
        config: PerformanceConfig = PerformanceConfig()
    ): MoleData? {
        if (!config.enableLocalCache) return null
        return localCacheManager.getCachedMole(
            moleId,
            LocalCacheManager.CacheConfig(cacheExpirationMs = config.cacheExpirationMs)
        )
    }
    suspend fun cacheMole(
        moleId: String,
        mole: MoleData,
        config: PerformanceConfig = PerformanceConfig()
    ) {
        if (!config.enableLocalCache) return
        localCacheManager.cacheMole(
            moleId,
            mole,
            LocalCacheManager.CacheConfig(cacheExpirationMs = config.cacheExpirationMs)
        )
    }
    suspend fun clearCaches() {
        try {
            lazyImageLoader.clearPreloadCache()
            lazyImageLoader.cancelAllLoads()
            localCacheManager.clearAllCache()
            Log.d("PerformanceManager", "Cachés limpiados exitosamente")
        } catch (e: Exception) {
            Log.w("PerformanceManager", "Error al limpiar cachés", e)
        }
    }
    fun getPerformanceStats(): PerformanceStats {
        val cacheStats = localCacheManager.getCacheStats()
        return PerformanceStats(
            memoryCacheSize = cacheStats.analysisMemoryCacheSize + cacheStats.moleMemoryCacheSize,
            persistentCacheSize = cacheStats.persistentCacheSize,
            analysisListCacheSize = cacheStats.analysisListCacheSize
        )
    }
    object Configs {
        fun highPerformance() = PerformanceConfig(
            enableImageLazyLoading = true,
            enablePagination = true,
            enableLocalCache = true,
            enableOptimizedQueries = true,
            preloadDistance = 5,
            pageSize = 15,
            cacheExpirationMs = 10 * 60 * 1000L
        )
        fun balanced() = PerformanceConfig(
            enableImageLazyLoading = true,
            enablePagination = true,
            enableLocalCache = true,
            enableOptimizedQueries = true,
            preloadDistance = 3,
            pageSize = 20,
            cacheExpirationMs = 5 * 60 * 1000L
        )
        fun lowMemory() = PerformanceConfig(
            enableImageLazyLoading = true,
            enablePagination = true,
            enableLocalCache = false,
            enableOptimizedQueries = true,
            preloadDistance = 1,
            pageSize = 10,
            cacheExpirationMs = 2 * 60 * 1000L
        )
        fun offline() = PerformanceConfig(
            enableImageLazyLoading = false,
            enablePagination = false,
            enableLocalCache = true,
            enableOptimizedQueries = false,
            preloadDistance = 0,
            pageSize = 50,
            cacheExpirationMs = 60 * 60 * 1000L
        )
    }
    data class PerformanceStats(
        val memoryCacheSize: Int,
        val persistentCacheSize: Int,
        val analysisListCacheSize: Int
    )
}
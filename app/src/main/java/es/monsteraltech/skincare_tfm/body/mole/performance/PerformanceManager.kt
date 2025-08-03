package es.monsteraltech.skincare_tfm.body.mole.performance

import android.content.Context
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Gestor central de rendimiento que coordina todas las optimizaciones
 * Proporciona una interfaz unificada para lazy loading, paginación y caché
 */
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

    /**
     * Configuración global de rendimiento
     */
    data class PerformanceConfig(
        val enableImageLazyLoading: Boolean = true,
        val enablePagination: Boolean = true,
        val enableLocalCache: Boolean = true,
        val enableOptimizedQueries: Boolean = true,
        val preloadDistance: Int = 3,
        val pageSize: Int = 20,
        val cacheExpirationMs: Long = 5 * 60 * 1000L // 5 minutos
    )

    // ==================== CACHÉ LOCAL ====================

    /**
     * Obtiene análisis con caché automático
     */
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

    /**
     * Guarda análisis en caché
     */
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

    /**
     * Obtiene lunar con caché automático
     */
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

    /**
     * Guarda lunar en caché
     */
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

    // ==================== GESTIÓN DE MEMORIA ====================

    /**
     * Limpia cachés y libera memoria
     */
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

    /**
     * Obtiene estadísticas de rendimiento
     */
    fun getPerformanceStats(): PerformanceStats {
        val cacheStats = localCacheManager.getCacheStats()
        
        return PerformanceStats(
            memoryCacheSize = cacheStats.analysisMemoryCacheSize + cacheStats.moleMemoryCacheSize,
            persistentCacheSize = cacheStats.persistentCacheSize,
            analysisListCacheSize = cacheStats.analysisListCacheSize
        )
    }

    /**
     * Configuraciones predefinidas para diferentes escenarios
     */
    object Configs {
        fun highPerformance() = PerformanceConfig(
            enableImageLazyLoading = true,
            enablePagination = true,
            enableLocalCache = true,
            enableOptimizedQueries = true,
            preloadDistance = 5,
            pageSize = 15,
            cacheExpirationMs = 10 * 60 * 1000L // 10 minutos
        )

        fun balanced() = PerformanceConfig(
            enableImageLazyLoading = true,
            enablePagination = true,
            enableLocalCache = true,
            enableOptimizedQueries = true,
            preloadDistance = 3,
            pageSize = 20,
            cacheExpirationMs = 5 * 60 * 1000L // 5 minutos
        )
        
        fun lowMemory() = PerformanceConfig(
            enableImageLazyLoading = true,
            enablePagination = true,
            enableLocalCache = false, // Deshabilitado para ahorrar memoria
            enableOptimizedQueries = true,
            preloadDistance = 1,
            pageSize = 10,
            cacheExpirationMs = 2 * 60 * 1000L // 2 minutos
        )
        
        fun offline() = PerformanceConfig(
            enableImageLazyLoading = false,
            enablePagination = false,
            enableLocalCache = true,
            enableOptimizedQueries = false,
            preloadDistance = 0,
            pageSize = 50,
            cacheExpirationMs = 60 * 60 * 1000L // 1 hora
        )
    }

    /**
     * Estadísticas de rendimiento
     */
    data class PerformanceStats(
        val memoryCacheSize: Int,
        val persistentCacheSize: Int,
        val analysisListCacheSize: Int
    )
}
package es.monsteraltech.skincare_tfm.body.mole.performance

import android.content.Context
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import es.monsteraltech.skincare_tfm.body.mole.performance.AnalysisPaginationManager
import es.monsteraltech.skincare_tfm.body.mole.performance.MolePaginationManager
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

    // ==================== IMAGEN LAZY LOADING ====================

    /**
     * Configura lazy loading para un RecyclerView de lunares
     */
    fun setupMoleImageLazyLoading(
        recyclerView: RecyclerView,
        config: PerformanceConfig = PerformanceConfig()
    ) {
        if (!config.enableImageLazyLoading) return
        
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        // Precargar imágenes cuando el scroll se detiene
                        preloadVisibleImages(recyclerView, config)
                    }
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        // Pausar cargas no críticas durante scroll rápido
                        lazyImageLoader.cancelAllLoads()
                    }
                }
            }
        })
    }

    /**
     * Carga una imagen con optimizaciones automáticas
     */
    fun loadOptimizedImage(
        imageView: android.widget.ImageView,
        imageUrl: String?,
        position: Int,
        recyclerView: RecyclerView,
        imageType: ImageType = ImageType.MOLE_THUMBNAIL
    ) {
        val config = when (imageType) {
            ImageType.MOLE_THUMBNAIL -> LazyImageLoader.Configs.forMoleList()
            ImageType.ANALYSIS_IMAGE -> LazyImageLoader.Configs.forAnalysisHistory()
            ImageType.FULL_SIZE -> LazyImageLoader.Configs.forMoleList().copy(thumbnailFirst = false)
        }
        
        lazyImageLoader.loadImageLazy(imageView, imageUrl, position, recyclerView, config)
    }

    private fun preloadVisibleImages(recyclerView: RecyclerView, config: PerformanceConfig) {
        val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            ?: return
        
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        
        if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
            val startPreload = maxOf(0, firstVisible - config.preloadDistance)
            val endPreload = minOf(recyclerView.adapter?.itemCount ?: 0, lastVisible + config.preloadDistance)
            
            // Aquí se podría implementar lógica específica de precarga
            // basada en el tipo de adapter y datos
        }
    }

    // ==================== PAGINACIÓN ====================

    /**
     * Configura paginación para análisis de un lunar
     */
    fun setupAnalysisPagination(
        recyclerView: RecyclerView,
        moleId: String,
        onDataLoaded: (List<AnalysisData>, Boolean) -> Unit,
        onError: (Exception) -> Unit,
        config: PerformanceConfig = PerformanceConfig()
    ): AnalysisPaginationManager? {
        if (!config.enablePagination) return null
        
        val paginationManager = AnalysisPaginationManager(
            pageSize = config.pageSize,
            prefetchDistance = config.preloadDistance
        )
        
        paginationManager.setCallbacks(
            onLoadComplete = onDataLoaded,
            onLoadError = onError
        )
        
        paginationManager.setupScrollListener(recyclerView) {
            CoroutineScope(Dispatchers.Main).launch {
                paginationManager.loadAnalysisPage(moleId, isFirstPage = false)
            }
        }
        
        return paginationManager
    }

    /**
     * Configura paginación para lunares
     */
    fun setupMolePagination(
        recyclerView: RecyclerView,
        onDataLoaded: (List<MoleData>, Boolean) -> Unit,
        onError: (Exception) -> Unit,
        config: PerformanceConfig = PerformanceConfig()
    ): MolePaginationManager? {
        if (!config.enablePagination) return null
        
        val paginationManager = MolePaginationManager(
            pageSize = config.pageSize,
            prefetchDistance = config.preloadDistance
        )
        
        paginationManager.setCallbacks(
            onLoadComplete = onDataLoaded,
            onLoadError = onError
        )
        
        paginationManager.setupScrollListener(recyclerView) {
            CoroutineScope(Dispatchers.Main).launch {
                paginationManager.loadMolesPage(isFirstPage = false)
            }
        }
        
        return paginationManager
    }

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

    // ==================== CONSULTAS OPTIMIZADAS ====================

    /**
     * Obtiene lunares con consultas optimizadas
     */
    suspend fun getOptimizedMoles(
        userId: String? = null,
        bodyPartColorCode: String? = null,
        config: PerformanceConfig = PerformanceConfig()
    ): Result<List<MoleData>> {
        if (!config.enableOptimizedQueries) {
            return Result.failure(Exception("Consultas optimizadas deshabilitadas"))
        }
        
        val queryConfig = OptimizedFirebaseQueries.QueryConfig(
            useCache = config.enableLocalCache,
            limit = config.pageSize
        )
        
        return optimizedQueries.getMolesOptimized(userId, bodyPartColorCode, queryConfig)
    }

    /**
     * Obtiene análisis con consultas optimizadas
     */
    suspend fun getOptimizedAnalysis(
        moleId: String? = null,
        config: PerformanceConfig = PerformanceConfig()
    ): Result<List<AnalysisData>> {
        if (!config.enableOptimizedQueries) {
            return Result.failure(Exception("Consultas optimizadas deshabilitadas"))
        }
        
        val queryConfig = OptimizedFirebaseQueries.QueryConfig(
            useCache = config.enableLocalCache,
            limit = config.pageSize
        )
        
        return optimizedQueries.getAnalysisOptimized(moleId, config = queryConfig)
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
     * Tipos de imagen para optimización específica
     */
    enum class ImageType {
        MOLE_THUMBNAIL,
        ANALYSIS_IMAGE,
        FULL_SIZE
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
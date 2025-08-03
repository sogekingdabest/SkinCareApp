package es.monsteraltech.skincare_tfm.body.mole.performance

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import androidx.core.content.edit

/**
 * Gestor de caché local para análisis y lunares recientes
 * Implementa estrategias de caché en memoria y persistente
 */
class LocalCacheManager private constructor(context: Context) {
    
    private val gson = Gson()
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
    
    // Caché en memoria
    private val analysisMemoryCache = ConcurrentHashMap<String, CacheEntry<AnalysisData>>()
    private val moleMemoryCache = ConcurrentHashMap<String, CacheEntry<MoleData>>()
    private val analysisListCache = ConcurrentHashMap<String, CacheEntry<List<AnalysisData>>>()
    
    companion object {
        private const val CACHE_PREFS_NAME = "mole_analysis_cache"
        private const val DEFAULT_CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutos
        private const val EXTENDED_CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutos
        private const val MAX_MEMORY_CACHE_SIZE = 100
        private const val MAX_PERSISTENT_CACHE_SIZE = 50
        
        @Volatile
        private var INSTANCE: LocalCacheManager? = null
        
        fun getInstance(context: Context): LocalCacheManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalCacheManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Entrada de caché con metadatos de expiración
     */
    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long,
        val expirationMs: Long = DEFAULT_CACHE_DURATION_MS
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > expirationMs
    }

    /**
     * Configuración de caché
     */
    data class CacheConfig(
        val enableMemoryCache: Boolean = true,
        val enablePersistentCache: Boolean = true,
        val cacheExpirationMs: Long = DEFAULT_CACHE_DURATION_MS,
        val maxMemoryCacheSize: Int = MAX_MEMORY_CACHE_SIZE
    )

    // ==================== ANÁLISIS CACHE ====================

    /**
     * Guarda un análisis en caché
     */
    suspend fun cacheAnalysis(
        analysisId: String,
        analysis: AnalysisData,
        config: CacheConfig = CacheConfig()
    ) = withContext(Dispatchers.IO) {
        try {
            val cacheEntry = CacheEntry(analysis, System.currentTimeMillis(), config.cacheExpirationMs)
            
            // Caché en memoria
            if (config.enableMemoryCache) {
                analysisMemoryCache[analysisId] = cacheEntry
                cleanupMemoryCache(analysisMemoryCache, config.maxMemoryCacheSize)
            }
            
            // Caché persistente
            if (config.enablePersistentCache) {
                val cacheKey = "analysis_$analysisId"
                val jsonData = gson.toJson(cacheEntry)
                sharedPrefs.edit() { putString(cacheKey, jsonData) }
                cleanupPersistentCache("analysis_")
            } else {
                // No hacer nada si el caché persistente está deshabilitado
            }
            
        } catch (e: Exception) {
            Log.w("LocalCacheManager", "Error al guardar análisis en caché", e)
        }
    }

    /**
     * Recupera un análisis del caché
     */
    suspend fun getCachedAnalysis(
        analysisId: String,
        config: CacheConfig = CacheConfig()
    ): AnalysisData? = withContext(Dispatchers.IO) {
        try {
            // Intentar caché en memoria primero
            if (config.enableMemoryCache) {
                analysisMemoryCache[analysisId]?.let { entry ->
                    if (!entry.isExpired()) {
                        return@withContext entry.data
                    } else {
                        analysisMemoryCache.remove(analysisId)
                    }
                }
            }
            
            // Intentar caché persistente
            if (config.enablePersistentCache) {
                val cacheKey = "analysis_$analysisId"
                val jsonData = sharedPrefs.getString(cacheKey, null)
                
                if (jsonData != null) {
                    val type = object : TypeToken<CacheEntry<AnalysisData>>() {}.type
                    val entry: CacheEntry<AnalysisData> = gson.fromJson(jsonData, type)
                    
                    if (!entry.isExpired()) {
                        // Restaurar en caché de memoria
                        if (config.enableMemoryCache) {
                            analysisMemoryCache[analysisId] = entry
                        }
                        return@withContext entry.data
                    } else {
                        // Eliminar entrada expirada
                        sharedPrefs.edit() { remove(cacheKey) }
                    }
                }
            }
            
            null
        } catch (e: Exception) {
            Log.w("LocalCacheManager", "Error al recuperar análisis del caché", e)
            null
        }
    }

    /**
     * Guarda una lista de análisis en caché
     */
    suspend fun cacheAnalysisList(
        cacheKey: String,
        analysisList: List<AnalysisData>,
        config: CacheConfig = CacheConfig()
    ) = withContext(Dispatchers.IO) {
        try {
            val cacheEntry = CacheEntry(analysisList, System.currentTimeMillis(), config.cacheExpirationMs)
            
            // Caché en memoria
            if (config.enableMemoryCache) {
                analysisListCache[cacheKey] = cacheEntry
                cleanupMemoryCache(analysisListCache, config.maxMemoryCacheSize)
            }
            
            // Caché persistente para listas pequeñas
            if (config.enablePersistentCache && analysisList.size <= 20) {
                val persistentKey = "analysis_list_$cacheKey"
                val jsonData = gson.toJson(cacheEntry)
                sharedPrefs.edit() { putString(persistentKey, jsonData) }
            } else {
                // No hacer nada si el caché persistente está deshabilitado o la lista es muy grande
            }
            
        } catch (e: Exception) {
            Log.w("LocalCacheManager", "Error al guardar lista de análisis en caché", e)
        }
    }

    /**
     * Recupera una lista de análisis del caché
     */
    suspend fun getCachedAnalysisList(
        cacheKey: String,
        config: CacheConfig = CacheConfig()
    ): List<AnalysisData>? = withContext(Dispatchers.IO) {
        try {
            // Intentar caché en memoria primero
            if (config.enableMemoryCache) {
                analysisListCache[cacheKey]?.let { entry ->
                    if (!entry.isExpired()) {
                        return@withContext entry.data
                    } else {
                        analysisListCache.remove(cacheKey)
                    }
                }
            }
            
            // Intentar caché persistente
            if (config.enablePersistentCache) {
                val persistentKey = "analysis_list_$cacheKey"
                val jsonData = sharedPrefs.getString(persistentKey, null)
                
                if (jsonData != null) {
                    val type = object : TypeToken<CacheEntry<List<AnalysisData>>>() {}.type
                    val entry: CacheEntry<List<AnalysisData>> = gson.fromJson(jsonData, type)
                    
                    if (!entry.isExpired()) {
                        // Restaurar en caché de memoria
                        if (config.enableMemoryCache) {
                            analysisListCache[cacheKey] = entry
                        }
                        return@withContext entry.data
                    } else {
                        // Eliminar entrada expirada
                        sharedPrefs.edit() { remove(persistentKey) }
                    }
                }
            }
            
            null
        } catch (e: Exception) {
            Log.w("LocalCacheManager", "Error al recuperar lista de análisis del caché", e)
            null
        }
    }

    // ==================== MOLE CACHE ====================

    /**
     * Guarda un lunar en caché
     */
    suspend fun cacheMole(
        moleId: String,
        mole: MoleData,
        config: CacheConfig = CacheConfig(cacheExpirationMs = EXTENDED_CACHE_DURATION_MS)
    ) = withContext(Dispatchers.IO) {
        try {
            val cacheEntry = CacheEntry(mole, System.currentTimeMillis(), config.cacheExpirationMs)
            
            // Caché en memoria
            if (config.enableMemoryCache) {
                moleMemoryCache[moleId] = cacheEntry
                cleanupMemoryCache(moleMemoryCache, config.maxMemoryCacheSize)
            }
            
            // Caché persistente
            if (config.enablePersistentCache) {
                val cacheKey = "mole_$moleId"
                val jsonData = gson.toJson(cacheEntry)
                sharedPrefs.edit() { putString(cacheKey, jsonData) }
                cleanupPersistentCache("mole_")
            } else {
                // No hacer nada si el caché persistente está deshabilitado
            }
            
        } catch (e: Exception) {
            Log.w("LocalCacheManager", "Error al guardar lunar en caché", e)
        }
    }

    /**
     * Recupera un lunar del caché
     */
    suspend fun getCachedMole(
        moleId: String,
        config: CacheConfig = CacheConfig(cacheExpirationMs = EXTENDED_CACHE_DURATION_MS)
    ): MoleData? = withContext(Dispatchers.IO) {
        try {
            // Intentar caché en memoria primero
            if (config.enableMemoryCache) {
                moleMemoryCache[moleId]?.let { entry ->
                    if (!entry.isExpired()) {
                        return@withContext entry.data
                    } else {
                        moleMemoryCache.remove(moleId)
                    }
                }
            }
            
            // Intentar caché persistente
            if (config.enablePersistentCache) {
                val cacheKey = "mole_$moleId"
                val jsonData = sharedPrefs.getString(cacheKey, null)
                
                if (jsonData != null) {
                    val type = object : TypeToken<CacheEntry<MoleData>>() {}.type
                    val entry: CacheEntry<MoleData> = gson.fromJson(jsonData, type)
                    
                    if (!entry.isExpired()) {
                        // Restaurar en caché de memoria
                        if (config.enableMemoryCache) {
                            moleMemoryCache[moleId] = entry
                        }
                        return@withContext entry.data
                    } else {
                        // Eliminar entrada expirada
                        sharedPrefs.edit() { remove(cacheKey) }
                    }
                }
            }
            
            null
        } catch (e: Exception) {
            Log.w("LocalCacheManager", "Error al recuperar lunar del caché", e)
            null
        }
    }

    // ==================== CACHE MANAGEMENT ====================

    /**
     * Limpia el caché en memoria cuando excede el tamaño máximo
     */
    private fun <T> cleanupMemoryCache(cache: ConcurrentHashMap<String, CacheEntry<T>>, maxSize: Int) {
        if (cache.size > maxSize) {
            // Eliminar entradas más antiguas
            val sortedEntries = cache.entries.sortedBy { it.value.timestamp }
            val toRemove = sortedEntries.take(cache.size - maxSize + 10) // Eliminar 10 extra para evitar limpieza frecuente
            
            toRemove.forEach { entry ->
                cache.remove(entry.key)
            }
        }
    }

    /**
     * Limpia el caché persistente cuando excede el tamaño máximo
     */
    private fun cleanupPersistentCache(prefix: String) {
        try {
            val allKeys = sharedPrefs.all.keys.filter { it.startsWith(prefix) }
            
            if (allKeys.size > MAX_PERSISTENT_CACHE_SIZE) {
                // Obtener timestamps y ordenar por antigüedad
                val keyTimestamps = allKeys.mapNotNull { key ->
                    try {
                        val jsonData = sharedPrefs.getString(key, null)
                        if (jsonData != null) {
                            val entry = gson.fromJson(jsonData, CacheEntry::class.java)
                            key to entry.timestamp
                        } else null
                    } catch (e: Exception) {
                        key to 0L // Marcar para eliminación
                    }
                }.sortedBy { it.second }
                
                // Eliminar las más antiguas
                val toRemove = keyTimestamps.take(allKeys.size - MAX_PERSISTENT_CACHE_SIZE + 5)
                sharedPrefs.edit() {

                    toRemove.forEach { (key, _) ->
                        remove(key)
                    }

                }
            }
        } catch (e: Exception) {
            Log.w("LocalCacheManager", "Error al limpiar caché persistente", e)
        }
    }

    /**
     * Invalida una entrada específica del caché
     */
    suspend fun invalidateAnalysis(analysisId: String) = withContext(Dispatchers.IO) {
        analysisMemoryCache.remove(analysisId)
        sharedPrefs.edit() { remove("analysis_$analysisId") }
    }

    /**
     * Limpia todo el caché
     */
    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        analysisMemoryCache.clear()
        moleMemoryCache.clear()
        analysisListCache.clear()
        sharedPrefs.edit() { clear() }
    }

    /**
     * Obtiene estadísticas del caché
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            analysisMemoryCacheSize = analysisMemoryCache.size,
            moleMemoryCacheSize = moleMemoryCache.size,
            analysisListCacheSize = analysisListCache.size,
            persistentCacheSize = sharedPrefs.all.size
        )
    }

    /**
     * Estadísticas del caché
     */
    data class CacheStats(
        val analysisMemoryCacheSize: Int,
        val moleMemoryCacheSize: Int,
        val analysisListCacheSize: Int,
        val persistentCacheSize: Int
    )

    /**
     * Configuraciones predefinidas para diferentes casos de uso
     */
    object Configs {
        fun shortTerm() = CacheConfig(
            enableMemoryCache = true,
            enablePersistentCache = false,
            cacheExpirationMs = 2 * 60 * 1000L // 2 minutos
        )
        
        fun mediumTerm() = CacheConfig(
            enableMemoryCache = true,
            enablePersistentCache = true,
            cacheExpirationMs = DEFAULT_CACHE_DURATION_MS
        )
        
        fun longTerm() = CacheConfig(
            enableMemoryCache = true,
            enablePersistentCache = true,
            cacheExpirationMs = EXTENDED_CACHE_DURATION_MS
        )
    }
}
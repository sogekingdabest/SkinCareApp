package es.monsteraltech.skincare_tfm.body.mole.performance
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class LocalCacheManager private constructor(context: Context) {
    private val gson = Gson()
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
    private val analysisMemoryCache = ConcurrentHashMap<String, CacheEntry<AnalysisData>>()
    private val moleMemoryCache = ConcurrentHashMap<String, CacheEntry<MoleData>>()
    private val analysisListCache = ConcurrentHashMap<String, CacheEntry<List<AnalysisData>>>()
    companion object {
        private const val CACHE_PREFS_NAME = "mole_analysis_cache"
        private const val DEFAULT_CACHE_DURATION_MS = 5 * 60 * 1000L
        private const val EXTENDED_CACHE_DURATION_MS = 30 * 60 * 1000L
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
    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long,
        val expirationMs: Long = DEFAULT_CACHE_DURATION_MS
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > expirationMs
    }
    data class CacheConfig(
        val enableMemoryCache: Boolean = true,
        val enablePersistentCache: Boolean = true,
        val cacheExpirationMs: Long = DEFAULT_CACHE_DURATION_MS,
        val maxMemoryCacheSize: Int = MAX_MEMORY_CACHE_SIZE
    )
    suspend fun cacheAnalysis(
        analysisId: String,
        analysis: AnalysisData,
        config: CacheConfig = CacheConfig()
    ) = withContext(Dispatchers.IO) {
        try {
            val cacheEntry = CacheEntry(analysis, System.currentTimeMillis(), config.cacheExpirationMs)
            if (config.enableMemoryCache) {
                analysisMemoryCache[analysisId] = cacheEntry
                cleanupMemoryCache(analysisMemoryCache, config.maxMemoryCacheSize)
            }
            if (config.enablePersistentCache) {
                val cacheKey = "analysis_$analysisId"
                val jsonData = gson.toJson(cacheEntry)
                sharedPrefs.edit() { putString(cacheKey, jsonData) }
                cleanupPersistentCache("analysis_")
            } else {
            }
        } catch (e: Exception) {
            Log.w("LocalCacheManager", "Error al guardar análisis en caché", e)
        }
    }
    suspend fun getCachedAnalysis(
        analysisId: String,
        config: CacheConfig = CacheConfig()
    ): AnalysisData? = withContext(Dispatchers.IO) {
        try {
            if (config.enableMemoryCache) {
                analysisMemoryCache[analysisId]?.let { entry ->
                    if (!entry.isExpired()) {
                        return@withContext entry.data
                    } else {
                        analysisMemoryCache.remove(analysisId)
                    }
                }
            }
            if (config.enablePersistentCache) {
                val cacheKey = "analysis_$analysisId"
                val jsonData = sharedPrefs.getString(cacheKey, null)
                if (jsonData != null) {
                    val type = object : TypeToken<CacheEntry<AnalysisData>>() {}.type
                    val entry: CacheEntry<AnalysisData> = gson.fromJson(jsonData, type)
                    if (!entry.isExpired()) {
                        if (config.enableMemoryCache) {
                            analysisMemoryCache[analysisId] = entry
                        }
                        return@withContext entry.data
                    } else {
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
    suspend fun cacheAnalysisList(
        cacheKey: String,
        analysisList: List<AnalysisData>,
        config: CacheConfig = CacheConfig()
    ) = withContext(Dispatchers.IO) {
        try {
            val cacheEntry = CacheEntry(analysisList, System.currentTimeMillis(), config.cacheExpirationMs)
            if (config.enableMemoryCache) {
                analysisListCache[cacheKey] = cacheEntry
                cleanupMemoryCache(analysisListCache, config.maxMemoryCacheSize)
            }
            if (config.enablePersistentCache && analysisList.size <= 20) {
                val persistentKey = "analysis_list_$cacheKey"
                val jsonData = gson.toJson(cacheEntry)
                sharedPrefs.edit() { putString(persistentKey, jsonData) }
            } else {
            }
        } catch (e: Exception) {
            Log.w("LocalCacheManager", "Error al guardar lista de análisis en caché", e)
        }
    }
    suspend fun getCachedAnalysisList(
        cacheKey: String,
        config: CacheConfig = CacheConfig()
    ): List<AnalysisData>? = withContext(Dispatchers.IO) {
        try {
            if (config.enableMemoryCache) {
                analysisListCache[cacheKey]?.let { entry ->
                    if (!entry.isExpired()) {
                        return@withContext entry.data
                    } else {
                        analysisListCache.remove(cacheKey)
                    }
                }
            }
            if (config.enablePersistentCache) {
                val persistentKey = "analysis_list_$cacheKey"
                val jsonData = sharedPrefs.getString(persistentKey, null)
                if (jsonData != null) {
                    val type = object : TypeToken<CacheEntry<List<AnalysisData>>>() {}.type
                    val entry: CacheEntry<List<AnalysisData>> = gson.fromJson(jsonData, type)
                    if (!entry.isExpired()) {
                        if (config.enableMemoryCache) {
                            analysisListCache[cacheKey] = entry
                        }
                        return@withContext entry.data
                    } else {
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
    suspend fun cacheMole(
        moleId: String,
        mole: MoleData,
        config: CacheConfig = CacheConfig(cacheExpirationMs = EXTENDED_CACHE_DURATION_MS)
    ) = withContext(Dispatchers.IO) {
        try {
            val cacheEntry = CacheEntry(mole, System.currentTimeMillis(), config.cacheExpirationMs)
            if (config.enableMemoryCache) {
                moleMemoryCache[moleId] = cacheEntry
                cleanupMemoryCache(moleMemoryCache, config.maxMemoryCacheSize)
            }
            if (config.enablePersistentCache) {
                val cacheKey = "mole_$moleId"
                val jsonData = gson.toJson(cacheEntry)
                sharedPrefs.edit() { putString(cacheKey, jsonData) }
                cleanupPersistentCache("mole_")
            } else {
            }
        } catch (e: Exception) {
            Log.w("LocalCacheManager", "Error al guardar lunar en caché", e)
        }
    }
    suspend fun getCachedMole(
        moleId: String,
        config: CacheConfig = CacheConfig(cacheExpirationMs = EXTENDED_CACHE_DURATION_MS)
    ): MoleData? = withContext(Dispatchers.IO) {
        try {
            if (config.enableMemoryCache) {
                moleMemoryCache[moleId]?.let { entry ->
                    if (!entry.isExpired()) {
                        return@withContext entry.data
                    } else {
                        moleMemoryCache.remove(moleId)
                    }
                }
            }
            if (config.enablePersistentCache) {
                val cacheKey = "mole_$moleId"
                val jsonData = sharedPrefs.getString(cacheKey, null)
                if (jsonData != null) {
                    val type = object : TypeToken<CacheEntry<MoleData>>() {}.type
                    val entry: CacheEntry<MoleData> = gson.fromJson(jsonData, type)
                    if (!entry.isExpired()) {
                        if (config.enableMemoryCache) {
                            moleMemoryCache[moleId] = entry
                        }
                        return@withContext entry.data
                    } else {
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
    private fun <T> cleanupMemoryCache(cache: ConcurrentHashMap<String, CacheEntry<T>>, maxSize: Int) {
        if (cache.size > maxSize) {
            val sortedEntries = cache.entries.sortedBy { it.value.timestamp }
            val toRemove = sortedEntries.take(cache.size - maxSize + 10)
            toRemove.forEach { entry ->
                cache.remove(entry.key)
            }
        }
    }
    private fun cleanupPersistentCache(prefix: String) {
        try {
            val allKeys = sharedPrefs.all.keys.filter { it.startsWith(prefix) }
            if (allKeys.size > MAX_PERSISTENT_CACHE_SIZE) {
                val keyTimestamps = allKeys.mapNotNull { key ->
                    try {
                        val jsonData = sharedPrefs.getString(key, null)
                        if (jsonData != null) {
                            val entry = gson.fromJson(jsonData, CacheEntry::class.java)
                            key to entry.timestamp
                        } else null
                    } catch (e: Exception) {
                        key to 0L
                    }
                }.sortedBy { it.second }
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
    suspend fun invalidateAnalysis(analysisId: String) = withContext(Dispatchers.IO) {
        analysisMemoryCache.remove(analysisId)
        sharedPrefs.edit() { remove("analysis_$analysisId") }
    }
    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        analysisMemoryCache.clear()
        moleMemoryCache.clear()
        analysisListCache.clear()
        sharedPrefs.edit() { clear() }
    }
    fun getCacheStats(): CacheStats {
        return CacheStats(
            analysisMemoryCacheSize = analysisMemoryCache.size,
            moleMemoryCacheSize = moleMemoryCache.size,
            analysisListCacheSize = analysisListCache.size,
            persistentCacheSize = sharedPrefs.all.size
        )
    }
    data class CacheStats(
        val analysisMemoryCacheSize: Int,
        val moleMemoryCacheSize: Int,
        val analysisListCacheSize: Int,
        val persistentCacheSize: Int
    )
    object Configs {
        fun shortTerm() = CacheConfig(
            enableMemoryCache = true,
            enablePersistentCache = false,
            cacheExpirationMs = 2 * 60 * 1000L
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
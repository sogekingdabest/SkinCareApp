package es.monsteraltech.skincare_tfm.body.mole.performance

import android.content.Context
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.util.ImageLoadingUtil
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestor de carga lazy de imágenes optimizado para RecyclerViews
 * Implementa estrategias de carga diferida, preload y gestión de memoria
 */
class LazyImageLoader(private val context: Context) {
    
    private val firebaseDataManager = FirebaseDataManager()
    private val loadingJobs = ConcurrentHashMap<String, Job>()
    private val preloadedImages = ConcurrentHashMap<String, Boolean>()
    
    companion object {
        private const val PRELOAD_DELAY_MS = 100L
        private const val THUMBNAIL_SIZE = 200
        private const val FULL_SIZE = 800
    }

    /**
     * Configuración para carga lazy de imágenes
     */
    data class LazyLoadConfig(
        val enablePreload: Boolean = true,
        val preloadDistance: Int = 3, // Número de elementos a precargar
        val thumbnailFirst: Boolean = true,
        val diskCacheStrategy: DiskCacheStrategy = DiskCacheStrategy.AUTOMATIC,
        val placeholderRes: Int = R.drawable.ic_image_placeholder,
        val errorRes: Int = R.drawable.ic_image_error,
        val enableMemoryCache: Boolean = true,
        val loadDelay: Long = 0L // Delay antes de cargar (para scroll rápido)
    )

    /**
     * Carga una imagen con estrategia lazy optimizada para listas
     */
    fun loadImageLazy(
        imageView: ImageView,
        imageUrl: String?,
        position: Int,
        recyclerView: RecyclerView,
        config: LazyLoadConfig = LazyLoadConfig()
    ) {
        // Cancelar carga previa si existe
        val imageKey = "${imageUrl}_${position}"
        loadingJobs[imageKey]?.cancel()
        
        if (imageUrl.isNullOrEmpty()) {
            imageView.setImageResource(config.errorRes)
            return
        }

        // Mostrar placeholder inmediatamente
        imageView.setImageResource(config.placeholderRes)

        // Verificar si la imagen está visible en pantalla
        if (!isImageViewVisible(imageView, recyclerView)) {
            // Si no está visible, solo precargar si está habilitado
            if (config.enablePreload) {
                schedulePreload(imageUrl, config)
            }
            return
        }

        // Crear job de carga con delay opcional
        val loadingJob = CoroutineScope(Dispatchers.Main).launch {
            if (config.loadDelay > 0) {
                delay(config.loadDelay)
            }

            // Verificar que la vista sigue siendo visible después del delay
            if (!isImageViewVisible(imageView, recyclerView)) {
                return@launch
            }

            loadImageWithStrategy(imageView, imageUrl, config)
        }

        loadingJobs[imageKey] = loadingJob
    }

    /**
     * Carga imagen con estrategia específica (thumbnail primero, luego full size)
     */
    private suspend fun loadImageWithStrategy(
        imageView: ImageView,
        imageUrl: String,
        config: LazyLoadConfig
    ) {
        try {
            val imageSource = getImageSource(imageUrl)
            
            if (config.thumbnailFirst) {
                // Cargar thumbnail primero para respuesta rápida
                loadThumbnail(imageView, imageSource, config)
                
                // Luego cargar imagen completa
                delay(50) // Pequeño delay para mostrar thumbnail
                loadFullImage(imageView, imageSource, config)
            } else {
                // Cargar directamente imagen completa
                loadFullImage(imageView, imageSource, config)
            }
        } catch (e: Exception) {
            imageView.setImageResource(config.errorRes)
        }
    }

    /**
     * Carga thumbnail de baja resolución
     */
    private fun loadThumbnail(imageView: ImageView, imageSource: Any, config: LazyLoadConfig) {
        val requestOptions = RequestOptions()
            .override(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
            .diskCacheStrategy(config.diskCacheStrategy)
            .skipMemoryCache(!config.enableMemoryCache)

        Glide.with(context)
            .load(imageSource)
            .apply(requestOptions)
            .placeholder(config.placeholderRes)
            .error(config.errorRes)
            .into(imageView)
    }

    /**
     * Carga imagen en resolución completa
     */
    private fun loadFullImage(imageView: ImageView, imageSource: Any, config: LazyLoadConfig) {
        val requestOptions = RequestOptions()
            .override(FULL_SIZE, FULL_SIZE)
            .diskCacheStrategy(config.diskCacheStrategy)
            .skipMemoryCache(!config.enableMemoryCache)

        Glide.with(context)
            .load(imageSource)
            .apply(requestOptions)
            .placeholder(config.placeholderRes)
            .error(config.errorRes)
            .into(imageView)
    }

    /**
     * Programa precarga de imagen
     */
    private fun schedulePreload(imageUrl: String, config: LazyLoadConfig) {
        if (preloadedImages.containsKey(imageUrl)) {
            return // Ya está precargada
        }

        CoroutineScope(Dispatchers.IO).launch {
            delay(PRELOAD_DELAY_MS)
            
            try {
                val imageSource = getImageSource(imageUrl)
                
                // Precargar solo thumbnail para ahorrar memoria
                Glide.with(context)
                    .load(imageSource)
                    .apply(RequestOptions().override(THUMBNAIL_SIZE, THUMBNAIL_SIZE))
                    .preload()
                
                preloadedImages[imageUrl] = true
            } catch (e: Exception) {
                // Ignorar errores de precarga
            }
        }
    }

    /**
     * Verifica si un ImageView está visible en el RecyclerView
     */
    private fun isImageViewVisible(imageView: ImageView, recyclerView: RecyclerView): Boolean {
        val layoutManager = recyclerView.layoutManager ?: return false
        
        val viewHolder = recyclerView.findContainingViewHolder(imageView) ?: return false
        val position = viewHolder.adapterPosition
        
        if (position == RecyclerView.NO_POSITION) return false
        
        val firstVisible = when (layoutManager) {
            is androidx.recyclerview.widget.LinearLayoutManager -> layoutManager.findFirstVisibleItemPosition()
            else -> 0
        }
        
        val lastVisible = when (layoutManager) {
            is androidx.recyclerview.widget.LinearLayoutManager -> layoutManager.findLastVisibleItemPosition()
            else -> layoutManager.itemCount - 1
        }
        
        return position in firstVisible..lastVisible
    }

    /**
     * Obtiene la fuente de imagen apropiada
     */
    private fun getImageSource(imageUrl: String): Any {
        return if (imageUrl.startsWith("http")) {
            imageUrl
        } else {
            File(firebaseDataManager.getFullImagePath(context, imageUrl))
        }
    }

    /**
     * Precarga imágenes para posiciones específicas
     */
    fun preloadImagesForPositions(
        imageUrls: List<String>,
        startPosition: Int,
        count: Int,
        config: LazyLoadConfig = LazyLoadConfig()
    ) {
        val endPosition = minOf(startPosition + count, imageUrls.size)
        
        CoroutineScope(Dispatchers.IO).launch {
            for (i in startPosition until endPosition) {
                val imageUrl = imageUrls[i]
                if (imageUrl.isNotEmpty() && !preloadedImages.containsKey(imageUrl)) {
                    schedulePreload(imageUrl, config)
                    delay(10) // Pequeño delay entre precargas
                }
            }
        }
    }

    /**
     * Cancela todas las cargas pendientes
     */
    fun cancelAllLoads() {
        loadingJobs.values.forEach { it.cancel() }
        loadingJobs.clear()
    }

    /**
     * Limpia la caché de imágenes precargadas
     */
    fun clearPreloadCache() {
        preloadedImages.clear()
    }

    /**
     * Configuraciones predefinidas para diferentes casos de uso
     */
    object Configs {
        fun forMoleList() = LazyLoadConfig(
            enablePreload = true,
            preloadDistance = 5,
            thumbnailFirst = true,
            loadDelay = 50L,
            placeholderRes = R.drawable.ic_image_placeholder,
            errorRes = R.drawable.ic_image_error
        )
        
        fun forAnalysisHistory() = LazyLoadConfig(
            enablePreload = true,
            preloadDistance = 3,
            thumbnailFirst = true,
            loadDelay = 30L,
            diskCacheStrategy = DiskCacheStrategy.ALL
        )
        
        fun forFastScrolling() = LazyLoadConfig(
            enablePreload = false,
            thumbnailFirst = true,
            loadDelay = 200L, // Mayor delay para scroll rápido
            diskCacheStrategy = DiskCacheStrategy.AUTOMATIC
        )
        
        fun forSlowConnection() = LazyLoadConfig(
            enablePreload = false,
            thumbnailFirst = true,
            loadDelay = 0L,
            diskCacheStrategy = DiskCacheStrategy.ALL
        )
    }
}
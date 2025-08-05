package es.monsteraltech.skincare_tfm.body.mole.performance
import android.content.Context
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
class LazyImageLoader(private val context: Context) {
    private val firebaseDataManager = FirebaseDataManager()
    private val loadingJobs = ConcurrentHashMap<String, Job>()
    private val preloadedImages = ConcurrentHashMap<String, Boolean>()
    companion object {
        private const val PRELOAD_DELAY_MS = 100L
        private const val THUMBNAIL_SIZE = 200
        private const val FULL_SIZE = 800
    }
    data class LazyLoadConfig(
        val enablePreload: Boolean = true,
        val preloadDistance: Int = 3,
        val thumbnailFirst: Boolean = true,
        val diskCacheStrategy: DiskCacheStrategy = DiskCacheStrategy.AUTOMATIC,
        val placeholderRes: Int = R.drawable.ic_image_placeholder,
        val errorRes: Int = R.drawable.ic_image_error,
        val enableMemoryCache: Boolean = true,
        val loadDelay: Long = 0L
    )
    fun loadImageLazy(
        imageView: ImageView,
        imageUrl: String?,
        position: Int,
        recyclerView: RecyclerView,
        config: LazyLoadConfig = LazyLoadConfig()
    ) {
        val imageKey = "${imageUrl}_${position}"
        loadingJobs[imageKey]?.cancel()
        if (imageUrl.isNullOrEmpty()) {
            imageView.setImageResource(config.errorRes)
            return
        }
        imageView.setImageResource(config.placeholderRes)
        if (!isImageViewVisible(imageView, recyclerView)) {
            if (config.enablePreload) {
                schedulePreload(imageUrl)
            }
            return
        }
        val loadingJob = CoroutineScope(Dispatchers.Main).launch {
            if (config.loadDelay > 0) {
                delay(config.loadDelay)
            }
            if (!isImageViewVisible(imageView, recyclerView)) {
                return@launch
            }
            loadImageWithStrategy(imageView, imageUrl, config)
        }
        loadingJobs[imageKey] = loadingJob
    }
    private suspend fun loadImageWithStrategy(
        imageView: ImageView,
        imageUrl: String,
        config: LazyLoadConfig
    ) {
        try {
            val imageSource = getImageSource(imageUrl)
            if (config.thumbnailFirst) {
                loadThumbnail(imageView, imageSource, config)
                delay(50)
                loadFullImage(imageView, imageSource, config)
            } else {
                loadFullImage(imageView, imageSource, config)
            }
        } catch (e: Exception) {
            imageView.setImageResource(config.errorRes)
        }
    }
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
    private fun schedulePreload(imageUrl: String) {
        if (preloadedImages.containsKey(imageUrl)) {
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            delay(PRELOAD_DELAY_MS)
            try {
                val imageSource = getImageSource(imageUrl)
                Glide.with(context)
                    .load(imageSource)
                    .apply(RequestOptions().override(THUMBNAIL_SIZE, THUMBNAIL_SIZE))
                    .preload()
                preloadedImages[imageUrl] = true
            } catch (e: Exception) {
            }
        }
    }
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
    private fun getImageSource(imageUrl: String): Any {
        return if (imageUrl.startsWith("http")) {
            imageUrl
        } else {
            File(firebaseDataManager.getFullImagePath(context, imageUrl))
        }
    }
    fun cancelAllLoads() {
        loadingJobs.values.forEach { it.cancel() }
        loadingJobs.clear()
    }
    fun clearPreloadCache() {
        preloadedImages.clear()
    }
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
            loadDelay = 200L,
            diskCacheStrategy = DiskCacheStrategy.AUTOMATIC
        )
    }
}
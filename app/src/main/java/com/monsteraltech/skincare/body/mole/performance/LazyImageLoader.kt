package com.monsteraltech.skincare.body.mole.performance
import android.content.Context
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.body.mole.util.ImageLoadingUtil
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
class LazyImageLoader(private val context: Context, private val scope: CoroutineScope) {
    private val requests = Glide.with(context)
    private val loadingJobs = ConcurrentHashMap<ImageView, Job>()
    private val preloadJobs = ConcurrentHashMap<String, Job>()
    private val activeViews = mutableSetOf<ImageView>()
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
        recyclerView: RecyclerView,
        config: LazyLoadConfig = LazyLoadConfig()
    ) {
        cancelLoad(imageView)
        activeViews += imageView
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
        val loadingJob = scope.launch(start = CoroutineStart.LAZY) {
            if (config.loadDelay > 0) {
                delay(config.loadDelay)
            }
            if (!isImageViewVisible(imageView, recyclerView)) {
                return@launch
            }
            loadImageWithStrategy(imageView, imageUrl, config)
        }
        loadingJobs[imageView] = loadingJob
        loadingJob.invokeOnCompletion { loadingJobs.remove(imageView, loadingJob) }
        loadingJob.start()
    }
    private suspend fun loadImageWithStrategy(
        imageView: ImageView,
        imageUrl: String,
        config: LazyLoadConfig
    ) {
        try {
            val imageSource = withContext(Dispatchers.IO) { getImageSource(imageUrl) } ?: run {
                imageView.setImageResource(config.errorRes)
                return
            }
            if (config.thumbnailFirst) {
                loadThumbnail(imageView, imageSource, config)
                delay(50)
                loadFullImage(imageView, imageSource, config)
            } else {
                loadFullImage(imageView, imageSource, config)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            imageView.setImageResource(config.errorRes)
        }
    }
    private fun loadThumbnail(imageView: ImageView, imageSource: Any, config: LazyLoadConfig) {
        val requestOptions = RequestOptions()
            .override(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
            .diskCacheStrategy(config.diskCacheStrategy)
            .skipMemoryCache(!config.enableMemoryCache)
        requests
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
        requests
            .load(imageSource)
            .apply(requestOptions)
            .placeholder(config.placeholderRes)
            .error(config.errorRes)
            .into(imageView)
    }
    private fun schedulePreload(imageUrl: String) {
        if (preloadedImages.containsKey(imageUrl) || preloadJobs.containsKey(imageUrl)) {
            return
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(PRELOAD_DELAY_MS)
            try {
                val imageSource = withContext(Dispatchers.IO) { getImageSource(imageUrl) }
                requests
                    .load(imageSource)
                    .apply(RequestOptions().override(THUMBNAIL_SIZE, THUMBNAIL_SIZE))
                    .preload()
                preloadedImages[imageUrl] = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { }
        }
        preloadJobs[imageUrl] = job
        job.invokeOnCompletion { preloadJobs.remove(imageUrl, job) }
        job.start()
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
    private fun getImageSource(imageUrl: String): Any? =
        ImageLoadingUtil.getImageSource(context, imageUrl)
    fun cancelLoad(imageView: ImageView) {
        loadingJobs.remove(imageView)?.cancel()
        activeViews.remove(imageView)
        requests.clear(imageView)
    }
    fun cancelAllLoads() {
        activeViews.toList().forEach(::cancelLoad)
        preloadJobs.values.toList().forEach { it.cancel() }
        preloadJobs.clear()
        preloadedImages.clear()
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

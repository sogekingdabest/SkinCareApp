package es.monsteraltech.skincare_tfm.body.mole.util

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.error.RetryManager
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Utilidad para carga de imágenes con fallbacks y reintentos automáticos
 * Maneja tanto URLs remotas como rutas locales con recuperación de errores
 */
object ImageLoadingUtil {

    private val firebaseDataManager = FirebaseDataManager()

    /**
     * Configuración para carga de imágenes
     */
    data class ImageLoadConfig(
        val showPlaceholder: Boolean = true,
        val showErrorPlaceholder: Boolean = true,
        val enableRetry: Boolean = true,
        val maxRetries: Int = 2,
        val placeholderRes: Int = R.drawable.ic_image_placeholder,
        val errorPlaceholderRes: Int = R.drawable.ic_image_error,
        val onLoadStart: (() -> Unit)? = null,
        val onLoadSuccess: (() -> Unit)? = null,
        val onLoadError: ((Exception) -> Unit)? = null,
        val onRetryAttempt: ((attempt: Int) -> Unit)? = null
    )

    /**
     * Carga una imagen con manejo completo de errores y fallbacks
     */
    fun loadImageWithFallback(
        context: Context,
        imageView: ImageView,
        imageUrl: String?,
        config: ImageLoadConfig = ImageLoadConfig(),
        coroutineScope: CoroutineScope? = null
    ) {
        if (imageUrl.isNullOrEmpty()) {
            loadPlaceholder(imageView, config.errorPlaceholderRes)
            config.onLoadError?.invoke(Exception("URL de imagen vacía"))
            return
        }

        config.onLoadStart?.invoke()

        val glideRequest = Glide.with(context)
            .load(getImageSource(context, imageUrl))
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.w("ImageLoadingUtil", "Error al cargar imagen: $imageUrl", e)
                    
                    if (config.enableRetry && coroutineScope != null) {
                        handleImageLoadRetry(
                            context = context,
                            imageView = imageView,
                            imageUrl = imageUrl,
                            config = config,
                            coroutineScope = coroutineScope
                        )
                    } else {
                        config.onLoadError?.invoke(e ?: Exception("Error al cargar imagen"))
                    }
                    
                    return false // Permitir que Glide maneje el error placeholder
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d("ImageLoadingUtil", "Imagen cargada exitosamente: $imageUrl")
                    config.onLoadSuccess?.invoke()
                    return false // Permitir que Glide maneje la imagen
                }
            })

        // Configurar placeholder y error
        if (config.showPlaceholder) {
            glideRequest.placeholder(config.placeholderRes)
        }
        
        if (config.showErrorPlaceholder) {
            glideRequest.error(config.errorPlaceholderRes)
        }

        // Aplicar configuraciones adicionales de Glide
        glideRequest
            .centerCrop()
            .into(imageView)
    }

    /**
     * Maneja reintentos automáticos para carga de imágenes fallidas
     */
    private fun handleImageLoadRetry(
        context: Context,
        imageView: ImageView,
        imageUrl: String,
        config: ImageLoadConfig,
        coroutineScope: CoroutineScope
    ) {
        coroutineScope.launch(Dispatchers.Main) {
            val retryManager = RetryManager()
            
            val retryResult = retryManager.executeWithRetry(
                operation = "Carga de imagen: $imageUrl",
                config = RetryManager.imageLoadConfig().copy(maxAttempts = config.maxRetries),
                onRetryAttempt = { attempt, _ ->
                    config.onRetryAttempt?.invoke(attempt)
                }
            ) {
                // Intentar cargar la imagen de nuevo
                loadImageSynchronously(context, imageUrl)
            }

            if (retryResult.result.isSuccess) {
                // Recargar la imagen después del reintento exitoso
                loadImageWithFallback(
                    context = context,
                    imageView = imageView,
                    imageUrl = imageUrl,
                    config = config.copy(enableRetry = false), // Evitar bucle infinito
                    coroutineScope = null
                )
            } else {
                config.onLoadError?.invoke(
                    (retryResult.result.exceptionOrNull() ?: Exception("Error después de reintentos")) as Exception
                )
            }
        }
    }

    /**
     * Carga una imagen de forma síncrona para reintentos
     */
    private fun loadImageSynchronously(context: Context, imageUrl: String): Result<Unit> {
        return try {
            val imageSource = getImageSource(context, imageUrl)
            
            // Verificar que el archivo existe si es local
            if (imageSource is File && !imageSource.exists()) {
                Result.failure(Exception("Archivo de imagen no encontrado: ${imageSource.absolutePath}"))
            } else {
                // Para URLs remotas, intentar una carga simple
                Glide.with(context)
                    .load(imageSource)
                    .submit()
                    .get() // Esto fuerza la carga síncrona
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene la fuente de imagen apropiada (File para rutas locales, String para URLs)
     */
    private fun getImageSource(context: Context, imageUrl: String): Any {
        return if (imageUrl.startsWith("http")) {
            // Es una URL remota
            imageUrl
        } else {
            // Es una ruta local, obtener el archivo completo
            val fullPath = firebaseDataManager.getFullImagePath(context, imageUrl)
            File(fullPath)
        }
    }

    /**
     * Carga un placeholder en el ImageView
     */
    private fun loadPlaceholder(imageView: ImageView, placeholderRes: Int) {
        Glide.with(imageView.context)
            .load(placeholderRes)
            .into(imageView)
    }

    /**
     * Configuraciones predefinidas para diferentes casos de uso
     */
    object Configs {
        fun thumbnail() = ImageLoadConfig(
            placeholderRes = R.drawable.ic_image_placeholder_small,
            errorPlaceholderRes = R.drawable.ic_image_error_small,
            maxRetries = 1
        )
        
        fun fullSize() = ImageLoadConfig(
            placeholderRes = R.drawable.ic_image_placeholder,
            errorPlaceholderRes = R.drawable.ic_image_error,
            maxRetries = 2
        )
        
        fun critical() = ImageLoadConfig(
            enableRetry = true,
            maxRetries = 3,
            placeholderRes = R.drawable.ic_image_placeholder,
            errorPlaceholderRes = R.drawable.ic_image_error
        )
        
        fun silent() = ImageLoadConfig(
            showPlaceholder = false,
            showErrorPlaceholder = false,
            enableRetry = false
        )
    }
}
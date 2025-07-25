package es.monsteraltech.skincare_tfm.camera

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.ZoomState
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controlador de zoom digital mejorado con estabilización para lunares pequeños
 * Implementa zoom suave y estabilización de imagen durante el zoom
 */
class DigitalZoomController(private val lifecycleOwner: LifecycleOwner) {
    
    companion object {
        private const val TAG = "DigitalZoomController"
        private const val MIN_ZOOM_RATIO = 1.0f
        private const val MAX_ZOOM_RATIO = 5.0f
        private const val ZOOM_STEP = 0.1f
        private const val STABILIZATION_THRESHOLD = 2.0f // Activar estabilización a partir de 2x
    }
    
    // Estado del zoom
    private val _currentZoomLevel = MutableStateFlow(MIN_ZOOM_RATIO)
    val currentZoomLevel: StateFlow<Float> = _currentZoomLevel.asStateFlow()
    
    private val _isStabilizationEnabled = MutableStateFlow(false)
    val isStabilizationEnabled: StateFlow<Boolean> = _isStabilizationEnabled.asStateFlow()
    
    private val _maxZoomRatio = MutableStateFlow(MAX_ZOOM_RATIO)
    val maxZoomRatio: StateFlow<Float> = _maxZoomRatio.asStateFlow()
    
    // Referencias de cámara
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var zoomStateObserver: Observer<ZoomState>? = null
    
    /**
     * Inicializa el controlador con la instancia de cámara
     */
    fun initialize(camera: Camera) {
        this.camera = camera
        this.cameraControl = camera.cameraControl
        
        // Observar cambios en el estado del zoom
        zoomStateObserver = Observer { zoomState ->
            val currentRatio = zoomState.zoomRatio
            _currentZoomLevel.value = currentRatio
            
            // Actualizar límite máximo basado en capacidades de la cámara
            _maxZoomRatio.value = minOf(zoomState.maxZoomRatio, MAX_ZOOM_RATIO)
            
            // Activar/desactivar estabilización automáticamente
            val shouldStabilize = currentRatio >= STABILIZATION_THRESHOLD
            if (_isStabilizationEnabled.value != shouldStabilize) {
                _isStabilizationEnabled.value = shouldStabilize
                Log.d(TAG, "Estabilización ${if (shouldStabilize) "activada" else "desactivada"} en zoom ${currentRatio}x")
            }
        }
        
        camera.cameraInfo.zoomState.observe(lifecycleOwner, zoomStateObserver!!)
        Log.d(TAG, "DigitalZoomController inicializado")
    }
    
    /**
     * Establece el nivel de zoom con transición suave
     */
    fun setZoomLevel(level: Float): Boolean {
        val cameraControl = this.cameraControl ?: return false
        
        val clampedLevel = level.coerceIn(MIN_ZOOM_RATIO, _maxZoomRatio.value)
        
        return try {
            val future = cameraControl.setZoomRatio(clampedLevel)
            Log.d(TAG, "Zoom establecido a ${clampedLevel}x")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error estableciendo zoom", e)
            false
        }
    }
    
    /**
     * Incrementa el zoom gradualmente
     */
    fun zoomIn(): Boolean {
        val newLevel = (_currentZoomLevel.value + ZOOM_STEP).coerceAtMost(_maxZoomRatio.value)
        return setZoomLevel(newLevel)
    }
    
    /**
     * Decrementa el zoom gradualmente
     */
    fun zoomOut(): Boolean {
        val newLevel = (_currentZoomLevel.value - ZOOM_STEP).coerceAtLeast(MIN_ZOOM_RATIO)
        return setZoomLevel(newLevel)
    }
    
    /**
     * Resetea el zoom al nivel mínimo
     */
    fun resetZoom(): Boolean {
        return setZoomLevel(MIN_ZOOM_RATIO)
    }
    
    /**
     * Establece el zoom a un nivel óptimo para lunares pequeños (3x)
     */
    fun setOptimalZoomForSmallMoles(): Boolean {
        val optimalZoom = 3.0f.coerceAtMost(_maxZoomRatio.value)
        return setZoomLevel(optimalZoom)
    }
    
    /**
     * Habilita o deshabilita la estabilización manual
     */
    fun enableStabilization(enabled: Boolean) {
        _isStabilizationEnabled.value = enabled
        Log.d(TAG, "Estabilización ${if (enabled) "habilitada" else "deshabilitada"} manualmente")
    }
    
    /**
     * Obtiene el nivel de zoom actual
     */
    fun getCurrentZoomLevel(): Float {
        return _currentZoomLevel.value
    }
    
    /**
     * Verifica si el zoom está en un nivel óptimo para lunares pequeños
     */
    fun isOptimalForSmallMoles(): Boolean {
        return _currentZoomLevel.value >= 2.5f
    }
    
    /**
     * Obtiene información del estado actual del zoom
     */
    fun getZoomInfo(): ZoomInfo {
        return ZoomInfo(
            currentLevel = _currentZoomLevel.value,
            minLevel = MIN_ZOOM_RATIO,
            maxLevel = _maxZoomRatio.value,
            isStabilizationActive = _isStabilizationEnabled.value,
            isOptimalForSmallMoles = isOptimalForSmallMoles()
        )
    }
    
    /**
     * Limpia recursos cuando se destruye el controlador
     */
    fun cleanup() {
        zoomStateObserver?.let { observer ->
            camera?.cameraInfo?.zoomState?.removeObserver(observer)
        }
        camera = null
        cameraControl = null
        zoomStateObserver = null
        Log.d(TAG, "DigitalZoomController limpiado")
    }
    
    /**
     * Clase de datos para información del zoom
     */
    data class ZoomInfo(
        val currentLevel: Float,
        val minLevel: Float,
        val maxLevel: Float,
        val isStabilizationActive: Boolean,
        val isOptimalForSmallMoles: Boolean
    )
}
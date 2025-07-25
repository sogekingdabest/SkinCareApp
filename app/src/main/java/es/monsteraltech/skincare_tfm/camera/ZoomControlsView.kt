package es.monsteraltech.skincare_tfm.camera

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import es.monsteraltech.skincare_tfm.R
import kotlinx.coroutines.flow.StateFlow

/**
 * Vista de controles de zoom digital integrada para la interfaz de cámara
 * Proporciona controles intuitivos para zoom con indicadores visuales
 */
class ZoomControlsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    companion object {
        private const val TAG = "ZoomControlsView"
        private const val SEEKBAR_MAX = 100
    }
    
    // Componentes UI
    private lateinit var zoomInButton: ImageButton
    private lateinit var zoomOutButton: ImageButton
    private lateinit var zoomResetButton: ImageButton
    private lateinit var zoomOptimalButton: ImageButton
    private lateinit var zoomSeekBar: SeekBar
    private lateinit var zoomLevelText: TextView
    private lateinit var stabilizationIndicator: View
    
    // Listener para eventos de zoom
    private var zoomListener: ZoomControlListener? = null
    
    // Estado actual
    private var currentZoomInfo: DigitalZoomController.ZoomInfo? = null
    
    init {
        initializeView()
        setupClickListeners()
    }
    
    private fun initializeView() {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.view_zoom_controls, this, true)
        
        // Inicializar componentes
        zoomOutButton = findViewById(R.id.zoom_out_button)
        zoomInButton = findViewById(R.id.zoom_in_button)
        zoomResetButton = findViewById(R.id.zoom_reset_button)
        zoomOptimalButton = findViewById(R.id.zoom_optimal_button)
        zoomSeekBar = findViewById(R.id.zoom_seekbar)
        zoomLevelText = findViewById(R.id.zoom_level_text)
        stabilizationIndicator = findViewById(R.id.stabilization_indicator)
        
        // Configurar SeekBar
        zoomSeekBar.max = SEEKBAR_MAX
        zoomSeekBar.progress = 0
        
        // Configurar accesibilidad
        setupAccessibility()
    }
    
    private fun setupClickListeners() {
        zoomInButton.setOnClickListener {
            zoomListener?.onZoomIn()
        }
        
        zoomOutButton.setOnClickListener {
            zoomListener?.onZoomOut()
        }
        
        zoomResetButton.setOnClickListener {
            zoomListener?.onZoomReset()
        }
        
        zoomOptimalButton.setOnClickListener {
            zoomListener?.onZoomOptimal()
        }
        
        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val zoomInfo = currentZoomInfo ?: return
                    val zoomRange = zoomInfo.maxLevel - zoomInfo.minLevel
                    val zoomLevel = zoomInfo.minLevel + (progress.toFloat() / SEEKBAR_MAX) * zoomRange
                    zoomListener?.onZoomLevelChanged(zoomLevel)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun setupAccessibility() {
        zoomInButton.contentDescription = "Aumentar zoom"
        zoomOutButton.contentDescription = "Disminuir zoom"
        zoomResetButton.contentDescription = "Resetear zoom a 1x"
        zoomOptimalButton.contentDescription = "Zoom óptimo para lunares pequeños"
        zoomSeekBar.contentDescription = "Control deslizante de zoom"
        stabilizationIndicator.contentDescription = "Indicador de estabilización activa"
    }
    
    /**
     * Actualiza la vista con nueva información de zoom
     */
    fun updateZoomInfo(zoomInfo: DigitalZoomController.ZoomInfo) {
        currentZoomInfo = zoomInfo
        
        // Actualizar texto del nivel de zoom
        zoomLevelText.text = String.format("%.1fx", zoomInfo.currentLevel)
        
        // Actualizar SeekBar
        val zoomRange = zoomInfo.maxLevel - zoomInfo.minLevel
        val normalizedProgress = ((zoomInfo.currentLevel - zoomInfo.minLevel) / zoomRange * SEEKBAR_MAX).toInt()
        zoomSeekBar.progress = normalizedProgress
        
        // Actualizar estado de botones
        zoomOutButton.isEnabled = zoomInfo.currentLevel > zoomInfo.minLevel
        zoomInButton.isEnabled = zoomInfo.currentLevel < zoomInfo.maxLevel
        
        // Actualizar indicador de estabilización
        stabilizationIndicator.visibility = if (zoomInfo.isStabilizationActive) View.VISIBLE else View.GONE
        
        // Resaltar botón óptimo si está en uso
        val isOptimal = zoomInfo.isOptimalForSmallMoles
        val optimalColor = if (isOptimal) {
            ContextCompat.getColor(context, R.color.risk_very_low)
        } else {
            ContextCompat.getColor(context, R.color.guidance_overlay_background)
        }
        zoomOptimalButton.backgroundTintList = android.content.res.ColorStateList.valueOf(optimalColor)
        
        // Actualizar accesibilidad
        updateAccessibilityInfo(zoomInfo)
    }
    
    private fun updateAccessibilityInfo(zoomInfo: DigitalZoomController.ZoomInfo) {
        val zoomDescription = "Zoom actual: ${String.format("%.1fx", zoomInfo.currentLevel)}"
        zoomLevelText.contentDescription = zoomDescription
        
        val stabilizationStatus = if (zoomInfo.isStabilizationActive) "activada" else "desactivada"
        stabilizationIndicator.contentDescription = "Estabilización $stabilizationStatus"
        
        val optimalStatus = if (zoomInfo.isOptimalForSmallMoles) "activo" else "inactivo"
        zoomOptimalButton.contentDescription = "Zoom óptimo para lunares pequeños - $optimalStatus"
    }
    
    /**
     * Establece el listener para eventos de zoom
     */
    fun setZoomControlListener(listener: ZoomControlListener) {
        this.zoomListener = listener
    }
    
    /**
     * Muestra u oculta los controles de zoom
     */
    fun setControlsVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }
    
    /**
     * Habilita o deshabilita todos los controles
     */
    fun setControlsEnabled(enabled: Boolean) {
        zoomInButton.isEnabled = enabled && (currentZoomInfo?.currentLevel ?: 1f) < (currentZoomInfo?.maxLevel ?: 5f)
        zoomOutButton.isEnabled = enabled && (currentZoomInfo?.currentLevel ?: 1f) > (currentZoomInfo?.minLevel ?: 1f)
        zoomResetButton.isEnabled = enabled
        zoomOptimalButton.isEnabled = enabled
        zoomSeekBar.isEnabled = enabled
    }
    
    /**
     * Interface para eventos de control de zoom
     */
    interface ZoomControlListener {
        fun onZoomIn()
        fun onZoomOut()
        fun onZoomReset()
        fun onZoomOptimal()
        fun onZoomLevelChanged(level: Float)
    }
}
package es.monsteraltech.skincare_tfm.camera.guidance

import android.content.Context
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Gestor de accesibilidad que proporciona soporte para TalkBack y otras tecnologías de asistencia.
 * Maneja descripciones de contenido, anuncios de voz y navegación accesible.
 */
class AccessibilityManager(private val context: Context) {

    companion object {
        private const val TAG = "AccessibilityManager"
    }

    private val accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as
                    AccessibilityManager
    private var lastAnnouncementTime = 0L
    private val announcementThrottleMs = 1000L // Evitar anuncios excesivos

    /** Verifica si los servicios de accesibilidad están habilitados */
    fun isAccessibilityEnabled(): Boolean {
        return accessibilityManager.isEnabled
    }

    /** Verifica si TalkBack está activo */
    fun isTalkBackEnabled(): Boolean {
        return accessibilityManager.isEnabled && accessibilityManager.isTouchExplorationEnabled
    }

    /** Anuncia un mensaje usando TalkBack */
    fun announceForAccessibility(
            message: String,
            priority: Int = AccessibilityEvent.TYPE_ANNOUNCEMENT
    ) {
        if (!canMakeAnnouncement()) {
            return
        }

        Log.d(TAG, "Anunciando para accesibilidad: $message")

        // Usar solo TalkBack para anuncios de accesibilidad
        if (isTalkBackEnabled()) {
            val event = AccessibilityEvent.obtain(priority)
            event.text.add(message)
            event.className = this::class.java.name
            event.packageName = context.packageName
            accessibilityManager.sendAccessibilityEvent(event)
        }

        lastAnnouncementTime = System.currentTimeMillis()
    }

    /** Proporciona descripción de accesibilidad para estados de guía */
    fun getAccessibilityDescriptionForState(state: CaptureValidationManager.GuideState): String {
        return when (state) {
            CaptureValidationManager.GuideState.SEARCHING ->
                    "Buscando lunar. Mueve la cámara para encontrar un lunar en el área circular."
            CaptureValidationManager.GuideState.CENTERING ->
                    "Lunar detectado. Centra el lunar en el círculo guía para continuar."
            CaptureValidationManager.GuideState.TOO_FAR ->
                    "Lunar muy lejos. Acércate más al lunar para obtener mejor detalle."
            CaptureValidationManager.GuideState.TOO_CLOSE ->
                    "Lunar muy cerca. Aléjate un poco para capturar el lunar completo."
            CaptureValidationManager.GuideState.POOR_LIGHTING ->
                    "Iluminación inadecuada. Busca mejor luz o ajusta la posición."
            CaptureValidationManager.GuideState.BLURRY ->
                    "Imagen borrosa. Mantén la cámara firme y asegúrate de que esté enfocada."
            CaptureValidationManager.GuideState.READY ->
                    "Listo para capturar. El lunar está correctamente posicionado y la calidad es buena. Toca el botón de captura."
        }
    }

    /** Configura la accesibilidad para una vista */
    fun setupViewAccessibility(view: View, description: String, hint: String? = null) {
        ViewCompat.setAccessibilityDelegate(
                view,
                object : androidx.core.view.AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                            host: View,
                            info: AccessibilityNodeInfoCompat
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.contentDescription = description
                        hint?.let { info.hintText = it }
                    }
                }
        )
    }

    /** Configura accesibilidad para el overlay de guía */
    fun setupGuidanceOverlayAccessibility(
            overlay: View,
            state: CaptureValidationManager.GuideState,
            moleDetected: Boolean = false
    ) {
        val description = buildString {
            append("Guía de captura de lunar. ")
            append(getAccessibilityDescriptionForState(state))

            if (moleDetected) {
                append(" Lunar detectado en pantalla.")
            }
        }

        val hint =
                when (state) {
                    CaptureValidationManager.GuideState.READY -> "Toca dos veces para capturar"
                    else -> "Ajusta la posición de la cámara según las instrucciones"
                }

        setupViewAccessibility(overlay, description, hint)

        // Anunciar cambios de estado
        announceForAccessibility(getAccessibilityDescriptionForState(state))
    }

    /** Configura accesibilidad para el botón de captura */
    fun setupCaptureButtonAccessibility(
            button: View,
            canCapture: Boolean,
            state: CaptureValidationManager.GuideState
    ) {
        val description =
                if (canCapture) {
                    "Botón de captura habilitado. Toca para capturar la imagen del lunar."
                } else {
                    "Botón de captura deshabilitado. ${getAccessibilityDescriptionForState(state)}"
                }

        val hint =
                if (canCapture) {
                    "Toca dos veces para capturar"
                } else {
                    "Ajusta la posición según las instrucciones para habilitar la captura"
                }

        setupViewAccessibility(button, description, hint)
    }

    /** Anuncia el progreso de centrado del lunar */
    fun announceCenteringProgress(centeringPercentage: Float) {
        if (!canMakeAnnouncement()) return

        val message =
                when {
                    centeringPercentage >= 90f -> "Lunar casi perfectamente centrado"
                    centeringPercentage >= 70f -> "Lunar bien centrado"
                    centeringPercentage >= 50f -> "Lunar parcialmente centrado"
                    else -> "Lunar necesita ser centrado"
                }

        announceForAccessibility(message)
    }

    /** Anuncia el progreso de distancia del lunar */
    fun announceDistanceProgress(sizePercentage: Float) {
        if (!canMakeAnnouncement()) return

        val message =
                when {
                    sizePercentage >= 90f -> "Distancia del lunar óptima"
                    sizePercentage >= 70f -> "Distancia del lunar buena"
                    sizePercentage >= 50f -> "Ajusta ligeramente la distancia"
                    else -> "Distancia del lunar necesita ajuste"
                }

        announceForAccessibility(message)
    }

    /** Proporciona instrucciones de navegación por gestos */
    fun announceGestureInstructions() {
        if (!isTalkBackEnabled()) return

        val instructions = buildString {
            append("Instrucciones de navegación: ")
            append("Desliza hacia arriba y abajo para explorar los controles. ")
            append("Toca dos veces para activar botones. ")
            append("Usa gestos de TalkBack para navegar por la pantalla.")
        }

        announceForAccessibility(instructions)
    }

    /** Anuncia el resultado de la captura */
    fun announceCaptureResult(success: Boolean, message: String? = null) {
        val announcement =
                if (success) {
                    "Captura exitosa. ${message ?: "Imagen guardada correctamente."}"
                } else {
                    "Error en la captura. ${message ?: "Inténtalo de nuevo."}"
                }

        announceForAccessibility(announcement)
    }

    /** Verifica si se puede hacer un anuncio (throttling) */
    private fun canMakeAnnouncement(): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - lastAnnouncementTime >= announcementThrottleMs
    }

    /** Configura el modo de exploración táctil */
    fun configureTouchExploration(view: View, enabled: Boolean) {
        view.importantForAccessibility =
                if (enabled) {
                    View.IMPORTANT_FOR_ACCESSIBILITY_YES
                } else {
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
    }

    /** Proporciona retroalimentación de navegación */
    fun provideNavigationFeedback(direction: String) {
        val message = "Navegando $direction"
        announceForAccessibility(message)
    }

    /** Limpia recursos */
    fun cleanup() {
        Log.d(TAG, "AccessibilityManager limpiado")
    }
}

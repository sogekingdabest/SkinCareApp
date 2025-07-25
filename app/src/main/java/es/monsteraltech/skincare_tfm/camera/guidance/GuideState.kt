package es.monsteraltech.skincare_tfm.camera.guidance

/**
 * Estados posibles de la guía de captura
 */
enum class GuideState {
    /**
     * Buscando lunar en la imagen
     */
    SEARCHING,
    
    /**
     * Lunar detectado, necesita ser centrado
     */
    CENTERING,
    
    /**
     * Usuario está muy lejos del lunar
     */
    TOO_FAR,
    
    /**
     * Usuario está muy cerca del lunar
     */
    TOO_CLOSE,
    
    /**
     * Condiciones de iluminación inadecuadas
     */
    POOR_LIGHTING,
    
    /**
     * Imagen borrosa o desenfocada
     */
    BLURRY,
    
    /**
     * Listo para capturar la imagen
     */
    READY
}
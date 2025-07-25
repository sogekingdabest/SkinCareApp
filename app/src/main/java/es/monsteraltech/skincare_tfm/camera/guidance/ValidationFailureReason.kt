package es.monsteraltech.skincare_tfm.camera.guidance

/**
 * Razones por las cuales la validación de captura puede fallar
 */
enum class ValidationFailureReason {
    /**
     * El lunar no está centrado en la guía
     */
    NOT_CENTERED,
    
    /**
     * El usuario está muy lejos del lunar
     */
    TOO_FAR,
    
    /**
     * El usuario está muy cerca del lunar
     */
    TOO_CLOSE,
    
    /**
     * La imagen está borrosa o desenfocada
     */
    BLURRY,
    
    /**
     * Las condiciones de iluminación son inadecuadas
     */
    POOR_LIGHTING,
    
    /**
     * No se detectó ningún lunar en la imagen
     */
    NO_MOLE_DETECTED
}
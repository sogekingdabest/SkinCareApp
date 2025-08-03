package es.monsteraltech.skincare_tfm.analysis

/**
 * Enum que define las etapas del procesamiento de análisis de imagen con sus pesos relativos
 */
enum class ProcessingStage(val message: String, val weight: Int) {
    /**
     * Etapa inicial - preparando el análisis
     */
    INITIALIZING("Preparando análisis...", 10),
    
    /**
     * Preprocesamiento de la imagen
     */
    PREPROCESSING("Procesando imagen...", 20),
    
    /**
     * Análisis con inteligencia artificial
     */
    AI_ANALYSIS("Analizando con IA...", 40),
    
    /**
     * Análisis usando criterios ABCDE
     */
    ABCDE_ANALYSIS("Aplicando criterios ABCDE...", 25),
    
    /**
     * Finalizando y combinando resultados
     */
    FINALIZING("Finalizando resultados...", 5);
    
    /**
     * Calcula el progreso acumulado hasta esta etapa (sin incluir la etapa actual)
     */
    fun getProgressUpToStage(): Int {
        return values().takeWhile { it != this }.sumOf { it.weight }
    }
    
    /**
     * Calcula el progreso total incluyendo esta etapa
     */
    fun getProgressIncludingStage(): Int {
        return getProgressUpToStage() + weight
    }
}
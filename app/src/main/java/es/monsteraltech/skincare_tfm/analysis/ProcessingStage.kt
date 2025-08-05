package es.monsteraltech.skincare_tfm.analysis
enum class ProcessingStage(val message: String, val weight: Int) {
    INITIALIZING("Preparando análisis...", 10),
    PREPROCESSING("Procesando imagen...", 20),
    AI_ANALYSIS("Analizando con IA...", 40),
    ABCDE_ANALYSIS("Aplicando criterios ABCDE...", 25),
    FINALIZING("Finalizando resultados...", 5);
    fun getProgressUpToStage(): Int {
        return values().takeWhile { it != this }.sumOf { it.weight }
    }
    fun getProgressIncludingStage(): Int {
        return getProgressUpToStage() + weight
    }
}
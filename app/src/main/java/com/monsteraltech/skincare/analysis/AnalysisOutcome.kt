package com.monsteraltech.skincare.analysis

/** Only a complete outcome can be combined or saved as an analysis. */
sealed interface AnalysisOutcome {
    data class Complete(val result: MelanomaAIDetector.CombinedAnalysisResult) : AnalysisOutcome

    data class Partial(
        val ai: AnalysisSignal<MelanomaAIDetector.AIInferenceResult>,
        val abcde: AnalysisSignal<ABCDEAnalyzerOpenCV.ABCDEResult>
    ) : AnalysisOutcome {
        init {
            require((ai is AnalysisSignal.Available) != (abcde is AnalysisSignal.Available))
        }
    }

    data class Failed(val error: AnalysisError) : AnalysisOutcome
}

sealed interface AnalysisSignal<out T> {
    data class Available<T>(val value: T) : AnalysisSignal<T>
    data class Unavailable(val reason: Reason, val message: String) : AnalysisSignal<Nothing>

    enum class Reason { DISABLED, TIMEOUT, ERROR }
}

data class AnalysisProgress(val stage: ProcessingStage, val progress: Int, val message: String)

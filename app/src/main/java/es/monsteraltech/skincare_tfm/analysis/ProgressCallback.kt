package es.monsteraltech.skincare_tfm.analysis
interface ProgressCallback {
    fun onProgressUpdate(progress: Int, message: String)
    fun onProgressUpdateWithTimeEstimate(progress: Int, message: String, estimatedTotalTimeMs: Long)
    fun onStageChanged(stage: ProcessingStage)
    fun onError(error: String)
    fun onCompleted(result: MelanomaAIDetector.CombinedAnalysisResult)
    fun onCancelled()
}
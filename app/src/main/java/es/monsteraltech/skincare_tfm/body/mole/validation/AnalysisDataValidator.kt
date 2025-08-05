package es.monsteraltech.skincare_tfm.body.mole.validation
import es.monsteraltech.skincare_tfm.body.mole.model.ABCDEScores
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
object AnalysisDataValidator {
    fun validateAnalysisData(analysis: AnalysisData): ValidationResult {
        val errors = mutableListOf<String>()
        errors.addAll(validateRequiredFields(analysis))
        errors.addAll(validateValueRanges(analysis))
        errors.addAll(validateRiskLevel(analysis.riskLevel))
        errors.addAll(validateABCDEScores(analysis.abcdeScores))
        errors.addAll(validateMetadata(analysis.analysisMetadata))
        return ValidationResult(errors.isEmpty(), errors)
    }
    private fun validateRequiredFields(analysis: AnalysisData): List<String> {
        val errors = mutableListOf<String>()
        if (analysis.moleId.isBlank()) {
            errors.add("ID de lunar requerido")
        }
        if (analysis.analysisResult.isBlank()) {
            errors.add("Resultado de análisis requerido")
        }
        if (analysis.riskLevel.isBlank()) {
            errors.add("Nivel de riesgo requerido")
        }
        if (analysis.recommendation.isBlank()) {
            errors.add("Recomendación requerida")
        }
        return errors
    }
    private fun validateValueRanges(analysis: AnalysisData): List<String> {
        val errors = mutableListOf<String>()
        if (analysis.aiProbability < 0f || analysis.aiProbability > 1f) {
            errors.add("Probabilidad de IA debe estar entre 0 y 1")
        }
        if (analysis.aiConfidence < 0f || analysis.aiConfidence > 1f) {
            errors.add("Confianza de IA debe estar entre 0 y 1")
        }
        if (analysis.combinedScore < 0f) {
            errors.add("Puntuación combinada no puede ser negativa")
        }
        return errors
    }
    private fun validateRiskLevel(riskLevel: String): List<String> {
        val validRiskLevels = listOf("VERY_LOW","LOW", "MEDIUM", "HIGH", "VERY_HIGH")
        return if (riskLevel.uppercase() !in validRiskLevels) {
            listOf("Nivel de riesgo debe ser uno de: ${validRiskLevels.joinToString(", ")}")
        } else {
            emptyList()
        }
    }
    private fun validateABCDEScores(scores: ABCDEScores): List<String> {
        val errors = mutableListOf<String>()
        if (scores.asymmetryScore < 0f || scores.asymmetryScore > 10f) {
            errors.add("Score de asimetría debe estar entre 0 y 10")
        }
        if (scores.borderScore < 0f || scores.borderScore > 10f) {
            errors.add("Score de borde debe estar entre 0 y 10")
        }
        if (scores.colorScore < 0f || scores.colorScore > 10f) {
            errors.add("Score de color debe estar entre 0 y 10")
        }
        if (scores.diameterScore < 0f || scores.diameterScore > 10f) {
            errors.add("Score de diámetro debe estar entre 0 y 10")
        }
        scores.evolutionScore?.let { evolutionScore ->
            if (evolutionScore < 0f || evolutionScore > 10f) {
                errors.add("Score de evolución debe estar entre 0 y 10")
            }
        }
        if (scores.totalScore < 0f) {
            errors.add("Score total no puede ser negativo")
        }
        return errors
    }
    private fun validateMetadata(metadata: Map<String, Any>): List<String> {
        val errors = mutableListOf<String>()
        if (metadata.size > 20) {
            errors.add("Demasiados campos de metadatos (máximo 20)")
        }
        metadata.forEach { (key, value) ->
            if (key.length > 100) {
                errors.add("Clave de metadato demasiado larga: $key")
            }
            when (value) {
                is String -> {
                    if (value.length > 1000) {
                        errors.add("Valor de metadato demasiado largo para clave: $key")
                    }
                }
                is Number -> {
                }
                is Boolean -> {
                }
                else -> {
                    if (value.toString().length > 1000) {
                        errors.add("Valor de metadato demasiado largo para clave: $key")
                    }
                }
            }
        }
        return errors
    }
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>
    ) {
        fun getErrorMessage(): String {
            return errors.joinToString("; ")
        }
    }
}
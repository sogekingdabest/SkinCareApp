package es.monsteraltech.skincare_tfm.body.mole.validation

import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData

/**
 * Utilidad para sanitización de datos de análisis
 * Previene inyecciones y limpia datos potencialmente maliciosos
 */
object AnalysisDataSanitizer {

    /**
     * Sanitiza completamente los datos de análisis
     */
    fun sanitizeAnalysisData(analysis: AnalysisData): AnalysisData {
        return analysis.copy(
            analysisResult = sanitizeText(analysis.analysisResult),
            riskLevel = sanitizeRiskLevel(analysis.riskLevel),
            recommendation = sanitizeText(analysis.recommendation),
            imageUrl = sanitizeUrl(analysis.imageUrl),
            analysisMetadata = sanitizeMetadata(analysis.analysisMetadata)
        )
    }

    /**
     * Sanitiza texto eliminando caracteres potencialmente peligrosos
     */
    private fun sanitizeText(text: String): String {
        return text.trim()
            .replace(Regex("[<>\"'&]"), "") // Eliminar caracteres HTML/XML peligrosos
            .replace(Regex("\\s+"), " ") // Normalizar espacios en blanco
            .take(5000) // Limitar longitud máxima
    }

    /**
     * Sanitiza el nivel de riesgo
     */
    private fun sanitizeRiskLevel(riskLevel: String): String {
        val sanitized = riskLevel.uppercase().trim()
        val validLevels = listOf("LOW", "MODERATE", "HIGH")
        return if (sanitized in validLevels) sanitized else "LOW"
    }

    /**
     * Sanitiza URLs
     */
    private fun sanitizeUrl(url: String): String {
        val trimmedUrl = url.trim()
        return when {
            trimmedUrl.isEmpty() -> ""
            trimmedUrl.startsWith("http://") || 
            trimmedUrl.startsWith("https://") || 
            trimmedUrl.startsWith("gs://") -> trimmedUrl.take(500)
            else -> "" // URL inválida, devolver vacío
        }
    }

    /**
     * Sanitiza metadatos eliminando valores potencialmente peligrosos
     */
    private fun sanitizeMetadata(metadata: Map<String, Any>): Map<String, Any> {
        return metadata
            .toList()
            .take(20) // Limitar número de campos
            .map { (key, value) -> 
                sanitizeMetadataKey(key) to sanitizeMetadataValue(value)
            }
            .toMap()
            .filterKeys { it.isNotBlank() } // Eliminar claves vacías
    }

    /**
     * Sanitiza una clave de metadato
     */
    private fun sanitizeMetadataKey(key: String): String {
        return key.trim()
            .replace(Regex("[^a-zA-Z0-9_-]"), "") // Solo permitir caracteres alfanuméricos, guiones y guiones bajos
            .take(100)
    }

    /**
     * Sanitiza un valor de metadato
     */
    private fun sanitizeMetadataValue(value: Any): Any {
        return when (value) {
            is String -> sanitizeText(value).take(1000)
            is Number -> {
                // Validar que el número esté en un rango razonable
                when (value) {
                    is Float -> value.coerceIn(-1000f, 1000f)
                    is Double -> value.coerceIn(-1000.0, 1000.0)
                    is Int -> value.coerceIn(-1000, 1000)
                    is Long -> value.coerceIn(-1000L, 1000L)
                    else -> value
                }
            }
            is Boolean -> value
            else -> sanitizeText(value.toString()).take(1000)
        }
    }
}
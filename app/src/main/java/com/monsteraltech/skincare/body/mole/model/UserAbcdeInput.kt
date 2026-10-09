package com.monsteraltech.skincare.body.mole.model

import kotlin.math.abs

/**
 * Valores ABCDE introducidos manualmente por el usuario para contrastarlos
 * con el análisis automático. Lógica pura (sin dependencias de Android),
 * compartida por las pantallas de resultado y de detalle del lunar.
 */
data class UserAbcdeInput(
    val asymmetry: Float = 0f,
    val border: Float = 0f,
    val color: Float = 0f,
    val diameter: Float = 0f,
    val evolution: Float = 0f
) {
    /**
     * Score total con la misma ponderación que usa el análisis automático:
     * la evolución actúa como multiplicador sobre el resto de criterios.
     */
    fun total(): Float {
        var total = (asymmetry * 1.3f) + (border * 0.1f) + (color * 0.5f) + (diameter * 0.5f)
        if (evolution > 0f) {
            total *= (1 + evolution * 0.2f)
        }
        return total
    }

    /** Claves de metadatos con las que se persiste en el historial local. */
    fun toMetadataMap(): Map<String, Any> = mapOf(
        "userAsymmetry" to asymmetry,
        "userBorder" to border,
        "userColor" to color,
        "userDiameter" to diameter,
        "userEvolution" to evolution,
        "userTotal" to total()
    )

    companion object {
        enum class ComparisonBucket { SIMILAR, SOMEWHAT_SIMILAR, SOME_DIFFERENCES, VERY_DIFFERENT }

        fun difference(aiTotal: Float, userTotal: Float): Float = abs(aiTotal - userTotal)

        fun comparisonBucket(aiTotal: Float, userTotal: Float): ComparisonBucket {
            val difference = difference(aiTotal, userTotal)
            return when {
                difference < 1.0f -> ComparisonBucket.SIMILAR
                difference < 3.0f -> ComparisonBucket.SOMEWHAT_SIMILAR
                difference < 5.0f -> ComparisonBucket.SOME_DIFFERENCES
                else -> ComparisonBucket.VERY_DIFFERENT
            }
        }

        /** Reconstruye los valores del usuario desde los metadatos persistidos, si existen. */
        fun fromMetadata(metadata: Map<String, Any>): UserAbcdeInput? {
            val asymmetry = (metadata["userAsymmetry"] as? Number)?.toFloat()
            val border = (metadata["userBorder"] as? Number)?.toFloat()
            val color = (metadata["userColor"] as? Number)?.toFloat()
            val diameter = (metadata["userDiameter"] as? Number)?.toFloat()
            val evolution = (metadata["userEvolution"] as? Number)?.toFloat()
            val hasAnyValue = asymmetry != null || border != null || color != null ||
                    diameter != null || evolution != null
            if (!hasAnyValue) return null
            return UserAbcdeInput(
                asymmetry = asymmetry ?: 0f,
                border = border ?: 0f,
                color = color ?: 0f,
                diameter = diameter ?: 0f,
                evolution = evolution ?: 0f
            )
        }
    }
}

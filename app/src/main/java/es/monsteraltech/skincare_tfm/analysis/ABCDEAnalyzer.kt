// ABCDEAnalyzer.kt - Sistema de análisis ABCDE para melanomas
package es.monsteraltech.skincare_tfm.analysis

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Analizador ABCDE para evaluación de lunares según criterios dermatológicos
 * Combina análisis tradicional con predicción de IA
 */
class ABCDEAnalyzer {

    companion object {
        // Umbrales basados en literatura médica
        const val ASYMMETRY_THRESHOLD = 0.15f  // 15% de diferencia
        const val BORDER_IRREGULARITY_THRESHOLD = 0.25f
        const val COLOR_VARIANCE_THRESHOLD = 50f
        const val DIAMETER_THRESHOLD_MM = 6f  // 6mm
        const val PIXEL_TO_MM_RATIO = 0.1f  // Calibrar según dispositivo
    }

    /**
     * Resultado del análisis ABCDE
     */
    data class ABCDEResult(
        val asymmetryScore: Float,      // 0-2 (0=simétrico, 2=muy asimétrico)
        val borderScore: Float,         // 0-8 (0=regular, 8=muy irregular)
        val colorScore: Float,          // 1-6 (número de colores detectados)
        val diameterScore: Float,       // 0-5 (basado en tamaño en mm)
        val evolutionScore: Float?,     // 0-3 (requiere imagen previa)
        val totalScore: Float,          // Suma ponderada
        val riskLevel: RiskLevel,
        val details: ABCDEDetails
    )

    data class ABCDEDetails(
        val asymmetryDetails: AsymmetryDetails,
        val borderDetails: BorderDetails,
        val colorDetails: ColorDetails,
        val diameterDetails: DiameterDetails,
        val evolutionDetails: EvolutionDetails?
    )

    data class AsymmetryDetails(
        val horizontalAsymmetry: Float,
        val verticalAsymmetry: Float,
        val description: String
    )

    data class BorderDetails(
        val irregularityIndex: Float,
        val numberOfSegments: Int,
        val description: String
    )

    data class ColorDetails(
        val dominantColors: List<Int>,
        val colorCount: Int,
        val hasBlueWhite: Boolean,
        val hasRedBlueCombination: Boolean,
        val description: String
    )

    data class DiameterDetails(
        val diameterMm: Float,
        val areaPx: Int,
        val description: String
    )

    data class EvolutionDetails(
        val sizeChange: Float,
        val colorChange: Float,
        val shapeChange: Float,
        val description: String
    )

    enum class RiskLevel {
        LOW,        // Score < 4.75
        MODERATE,   // Score 4.75-6.8
        HIGH        // Score > 6.8
    }

    /**
     * Analiza un lunar usando criterios ABCDE
     */
    fun analyzeMole(
        bitmap: Bitmap,
        previousBitmap: Bitmap? = null,
        pixelDensity: Float = 1.0f
    ): ABCDEResult {
        // Pre-procesar imagen
        val processedBitmap = preprocessImage(bitmap)
        val moleMask = segmentMole(processedBitmap)

        // A - Asimetría
        val asymmetryDetails = analyzeAsymmetry(processedBitmap, moleMask)
        val asymmetryScore = calculateAsymmetryScore(asymmetryDetails)

        // B - Bordes
        val borderDetails = analyzeBorder(moleMask)
        val borderScore = calculateBorderScore(borderDetails)

        // C - Color
        val colorDetails = analyzeColor(processedBitmap, moleMask)
        val colorScore = calculateColorScore(colorDetails)

        // D - Diámetro
        val diameterDetails = analyzeDiameter(moleMask, pixelDensity)
        val diameterScore = calculateDiameterScore(diameterDetails)

        // E - Evolución (si hay imagen previa)
        val evolutionDetails = previousBitmap?.let {
            analyzeEvolution(processedBitmap, it)
        }
        val evolutionScore = evolutionDetails?.let {
            calculateEvolutionScore(it)
        }

        // Calcular score total (TDS - Total Dermoscopy Score modificado)
        val totalScore = calculateTotalScore(
            asymmetryScore, borderScore, colorScore, diameterScore, evolutionScore
        )

        // Determinar nivel de riesgo
        val riskLevel = when {
            totalScore < 4.75f -> RiskLevel.LOW
            totalScore < 6.8f -> RiskLevel.MODERATE
            else -> RiskLevel.HIGH
        }

        return ABCDEResult(
            asymmetryScore = asymmetryScore,
            borderScore = borderScore,
            colorScore = colorScore,
            diameterScore = diameterScore,
            evolutionScore = evolutionScore,
            totalScore = totalScore,
            riskLevel = riskLevel,
            details = ABCDEDetails(
                asymmetryDetails = asymmetryDetails,
                borderDetails = borderDetails,
                colorDetails = colorDetails,
                diameterDetails = diameterDetails,
                evolutionDetails = evolutionDetails
            )
        )
    }

    /**
     * Pre-procesa la imagen para mejorar el análisis
     */
    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        // Aplicar filtros para mejorar contraste y reducir ruido
        // Implementación simplificada - en producción usar OpenCV
        return bitmap // Por ahora retornar original
    }

    /**
     * Segmenta el lunar del fondo
     */
    private fun segmentMole(bitmap: Bitmap): Array<BooleanArray> {
        val width = bitmap.width
        val height = bitmap.height
        val mask = Array(height) { BooleanArray(width) }

        // Algoritmo simplificado de segmentación
        // En producción usar algoritmos más sofisticados (watershed, grabcut, etc.)
        val centerX = width / 2
        val centerY = height / 2

        // Encontrar color promedio del centro (asumiendo que el lunar está centrado)
        val centerColor = bitmap.getPixel(centerX, centerY)
        val threshold = 50 // Umbral de diferencia de color

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixelColor = bitmap.getPixel(x, y)
                val diff = colorDistance(pixelColor, centerColor)
                mask[y][x] = diff < threshold
            }
        }

        return mask
    }

    /**
     * Analiza la asimetría del lunar
     */
    private fun analyzeAsymmetry(bitmap: Bitmap, mask: Array<BooleanArray>): AsymmetryDetails {
        val width = bitmap.width
        val height = bitmap.height

        // Encontrar centro de masa
        var cx = 0.0
        var cy = 0.0
        var count = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y][x]) {
                    cx += x
                    cy += y
                    count++
                }
            }
        }

        cx /= count
        cy /= count

        // Calcular asimetría horizontal
        var leftCount = 0
        var rightCount = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y][x]) {
                    if (x < cx) leftCount++ else rightCount++
                }
            }
        }

        val horizontalAsymmetry = abs(leftCount - rightCount).toFloat() / count

        // Calcular asimetría vertical
        var topCount = 0
        var bottomCount = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y][x]) {
                    if (y < cy) topCount++ else bottomCount++
                }
            }
        }

        val verticalAsymmetry = abs(topCount - bottomCount).toFloat() / count

        val description = when {
            horizontalAsymmetry < 0.1f && verticalAsymmetry < 0.1f ->
                "Lunar simétrico en ambos ejes"
            horizontalAsymmetry > verticalAsymmetry ->
                "Asimetría principalmente horizontal"
            else ->
                "Asimetría principalmente vertical"
        }

        return AsymmetryDetails(
            horizontalAsymmetry = horizontalAsymmetry,
            verticalAsymmetry = verticalAsymmetry,
            description = description
        )
    }

    /**
     * Analiza los bordes del lunar
     */
    private fun analyzeBorder(mask: Array<BooleanArray>): BorderDetails {
        val height = mask.size
        val width = mask[0].size
        val borderPixels = mutableListOf<Pair<Int, Int>>()

        // Detectar píxeles del borde
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                if (mask[y][x] && (
                            !mask[y-1][x] || !mask[y+1][x] ||
                                    !mask[y][x-1] || !mask[y][x+1]
                            )) {
                    borderPixels.add(Pair(x, y))
                }
            }
        }

        // Calcular irregularidad del borde
        val convexHullArea = calculateConvexHullArea(borderPixels)
        val actualArea = mask.flatMap { it.asList() }.count { it }
        val irregularityIndex = 1 - (actualArea.toFloat() / convexHullArea)

        // Contar segmentos (simplificado)
        val numberOfSegments = detectBorderSegments(borderPixels)

        val description = when {
            irregularityIndex < 0.15f -> "Bordes regulares y bien definidos"
            irregularityIndex < 0.30f -> "Bordes ligeramente irregulares"
            else -> "Bordes muy irregulares con múltiples proyecciones"
        }

        return BorderDetails(
            irregularityIndex = irregularityIndex,
            numberOfSegments = numberOfSegments,
            description = description
        )
    }

    /**
     * Analiza los colores del lunar
     */
    private fun analyzeColor(bitmap: Bitmap, mask: Array<BooleanArray>): ColorDetails {
        val colorHistogram = mutableMapOf<Int, Int>()

        // Recolectar colores dentro del lunar
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (mask[y][x]) {
                    val color = bitmap.getPixel(x, y)
                    val quantizedColor = quantizeColor(color)
                    colorHistogram[quantizedColor] =
                        colorHistogram.getOrDefault(quantizedColor, 0) + 1
                }
            }
        }

        // Encontrar colores dominantes
        val dominantColors = colorHistogram.entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key }

        // Detectar colores específicos de riesgo
        val hasBlueWhite = dominantColors.any { isBlueWhite(it) }
        val hasRedBlueCombination = hasRedAndBlue(dominantColors)

        val colorCount = dominantColors.size

        val description = buildString {
            append("Se detectaron $colorCount colores principales. ")
            if (hasBlueWhite) append("Presencia de velo azul-blanquecino (signo de alarma). ")
            if (hasRedBlueCombination) append("Combinación rojo-azul detectada. ")
            if (colorCount >= 4) append("Múltiples colores sugieren mayor riesgo. ")
        }

        return ColorDetails(
            dominantColors = dominantColors,
            colorCount = colorCount,
            hasBlueWhite = hasBlueWhite,
            hasRedBlueCombination = hasRedBlueCombination,
            description = description
        )
    }

    /**
     * Analiza el diámetro del lunar
     */
    private fun analyzeDiameter(
        mask: Array<BooleanArray>,
        pixelDensity: Float
    ): DiameterDetails {
        var minX = Int.MAX_VALUE
        var maxX = 0
        var minY = Int.MAX_VALUE
        var maxY = 0
        var area = 0

        // Encontrar límites y área
        for (y in mask.indices) {
            for (x in mask[0].indices) {
                if (mask[y][x]) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                    area++
                }
            }
        }

        val widthPx = maxX - minX
        val heightPx = maxY - minY
        val maxDiameterPx = maxOf(widthPx, heightPx)

        // Convertir a mm (requiere calibración)
        val diameterMm = maxDiameterPx * PIXEL_TO_MM_RATIO / pixelDensity

        val description = when {
            diameterMm < 6f -> "Diámetro menor a 6mm (normal)"
            diameterMm < 10f -> "Diámetro entre 6-10mm (vigilar)"
            else -> "Diámetro mayor a 10mm (evaluar con especialista)"
        }

        return DiameterDetails(
            diameterMm = diameterMm,
            areaPx = area,
            description = description
        )
    }

    /**
     * Analiza la evolución comparando con imagen previa
     */
    private fun analyzeEvolution(
        currentBitmap: Bitmap,
        previousBitmap: Bitmap
    ): EvolutionDetails {
        // Análisis simplificado - en producción usar algoritmos más sofisticados

        // Comparar tamaños
        val currentArea = segmentMole(currentBitmap).flatMap { it.asList() }.count { it }
        val previousArea = segmentMole(previousBitmap).flatMap { it.asList() }.count { it }
        val sizeChange = (currentArea - previousArea).toFloat() / previousArea

        // Comparar colores (simplificado)
        val colorChange = compareColors(currentBitmap, previousBitmap)

        // Comparar formas (simplificado)
        val shapeChange = compareShapes(currentBitmap, previousBitmap)

        val description = buildString {
            if (abs(sizeChange) > 0.2f) {
                append("Cambio significativo en tamaño (${(sizeChange * 100).toInt()}%). ")
            }
            if (colorChange > 0.3f) {
                append("Cambio notable en coloración. ")
            }
            if (shapeChange > 0.25f) {
                append("Cambio en la forma detectado. ")
            }
            if (sizeChange < 0.1f && colorChange < 0.1f && shapeChange < 0.1f) {
                append("Sin cambios significativos.")
            }
        }

        return EvolutionDetails(
            sizeChange = sizeChange,
            colorChange = colorChange,
            shapeChange = shapeChange,
            description = description
        )
    }

    // Funciones auxiliares

    private fun colorDistance(color1: Int, color2: Int): Float {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)
        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)

        return sqrt(
            ((r1 - r2) * (r1 - r2) +
                    (g1 - g2) * (g1 - g2) +
                    (b1 - b2) * (b1 - b2)).toFloat()
        )
    }

    private fun quantizeColor(color: Int): Int {
        // Cuantizar color a paleta reducida
        val r = (Color.red(color) / 51) * 51
        val g = (Color.green(color) / 51) * 51
        val b = (Color.blue(color) / 51) * 51
        return Color.rgb(r, g, b)
    }

    private fun isBlueWhite(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        // Detectar tonos azul-blanquecinos
        return b > 150 && r > 180 && g > 180
    }

    private fun hasRedAndBlue(colors: List<Int>): Boolean {
        var hasRed = false
        var hasBlue = false

        for (color in colors) {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)

            if (r > 150 && g < 100 && b < 100) hasRed = true
            if (b > 150 && r < 100 && g < 100) hasBlue = true
        }

        return hasRed && hasBlue
    }

    private fun calculateConvexHullArea(points: List<Pair<Int, Int>>): Float {
        // Implementación simplificada - usar algoritmo real en producción
        if (points.size < 3) return 0f

        val minX = points.minByOrNull { it.first }?.first ?: 0
        val maxX = points.maxByOrNull { it.first }?.first ?: 0
        val minY = points.minByOrNull { it.second }?.second ?: 0
        val maxY = points.maxByOrNull { it.second }?.second ?: 0

        return ((maxX - minX) * (maxY - minY)).toFloat()
    }

    private fun detectBorderSegments(borderPixels: List<Pair<Int, Int>>): Int {
        // Implementación simplificada
        return minOf(8, maxOf(1, borderPixels.size / 50))
    }

    private fun compareColors(bitmap1: Bitmap, bitmap2: Bitmap): Float {
        // Comparación simplificada de histogramas de color
        return 0.1f // Placeholder
    }

    private fun compareShapes(bitmap1: Bitmap, bitmap2: Bitmap): Float {
        // Comparación simplificada de formas
        return 0.1f // Placeholder
    }

    // Cálculo de scores

    private fun calculateAsymmetryScore(details: AsymmetryDetails): Float {
        val maxAsymmetry = maxOf(details.horizontalAsymmetry, details.verticalAsymmetry)
        return when {
            maxAsymmetry < 0.1f -> 0f
            maxAsymmetry < 0.2f -> 1f
            else -> 2f
        }
    }

    private fun calculateBorderScore(details: BorderDetails): Float {
        return minOf(8f, details.numberOfSegments.toFloat())
    }

    private fun calculateColorScore(details: ColorDetails): Float {
        var score = details.colorCount.toFloat()
        if (details.hasBlueWhite) score += 1f
        if (details.hasRedBlueCombination) score += 1f
        return minOf(6f, score)
    }

    private fun calculateDiameterScore(details: DiameterDetails): Float {
        return when {
            details.diameterMm < 6f -> 0f
            details.diameterMm < 10f -> 2f
            details.diameterMm < 15f -> 3f
            else -> 5f
        }
    }

    private fun calculateEvolutionScore(details: EvolutionDetails): Float {
        val totalChange = abs(details.sizeChange) +
                details.colorChange +
                details.shapeChange

        return when {
            totalChange < 0.2f -> 0f
            totalChange < 0.5f -> 1f
            totalChange < 0.8f -> 2f
            else -> 3f
        }
    }

    private fun calculateTotalScore(
        asymmetry: Float,
        border: Float,
        color: Float,
        diameter: Float,
        evolution: Float?
    ): Float {
        // Fórmula basada en TDS (Total Dermoscopy Score) modificada
        var score = (asymmetry * 1.3f) +
                (border * 0.1f) +
                (color * 0.5f) +
                (diameter * 0.5f)

        // Si hay datos de evolución, ajustar el score
        evolution?.let {
            score *= (1 + it * 0.2f)
        }

        return score
    }
}
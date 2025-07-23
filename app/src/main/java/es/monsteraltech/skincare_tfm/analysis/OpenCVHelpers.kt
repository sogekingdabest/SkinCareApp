// OpenCVHelpers.kt - Funciones auxiliares para análisis dermatológico
package es.monsteraltech.skincare_tfm.analysis

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

/**
 * Funciones auxiliares para análisis dermatológico con OpenCV
 */
object OpenCVHelpers {

    /**
     * Detectar y analizar patrones de red pigmentada
     */
    fun analyzePigmentNetwork(bitmap: Bitmap): NetworkAnalysis {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // Convertir a escala de grises
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

        // Aplicar filtros de Gabor para detectar texturas
        val gaborKernels = createGaborKernels()
        val responses = mutableListOf<Mat>()

        gaborKernels.forEach { kernel ->
            val response = Mat()
            Imgproc.filter2D(gray, response, CvType.CV_32F, kernel)
            responses.add(response)
        }

        // Analizar respuestas
        val networkScore = analyzeGaborResponses(responses)

        // Detectar regularidad
        val regularityScore = detectNetworkRegularity(responses)

        // Limpiar
        mat.release()
        gray.release()
        gaborKernels.forEach { it.release() }
        responses.forEach { it.release() }

        return NetworkAnalysis(
            isPresent = networkScore > 0.3f,
            regularity = regularityScore,
            coverage = networkScore,
            description = when {
                networkScore < 0.2f -> "Red pigmentada ausente o mínima"
                regularityScore > 0.7f -> "Red pigmentada regular y típica"
                regularityScore < 0.4f -> "Red pigmentada atípica e irregular"
                else -> "Red pigmentada presente con irregularidades moderadas"
            }
        )
    }

    /**
     * Detectar puntos y glóbulos
     */
    fun detectDotsAndGlobules(bitmap: Bitmap): DotsGlobulesAnalysis {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // Preprocesar
        val processed = Mat()
        Imgproc.cvtColor(mat, processed, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(processed, processed, Size(3.0, 3.0), 0.0)

        // Detectar blobs usando SimpleBlobDetector
        val params = createBlobDetectorParams()
        val keypoints = detectBlobs(processed, params)

        // Clasificar por tamaño
        val dots = keypoints.filter { it.size < 3 }
        val globules = keypoints.filter { it.size in 3.0..10.0 }
        val largeGlobules = keypoints.filter { it.size > 10 }

        // Analizar distribución
        val distribution = analyzeDistribution(keypoints)

        // Limpiar
        mat.release()
        processed.release()

        return DotsGlobulesAnalysis(
            dotsCount = dots.size,
            globulesCount = globules.size,
            largeGlobulesCount = largeGlobules.size,
            distribution = distribution,
            description = buildString {
                if (dots.size > 20) append("Múltiples puntos detectados. ")
                if (globules.size > 10) append("Numerosos glóbulos presentes. ")
                if (distribution == "irregular") append("Distribución irregular (signo de alarma). ")
            }
        )
    }

    /**
     * Detectar estructuras de regresión (áreas blanquecinas)
     */
    fun detectRegressionStructures(bitmap: Bitmap, mask: Mat): RegressionAnalysis {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // Convertir a HSV
        val hsv = Mat()
        Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV)

        // Detectar áreas blanquecinas/azuladas
        val whitishMask = Mat()
        Core.inRange(hsv, Scalar(0.0, 0.0, 180.0), Scalar(180.0, 30.0, 255.0), whitishMask)

        // Detectar áreas azul-grisáceas (cicatrices)
        val bluishMask = Mat()
        Core.inRange(hsv, Scalar(100.0, 20.0, 100.0), Scalar(130.0, 100.0, 200.0), bluishMask)

        // Combinar máscaras
        val regressionMask = Mat()
        Core.bitwise_or(whitishMask, bluishMask, regressionMask)

        // Aplicar máscara del lunar
        Core.bitwise_and(regressionMask, mask, regressionMask)

        // Calcular área de regresión
        val regressionArea = Core.countNonZero(regressionMask)
        val totalArea = Core.countNonZero(mask)
        val regressionPercentage = (regressionArea.toFloat() / totalArea) * 100

        // Detectar patrones específicos
        val hasPeppering = detectPeppering(mat, mask)
        val hasScarLike = regressionPercentage > 15

        // Limpiar
        mat.release()
        hsv.release()
        whitishMask.release()
        bluishMask.release()
        regressionMask.release()

        return RegressionAnalysis(
            percentage = regressionPercentage,
            hasPeppering = hasPeppering,
            hasScarLikeAreas = hasScarLike,
            description = when {
                regressionPercentage > 30 -> "Regresión extensa detectada (>30%)"
                regressionPercentage > 15 -> "Áreas de regresión moderadas"
                hasPeppering -> "Patrón de peppering detectado"
                regressionPercentage > 5 -> "Regresión mínima presente"
                else -> "Sin signos evidentes de regresión"
            }
        )
    }

    /**
     * Análisis de vasos sanguíneos
     */
    fun analyzeVascularPatterns(bitmap: Bitmap, mask: Mat): VascularAnalysis {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // Extraer canal rojo (donde los vasos son más visibles)
        val channels = mutableListOf<Mat>()
        Core.split(mat, channels)
        val redChannel = channels[2]

        // Mejorar contraste
        val enhanced = Mat()
        Imgproc.createCLAHE(2.0, Size(8.0, 8.0)).apply(redChannel, enhanced)

        // Detectar estructuras lineales (vasos)
        val vessels = detectLinearStructures(enhanced, mask)

        // Analizar patrones
        val patterns = analyzeVesselPatterns(vessels)

        // Limpiar
        mat.release()
        channels.forEach { it.release() }
        enhanced.release()
        vessels.release()

        return VascularAnalysis(
            hasLinearVessels = patterns.linear > 5,
            hasDottedVessels = patterns.dotted > 10,
            hasPolymorphousVessels = patterns.polymorphous,
            hasCommaVessels = patterns.comma > 3,
            description = buildString {
                if (patterns.polymorphous) append("Vasos polimorfos detectados (alto riesgo). ")
                if (patterns.comma > 5) append("Múltiples vasos en coma. ")
                if (patterns.linear > 10) append("Patrón vascular prominente. ")
            }
        )
    }

    /**
     * Calibración automática de tamaño usando detección de moneda
     */
    fun calibrateWithCoinDetection(bitmap: Bitmap): CalibrationResult {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // Detectar círculos (posibles monedas)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(9.0, 9.0), 2.0)

        val circles = Mat()
        Imgproc.HoughCircles(
            gray, circles, Imgproc.CV_HOUGH_GRADIENT,
            1.0, gray.rows() / 8.0, 100.0, 30.0, 20, 100
        )

        var calibrationFactor = 0.0264f // Valor por defecto
        var coinDetected = false

        if (circles.cols() > 0) {
            // Asumir que el círculo más grande es una moneda
            var maxRadius = 0.0
            var selectedCircle: DoubleArray? = null

            for (i in 0 until circles.cols()) {
                val circle = circles.get(0, i)
                if (circle[2] > maxRadius) {
                    maxRadius = circle[2]
                    selectedCircle = circle
                }
            }

            selectedCircle?.let {
                // Verificar que parece una moneda (color, textura)
                if (isCoinLike(mat, it)) {
                    coinDetected = true
                    // Moneda de 1 euro = 23.25mm de diámetro
                    calibrationFactor = 23.25f / (maxRadius * 2).toFloat()
                }
            }
        }

        // Limpiar
        mat.release()
        gray.release()
        circles.release()

        return CalibrationResult(
            calibrated = coinDetected,
            pixelToMmRatio = calibrationFactor,
            confidence = if (coinDetected) 0.9f else 0.5f,
            method = if (coinDetected) "Detección de moneda" else "Estimación por defecto"
        )
    }

    /**
     * Análisis de textura usando Local Binary Patterns (LBP)
     */
    fun analyzeTextureWithLBP(bitmap: Bitmap, mask: Mat): TextureAnalysis {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

        // Calcular LBP
        val lbp = calculateLBP(gray, mask)

        // Calcular histograma LBP
        val histogram = calculateLBPHistogram(lbp, mask)

        // Analizar características del histograma
        val uniformity = calculateUniformity(histogram)
        val entropy = calculateEntropy(histogram)

        // Detectar patrones específicos
        val hasReticular = detectReticularPattern(histogram)
        val hasHomogeneous = uniformity > 0.7f
        val hasChaotic = entropy > 0.8f

        // Limpiar
        mat.release()
        gray.release()
        lbp.release()

        return TextureAnalysis(
            uniformity = uniformity,
            entropy = entropy,
            hasReticularPattern = hasReticular,
            hasHomogeneousPattern = hasHomogeneous,
            hasChaoticPattern = hasChaotic,
            description = when {
                hasChaotic -> "Textura caótica e irregular"
                hasReticular -> "Patrón reticular presente"
                hasHomogeneous -> "Textura homogénea y uniforme"
                else -> "Textura con irregularidades moderadas"
            }
        )
    }

    // Estructuras de datos para los resultados

    data class NetworkAnalysis(
        val isPresent: Boolean,
        val regularity: Float,
        val coverage: Float,
        val description: String
    )

    data class DotsGlobulesAnalysis(
        val dotsCount: Int,
        val globulesCount: Int,
        val largeGlobulesCount: Int,
        val distribution: String,
        val description: String
    )

    data class RegressionAnalysis(
        val percentage: Float,
        val hasPeppering: Boolean,
        val hasScarLikeAreas: Boolean,
        val description: String
    )

    data class VascularAnalysis(
        val hasLinearVessels: Boolean,
        val hasDottedVessels: Boolean,
        val hasPolymorphousVessels: Boolean,
        val hasCommaVessels: Boolean,
        val description: String
    )

    data class CalibrationResult(
        val calibrated: Boolean,
        val pixelToMmRatio: Float,
        val confidence: Float,
        val method: String
    )

    data class TextureAnalysis(
        val uniformity: Float,
        val entropy: Float,
        val hasReticularPattern: Boolean,
        val hasHomogeneousPattern: Boolean,
        val hasChaoticPattern: Boolean,
        val description: String
    )

    // Funciones auxiliares privadas

    private fun createGaborKernels(): List<Mat> {
        val kernels = mutableListOf<Mat>()
        val ksize = 31

        for (theta in 0 until 180 step 45) {
            for (lambda in listOf(5.0, 10.0)) {
                val kernel = Imgproc.getGaborKernel(
                    Size(ksize.toDouble(), ksize.toDouble()),
                    4.0, // sigma
                    theta * Math.PI / 180, // theta
                    lambda, // lambda
                    0.5, // gamma
                    0.0 // psi
                )
                kernels.add(kernel)
            }
        }

        return kernels
    }

    private fun analyzeGaborResponses(responses: List<Mat>): Float {
        var totalEnergy = 0.0

        responses.forEach { response ->
            val mean = Core.mean(response)
            totalEnergy += mean.`val`[0]
        }

        return (totalEnergy / responses.size / 255).toFloat().coerceIn(0f, 1f)
    }

    private fun detectNetworkRegularity(responses: List<Mat>): Float {
        // Analizar varianza entre respuestas
        val variances = responses.map {
            val mean = MatOfDouble()
            val stddev = MatOfDouble()
            Core.meanStdDev(it, mean, stddev)
            stddev.get(0, 0)[0]
        }

        val avgVariance = variances.average()
        val regularityScore = 1f - (avgVariance / 128f).toFloat().coerceIn(0f, 1f)

        return regularityScore
    }

    private fun createBlobDetectorParams(): SimpleBlobDetectorParams {
        return SimpleBlobDetectorParams().apply {
            minThreshold = 10f
            maxThreshold = 200f
            filterByArea = true
            minArea = 5f
            maxArea = 500f
            filterByCircularity = true
            minCircularity = 0.5f
            filterByInertia = false
            filterByConvexity = false
        }
    }

    private fun detectBlobs(image: Mat, params: SimpleBlobDetectorParams): List<KeyPoint> {
        // Nota: SimpleBlobDetector no está disponible directamente en OpenCV Android
        // Esta es una implementación alternativa usando detección de contornos

        val binary = Mat()
        Imgproc.threshold(image, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(binary, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val keypoints = mutableListOf<KeyPoint>()

        contours.forEach { contour ->
            val area = Imgproc.contourArea(contour)
            if (area in params.minArea..params.maxArea) {
                val moments = Imgproc.moments(contour)
                val cx = (moments.m10 / moments.m00).toFloat()
                val cy = (moments.m01 / moments.m00).toFloat()
                val diameter = sqrt(area * 4 / Math.PI).toFloat()

                keypoints.add(KeyPoint(cx, cy, diameter))
            }
        }

        binary.release()

        return keypoints
    }

    private fun analyzeDistribution(keypoints: List<KeyPoint>): String {
        if (keypoints.size < 3) return "sparse"

        // Calcular distancia promedio al vecino más cercano
        val distances = mutableListOf<Float>()

        keypoints.forEach { kp1 ->
            var minDist = Float.MAX_VALUE
            keypoints.forEach { kp2 ->
                if (kp1 != kp2) {
                    val dist = sqrt((kp1.pt.x - kp2.pt.x).pow(2) + (kp1.pt.y - kp2.pt.y).pow(2))
                    minDist = minOf(minDist, dist)
                }
            }
            distances.add(minDist)
        }

        val avgDistance = distances.average()
        val stdDev = sqrt(distances.map { (it - avgDistance).pow(2) }.average())

        return when {
            stdDev / avgDistance > 0.5 -> "irregular"
            stdDev / avgDistance < 0.2 -> "regular"
            else -> "mixed"
        }
    }

    private fun detectPeppering(image: Mat, mask: Mat): Boolean {
        // Detectar puntos muy pequeños y oscuros
        val gray = Mat()
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY)

        val darkSpots = Mat()
        Imgproc.threshold(gray, darkSpots, 50.0, 255.0, Imgproc.THRESH_BINARY_INV)
        Core.bitwise_and(darkSpots, mask, darkSpots)

        val kernel = Mat.ones(2, 2, CvType.CV_8U)
        Imgproc.erode(darkSpots, darkSpots, kernel)

        val spotCount = Core.countNonZero(darkSpots)

        gray.release()
        darkSpots.release()
        kernel.release()

        return spotCount > 50 // Umbral para considerar peppering
    }

    private fun detectLinearStructures(image: Mat, mask: Mat): Mat {
        // Usar filtro de Frangi para detectar estructuras vasculares
        val result = Mat()

        // Implementación simplificada usando detección de líneas
        val edges = Mat()
        Imgproc.Canny(image, edges, 50.0, 150.0)
        Core.bitwise_and(edges, mask, result)

        edges.release()

        return result
    }

    private fun analyzeVesselPatterns(vessels: Mat): VesselPatterns {
        // Análisis simplificado de patrones vasculares
        val lines = Mat()
        Imgproc.HoughLinesP(vessels, lines, 1.0, Math.PI/180, 30, 20.0, 10.0)

        val patterns = VesselPatterns(
            linear = lines.rows(),
            dotted = 0, // Simplificado
            polymorphous = lines.rows() > 20,
            comma = 0 // Simplificado
        )

        lines.release()

        return patterns
    }

    private fun isCoinLike(image: Mat, circle: DoubleArray): Boolean {
        // Verificar si el círculo detectado parece una moneda
        val x = circle[0].toInt()
        val y = circle[1].toInt()
        val radius = circle[2].toInt()

        // Extraer ROI circular
        val roi = Mat(image, Rect(
            (x - radius).coerceAtLeast(0),
            (y - radius).coerceAtLeast(0),
            (radius * 2).coerceAtMost(image.cols() - x + radius),
            (radius * 2).coerceAtMost(image.rows() - y + radius)
        ))

        // Verificar color metálico (dorado/plateado)
        val hsv = Mat()
        Imgproc.cvtColor(roi, hsv, Imgproc.COLOR_BGR2HSV)

        val meanColor = Core.mean(hsv)
        val saturation = meanColor.`val`[1]
        val value = meanColor.`val`[2]

        roi.release()
        hsv.release()

        // Las monedas suelen tener baja saturación y alto brillo
        return saturation < 100 && value > 100
    }

    private fun calculateLBP(image: Mat, mask: Mat): Mat {
        val lbp = Mat.zeros(image.size(), CvType.CV_8U)
        val radius = 1
        val neighbors = 8

        for (y in radius until image.rows() - radius) {
            for (x in radius until image.cols() - radius) {
                if (mask.get(y, x)[0] > 0) {
                    val center = image.get(y, x)[0]
                    var lbpValue = 0

                    for (n in 0 until neighbors) {
                        val angle = 2 * Math.PI * n / neighbors
                        val nx = x + radius * cos(angle)
                        val ny = y + radius * sin(angle)

                        val neighborValue = interpolate(image, nx, ny)
                        if (neighborValue >= center) {
                            lbpValue = lbpValue or (1 shl n)
                        }
                    }

                    lbp.put(y, x, lbpValue.toDouble())
                }
            }
        }

        return lbp
    }

    private fun interpolate(image: Mat, x: Double, y: Double): Double {
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = x0 + 1
        val y1 = y0 + 1

        if (x1 >= image.cols() || y1 >= image.rows()) {
            return image.get(y0, x0)[0]
        }

        val fx = x - x0
        val fy = y - y0

        val v00 = image.get(y0, x0)[0]
        val v10 = image.get(y0, x1)[0]
        val v01 = image.get(y1, x0)[0]
        val v11 = image.get(y1, x1)[0]

        return v00 * (1 - fx) * (1 - fy) +
                v10 * fx * (1 - fy) +
                v01 * (1 - fx) * fy +
                v11 * fx * fy
    }

    private fun calculateLBPHistogram(lbp: Mat, mask: Mat): DoubleArray {
        val histogram = DoubleArray(256)
        var count = 0

        for (y in 0 until lbp.rows()) {
            for (x in 0 until lbp.cols()) {
                if (mask.get(y, x)[0] > 0) {
                    val value = lbp.get(y, x)[0].toInt()
                    if (value >= 0 && value < histogram.size) {
                        histogram[value] = histogram[value] + 1
                        count++
                    }
                }
            }
        }

        // Normalizar
        if (count > 0) {
            for (i in histogram.indices) {
                histogram[i] = histogram[i] / count
            }
        }

        return histogram
    }

    private fun calculateUniformity(histogram: DoubleArray): Float {
        return histogram.sumOf { it * it }.toFloat()
    }

    private fun calculateEntropy(histogram: DoubleArray): Float {
        var entropy = 0.0

        histogram.forEach { p ->
            if (p > 0) {
                entropy -= p * ln(p)
            }
        }

        return (entropy / ln(histogram.size.toDouble())).toFloat()
    }

    private fun detectReticularPattern(histogram: DoubleArray): Boolean {
        // Los patrones reticulares tienden a tener picos específicos en el histograma LBP
        val uniformPatterns = listOf(0, 1, 3, 7, 15, 31, 63, 127, 255)
        val uniformSum = uniformPatterns.sumOf { histogram[it] }

        return uniformSum > 0.7 // Más del 70% son patrones uniformes
    }

    // Clases auxiliares

    data class VesselPatterns(
        val linear: Int,
        val dotted: Int,
        val polymorphous: Boolean,
        val comma: Int
    )

    data class SimpleBlobDetectorParams(
        var minThreshold: Float = 10f,
        var maxThreshold: Float = 200f,
        var filterByArea: Boolean = true,
        var minArea: Float = 25f,
        var maxArea: Float = 5000f,
        var filterByCircularity: Boolean = true,
        var minCircularity: Float = 0.8f,
        var filterByInertia: Boolean = true,
        var minInertiaRatio: Float = 0.1f,
        var filterByConvexity: Boolean = true,
        var minConvexity: Float = 0.95f
    )

    data class KeyPoint(
        val pt: PointF,
        val size: Float,
        val angle: Float = -1f,
        val response: Float = 0f,
        val octave: Int = 0,
        val classId: Int = -1
    ) {
        constructor(x: Float, y: Float, size: Float) : this(PointF(x, y), size)
    }

    data class PointF(val x: Float, val y: Float)
}
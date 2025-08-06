package es.monsteraltech.skincare_tfm.analysis
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfInt4
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
class ABCDEAnalyzerOpenCV(private val context: Context) {
    companion object {
        private const val TAG = "ABCDEAnalyzerOpenCV"
        const val BLUR_SIZE = 5
        const val MORPH_SIZE = 5
    }
    data class ABCDEResult(
        val asymmetryScore: Float,
        val borderScore: Float,
        val colorScore: Float,
        val diameterScore: Float,
        val evolutionScore: Float?,
        val totalScore: Float,
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
        VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH
    }
    fun analyzeMole(
        bitmap: Bitmap,
        previousBitmap: Bitmap? = null,
        pixelDensity: Float = 1.0f
    ): ABCDEResult {
        Log.d(TAG, "Iniciando análisis ABCDE con OpenCV")
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val processedMat = preprocessImage(mat)
        val (mask, contour) = segmentMole(processedMat)
        if (!isValidSegmentation(mask, contour)) {
            Log.w(TAG, "Segmentación inicial inválida, intentando método alternativo")
            val (altMask, altContour) = segmentMoleAlternative(processedMat)
            if (isValidSegmentation(altMask, altContour)) {
                mask.release()
                altMask.copyTo(mask)
                contour.fromArray(*altContour.toArray())
            } else {
                Log.e(TAG, "Ambos métodos de segmentación fallaron")
                // Crear un contorno por defecto en el centro de la imagen
                val centerX = processedMat.cols() / 2
                val centerY = processedMat.rows() / 2
                val radius = minOf(centerX, centerY) / 3
                val defaultContour = MatOfPoint()
                val points = mutableListOf<Point>()
                for (i in 0 until 20) {
                    val angle = 2 * Math.PI * i / 20
                    points.add(Point(
                        centerX + radius * kotlin.math.cos(angle),
                        centerY + radius * kotlin.math.sin(angle)
                    ))
                }
                defaultContour.fromArray(*points.toTypedArray())
                contour.fromArray(*defaultContour.toArray())
                
                // Crear máscara por defecto
                mask.setTo(Scalar(0.0))
                Imgproc.circle(mask, Point(centerX.toDouble(), centerY.toDouble()), radius, Scalar(255.0), -1)
            }
        }
        val asymmetryDetails = analyzeAsymmetry(mask, contour)
        val borderDetails = analyzeBorder(contour)
        val colorDetails = analyzeColor(mat, mask)
        val diameterDetails = analyzeDiameter(contour, pixelDensity)
        val evolutionDetails = previousBitmap?.let {
            val prevMat = Mat()
            Utils.bitmapToMat(it, prevMat)
            analyzeEvolution(mat, prevMat)
        }
        val asymmetryScore = calculateAsymmetryScore(asymmetryDetails)
        val borderScore = calculateBorderScore(borderDetails)
        val colorScore = calculateColorScore(colorDetails)
        val diameterScore = calculateDiameterScore(diameterDetails)
        val evolutionScore = evolutionDetails?.let { calculateEvolutionScore(it) }
        val totalScore = calculateTotalScore(
            asymmetryScore, borderScore, colorScore, diameterScore, evolutionScore
        )
        val riskLevel = when {
            totalScore < 4.75f -> RiskLevel.LOW
            totalScore < 6.8f -> RiskLevel.MEDIUM
            else -> RiskLevel.HIGH
        }
        mat.release()
        processedMat.release()
        mask.release()
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
    private fun preprocessImage(src: Mat): Mat {
        val processed = Mat()
        Imgproc.GaussianBlur(src, processed, Size(BLUR_SIZE.toDouble(), BLUR_SIZE.toDouble()), 0.0)
        val lab = Mat()
        Imgproc.cvtColor(processed, lab, Imgproc.COLOR_BGR2Lab)
        val labChannels = mutableListOf<Mat>()
        Core.split(lab, labChannels)
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        clahe.apply(labChannels[0], labChannels[0])
        Core.merge(labChannels, lab)
        Imgproc.cvtColor(lab, processed, Imgproc.COLOR_Lab2BGR)
        lab.release()
        labChannels.forEach { it.release() }
        return processed
    }
    private fun segmentMole(src: Mat): Pair<Mat, MatOfPoint> {
        val mask = Mat()
        val bgModel = Mat()
        val fgModel = Mat()
        val rect = Rect(
            (src.cols() * 0.1).toInt(),
            (src.rows() * 0.1).toInt(),
            (src.cols() * 0.8).toInt(),
            (src.rows() * 0.8).toInt()
        )
        Imgproc.grabCut(src, mask, rect, bgModel, fgModel, 5, Imgproc.GC_INIT_WITH_RECT)
        val binaryMask = Mat()
        Core.compare(mask, Scalar(Imgproc.GC_PR_FGD.toDouble()), binaryMask, Core.CMP_EQ)
        val temp = Mat()
        Core.compare(mask, Scalar(Imgproc.GC_FGD.toDouble()), temp, Core.CMP_EQ)
        Core.bitwise_or(binaryMask, temp, binaryMask)
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE,
            Size(MORPH_SIZE.toDouble(), MORPH_SIZE.toDouble())
        )
        Imgproc.morphologyEx(binaryMask, binaryMask, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.morphologyEx(binaryMask, binaryMask, Imgproc.MORPH_OPEN, kernel)
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            binaryMask, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )
        val largestContour = contours.maxByOrNull { Imgproc.contourArea(it) }
            ?: MatOfPoint()
        val finalMask = Mat.zeros(src.size(), CvType.CV_8UC1)
        if (largestContour.total() > 0) {
            Imgproc.drawContours(
                finalMask, listOf(largestContour), -1,
                Scalar(255.0), Core.FILLED
            )
        }
        bgModel.release()
        fgModel.release()
        temp.release()
        kernel.release()
        hierarchy.release()
        return Pair(finalMask, largestContour)
    }
    private fun segmentMoleAlternative(src: Mat): Pair<Mat, MatOfPoint> {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        val binary = Mat()
        Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)
        val kernel = Mat.ones(3, 3, CvType.CV_8U)
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel)
        val sureBg = Mat()
        Imgproc.dilate(binary, sureBg, kernel, Point(-1.0, -1.0), 3)
        val distTransform = Mat()
        Imgproc.distanceTransform(binary, distTransform, Imgproc.DIST_L2, 5)
        val sureFg = Mat()
        val maxVal = Core.minMaxLoc(distTransform).maxVal
        Imgproc.threshold(distTransform, sureFg, 0.7 * maxVal, 255.0, Imgproc.THRESH_BINARY)
        sureFg.convertTo(sureFg, CvType.CV_8U)
        val unknown = Mat()
        Core.subtract(sureBg, sureFg, unknown)
        val markers = Mat()
        Imgproc.connectedComponents(sureFg, markers)
        Core.add(markers, Mat.ones(markers.size(), CvType.CV_32S), markers)
        val unknownIdx = Mat()
        Core.compare(unknown, Scalar(255.0), unknownIdx, Core.CMP_EQ)
        markers.setTo(Scalar(0.0), unknownIdx)
        Imgproc.watershed(src, markers)
        val mask = Mat()
        Core.compare(markers, Scalar(1.0), mask, Core.CMP_GT)
        mask.convertTo(mask, CvType.CV_8U)
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(mask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        val largestContour = contours.maxByOrNull { Imgproc.contourArea(it) } ?: MatOfPoint()
        gray.release()
        binary.release()
        kernel.release()
        sureBg.release()
        distTransform.release()
        sureFg.release()
        unknown.release()
        markers.release()
        unknownIdx.release()
        return Pair(mask, largestContour)
    }
    private fun isValidSegmentationOld(mask: Mat, contour: MatOfPoint): Boolean {
        if (contour.total() < 10) return false
        val area = Imgproc.contourArea(contour)
        val totalArea = mask.rows() * mask.cols()
        val ratio = area / totalArea
        return ratio in 0.01..0.8
    }
    private fun isValidSegmentation(mask: Mat, contour: MatOfPoint): Boolean {
        // Verificar que el contorno no esté vacío
        if (contour.total() == 0L || contour.toArray().isEmpty()) {
            Log.w(TAG, "Contorno vacío detectado")
            return false
        }
        
        try {
            val area = Imgproc.contourArea(contour)
            if (area <= 0) {
                Log.w(TAG, "Área del contorno inválida: $area")
                return false
            }
            
            val totalArea = mask.rows() * mask.cols()
            val ratio = area / totalArea
            
            val contourPoints = contour.toArray()
            if (contourPoints.size < 3) {
                Log.w(TAG, "Contorno con muy pocos puntos: ${contourPoints.size}")
                return false
            }
            
            val perimeter = Imgproc.arcLength(MatOfPoint2f(*contourPoints), true)
            if (perimeter <= 0) {
                Log.w(TAG, "Perímetro inválido: $perimeter")
                return false
            }
            
            val circularity = 4 * Math.PI * area / (perimeter * perimeter)
            
            return ratio in 0.01..0.8 &&
                    circularity > 0.3 &&
                    contour.total() > 50
        } catch (e: Exception) {
            Log.e(TAG, "Error validando segmentación: ${e.message}")
            return false
        }
    }
    private fun analyzeAsymmetry(mask: Mat, contour: MatOfPoint): AsymmetryDetails {
        val points = contour.toArray()
        if (points.isEmpty()) {
            Log.w(TAG, "Contorno vacío en analyzeAsymmetry")
            return AsymmetryDetails(0f, 0f, "❌ No se pudo analizar la asimetría - contorno inválido")
        }
        
        val moments = try {
            Imgproc.moments(contour)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando momentos: ${e.message}")
            return AsymmetryDetails(0f, 0f, "❌ Error en el cálculo de asimetría")
        }
        
        if (moments.m00 == 0.0) {
            Log.w(TAG, "Momento m00 es cero")
            return AsymmetryDetails(0f, 0f, "❌ No se pudo calcular el centro de masa")
        }
        
        val cx = moments.m10 / moments.m00
        val cy = moments.m01 / moments.m00
        val angle = 0.5 * atan2(2 * moments.mu11, moments.mu20 - moments.mu02)
        val center = Point(cx, cy)
        val rotMatrix = Imgproc.getRotationMatrix2D(center, Math.toDegrees(angle), 1.0)
        val rotatedMask = Mat()
        Imgproc.warpAffine(mask, rotatedMask, rotMatrix, mask.size())
        val (horizAsym, vertAsym) = calculateAxialAsymmetry(rotatedMask, cx, cy)
        val huMoments = Mat()
        Imgproc.HuMoments(moments, huMoments)
        val hu1 = huMoments.get(0, 0)[0]
        val overallAsymmetry = (horizAsym + vertAsym + abs(hu1).toFloat()) / 3
        val description = when {
            overallAsymmetry < 0.1f -> "Lunar altamente simétrico"
            overallAsymmetry < 0.2f -> "Asimetría leve"
            overallAsymmetry < 0.3f -> "Asimetría moderada"
            else -> "Asimetría significativa (factor de riesgo)"
        }
        rotMatrix.release()
        rotatedMask.release()
        huMoments.release()
        return AsymmetryDetails(horizAsym, vertAsym, description)
    }
    private fun calculateAxialAsymmetry(mask: Mat, cx: Double, cy: Double): Pair<Float, Float> {
        var leftPixels = 0
        var rightPixels = 0
        var topPixels = 0
        var bottomPixels = 0
        for (y in 0 until mask.rows()) {
            for (x in 0 until mask.cols()) {
                if (mask.get(y, x)[0] > 0) {
                    if (x < cx) leftPixels++ else rightPixels++
                    if (y < cy) topPixels++ else bottomPixels++
                }
            }
        }
        val total = leftPixels + rightPixels
        val horizAsym = abs(leftPixels - rightPixels).toFloat() / total
        val vertAsym = abs(topPixels - bottomPixels).toFloat() / total
        return Pair(horizAsym, vertAsym)
    }
    private fun analyzeBorder(contour: MatOfPoint): BorderDetails {
        val points = contour.toArray()
        if (points.isEmpty() || points.size < 3) {
            Log.w(TAG, "Contorno inválido en analyzeBorder")
            return BorderDetails(0f, 0, "❌ No se pudo analizar los bordes - contorno inválido")
        }
        
        val epsilon = 0.02 * Imgproc.arcLength(MatOfPoint2f(*points), true)
        val approxContour = MatOfPoint2f()
        Imgproc.approxPolyDP(MatOfPoint2f(*points), approxContour, epsilon, true)
        val hull = MatOfInt()
        Imgproc.convexHull(contour, hull)
        val defects = MatOfInt4()
        Imgproc.convexityDefects(contour, hull, defects)
        
        val contourArea = try {
            Imgproc.contourArea(contour)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando área del contorno: ${e.message}")
            0.0
        }
        
        val hullPoints = hull.toArray().map { points[it] }.toTypedArray()
        val hullArea = try {
            Imgproc.contourArea(MatOfPoint(*hullPoints))
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando área del hull: ${e.message}")
            contourArea
        }
        val solidity = contourArea / hullArea
        val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
        val compactness = 4 * Math.PI * contourArea / (perimeter * perimeter)
        val defectsArray = defects.toArray()
        var significantDefects = 0
        for (i in defectsArray.indices step 4) {
            if (i + 3 < defectsArray.size) {
                val depth = defectsArray[i + 3] / 256.0
                if (depth > perimeter * 0.01) {
                    significantDefects++
                }
            }
        }
        val irregularityIndex = (1 - solidity.toFloat()) * (1 - compactness.toFloat())
        val description = when {
            irregularityIndex < 0.1f -> "Bordes muy regulares y bien definidos"
            irregularityIndex < 0.2f -> "Bordes ligeramente irregulares"
            irregularityIndex < 0.3f -> "Bordes moderadamente irregulares"
            else -> "Bordes muy irregulares con múltiples protrusiones"
        }
        approxContour.release()
        hull.release()
        defects.release()
        return BorderDetails(
            irregularityIndex = irregularityIndex,
            numberOfSegments = significantDefects,
            description = description
        )
    }
    private fun analyzeColor(src: Mat, mask: Mat): ColorDetails {
        val lab = Mat()
        Imgproc.cvtColor(src, lab, Imgproc.COLOR_BGR2Lab)
        val molePixels = mutableListOf<DoubleArray>()
        for (y in 0 until lab.rows()) {
            for (x in 0 until lab.cols()) {
                if (mask.get(y, x)[0] > 0) {
                    molePixels.add(lab.get(y, x))
                }
            }
        }
        val k = 6
        val data = Mat(molePixels.size, 3, CvType.CV_32F)
        molePixels.forEachIndexed { i, pixel ->
            data.put(i, 0, floatArrayOf(pixel[0].toFloat(), pixel[1].toFloat(), pixel[2].toFloat()))
        }
        val labels = Mat()
        val centers = Mat()
        val criteria = TermCriteria(TermCriteria.EPS + TermCriteria.COUNT, 100, 0.2)
        Core.kmeans(data, k, labels, criteria, 10, Core.KMEANS_PP_CENTERS, centers)
        val clusterSizes = IntArray(k)
        for (i in 0 until labels.rows()) {
            val label = labels.get(i, 0)[0].toInt()
            clusterSizes[label]++
        }
        val sortedClusters = centers.toList()
            .mapIndexed { i, center -> Pair(center, clusterSizes[i]) }
            .sortedByDescending { it.second }
            .take(k)
        val hasBlueWhite = sortedClusters.any { (center, size) ->
            val l = center[0]
            val a = center[1]
            val b = center[2]
            l > 80 && abs(a) < 5 && b in -15.0..-5.0 &&
                    size > molePixels.size * 0.05
        }
        val hasRedBlue = detectRedBlueCombination(sortedClusters)
        val dominantColors = sortedClusters.map { (center, _) ->
            labToRgb(center[0], center[1], center[2])
        }
        val colorDiversity = calculateColorDiversity(centers)
        val description = buildString {
            append("${sortedClusters.filter { it.second > molePixels.size * 0.05 }.size} colores significativos. ")
            if (hasBlueWhite) append("⚠️ Velo azul-blanquecino detectado. ")
            if (hasRedBlue) append("⚠️ Combinación rojo-azul presente. ")
            if (colorDiversity > 0.7f) append("Alta diversidad cromática. ")
        }
        lab.release()
        data.release()
        labels.release()
        centers.release()
        return ColorDetails(
            dominantColors = dominantColors,
            colorCount = sortedClusters.filter { it.second > molePixels.size * 0.05 }.size,
            hasBlueWhite = hasBlueWhite,
            hasRedBlueCombination = hasRedBlue,
            description = description
        )
    }
    private fun detectRedBlueCombination(clusters: List<Pair<DoubleArray, Int>>): Boolean {
        val significantClusters = clusters.filter { it.second > clusters.sumOf { c -> c.second } * 0.05 }
        val hasRed = significantClusters.any { (center, _) ->
            center[1] > 20 && center[2] > 0
        }
        val hasBlue = significantClusters.any { (center, _) ->
            center[2] < -10
        }
        return hasRed && hasBlue
    }
    private fun calculateColorDiversity(centers: Mat): Float {
        if (centers.rows() < 2) return 0f
        var totalDistance = 0.0
        var count = 0
        for (i in 0 until centers.rows()) {
            for (j in i + 1 until centers.rows()) {
                val c1 = centers.row(i)
                val c2 = centers.row(j)
                val distance = Core.norm(c1, c2)
                totalDistance += distance
                count++
            }
        }
        val avgDistance = if (count > 0) totalDistance / count else 0.0
        return (avgDistance / 170.0).toFloat().coerceIn(0f, 1f)
    }
    private fun labToRgb(l: Double, a: Double, b: Double): Int {
        var y = (l + 16) / 116
        var x = a / 500 + y
        var z = y - b / 200
        val pow3 = { v: Double -> v * v * v }
        x = if (pow3(x) > 0.008856) pow3(x) else (x - 16.0/116) / 7.787
        y = if (pow3(y) > 0.008856) pow3(y) else (y - 16.0/116) / 7.787
        z = if (pow3(z) > 0.008856) pow3(z) else (z - 16.0/116) / 7.787
        x *= 95.047
        y *= 100.000
        z *= 108.883
        var r = x * 3.2406 + y * -1.5372 + z * -0.4986
        var g = x * -0.9689 + y * 1.8758 + z * 0.0415
        var b2 = x * 0.0557 + y * -0.2040 + z * 1.0570
        r = if (r > 0.0031308) 1.055 * r.pow(1/2.4) - 0.055 else 12.92 * r
        g = if (g > 0.0031308) 1.055 * g.pow(1/2.4) - 0.055 else 12.92 * g
        b2 = if (b2 > 0.0031308) 1.055 * b2.pow(1/2.4) - 0.055 else 12.92 * b2
        return android.graphics.Color.rgb(
            (r * 255).toInt().coerceIn(0, 255),
            (g * 255).toInt().coerceIn(0, 255),
            (b2 * 255).toInt().coerceIn(0, 255)
        )
    }
    private fun calculatePixelsPerMm(): Float {
        val metrics = context.resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2f
        val validatedDpi = when {
            averageDpi < 120f -> 160f
            averageDpi > 700f -> 450f
            else -> averageDpi
        }
        return validatedDpi / 25.4f
    }
    private fun analyzeDiameter(contour: MatOfPoint,
                                scaleFactor: Float = 1.0f
    ): DiameterDetails {
        val points = contour.toArray()
        if (points.isEmpty()) {
            Log.w(TAG, "Contorno vacío en analyzeDiameter")
            return DiameterDetails(0f, 0, "❌ No se pudo calcular el diámetro - contorno inválido")
        }
        
        val area = try {
            Imgproc.contourArea(contour)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando área: ${e.message}")
            0.0
        }
        
        var maxDistance = 0.0
        val step = maxOf(1, points.size / 50)
        for (i in points.indices step step) {
            for (j in i + step until points.size step step) {
                val distance = sqrt(
                    (points[i].x - points[j].x).pow(2) +
                            (points[i].y - points[j].y).pow(2)
                )
                maxDistance = maxOf(maxDistance, distance)
            }
        }
        val equivalentDiameter = if (area > 0) sqrt(4 * area / Math.PI) else maxDistance
        val diameterPx = maxOf(maxDistance, equivalentDiameter)
        val pixelsPerMm = calculatePixelsPerMm()
        val diameterMm = (diameterPx / (pixelsPerMm * scaleFactor)).toFloat()
        val description = when {
            diameterMm < 6f -> "Diámetro pequeño (<6mm) - Bajo riesgo"
            diameterMm < 10f -> "Diámetro medio (6-10mm) - Vigilar crecimiento"
            diameterMm < 15f -> "Diámetro grande (10-15mm) - Evaluar con dermatólogo"
            else -> "Diámetro muy grande (>15mm) - Alto riesgo, consultar urgente"
        }
        return DiameterDetails(
            diameterMm = diameterMm,
            areaPx = area.toInt(),
            description = description
        )
    }
    private fun analyzeEvolution(currentMat: Mat, previousMat: Mat): EvolutionDetails {
        val (alignedCurrent, alignedPrevious) = alignImages(currentMat, previousMat)
        val (currentMask, currentContour) = segmentMole(alignedCurrent)
        val (previousMask, previousContour) = segmentMole(alignedPrevious)
        val currentArea = Imgproc.contourArea(currentContour)
        val previousArea = Imgproc.contourArea(previousContour)
        val sizeChange = ((currentArea - previousArea) / previousArea).toFloat()
        val colorChange = compareColors(alignedCurrent, alignedPrevious, currentMask, previousMask)
        val shapeChange = compareShapes(currentContour, previousContour)
        val description = buildString {
            if (abs(sizeChange) > 0.15f) {
                append("Cambio de tamaño del ${(abs(sizeChange) * 100).toInt()}%. ")
            }
            if (colorChange > 0.2f) {
                append("Cambio significativo en color. ")
            }
            if (shapeChange > 0.25f) {
                append("Cambio en forma detectado. ")
            }
            if (abs(sizeChange) < 0.05f && colorChange < 0.1f && shapeChange < 0.1f) {
                append("Sin cambios significativos.")
            }
        }
        currentMask.release()
        previousMask.release()
        alignedCurrent.release()
        alignedPrevious.release()
        return EvolutionDetails(sizeChange, colorChange, shapeChange, description)
    }
    private fun alignImages(img1: Mat, img2: Mat): Pair<Mat, Mat> {
        val size = Size(
            minOf(img1.cols(), img2.cols()).toDouble(),
            minOf(img1.rows(), img2.rows()).toDouble()
        )
        val resized1 = Mat()
        val resized2 = Mat()
        Imgproc.resize(img1, resized1, size)
        Imgproc.resize(img2, resized2, size)
        return Pair(resized1, resized2)
    }
    private fun compareColors(img1: Mat, img2: Mat, mask1: Mat, mask2: Mat): Float {
        val hsv1 = Mat()
        val hsv2 = Mat()
        Imgproc.cvtColor(img1, hsv1, Imgproc.COLOR_BGR2HSV)
        Imgproc.cvtColor(img2, hsv2, Imgproc.COLOR_BGR2HSV)
        val histSize = MatOfInt(50, 60)
        val ranges = MatOfFloat(0f, 180f, 0f, 256f)
        val channels = MatOfInt(0, 1)
        val hist1 = Mat()
        val hist2 = Mat()
        Imgproc.calcHist(listOf(hsv1), channels, mask1, hist1, histSize, ranges)
        Imgproc.calcHist(listOf(hsv2), channels, mask2, hist2, histSize, ranges)
        Core.normalize(hist1, hist1, 0.0, 1.0, Core.NORM_MINMAX)
        Core.normalize(hist2, hist2, 0.0, 1.0, Core.NORM_MINMAX)
        val correlation = Imgproc.compareHist(hist1, hist2, Imgproc.CV_COMP_CORREL)
        hsv1.release()
        hsv2.release()
        hist1.release()
        hist2.release()
        return (1 - correlation).toFloat().coerceIn(0f, 1f)
    }
    private fun compareShapes(contour1: MatOfPoint, contour2: MatOfPoint): Float {
        val moments1 = Imgproc.moments(contour1)
        val moments2 = Imgproc.moments(contour2)
        val huMoments1 = Mat()
        val huMoments2 = Mat()
        Imgproc.HuMoments(moments1, huMoments1)
        Imgproc.HuMoments(moments2, huMoments2)
        var distance = 0.0
        for (i in 0 until 7) {
            val m1 = huMoments1.get(i, 0)[0]
            val m2 = huMoments2.get(i, 0)[0]
            val logM1 = -1 * Math.signum(m1) * ln(abs(m1) + 1e-10)
            val logM2 = -1 * Math.signum(m2) * ln(abs(m2) + 1e-10)
            distance += abs(logM1 - logM2)
        }
        huMoments1.release()
        huMoments2.release()
        return (distance / 7).toFloat().coerceIn(0f, 1f)
    }
    private fun calculateAsymmetryScore(details: AsymmetryDetails): Float {
        val maxAsymmetry = maxOf(details.horizontalAsymmetry, details.verticalAsymmetry)
        return when {
            maxAsymmetry < 0.05f -> 0f
            maxAsymmetry < 0.10f -> 0.5f
            maxAsymmetry < 0.15f -> 1f
            maxAsymmetry < 0.25f -> 1.5f
            else -> 2f
        }
    }
    private fun calculateBorderScore(details: BorderDetails): Float {
        return minOf(8f, details.numberOfSegments.toFloat() + details.irregularityIndex * 4)
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
        val totalChange = abs(details.sizeChange) + details.colorChange + details.shapeChange
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
        var score = (asymmetry * 1.3f) + (border * 0.1f) + (color * 0.5f) + (diameter * 0.5f)
        evolution?.let { score *= (1 + it * 0.2f) }
        return score
    }
    private fun Mat.toList(): List<DoubleArray> {
        val list = mutableListOf<DoubleArray>()
        for (i in 0 until this.rows()) {
            val row = when (this.type()) {
                CvType.CV_64F -> {
                    val temp = DoubleArray(this.cols())
                    this.get(i, 0, temp)
                    temp
                }
                CvType.CV_32F -> {
                    val temp = FloatArray(this.cols())
                    this.get(i, 0, temp)
                    temp.map { it.toDouble() }.toDoubleArray()
                }
                else -> throw UnsupportedOperationException("Mat data type is not compatible: ${this.type()}")
            }
            list.add(row)
        }
        return list
    }
}
package es.monsteraltech.skincare_tfm.camera.guidance

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc


class MoleDetectionProcessor {
    companion object {
        private const val TAG = "MoleDetectionProcessor"
        private const val MIN_MOLE_SIZE = 30
        private const val MAX_MOLE_SIZE = 1200
        private const val CENTER_TOLERANCE = 150f
        private const val THRESHOLD_VALUE = 120.0
    }

    data class MoleDetection(
        val centerPoint: Point,
        val confidence: Float,
        val area: Double,
        val isInCenter: Boolean
    )

    suspend fun detectMoleInCenter(frame: Mat): MoleDetection? = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Buscando lunar en el centro del frame ${frame.cols()}x${frame.rows()}")
            
            if (frame.empty() || frame.cols() < 100 || frame.rows() < 100) {
                Log.w(TAG, "Frame inválido o muy pequeño")
                return@withContext null
            }

            val centerX = frame.cols() / 2.0
            val centerY = frame.rows() / 2.0
            val centerPoint = Point(centerX, centerY)

            val grayFrame = Mat()
            if (frame.channels() == 3) {
                Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY)
            } else {
                frame.copyTo(grayFrame)
            }

            var detection = findDarkSpotInCenter(grayFrame, centerPoint)

            if (detection == null) {
                Log.d(TAG, "Primera detección falló, intentando con umbral más permisivo")
                detection = findDarkSpotWithLowerThreshold(grayFrame, centerPoint)
            }

            if (detection == null) {
                Log.d(TAG, "Segunda detección falló, intentando detectar variaciones de intensidad")
                detection = findAnyIntensityVariation(grayFrame, centerPoint)
            }
            
            grayFrame.release()
            
            detection?.let {
                Log.d(TAG, "Lunar detectado en centro: confianza=${it.confidence}, área=${it.area}")
            } ?: Log.d(TAG, "No se detectó lunar en el centro")
            
            return@withContext detection
            
        } catch (e: Exception) {
            Log.e(TAG, "Error durante detección de lunar", e)
            return@withContext null
        }
    }

    private fun findDarkSpotInCenter(grayFrame: Mat, imageCenter: Point): MoleDetection? {
        try {
            val darkMask = Mat()
            Imgproc.threshold(grayFrame, darkMask, THRESHOLD_VALUE, 255.0, Imgproc.THRESH_BINARY_INV)

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0)) // Kernel más pequeño
            Imgproc.morphologyEx(darkMask, darkMask, Imgproc.MORPH_CLOSE, kernel)

            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(darkMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            var bestDetection: MoleDetection? = null
            var minDistanceToCenter = Double.MAX_VALUE
            
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)

                if (area < MIN_MOLE_SIZE || area > MAX_MOLE_SIZE) continue

                val moments = Imgproc.moments(contour)
                if (moments.m00 == 0.0) continue
                
                val centerX = moments.m10 / moments.m00
                val centerY = moments.m01 / moments.m00
                val contourCenter = Point(centerX, centerY)

                val distanceToCenter = Math.sqrt(
                    Math.pow(contourCenter.x - imageCenter.x, 2.0) + 
                    Math.pow(contourCenter.y - imageCenter.y, 2.0)
                )

                if (distanceToCenter < CENTER_TOLERANCE && distanceToCenter < minDistanceToCenter) {
                    val confidence = calculateSimpleConfidence(area, distanceToCenter)
                    val isInCenter = distanceToCenter < CENTER_TOLERANCE
                    
                    bestDetection = MoleDetection(
                        centerPoint = contourCenter,
                        confidence = confidence,
                        area = area,
                        isInCenter = isInCenter
                    )
                    minDistanceToCenter = distanceToCenter
                }
            }

            darkMask.release()
            kernel.release()
            hierarchy.release()
            contours.forEach { it.release() }
            
            return bestDetection
            
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando mancha oscura en centro", e)
            return null
        }
    }
    

    private fun calculateSimpleConfidence(area: Double, distanceToCenter: Double): Float {

        var confidence = 0.6f

        val areaFactor = when {
            area in 50.0..500.0 -> 0.3f
            area in 20.0..1000.0 -> 0.2f
            else -> 0.1f
        }
        confidence += areaFactor

        val centerFactor = (1.0f - (distanceToCenter / CENTER_TOLERANCE).toFloat()) * 0.2f
        confidence += centerFactor.coerceAtLeast(0f)

        return confidence.coerceIn(0.5f, 1f)  // Mínimo 0.5f en lugar de 0f
    }
    
    /**
     * Método de detección con umbral aún más bajo para casos difíciles
     */
    private fun findDarkSpotWithLowerThreshold(grayFrame: Mat, imageCenter: Point): MoleDetection? {
        try {
            Log.d(TAG, "Intentando detección con umbral muy bajo")
            
            val darkMask = Mat()

            Imgproc.threshold(grayFrame, darkMask, 140.0, 255.0, Imgproc.THRESH_BINARY_INV)

            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(darkMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            
            var bestDetection: MoleDetection? = null
            var minDistanceToCenter = Double.MAX_VALUE
            
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)

                if (area < 10 || area > 5000) continue
                
                val moments = Imgproc.moments(contour)
                if (moments.m00 == 0.0) continue
                
                val centerX = moments.m10 / moments.m00
                val centerY = moments.m01 / moments.m00
                val contourCenter = Point(centerX, centerY)
                
                val distanceToCenter = Math.sqrt(
                    Math.pow(contourCenter.x - imageCenter.x, 2.0) + 
                    Math.pow(contourCenter.y - imageCenter.y, 2.0)
                )

                if (distanceToCenter < CENTER_TOLERANCE * 1.5 && distanceToCenter < minDistanceToCenter) {
                    val confidence = 0.7f
                    val isInCenter = distanceToCenter < CENTER_TOLERANCE * 1.2
                    
                    bestDetection = MoleDetection(
                        centerPoint = contourCenter,
                        confidence = confidence,
                        area = area,
                        isInCenter = isInCenter
                    )
                    minDistanceToCenter = distanceToCenter
                }
            }
            
            darkMask.release()
            hierarchy.release()
            contours.forEach { it.release() }
            
            return bestDetection
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en detección con umbral bajo", e)
            return null
        }
    }
    

    private fun findAnyIntensityVariation(grayFrame: Mat, imageCenter: Point): MoleDetection? {
        try {
            Log.d(TAG, "Intentando detectar cualquier variación de intensidad")

            val edges = Mat()
            Imgproc.Canny(grayFrame, edges, 30.0, 80.0)

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0))
            Imgproc.dilate(edges, edges, kernel)
            
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area < 5) continue
                
                val moments = Imgproc.moments(contour)
                if (moments.m00 == 0.0) continue
                
                val centerX = moments.m10 / moments.m00
                val centerY = moments.m01 / moments.m00
                val contourCenter = Point(centerX, centerY)
                
                val distanceToCenter = Math.sqrt(
                    Math.pow(contourCenter.x - imageCenter.x, 2.0) + 
                    Math.pow(contourCenter.y - imageCenter.y, 2.0)
                )

                if (distanceToCenter < CENTER_TOLERANCE * 2.0) {
                    Log.d(TAG, "Detectada variación de intensidad como posible lunar")
                    
                    edges.release()
                    kernel.release()
                    hierarchy.release()
                    contours.forEach { it.release() }
                    
                    return MoleDetection(
                        centerPoint = contourCenter,
                        confidence = 0.6f,  // Confianza moderada-alta
                        area = area,
                        isInCenter = distanceToCenter < CENTER_TOLERANCE * 1.5
                    )
                }
            }
            
            edges.release()
            kernel.release()
            hierarchy.release()
            contours.forEach { it.release() }
            
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en detección de variaciones", e)
            return null
        }
    }
}
package es.monsteraltech.skincare_tfm.camera.guidance

import android.graphics.PointF
import android.graphics.RectF
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.MatOfPoint

/**
 * Tests de escenarios complejos para CaptureValidationManager
 * Verifica combinaciones de condiciones y casos límite
 */
class CaptureValidationScenariosTest {

    private lateinit var validationManager: CaptureValidationManager
    private lateinit var guideArea: RectF
    
    private val guideCenterX = 400f
    private val guideCenterY = 400f
    private val guideRadius = 150f

    @Before
    fun setUp() {
        validationManager = CaptureValidationManager()
        guideArea = RectF(
            guideCenterX - guideRadius,
            guideCenterY - guideRadius,
            guideCenterX + guideRadius,
            guideCenterY + guideRadius
        )
    }

    @Test
    fun `scenario - perfect conditions should always pass`() {
        // Arrange: Condiciones perfectas
        val moleDetection = createMoleDetection(
            centerX = guideCenterX,
            centerY = guideCenterY,
            area = calculateOptimalArea(),
            confidence = 0.95f
        )
        
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.8f,
            brightness = 130f,
            contrast = 60f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert
        assertTrue("Perfect conditions should allow capture", result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.READY, result.guideState)
        assertEquals("Listo para capturar", result.message)
        assertNull(result.failureReason)
        assertTrue("High confidence expected", result.confidence > 0.9f)
    }

    @Test
    fun `scenario - borderline centering should be handled correctly`() {
        // Arrange: Lunar exactamente en el límite de tolerancia de centrado
        val toleranceDistance = 50f
        val moleDetection = createMoleDetection(
            centerX = guideCenterX + toleranceDistance, // Exactamente en el límite
            centerY = guideCenterY,
            area = calculateOptimalArea(),
            confidence = 0.8f
        )
        val qualityMetrics = createGoodQualityMetrics()
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert
        assertTrue("Borderline centering should pass", result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.READY, result.guideState)
        assertEquals(toleranceDistance, result.distanceFromCenter, 0.1f)
    }

    @Test
    fun `scenario - just outside centering tolerance should fail`() {
        // Arrange: Lunar ligeramente fuera de tolerancia
        val moleDetection = createMoleDetection(
            centerX = guideCenterX + 51f, // 1 pixel fuera de tolerancia
            centerY = guideCenterY,
            area = calculateOptimalArea(),
            confidence = 0.8f
        )
        val qualityMetrics = createGoodQualityMetrics()
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert
        assertFalse("Just outside tolerance should fail", result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.CENTERING, result.guideState)
        assertEquals(CaptureValidationManager.ValidationFailureReason.NOT_CENTERED, result.failureReason)
    }

    @Test
    fun `scenario - minimum size threshold should be respected`() {
        // Arrange: Lunar exactamente en el tamaño mínimo
        val guideAreaSize = guideArea.width() * guideArea.height()
        val minArea = guideAreaSize * 0.15 // Exactamente 15%
        
        val moleDetection = createMoleDetection(
            centerX = guideCenterX,
            centerY = guideCenterY,
            area = minArea.toDouble(),
            confidence = 0.8f
        )
        val qualityMetrics = createGoodQualityMetrics()
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert
        assertTrue("Minimum size should pass", result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.READY, result.guideState)
        assertEquals(0.15f, result.moleAreaRatio, 0.01f)
    }

    @Test
    fun `scenario - maximum size threshold should be respected`() {
        // Arrange: Lunar exactamente en el tamaño máximo
        val guideAreaSize = guideArea.width() * guideArea.height()
        val maxArea = guideAreaSize * 0.80 // Exactamente 80%
        
        val moleDetection = createMoleDetection(
            centerX = guideCenterX,
            centerY = guideCenterY,
            area = maxArea.toDouble(),
            confidence = 0.8f
        )
        val qualityMetrics = createGoodQualityMetrics()
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert
        assertTrue("Maximum size should pass", result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.READY, result.guideState)
        assertEquals(0.80f, result.moleAreaRatio, 0.01f)
    }

    @Test
    fun `scenario - multiple quality issues should prioritize most critical`() {
        // Arrange: Múltiples problemas de calidad
        val moleDetection = createMoleDetection(
            centerX = guideCenterX + 80f, // No centrado
            centerY = guideCenterY,
            area = calculateOptimalArea(),
            confidence = 0.8f
        )
        
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.1f, // Borroso
            brightness = 50f,  // Subexpuesto
            contrast = 20f,
            isBlurry = true,
            isOverexposed = false,
            isUnderexposed = true
        )
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert - La calidad de imagen debe tener prioridad sobre el posicionamiento
        assertFalse(result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.BLURRY, result.guideState)
        assertEquals(CaptureValidationManager.ValidationFailureReason.BLURRY, result.failureReason)
    }

    @Test
    fun `scenario - diagonal positioning should calculate distance correctly`() {
        // Arrange: Lunar en posición diagonal
        val offset = 35f // Debería resultar en distancia ~49.5 (dentro de tolerancia)
        val moleDetection = createMoleDetection(
            centerX = guideCenterX + offset,
            centerY = guideCenterY + offset,
            area = calculateOptimalArea(),
            confidence = 0.8f
        )
        val qualityMetrics = createGoodQualityMetrics()
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert
        assertTrue("Diagonal positioning within tolerance should pass", result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.READY, result.guideState)
        
        // Verificar cálculo de distancia diagonal
        val expectedDistance = kotlin.math.sqrt((offset * offset * 2).toDouble()).toFloat()
        assertEquals(expectedDistance, result.distanceFromCenter, 1f)
    }

    @Test
    fun `scenario - confidence threshold edge cases`() {
        // Arrange: Confianza exactamente en el límite
        val moleDetection = createMoleDetection(
            centerX = guideCenterX,
            centerY = guideCenterY,
            area = calculateOptimalArea(),
            confidence = 0.6f // Exactamente en el mínimo
        )
        val qualityMetrics = createGoodQualityMetrics()
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert
        assertTrue("Minimum confidence should pass", result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.READY, result.guideState)
        assertEquals(0.6f, result.confidence, 0.01f)
    }

    @Test
    fun `scenario - just below confidence threshold should fail`() {
        // Arrange: Confianza ligeramente por debajo del límite
        val moleDetection = createMoleDetection(
            centerX = guideCenterX,
            centerY = guideCenterY,
            area = calculateOptimalArea(),
            confidence = 0.59f // Justo por debajo del mínimo
        )
        val qualityMetrics = createGoodQualityMetrics()
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert
        assertFalse("Below confidence threshold should fail", result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.SEARCHING, result.guideState)
        assertEquals(CaptureValidationManager.ValidationFailureReason.LOW_CONFIDENCE, result.failureReason)
    }

    @Test
    fun `scenario - very small guide area should handle ratios correctly`() {
        // Arrange: Área de guía muy pequeña
        val smallGuideArea = RectF(390f, 390f, 410f, 410f) // 20x20 pixels
        val moleDetection = createMoleDetection(
            centerX = 400f,
            centerY = 400f,
            area = 100.0, // 25% del área pequeña
            confidence = 0.8f
        )
        val qualityMetrics = createGoodQualityMetrics()
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, smallGuideArea)
        
        // Assert
        assertTrue("Small guide area should work correctly", result.canCapture)
        assertEquals(0.25f, result.moleAreaRatio, 0.01f)
    }

    @Test
    fun `scenario - very large guide area should handle ratios correctly`() {
        // Arrange: Área de guía muy grande
        val largeGuideArea = RectF(0f, 0f, 1000f, 1000f) // 1000x1000 pixels
        val moleDetection = createMoleDetection(
            centerX = 500f,
            centerY = 500f,
            area = 200000.0, // 20% del área grande
            confidence = 0.8f
        )
        val qualityMetrics = createGoodQualityMetrics()
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, largeGuideArea)
        
        // Assert
        assertTrue("Large guide area should work correctly", result.canCapture)
        assertEquals(0.20f, result.moleAreaRatio, 0.01f)
    }

    @Test
    fun `scenario - centering percentage calculation accuracy`() {
        // Arrange: Diferentes posiciones para verificar cálculo de porcentaje
        val guideCenter = PointF(guideCenterX, guideCenterY)
        
        // Act & Assert
        // Centro perfecto
        val perfectCenter = PointF(guideCenterX, guideCenterY)
        assertEquals(100f, validationManager.calculateCenteringPercentage(perfectCenter, guideCenter), 0.1f)
        
        // 25% fuera del centro
        val quarterOff = PointF(guideCenterX + 12.5f, guideCenterY)
        assertEquals(75f, validationManager.calculateCenteringPercentage(quarterOff, guideCenter), 1f)
        
        // 50% fuera del centro
        val halfOff = PointF(guideCenterX + 25f, guideCenterY)
        assertEquals(50f, validationManager.calculateCenteringPercentage(halfOff, guideCenter), 1f)
    }

    @Test
    fun `scenario - size percentage calculation accuracy`() {
        // Act & Assert
        val optimalSize = (0.15f + 0.80f) / 2f // 0.475f
        
        // Tamaño óptimo
        assertEquals(100f, validationManager.calculateSizePercentage(optimalSize), 0.1f)
        
        // 25% más pequeño que óptimo
        val smallerSize = optimalSize - (optimalSize - 0.15f) * 0.25f
        assertTrue("25% smaller should be around 75%", 
            validationManager.calculateSizePercentage(smallerSize) > 70f)
        
        // 25% más grande que óptimo
        val largerSize = optimalSize + (0.80f - optimalSize) * 0.25f
        assertTrue("25% larger should be around 75%", 
            validationManager.calculateSizePercentage(largerSize) > 70f)
    }

    // Métodos auxiliares

    private fun createGoodQualityMetrics(): ImageQualityAnalyzer.QualityMetrics {
        return ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.5f,
            brightness = 120f,
            contrast = 50f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
    }

    private fun calculateOptimalArea(): Double {
        val guideAreaSize = guideArea.width() * guideArea.height()
        val optimalRatio = (0.15f + 0.80f) / 2f // 0.475f
        return (guideAreaSize * optimalRatio).toDouble()
    }

    private fun createMoleDetection(
        centerX: Float,
        centerY: Float,
        area: Double,
        confidence: Float
    ): MoleDetectionProcessor.MoleDetection {
        val boundingBox = Rect(
            (centerX - 25).toInt(),
            (centerY - 25).toInt(),
            50,
            50
        )
        
        val centerPoint = Point(centerX.toDouble(), centerY.toDouble())
        val contour = MatOfPoint()
        
        return MoleDetectionProcessor.MoleDetection(
            boundingBox = boundingBox,
            centerPoint = centerPoint,
            confidence = confidence,
            area = area,
            contour = contour,
            method = "test"
        )
    }
}
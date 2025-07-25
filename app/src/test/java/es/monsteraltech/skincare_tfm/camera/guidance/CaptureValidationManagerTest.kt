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
 * Tests unitarios para CaptureValidationManager
 * Verifica la lógica de validación de captura en diferentes escenarios
 */
class CaptureValidationManagerTest {

    private lateinit var validationManager: CaptureValidationManager
    private lateinit var guideArea: RectF
    
    // Área de guía estándar centrada en 400x400
    private val guideCenterX = 400f
    private val guideCenterY = 400f
    private val guideRadius = 150f

    @Before
    fun setUp() {
        validationManager = CaptureValidationManager()
        
        // Crear área de guía circular simulada como RectF
        guideArea = RectF(
            guideCenterX - guideRadius,
            guideCenterY - guideRadius,
            guideCenterX + guideRadius,
            guideCenterY + guideRadius
        )
    }

    @Test
    fun `validateCapture should return SEARCHING when no mole detected`() {
        // Arrange
        val qualityMetrics = createGoodQualityMetrics()
        
        // Act
        val result = validationManager.validateCapture(null, qualityMetrics, guideArea)
        
        // Assert
        assertFalse(result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.SEARCHING, result.guideState)
        assertEquals("Buscando lunar...", result.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.NO_MOLE_DETECTED, result.failureReason)
        assertEquals(0f, result.confidence, 0.01f)
    }

    @Test
    fun `validateCapture should return BLURRY when image is blurry`() {
        // Arrange
        val moleDetection = createCenteredMoleDetection()
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.1f, // Muy baja nitidez
            brightness = 120f,
            contrast = 50f,
            isBlurry = true,
            isOverexposed = false,
            isUnderexposed = false
        )
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert
        assertFalse(result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.BLURRY, result.guideState)
        assertEquals("Imagen borrosa - mantén firme la cámara", result.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.BLURRY, result.failureReason)
    }

    @Test
    fun `validateCapture should return POOR_LIGHTING when underexposed`() {
        // Arrange
        val moleDetection = createCenteredMoleDetection()
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.5f,
            brightness = 50f, // Muy oscuro
            contrast = 30f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = true
        )
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert
        assertFalse(result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.POOR_LIGHTING, result.guideState)
        assertEquals("Necesitas más luz", result.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.POOR_LIGHTING, result.failureReason)
    }

    @Test
    fun `validateCapture should return POOR_LIGHTING when overexposed`() {
        // Arrange
        val moleDetection = createCenteredMoleDetection()
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.5f,
            brightness = 220f, // Muy brillante
            contrast = 30f,
            isBlurry = false,
            isOverexposed = true,
            isUnderexposed = false
        )
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert
        assertFalse(result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.POOR_LIGHTING, result.guideState)
        assertEquals("Demasiada luz - busca sombra", result.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.POOR_LIGHTING, result.failureReason)
    }

    @Test
    fun `validateCapture should return CENTERING when mole not centered`() {
        // Arrange
        val moleDetection = createMoleDetection(
            centerX = guideCenterX + 80f, // Fuera de tolerancia de centrado (50px)
            centerY = guideCenterY,
            area = 5000.0, // Tamaño apropiado
            confidence = 0.8f
        )
        val qualityMetrics = createGoodQualityMetrics()
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert
        assertFalse(result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.CENTERING, result.guideState)
        assertEquals("Centra el lunar en la guía", result.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.NOT_CENTERED, result.failureReason)
        assertTrue(result.distanceFromCenter > 50f)
    }

    @Test
    fun `validateCapture should return TOO_FAR when mole is too small`() {
        // Arrange
        val guideAreaSize = guideArea.width() * guideArea.height()
        val smallArea = guideAreaSize * 0.10 // 10% del área de guía (menor al mínimo 15%)
        
        val moleDetection = createMoleDetection(
            centerX = guideCenterX,
            centerY = guideCenterY,
            area = smallArea.toDouble(),
            confidence = 0.8f
        )
        val qualityMetrics = createGoodQualityMetrics()
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert
        assertFalse(result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.TOO_FAR, result.guideState)
        assertEquals("Acércate más al lunar", result.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.TOO_FAR, result.failureReason)
        assertTrue(result.moleAreaRatio < 0.15f)
    }

    @Test
    fun `validateCapture should return TOO_CLOSE when mole is too large`() {
        // Arrange
        val guideAreaSize = guideArea.width() * guideArea.height()
        val largeArea = guideAreaSize * 0.90 // 90% del área de guía (mayor al máximo 80%)
        
        val moleDetection = createMoleDetection(
            centerX = guideCenterX,
            centerY = guideCenterY,
            area = largeArea.toDouble(),
            confidence = 0.8f
        )
        val qualityMetrics = createGoodQualityMetrics()
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert
        assertFalse(result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.TOO_CLOSE, result.guideState)
        assertEquals("Aléjate un poco", result.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.TOO_CLOSE, result.failureReason)
        assertTrue(result.moleAreaRatio > 0.80f)
    }

    @Test
    fun `validateCapture should return READY when all conditions are met`() {
        // Arrange
        val moleDetection = createCenteredMoleDetection()
        val qualityMetrics = createGoodQualityMetrics()
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert
        assertTrue(result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.READY, result.guideState)
        assertEquals("Listo para capturar", result.message)
        assertNull(result.failureReason)
        assertTrue(result.confidence > 0.6f)
        assertTrue(result.distanceFromCenter <= 50f)
        assertTrue(result.moleAreaRatio >= 0.15f && result.moleAreaRatio <= 0.80f)
    }

    @Test
    fun `validateCapture should reject low confidence detection`() {
        // Arrange
        val moleDetection = createMoleDetection(
            centerX = guideCenterX,
            centerY = guideCenterY,
            area = 5000.0,
            confidence = 0.4f // Menor al mínimo requerido (0.6f)
        )
        val qualityMetrics = createGoodQualityMetrics()
        
        // Act
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        
        // Assert
        assertFalse(result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.SEARCHING, result.guideState)
        assertEquals("Detección poco confiable - ajusta la posición", result.message)
        assertEquals(CaptureValidationManager.ValidationFailureReason.LOW_CONFIDENCE, result.failureReason)
        assertEquals(0.4f, result.confidence, 0.01f)
    }

    @Test
    fun `calculateCenteringPercentage should return correct values`() {
        // Arrange
        val perfectCenter = PointF(guideCenterX, guideCenterY)
        val slightlyOff = PointF(guideCenterX + 10f, guideCenterY)
        val atTolerance = PointF(guideCenterX + 50f, guideCenterY)
        val beyondTolerance = PointF(guideCenterX + 80f, guideCenterY)
        val guideCenter = PointF(guideCenterX, guideCenterY)
        
        // Act & Assert
        assertEquals(100f, validationManager.calculateCenteringPercentage(perfectCenter, guideCenter), 0.1f)
        assertEquals(80f, validationManager.calculateCenteringPercentage(slightlyOff, guideCenter), 0.1f)
        assertEquals(0f, validationManager.calculateCenteringPercentage(atTolerance, guideCenter), 0.1f)
        assertEquals(0f, validationManager.calculateCenteringPercentage(beyondTolerance, guideCenter), 0.1f)
    }

    @Test
    fun `calculateSizePercentage should return correct values`() {
        // Act & Assert
        val optimalRatio = (0.15f + 0.80f) / 2f // 0.475f
        assertEquals(100f, validationManager.calculateSizePercentage(optimalRatio), 0.1f)
        assertEquals(0f, validationManager.calculateSizePercentage(0.10f), 0.1f) // Muy pequeño
        assertEquals(0f, validationManager.calculateSizePercentage(0.90f), 0.1f) // Muy grande
        
        // Valores intermedios
        assertTrue(validationManager.calculateSizePercentage(0.30f) > 50f)
        assertTrue(validationManager.calculateSizePercentage(0.60f) > 50f)
    }

    @Test
    fun `getDetailedGuidanceMessage should return appropriate messages`() {
        // Arrange & Act & Assert
        val searchingResult = CaptureValidationManager.ValidationResult(
            false, CaptureValidationManager.GuideState.SEARCHING, "", 0f
        )
        assertEquals("Busca un lunar y colócalo en el centro de la guía", 
            validationManager.getDetailedGuidanceMessage(searchingResult))
        
        val centeringResult = CaptureValidationManager.ValidationResult(
            false, CaptureValidationManager.GuideState.CENTERING, "", 0.8f, 
            distanceFromCenter = 30f
        )
        assertEquals("Mueve la cámara para centrar el lunar", 
            validationManager.getDetailedGuidanceMessage(centeringResult))
        
        val readyResult = CaptureValidationManager.ValidationResult(
            true, CaptureValidationManager.GuideState.READY, "", 0.9f
        )
        assertEquals("¡Perfecto! Toca para capturar", 
            validationManager.getDetailedGuidanceMessage(readyResult))
    }

    // Métodos auxiliares para crear objetos de prueba

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

    private fun createCenteredMoleDetection(): MoleDetectionProcessor.MoleDetection {
        return createMoleDetection(
            centerX = guideCenterX,
            centerY = guideCenterY,
            area = 5000.0, // Aproximadamente 22% del área de guía (300x300 = 90000)
            confidence = 0.8f
        )
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
        val contour = MatOfPoint() // Mock contour
        
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
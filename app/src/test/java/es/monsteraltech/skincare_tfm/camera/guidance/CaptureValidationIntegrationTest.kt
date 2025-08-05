package es.monsteraltech.skincare_tfm.camera.guidance
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Rect

class CaptureValidationIntegrationTest {
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
    fun `integration - complete validation workflow with good conditions`() {
        val moleDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(375, 375, 50, 50),
            centerPoint = Point(400.0, 400.0),
            confidence = 0.85f,
            area = 5000.0,
            contour = MatOfPoint(),
            method = "primary_color"
        )
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.6f,
            brightness = 125f,
            contrast = 55f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        assertTrue("Integration with good conditions should succeed", result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.READY, result.guideState)
        assertEquals("Listo para capturar", result.message)
        assertNull(result.failureReason)
        assertTrue("Distance should be minimal", result.distanceFromCenter < 10f)
        assertTrue("Area ratio should be reasonable", result.moleAreaRatio > 0.15f && result.moleAreaRatio < 0.80f)
        assertEquals(0.85f, result.confidence, 0.01f)
    }
    @Test
    fun `integration - validation priority order should be correct`() {
        val moleDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(300, 300, 50, 50),
            centerPoint = Point(325.0, 325.0),
            confidence = 0.4f,
            area = 1000.0,
            contour = MatOfPoint(),
            method = "alternative_watershed"
        )
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.1f,
            brightness = 40f,
            contrast = 15f,
            isBlurry = true,
            isOverexposed = false,
            isUnderexposed = true
        )
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        assertFalse(result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.BLURRY, result.guideState)
        assertEquals(CaptureValidationManager.ValidationFailureReason.BLURRY, result.failureReason)
        assertEquals("Imagen borrosa - mantén firme la cámara", result.message)
    }
    @Test
    fun `integration - confidence validation should come after quality`() {
        val moleDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(375, 375, 50, 50),
            centerPoint = Point(400.0, 400.0),
            confidence = 0.3f,
            area = 5000.0,
            contour = MatOfPoint(),
            method = "primary_color"
        )
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.7f,
            brightness = 130f,
            contrast = 60f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        assertFalse(result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.SEARCHING, result.guideState)
        assertEquals(CaptureValidationManager.ValidationFailureReason.LOW_CONFIDENCE, result.failureReason)
        assertEquals("Detección poco confiable - ajusta la posición", result.message)
        assertEquals(0.3f, result.confidence, 0.01f)
    }
    @Test
    fun `integration - positioning validation should come after confidence`() {
        val moleDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(300, 300, 50, 50),
            centerPoint = Point(325.0, 325.0),
            confidence = 0.8f,
            area = 5000.0,
            contour = MatOfPoint(),
            method = "primary_color"
        )
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.7f,
            brightness = 130f,
            contrast = 60f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        assertFalse(result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.CENTERING, result.guideState)
        assertEquals(CaptureValidationManager.ValidationFailureReason.NOT_CENTERED, result.failureReason)
        assertEquals("Centra el lunar en la guía", result.message)
        assertTrue("Distance should be significant", result.distanceFromCenter > 50f)
    }
    @Test
    fun `integration - size validation should be last positioning check`() {
        val guideAreaSize = guideArea.width() * guideArea.height()
        val tooSmallArea = guideAreaSize * 0.05
        val moleDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(375, 375, 50, 50),
            centerPoint = Point(400.0, 400.0),
            confidence = 0.8f,
            area = tooSmallArea.toDouble(),
            contour = MatOfPoint(),
            method = "primary_color"
        )
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.7f,
            brightness = 130f,
            contrast = 60f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        assertFalse(result.canCapture)
        assertEquals(CaptureValidationManager.GuideState.TOO_FAR, result.guideState)
        assertEquals(CaptureValidationManager.ValidationFailureReason.TOO_FAR, result.failureReason)
        assertEquals("Acércate más al lunar", result.message)
        assertTrue("Area ratio should be very small", result.moleAreaRatio < 0.10f)
    }
    @Test
    fun `integration - detailed guidance messages should reflect current state`() {
        val searchingResult = CaptureValidationManager.ValidationResult(
            canCapture = false,
            guideState = CaptureValidationManager.GuideState.SEARCHING,
            message = "Buscando lunar...",
            confidence = 0f
        )
        assertEquals("Busca un lunar y colócalo en el centro de la guía",
            validationManager.getDetailedGuidanceMessage(searchingResult))
        val centeringResult = CaptureValidationManager.ValidationResult(
            canCapture = false,
            guideState = CaptureValidationManager.GuideState.CENTERING,
            message = "Centra el lunar en la guía",
            confidence = 0.8f,
            distanceFromCenter = 35f
        )
        assertEquals("Mueve la cámara para centrar el lunar",
            validationManager.getDetailedGuidanceMessage(centeringResult))
        val readyResult = CaptureValidationManager.ValidationResult(
            canCapture = true,
            guideState = CaptureValidationManager.GuideState.READY,
            message = "Listo para capturar",
            confidence = 0.9f
        )
        assertEquals("¡Perfecto! Toca para capturar",
            validationManager.getDetailedGuidanceMessage(readyResult))
    }
    @Test
    fun `integration - percentage calculations should be consistent with validation`() {
        val moleDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(385, 385, 30, 30),
            centerPoint = Point(400.0, 400.0),
            confidence = 0.8f,
            area = 4500.0,
            contour = MatOfPoint(),
            method = "primary_color"
        )
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.6f,
            brightness = 125f,
            contrast = 55f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        val result = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        assertTrue("Should pass validation", result.canCapture)
        val centeringPercentage = validationManager.calculateCenteringPercentage(
            android.graphics.PointF(400f, 400f),
            android.graphics.PointF(guideCenterX, guideCenterY)
        )
        assertEquals(100f, centeringPercentage, 0.1f)
        val sizePercentage = validationManager.calculateSizePercentage(result.moleAreaRatio)
        assertTrue("Size percentage should be reasonable", sizePercentage > 50f)
    }
    @Test
    fun `integration - config updates should affect validation behavior`() {
        val strictConfig = CaptureValidationManager.ValidationConfig(
            centeringTolerance = 25f,
            minMoleAreaRatio = 0.20f,
            maxMoleAreaRatio = 0.70f,
            minConfidence = 0.8f
        )
        val moleDetection = MoleDetectionProcessor.MoleDetection(
            boundingBox = Rect(360, 360, 50, 50),
            centerPoint = Point(385.0, 385.0),
            confidence = 0.7f,
            area = 4000.0,
            contour = MatOfPoint(),
            method = "primary_color"
        )
        val qualityMetrics = ImageQualityAnalyzer.QualityMetrics(
            sharpness = 0.6f,
            brightness = 125f,
            contrast = 55f,
            isBlurry = false,
            isOverexposed = false,
            isUnderexposed = false
        )
        val normalResult = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        validationManager.updateConfig(strictConfig)
        val strictResult = validationManager.validateCapture(moleDetection, qualityMetrics, guideArea)
        assertNotNull("Normal result should exist", normalResult)
        assertNotNull("Strict result should exist", strictResult)
        val currentConfig = validationManager.getConfig()
        assertNotNull("Config should be retrievable", currentConfig)
    }
}
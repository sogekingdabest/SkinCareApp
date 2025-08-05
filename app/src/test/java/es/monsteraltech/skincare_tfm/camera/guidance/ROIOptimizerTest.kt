package es.monsteraltech.skincare_tfm.camera.guidance
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
class ROIOptimizerTest {
    private lateinit var roiOptimizer: ROIOptimizer
    private val testImageSize = Size(1920.0, 1080.0)
    @Before
    fun setUp() {
        roiOptimizer = ROIOptimizer()
    }
    @Test
    fun `test basic ROI calculation`() {
        val roiResult = roiOptimizer.calculateROI(testImageSize, 0.7f)
        assertNotNull(roiResult)
        assertTrue(roiResult.roi.width > 0)
        assertTrue(roiResult.roi.height > 0)
        assertEquals(0.7f, roiResult.scaleFactor, 0.01f)
        assertTrue(roiResult.roi.x >= 0)
        assertTrue(roiResult.roi.y >= 0)
        assertTrue(roiResult.roi.x + roiResult.roi.width <= testImageSize.width.toInt())
        assertTrue(roiResult.roi.y + roiResult.roi.height <= testImageSize.height.toInt())
    }
    @Test
    fun `test ROI with different scales`() {
        val scales = listOf(0.3f, 0.5f, 0.7f, 1.0f)
        scales.forEach { scale ->
            val roiResult = roiOptimizer.calculateROI(testImageSize, scale)
            val expectedWidth = (testImageSize.width * scale).toInt()
            val expectedHeight = (testImageSize.height * scale).toInt()
            assertEquals(expectedWidth, roiResult.roi.width, 1)
            assertEquals(expectedHeight, roiResult.roi.height, 1)
        }
    }
    @Test
    fun `test adaptive ROI with detection history`() {
        val config = ROIOptimizer.ROIConfig(adaptiveROI = true)
        roiOptimizer.updateConfig(config)
        val detectionPoint = Point(400.0, 300.0)
        roiOptimizer.updateDetectionHistory(detectionPoint)
        roiOptimizer.updateDetectionHistory(Point(420.0, 320.0))
        roiOptimizer.updateDetectionHistory(Point(380.0, 280.0))
        val roiResult = roiOptimizer.calculateROI(testImageSize, 0.5f)
        val roiCenterX = roiResult.roi.x + roiResult.roi.width / 2
        val roiCenterY = roiResult.roi.y + roiResult.roi.height / 2
        val distance = kotlin.math.sqrt(
            kotlin.math.pow((roiCenterX - detectionPoint.x).toDouble(), 2.0) +
            kotlin.math.pow((roiCenterY - detectionPoint.y).toDouble(), 2.0)
        )
        assertTrue("ROI center should be near detection point", distance < 200.0)
    }
    @Test
    fun `test coordinate conversion ROI to image`() {
        val roiResult = roiOptimizer.calculateROI(testImageSize, 0.5f)
        val roiPoint = Point(100.0, 50.0)
        val imagePoint = roiOptimizer.roiToImageCoordinates(roiPoint, roiResult)
        assertEquals(roiPoint.x + roiResult.offsetX, imagePoint.x, 0.1)
        assertEquals(roiPoint.y + roiResult.offsetY, imagePoint.y, 0.1)
    }
    @Test
    fun `test coordinate conversion image to ROI`() {
        val roiResult = roiOptimizer.calculateROI(testImageSize, 0.5f)
        val imagePoint = Point(500.0, 400.0)
        val roiPoint = roiOptimizer.imageToROICoordinates(imagePoint, roiResult)
        assertEquals(imagePoint.x - roiResult.offsetX, roiPoint.x, 0.1)
        assertEquals(imagePoint.y - roiResult.offsetY, roiPoint.y, 0.1)
    }
    @Test
    fun `test expanded ROI calculation`() {
        val detectionRect = Rect(400, 300, 200, 150)
        val expansionFactor = 1.5f
        val roiResult = roiOptimizer.calculateExpandedROI(
            testImageSize,
            detectionRect,
            expansionFactor
        )
        assertTrue(roiResult.roi.width > detectionRect.width)
        assertTrue(roiResult.roi.height > detectionRect.height)
        val detectionCenterX = detectionRect.x + detectionRect.width / 2
        val detectionCenterY = detectionRect.y + detectionRect.height / 2
        val roiCenterX = roiResult.roi.x + roiResult.roi.width / 2
        val roiCenterY = roiResult.roi.y + roiResult.roi.height / 2
        assertEquals(detectionCenterX, roiCenterX, 1)
        assertEquals(detectionCenterY, roiCenterY, 1)
    }
    @Test
    fun `test point in ROI detection`() {
        val roiResult = roiOptimizer.calculateROI(testImageSize, 0.5f)
        val pointInside = Point(
            (roiResult.roi.x + roiResult.roi.width / 2).toDouble(),
            (roiResult.roi.y + roiResult.roi.height / 2).toDouble()
        )
        assertTrue(roiOptimizer.isPointInROI(pointInside, roiResult))
        val pointOutside = Point(0.0, 0.0)
        assertFalse(roiOptimizer.isPointInROI(pointOutside, roiResult))
    }
    @Test
    fun `test ROI area ratio calculation`() {
        val roiResult = roiOptimizer.calculateROI(testImageSize, 0.5f)
        val areaRatio = roiOptimizer.calculateROIAreaRatio(testImageSize, roiResult)
        assertEquals(0.25f, areaRatio, 0.05f)
    }
    @Test
    fun `test performance-based ROI optimization`() {
        val currentProcessingTime = 100L
        val targetProcessingTime = 50L
        val roiResult = roiOptimizer.optimizeROIForPerformance(
            testImageSize,
            currentProcessingTime,
            targetProcessingTime
        )
        val areaRatio = roiOptimizer.calculateROIAreaRatio(testImageSize, roiResult)
        assertTrue("ROI should be small for slow processing", areaRatio < 0.5f)
    }
    @Test
    fun `test detection history management`() {
        repeat(15) { i ->
            roiOptimizer.updateDetectionHistory(Point(i * 10.0, i * 5.0))
        }
        val stats = roiOptimizer.getROIStats()
        val historySize = stats["historySize"] as Int
        assertTrue(historySize <= 10)
    }
    @Test
    fun `test ROI config updates`() {
        val newConfig = ROIOptimizer.ROIConfig(
            centerRatio = 0.6f,
            adaptiveROI = false,
            minROISize = 0.2f,
            maxROISize = 0.9f,
            expansionFactor = 2.0f
        )
        roiOptimizer.updateConfig(newConfig)
        val stats = roiOptimizer.getROIStats()
        assertEquals(false, stats["adaptiveROI"])
        assertEquals(0.2f, stats["minROISize"])
        assertEquals(0.9f, stats["maxROISize"])
        assertEquals(2.0f, stats["expansionFactor"])
    }
    @Test
    fun `test ROI boundary constraints`() {
        val edgeDetection = Rect(1800, 1000, 100, 50)
        val roiResult = roiOptimizer.calculateExpandedROI(testImageSize, edgeDetection, 2.0f)
        assertTrue(roiResult.roi.x >= 0)
        assertTrue(roiResult.roi.y >= 0)
        assertTrue(roiResult.roi.x + roiResult.roi.width <= testImageSize.width.toInt())
        assertTrue(roiResult.roi.y + roiResult.roi.height <= testImageSize.height.toInt())
    }
    @Test
    fun `test clear history functionality`() {
        roiOptimizer.updateDetectionHistory(Point(100.0, 100.0))
        roiOptimizer.updateDetectionHistory(Point(200.0, 200.0))
        var stats = roiOptimizer.getROIStats()
        assertTrue((stats["historySize"] as Int) > 0)
        assertTrue(stats["hasLastDetection"] as Boolean)
        roiOptimizer.clearHistory()
        stats = roiOptimizer.getROIStats()
        assertEquals(0, stats["historySize"])
        assertEquals(false, stats["hasLastDetection"])
    }
    @Test
    fun `test ROI stats completeness`() {
        val stats = roiOptimizer.getROIStats()
        val expectedKeys = listOf(
            "historySize",
            "hasLastDetection",
            "adaptiveROI",
            "minROISize",
            "maxROISize",
            "expansionFactor"
        )
        expectedKeys.forEach { key ->
            assertTrue("Missing key: $key", stats.containsKey(key))
        }
    }
}
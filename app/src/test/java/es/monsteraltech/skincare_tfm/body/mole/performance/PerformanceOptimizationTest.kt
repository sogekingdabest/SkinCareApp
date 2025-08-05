package es.monsteraltech.skincare_tfm.body.mole.performance
import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import es.monsteraltech.skincare_tfm.body.mole.model.ABCDEScores
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
@RunWith(AndroidJUnit4::class)
class PerformanceOptimizationTest {
    private lateinit var context: Context
    private lateinit var performanceManager: PerformanceManager
    private lateinit var localCacheManager: LocalCacheManager
    private lateinit var lazyImageLoader: LazyImageLoader
    @Mock
    private lateinit var mockRecyclerView: RecyclerView
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
        performanceManager = PerformanceManager.getInstance(context)
        localCacheManager = LocalCacheManager.getInstance(context)
        lazyImageLoader = LazyImageLoader(context)
    }
    @Test
    fun testLazyImageLoaderConfiguration() {
        val moleListConfig = LazyImageLoader.Configs.forMoleList()
        assertTrue(moleListConfig.enablePreload)
        assertEquals(5, moleListConfig.preloadDistance)
        assertTrue(moleListConfig.thumbnailFirst)
        val analysisHistoryConfig = LazyImageLoader.Configs.forAnalysisHistory()
        assertTrue(analysisHistoryConfig.enablePreload)
        assertEquals(3, analysisHistoryConfig.preloadDistance)
        assertTrue(analysisHistoryConfig.thumbnailFirst)
        val fastScrollingConfig = LazyImageLoader.Configs.forFastScrolling()
        assertEquals(false, fastScrollingConfig.enablePreload)
        assertTrue(fastScrollingConfig.thumbnailFirst)
        assertEquals(200L, fastScrollingConfig.loadDelay)
    }
    @Test
    fun testPaginationManagerConfiguration() {
        val analysisPaginationManager = AnalysisPaginationManager(pageSize = 15, prefetchDistance = 3)
        assertNotNull(analysisPaginationManager)
        val molePaginationManager = MolePaginationManager(pageSize = 10, prefetchDistance = 2)
        assertNotNull(molePaginationManager)
    }
    @Test
    fun testLocalCacheManagerBasicOperations() = runBlocking {
        val analysisData = createTestAnalysisData()
        localCacheManager.cacheAnalysis(
            analysisId = analysisData.id,
            analysis = analysisData,
            config = LocalCacheManager.Configs.shortTerm()
        )
        val cachedAnalysis = localCacheManager.getCachedAnalysis(
            analysisId = analysisData.id,
            config = LocalCacheManager.Configs.shortTerm()
        )
        assertNotNull(cachedAnalysis)
        assertEquals(analysisData.id, cachedAnalysis.id)
        assertEquals(analysisData.moleId, cachedAnalysis.moleId)
        assertEquals(analysisData.riskLevel, cachedAnalysis.riskLevel)
    }
    @Test
    fun testLocalCacheManagerMoleOperations() = runBlocking {
        val moleData = createTestMoleData()
        localCacheManager.cacheMole(
            moleId = moleData.id,
            mole = moleData,
            config = LocalCacheManager.Configs.longTerm()
        )
        val cachedMole = localCacheManager.getCachedMole(
            moleId = moleData.id,
            config = LocalCacheManager.Configs.longTerm()
        )
        assertNotNull(cachedMole)
        assertEquals(moleData.id, cachedMole.id)
        assertEquals(moleData.title, cachedMole.title)
        assertEquals(moleData.bodyPart, cachedMole.bodyPart)
    }
    @Test
    fun testLocalCacheManagerListOperations() = runBlocking {
        val analysisList = listOf(
            createTestAnalysisData("analysis1"),
            createTestAnalysisData("analysis2"),
            createTestAnalysisData("analysis3")
        )
        val cacheKey = "test_mole_analyses"
        localCacheManager.cacheAnalysisList(
            cacheKey = cacheKey,
            analysisList = analysisList,
            config = LocalCacheManager.Configs.mediumTerm()
        )
        val cachedList = localCacheManager.getCachedAnalysisList(
            cacheKey = cacheKey,
            config = LocalCacheManager.Configs.mediumTerm()
        )
        assertNotNull(cachedList)
        assertEquals(3, cachedList.size)
        assertEquals("analysis1", cachedList[0].id)
        assertEquals("analysis2", cachedList[1].id)
        assertEquals("analysis3", cachedList[2].id)
    }
    @Test
    fun testCacheInvalidation() = runBlocking {
        val analysisData = createTestAnalysisData()
        localCacheManager.cacheAnalysis(
            analysisId = analysisData.id,
            analysis = analysisData
        )
        val cachedBefore = localCacheManager.getCachedAnalysis(analysisData.id)
        assertNotNull(cachedBefore)
        localCacheManager.invalidateAnalysis(analysisData.id)
        val cachedAfter = localCacheManager.getCachedAnalysis(analysisData.id)
        assertEquals(null, cachedAfter)
    }
    @Test
    fun testCacheStats() = runBlocking {
        val analysisData = createTestAnalysisData()
        val moleData = createTestMoleData()
        localCacheManager.cacheAnalysis(analysisData.id, analysisData)
        localCacheManager.cacheMole(moleData.id, moleData)
        val stats = localCacheManager.getCacheStats()
        assertTrue(stats.analysisMemoryCacheSize >= 1)
        assertTrue(stats.moleMemoryCacheSize >= 1)
    }
    @Test
    fun testPerformanceManagerConfigurations() {
        val highPerformanceConfig = PerformanceManager.Configs.highPerformance()
        assertTrue(highPerformanceConfig.enableImageLazyLoading)
        assertTrue(highPerformanceConfig.enablePagination)
        assertTrue(highPerformanceConfig.enableLocalCache)
        assertTrue(highPerformanceConfig.enableOptimizedQueries)
        assertEquals(5, highPerformanceConfig.preloadDistance)
        assertEquals(15, highPerformanceConfig.pageSize)
        val lowMemoryConfig = PerformanceManager.Configs.lowMemory()
        assertTrue(lowMemoryConfig.enableImageLazyLoading)
        assertTrue(lowMemoryConfig.enablePagination)
        assertEquals(false, lowMemoryConfig.enableLocalCache)
        assertTrue(lowMemoryConfig.enableOptimizedQueries)
        assertEquals(1, lowMemoryConfig.preloadDistance)
        assertEquals(10, lowMemoryConfig.pageSize)
        val offlineConfig = PerformanceManager.Configs.offline()
        assertEquals(false, offlineConfig.enableImageLazyLoading)
        assertEquals(false, offlineConfig.enablePagination)
        assertTrue(offlineConfig.enableLocalCache)
        assertEquals(false, offlineConfig.enableOptimizedQueries)
    }
    @Test
    fun testPerformanceManagerCacheOperations() = runBlocking {
        val analysisData = createTestAnalysisData()
        val moleData = createTestMoleData()
        performanceManager.cacheAnalysis(analysisData.id, analysisData)
        performanceManager.cacheMole(moleData.id, moleData)
        val cachedAnalysis = performanceManager.getCachedAnalysis(analysisData.id)
        val cachedMole = performanceManager.getCachedMole(moleData.id)
        assertNotNull(cachedAnalysis)
        assertNotNull(cachedMole)
        assertEquals(analysisData.id, cachedAnalysis.id)
        assertEquals(moleData.id, cachedMole.id)
    }
    @Test
    fun testPerformanceStats() = runBlocking {
        val analysisData = createTestAnalysisData()
        val moleData = createTestMoleData()
        performanceManager.cacheAnalysis(analysisData.id, analysisData)
        performanceManager.cacheMole(moleData.id, moleData)
        val stats = performanceManager.getPerformanceStats()
        assertTrue(stats.memoryCacheSize >= 2)
    }
    @Test
    fun testClearCaches() = runBlocking {
        val analysisData = createTestAnalysisData()
        val moleData = createTestMoleData()
        performanceManager.cacheAnalysis(analysisData.id, analysisData)
        performanceManager.cacheMole(moleData.id, moleData)
        assertNotNull(performanceManager.getCachedAnalysis(analysisData.id))
        assertNotNull(performanceManager.getCachedMole(moleData.id))
        performanceManager.clearCaches()
        assertEquals(null, performanceManager.getCachedAnalysis(analysisData.id))
        assertEquals(null, performanceManager.getCachedMole(moleData.id))
    }
    private fun createTestAnalysisData(id: String = "test_analysis_id"): AnalysisData {
        return AnalysisData(
            id = id,
            moleId = "test_mole_id",
            analysisResult = "Test analysis result",
            aiProbability = 0.75f,
            aiConfidence = 0.85f,
            abcdeScores = ABCDEScores(
                asymmetryScore = 2.0f,
                borderScore = 1.5f,
                colorScore = 2.5f,
                diameterScore = 1.0f,
                evolutionScore = null,
                totalScore = 7.0f
            ),
            combinedScore = 7.5f,
            riskLevel = "MODERATE",
            recommendation = "Monitor closely",
            imageUrl = "test_image_url.jpg",
            createdAt = Timestamp.now(),
            analysisMetadata = mapOf("test" to "metadata")
        )
    }
    private fun createTestMoleData(id: String = "test_mole_id"): MoleData {
        return MoleData(
            id = id,
            title = "Test Mole",
            description = "Test mole description",
            bodyPart = "Arm",
            bodyPartColorCode = "#FF0000",
            imageUrl = "test_mole_image.jpg",
            aiResult = "Test AI result",
            userId = "test_user_id",
            analysisCount = 3,
            lastAnalysisDate = Timestamp.now(),
            firstAnalysisDate = Timestamp.now(),
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
    }
}
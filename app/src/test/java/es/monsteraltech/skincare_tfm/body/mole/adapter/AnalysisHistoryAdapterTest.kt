package es.monsteraltech.skincare_tfm.body.mole.adapter
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.Timestamp
import es.monsteraltech.skincare_tfm.body.mole.model.ABCDEScores
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date
@RunWith(RobolectricTestRunner::class)
class AnalysisHistoryAdapterTest {
    private lateinit var context: Context
    private lateinit var adapter: AnalysisHistoryAdapter
    private lateinit var sampleAnalysisList: List<AnalysisData>
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sampleAnalysisList = listOf(
            AnalysisData(
                id = "1",
                moleId = "mole1",
                analysisResult = "Análisis de prueba 1",
                aiProbability = 0.75f,
                aiConfidence = 0.85f,
                abcdeScores = ABCDEScores(
                    asymmetryScore = 2.1f,
                    borderScore = 1.8f,
                    colorScore = 2.5f,
                    diameterScore = 1.2f,
                    totalScore = 7.6f
                ),
                combinedScore = 0.65f,
                riskLevel = "MODERATE",
                recommendation = "Seguimiento regular",
                imageUrl = "test_image.jpg",
                createdAt = Timestamp.now()
            ),
            AnalysisData(
                id = "2",
                moleId = "mole1",
                analysisResult = "Análisis de prueba 2",
                aiProbability = 0.65f,
                aiConfidence = 0.80f,
                abcdeScores = ABCDEScores(
                    asymmetryScore = 1.9f,
                    borderScore = 1.5f,
                    colorScore = 2.2f,
                    diameterScore = 1.0f,
                    totalScore = 6.6f
                ),
                combinedScore = 0.55f,
                riskLevel = "LOW",
                recommendation = "Monitoreo normal",
                imageUrl = "test_image2.jpg",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000))
            )
        )
        adapter = AnalysisHistoryAdapter(context, sampleAnalysisList) { }
    }
    @Test
    fun testAdapterInitialization() {
        assertNotNull(adapter)
        assertEquals(2, adapter.itemCount)
    }
}
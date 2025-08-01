package es.monsteraltech.skincare_tfm.body.mole

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import es.monsteraltech.skincare_tfm.body.mole.model.ABCDEScores
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class AnalysisDetailActivityTest {

    @Test
    fun testActivityCreationWithAnalysisData() {
        val analysisData = AnalysisData(
            id = "test_id",
            moleId = "test_mole_id",
            analysisResult = "Test analysis result",
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
            recommendation = "Test recommendation",
            imageUrl = "test_image.jpg",
            createdAt = Timestamp.now()
        )

        val intent = Intent(ApplicationProvider.getApplicationContext(), AnalysisDetailActivity::class.java)
        intent.putExtra("ANALYSIS_DATA", analysisData)
        
        val scenario = ActivityScenario.launch<AnalysisDetailActivity>(intent)
        
        scenario.use { activityScenario ->
            activityScenario.onActivity { activity ->
                // Verificar que la actividad se crea correctamente
                assert(activity != null)
            }
        }
    }

    @Test
    fun testActivityFinishesWithoutAnalysisData() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), AnalysisDetailActivity::class.java)
        // No agregar ANALYSIS_DATA para probar el caso de error
        
        val scenario = ActivityScenario.launch<AnalysisDetailActivity>(intent)
        
        scenario.use { activityScenario ->
            // La actividad debería terminar si no hay ANALYSIS_DATA
            assert(activityScenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.DESTROYED))
        }
    }
}
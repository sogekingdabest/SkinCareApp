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
import java.util.*

@RunWith(AndroidJUnit4::class)
class AnalysisComparisonActivityTest {

    @Test
    fun testComparisonActivityLaunch() {
        // Crear datos de prueba
        val currentAnalysis = AnalysisData(
            id = "current_id",
            moleId = "mole_123",
            analysisResult = "Análisis actual con mejoras",
            aiProbability = 0.75f,
            aiConfidence = 0.85f,
            abcdeScores = ABCDEScores(
                asymmetryScore = 2.1f,
                borderScore = 1.8f,
                colorScore = 2.5f,
                diameterScore = 1.2f,
                evolutionScore = 0.8f,
                totalScore = 8.4f
            ),
            combinedScore = 0.65f,
            riskLevel = "MODERATE",
            recommendation = "Seguimiento regular",
            imageUrl = "",
            createdAt = Timestamp.now(),
            analysisMetadata = emptyMap()
        )

        val previousAnalysis = AnalysisData(
            id = "previous_id",
            moleId = "mole_123",
            analysisResult = "Análisis anterior",
            aiProbability = 0.80f,
            aiConfidence = 0.80f,
            abcdeScores = ABCDEScores(
                asymmetryScore = 1.8f,
                borderScore = 2.0f,
                colorScore = 2.4f,
                diameterScore = 1.3f,
                evolutionScore = 0.9f,
                totalScore = 8.4f
            ),
            combinedScore = 0.70f,
            riskLevel = "HIGH",
            recommendation = "Consultar especialista",
            imageUrl = "",
            createdAt = Timestamp(Date(System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000)), // 30 días atrás
            analysisMetadata = emptyMap()
        )

        // Crear intent con datos
        val intent = Intent(ApplicationProvider.getApplicationContext(), AnalysisComparisonActivity::class.java).apply {
            putExtra("CURRENT_ANALYSIS", currentAnalysis)
            putExtra("PREVIOUS_ANALYSIS", previousAnalysis)
        }

        // Lanzar actividad
        ActivityScenario.launch<AnalysisComparisonActivity>(intent).use { scenario ->
            // Verificar que la actividad se lanza correctamente
            scenario.onActivity { activity ->
                assert(activity != null)
                assert(!activity.isFinishing)
            }
        }
    }

    @Test
    fun testComparisonActivityWithInvalidData() {
        // Crear intent sin datos
        val intent = Intent(ApplicationProvider.getApplicationContext(), AnalysisComparisonActivity::class.java)

        // Lanzar actividad
        ActivityScenario.launch<AnalysisComparisonActivity>(intent).use { scenario ->
            // Verificar que la actividad se cierra cuando no hay datos válidos
            scenario.onActivity { activity ->
                // La actividad debería cerrarse automáticamente
                assert(activity.isFinishing)
            }
        }
    }
}
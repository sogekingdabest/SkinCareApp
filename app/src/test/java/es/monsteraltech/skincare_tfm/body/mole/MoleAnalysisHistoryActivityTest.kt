package es.monsteraltech.skincare_tfm.body.mole
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class MoleAnalysisHistoryActivityTest {
    @Test
    fun testActivityCreationWithMoleId() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MoleAnalysisHistoryActivity::class.java)
        intent.putExtra("MOLE_ID", "test_mole_id")
        val scenario = ActivityScenario.launch<MoleAnalysisHistoryActivity>(intent)
        scenario.use { activityScenario ->
            activityScenario.onActivity { activity ->
                assert(activity != null)
            }
        }
    }
    @Test
    fun testActivityFinishesWithoutMoleId() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MoleAnalysisHistoryActivity::class.java)
        val scenario = ActivityScenario.launch<MoleAnalysisHistoryActivity>(intent)
        scenario.use { activityScenario ->
            assert(activityScenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.DESTROYED))
        }
    }
}
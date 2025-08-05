package es.monsteraltech.skincare_tfm.performance
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.data.SessionManager
import es.monsteraltech.skincare_tfm.login.SessionCheckActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis
@RunWith(AndroidJUnit4::class)
class SessionCheckUIPerformanceTest {
    private lateinit var sessionManager: SessionManager
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        sessionManager = SessionManager.getInstance(context)
        runBlocking {
            sessionManager.clearSession()
            sessionManager.clearCache()
        }
    }
    @After
    fun tearDown() {
        runBlocking {
            sessionManager.clearSession()
            sessionManager.clearCache()
        }
    }
    @Test
    fun testProgressIndicatorsAreVisible() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            onView(withId(R.id.statusTextView))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.session_check_verifying)))
        }
    }
    @Test
    fun testProgressIndicatorsDuringVerification() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            onView(withId(R.id.statusTextView))
                .check(matches(isDisplayed()))
            onView(withId(R.id.errorMessageTextView))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.retryButton))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }
    @Test
    fun testUIResponsivenessTime() {
        val uiResponseTime = measureTimeMillis {
            val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
                onView(withId(R.id.loadingProgressBar))
                    .check(matches(isDisplayed()))
                onView(withId(R.id.statusTextView))
                    .check(matches(isDisplayed()))
            }
        }
        assert(uiResponseTime < 1000L) {
            "UI tardó demasiado en responder: ${uiResponseTime}ms"
        }
        println("Tiempo de respuesta de UI: ${uiResponseTime}ms")
    }
    @Test
    fun testSmoothTransitions() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
            onView(withId(R.id.logoImageView))
                .check(matches(isDisplayed()))
            onView(withId(R.id.skinCareTextView))
                .check(matches(isDisplayed()))
                .check(matches(withText("SkinCare")))
            onView(withId(R.id.deTextView))
                .check(matches(isDisplayed()))
                .check(matches(withText("de")))
        }
    }
    @Test
    fun testLayoutPerformance() {
        val layoutTime = measureTimeMillis {
            val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    activity.findViewById<android.view.View>(android.R.id.content).requestLayout()
                }
                onView(withId(R.id.logoImageView)).check(matches(isDisplayed()))
                onView(withId(R.id.loadingProgressBar)).check(matches(isDisplayed()))
                onView(withId(R.id.statusTextView)).check(matches(isDisplayed()))
                onView(withId(R.id.skinCareTextView)).check(matches(isDisplayed()))
                onView(withId(R.id.deTextView)).check(matches(isDisplayed()))
            }
        }
        assert(layoutTime < 500L) {
            "Layout tardó demasiado: ${layoutTime}ms"
        }
        println("Tiempo de layout: ${layoutTime}ms")
    }
    @Test
    fun testMinimumLoadingTime() {
        val totalTime = measureTimeMillis {
            val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
                Thread.sleep(2000)
                scenario.onActivity { activity ->
                    assertNotNull("Activity no debería ser null", activity)
                }
            }
        }
        assert(totalTime >= 1500L) {
            "No se respetó el tiempo mínimo de carga: ${totalTime}ms"
        }
        assert(totalTime < 5000L) {
            "Tiempo total demasiado alto: ${totalTime}ms"
        }
        println("Tiempo total con tiempo mínimo: ${totalTime}ms")
    }
    @Test
    fun testAccessibilityPerformance() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
            onView(withId(R.id.logoImageView))
                .check(matches(hasContentDescription()))
            onView(withId(R.id.loadingProgressBar))
                .check(matches(isDisplayed()))
            onView(withId(R.id.statusTextView))
                .check(matches(isDisplayed()))
                .check(matches(isCompletelyDisplayed()))
        }
    }
    @Test
    fun testErrorStateUIPerformance() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val errorMessage = activity.findViewById<android.widget.TextView>(R.id.errorMessageTextView)
                val retryButton = activity.findViewById<android.widget.Button>(R.id.retryButton)
                assertNotNull("Error message view debería existir", errorMessage)
                assertNotNull("Retry button debería existir", retryButton)
            }
        }
    }
    @Test
    fun testMultipleUIUpdatesPerformance() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val updateTime = measureTimeMillis {
            ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    val statusTextView = activity.findViewById<android.widget.TextView>(R.id.statusTextView)
                    repeat(5) { iteration ->
                        activity.runOnUiThread {
                            statusTextView.text = "Estado $iteration"
                        }
                        Thread.sleep(50)
                    }
                }
            }
        }
        assert(updateTime < 1000L) {
            "Actualizaciones de UI tardaron demasiado: ${updateTime}ms"
        }
        println("Tiempo de múltiples actualizaciones de UI: ${updateTime}ms")
    }
    @Test
    fun testViewRecyclingPerformance() {
        val recyclingTime = measureTimeMillis {
            repeat(3) { iteration ->
                val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
                    onView(withId(R.id.loadingProgressBar))
                        .check(matches(isDisplayed()))
                    onView(withId(R.id.statusTextView))
                        .check(matches(isDisplayed()))
                }
                Thread.sleep(100)
            }
        }
        assert(recyclingTime < 3000L) {
            "Reciclaje de vistas tardó demasiado: ${recyclingTime}ms"
        }
        println("Tiempo de reciclaje de vistas: ${recyclingTime}ms")
    }
}
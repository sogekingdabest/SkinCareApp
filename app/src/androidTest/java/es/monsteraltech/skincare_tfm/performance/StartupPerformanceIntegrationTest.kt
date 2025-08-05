package es.monsteraltech.skincare_tfm.performance
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import es.monsteraltech.skincare_tfm.data.SessionManager
import es.monsteraltech.skincare_tfm.login.SessionCheckActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis
@RunWith(AndroidJUnit4::class)
class StartupPerformanceIntegrationTest {
    private lateinit var sessionManager: SessionManager
    private lateinit var uiDevice: UiDevice
    companion object {
        private const val MAX_ACTIVITY_STARTUP_TIME_MS = 3000L
        private const val MAX_SESSION_CHECK_TIME_MS = 5000L
        private const val MAX_NAVIGATION_TIME_MS = 2000L
        private const val MAX_TOTAL_STARTUP_TIME_MS = 8000L
    }
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        sessionManager = SessionManager.getInstance(context)
        uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
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
    fun testSessionCheckActivityStartupTime() {
        val startupTime = measureTimeMillis {
            val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    assertNotNull("Activity no debería ser null", activity)
                }
            }
        }
        assertTrue(
            "SessionCheckActivity tardó demasiado en iniciar: ${startupTime}ms",
            startupTime < MAX_ACTIVITY_STARTUP_TIME_MS
        )
        println("Tiempo de inicio de SessionCheckActivity: ${startupTime}ms")
    }
    @Test
    fun testCompleteStartupFlowWithoutSession() {
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
        assertTrue(
            "Flujo completo de inicio sin sesión tardó demasiado: ${totalTime}ms",
            totalTime < MAX_TOTAL_STARTUP_TIME_MS
        )
        println("Tiempo total de flujo de inicio sin sesión: ${totalTime}ms")
    }
    @Test
    fun testSessionVerificationPerformanceInActivity() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val verificationTime = measureTimeMillis {
                    runBlocking {
                        sessionManager.isSessionValid(fastMode = true)
                    }
                }
                assertTrue(
                    "Verificación de sesión en actividad tardó demasiado: ${verificationTime}ms",
                    verificationTime < MAX_SESSION_CHECK_TIME_MS
                )
                println("Tiempo de verificación en actividad: ${verificationTime}ms")
            }
        }
    }
    @Test
    fun testCacheEffectivenessInRealScenario() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                runBlocking {
                    val firstVerificationTime = measureTimeMillis {
                        sessionManager.isSessionValid(fastMode = false)
                    }
                    val secondVerificationTime = measureTimeMillis {
                        sessionManager.isSessionValid(fastMode = true)
                    }
                    assertTrue(
                        "Cache no está mejorando el rendimiento en escenario real. " +
                        "Primera: ${firstVerificationTime}ms, Segunda: ${secondVerificationTime}ms",
                        secondVerificationTime <= firstVerificationTime * 1.5
                    )
                    println("Primera verificación: ${firstVerificationTime}ms")
                    println("Segunda verificación (con cache): ${secondVerificationTime}ms")
                }
            }
        }
    }
    @Test
    fun testMultipleActivityLaunchesPerformance() {
        val numberOfLaunches = 3
        val times = mutableListOf<Long>()
        repeat(numberOfLaunches) { iteration ->
            val launchTime = measureTimeMillis {
                val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
                    scenario.onActivity { activity ->
                        assertNotNull("Activity no debería ser null en iteración $iteration", activity)
                    }
                }
            }
            times.add(launchTime)
            Thread.sleep(500)
        }
        val averageTime = times.average()
        val maxTime = times.maxOrNull() ?: 0L
        assertTrue(
            "Tiempo promedio de lanzamiento demasiado alto: ${averageTime}ms",
            averageTime < MAX_ACTIVITY_STARTUP_TIME_MS
        )
        assertTrue(
            "Tiempo máximo de lanzamiento demasiado alto: ${maxTime}ms",
            maxTime < MAX_ACTIVITY_STARTUP_TIME_MS * 1.5
        )
        println("Tiempos de lanzamiento: $times")
        println("Tiempo promedio: ${averageTime}ms")
        println("Tiempo máximo: ${maxTime}ms")
    }
    @Test
    fun testMemoryUsageDuringStartup() {
        val runtime = Runtime.getRuntime()
        val memoryBefore = runtime.totalMemory() - runtime.freeMemory()
        val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                runBlocking {
                    sessionManager.isSessionValid(fastMode = true)
                    sessionManager.getCacheStats()
                }
            }
        }
        System.gc()
        Thread.sleep(100)
        val memoryAfter = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = memoryAfter - memoryBefore
        val maxMemoryIncrease = 10 * 1024 * 1024
        assertTrue(
            "Aumento de memoria demasiado alto: ${memoryIncrease / 1024 / 1024}MB",
            memoryIncrease < maxMemoryIncrease
        )
        println("Memoria antes: ${memoryBefore / 1024 / 1024}MB")
        println("Memoria después: ${memoryAfter / 1024 / 1024}MB")
        println("Aumento: ${memoryIncrease / 1024 / 1024}MB")
    }
    @Test
    fun testStartupTimeConsistency() {
        val numberOfTests = 5
        val times = mutableListOf<Long>()
        repeat(numberOfTests) { iteration ->
            runBlocking {
                sessionManager.clearCache()
            }
            val startupTime = measureTimeMillis {
                val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
                    scenario.onActivity { activity ->
                        runBlocking {
                            sessionManager.isSessionValid(fastMode = true)
                        }
                    }
                }
            }
            times.add(startupTime)
            Thread.sleep(200)
        }
        val averageTime = times.average()
        val standardDeviation = calculateStandardDeviation(times, averageTime)
        assertTrue(
            "Tiempos de inicio inconsistentes. Promedio: ${averageTime}ms, Desviación: ${standardDeviation}ms",
            standardDeviation < averageTime * 0.3
        )
        println("Tiempos de inicio: $times")
        println("Tiempo promedio: ${averageTime}ms")
        println("Desviación estándar: ${standardDeviation}ms")
    }
    private fun calculateStandardDeviation(values: List<Long>, mean: Double): Double {
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }
    @Test
    fun testColdStartVsWarmStart() {
        runBlocking {
            sessionManager.clearCache()
        }
        val coldStartTime = measureTimeMillis {
            val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    runBlocking {
                        sessionManager.isSessionValid(fastMode = true)
                    }
                }
            }
        }
        val warmStartTime = measureTimeMillis {
            val intent = Intent(ApplicationProvider.getApplicationContext(), SessionCheckActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<SessionCheckActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    runBlocking {
                        sessionManager.isSessionValid(fastMode = true)
                    }
                }
            }
        }
        assertTrue(
            "Warm start no es más rápido que cold start. Cold: ${coldStartTime}ms, Warm: ${warmStartTime}ms",
            warmStartTime <= coldStartTime * 1.2
        )
        println("Cold start: ${coldStartTime}ms")
        println("Warm start: ${warmStartTime}ms")
        println("Mejora: ${((coldStartTime - warmStartTime).toDouble() / coldStartTime * 100).toInt()}%")
    }
}
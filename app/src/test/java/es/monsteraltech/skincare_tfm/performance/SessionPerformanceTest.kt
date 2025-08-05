package es.monsteraltech.skincare_tfm.performance
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.monsteraltech.skincare_tfm.data.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class SessionPerformanceTest {
    private lateinit var context: Context
    private lateinit var sessionManager: SessionManager
    companion object {
        private const val MAX_SESSION_VERIFICATION_TIME_MS = 5000L
        private const val MAX_CACHE_ACCESS_TIME_MS = 100L
        private const val MAX_SESSION_SAVE_TIME_MS = 2000L
        private const val MAX_SESSION_CLEAR_TIME_MS = 1000L
        private const val MAX_FAST_MODE_VERIFICATION_TIME_MS = 3000L
    }
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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
    fun testSessionVerificationPerformance() = runBlocking {
        val verificationTime = measureTimeMillis {
            val isValid = sessionManager.isSessionValid()
            assertFalse("No debería haber sesión válida", isValid)
        }
        assertTrue(
            "Verificación de sesión tomó demasiado tiempo: ${verificationTime}ms",
            verificationTime < MAX_SESSION_VERIFICATION_TIME_MS
        )
        println("Tiempo de verificación de sesión: ${verificationTime}ms")
    }
    @Test
    fun testFastModePerformance() = runBlocking {
        val fastModeTime = measureTimeMillis {
            val isValid = sessionManager.isSessionValid(fastMode = true)
            assertFalse("No debería haber sesión válida", isValid)
        }
        assertTrue(
            "Verificación en modo rápido tomó demasiado tiempo: ${fastModeTime}ms",
            fastModeTime < MAX_FAST_MODE_VERIFICATION_TIME_MS
        )
        println("Tiempo de verificación en modo rápido: ${fastModeTime}ms")
    }
    @Test
    fun testCacheAccessPerformance() = runBlocking {
        sessionManager.getStoredSession(useCache = false)
        val cacheAccessTime = measureTimeMillis {
            val sessionData = sessionManager.getStoredSession(useCache = true)
        }
        assertTrue(
            "Acceso a cache tomó demasiado tiempo: ${cacheAccessTime}ms",
            cacheAccessTime < MAX_CACHE_ACCESS_TIME_MS
        )
        println("Tiempo de acceso a cache: ${cacheAccessTime}ms")
    }
    @Test
    fun testSessionClearPerformance() = runBlocking {
        val clearTime = measureTimeMillis {
            val cleared = sessionManager.clearSession()
            assertTrue("Limpieza debería ser exitosa", cleared)
        }
        assertTrue(
            "Limpieza de sesión tomó demasiado tiempo: ${clearTime}ms",
            clearTime < MAX_SESSION_CLEAR_TIME_MS
        )
        println("Tiempo de limpieza de sesión: ${clearTime}ms")
    }
    @Test
    fun testCacheEffectiveness() = runBlocking {
        val timeWithoutCache = measureTimeMillis {
            sessionManager.getStoredSession(useCache = false)
        }
        val timeWithCache = measureTimeMillis {
            sessionManager.getStoredSession(useCache = true)
        }
        assertTrue(
            "Cache no está mejorando el rendimiento. Sin cache: ${timeWithoutCache}ms, Con cache: ${timeWithCache}ms",
            timeWithCache <= timeWithoutCache || timeWithCache < MAX_CACHE_ACCESS_TIME_MS
        )
        println("Tiempo sin cache: ${timeWithoutCache}ms, Tiempo con cache: ${timeWithCache}ms")
    }
    @Test
    fun testCacheStatsPerformance() {
        val statsTime = measureTimeMillis {
            val stats = sessionManager.getCacheStats()
            assertNotNull("Estadísticas no deberían ser null", stats)
            assertTrue("Estadísticas deberían tener datos", stats.isNotEmpty())
        }
        assertTrue(
            "Obtención de estadísticas tomó demasiado tiempo: ${statsTime}ms",
            statsTime < 50L
        )
        println("Tiempo de obtención de estadísticas: ${statsTime}ms")
    }
    @Test
    fun testMultipleVerificationsPerformance() = runBlocking {
        val numberOfVerifications = 5
        val totalTime = measureTimeMillis {
            repeat(numberOfVerifications) {
                sessionManager.isSessionValid(fastMode = true)
            }
        }
        val averageTime = totalTime / numberOfVerifications
        assertTrue(
            "Tiempo promedio de verificación demasiado alto: ${averageTime}ms",
            averageTime < MAX_FAST_MODE_VERIFICATION_TIME_MS
        )
        println("Tiempo total para $numberOfVerifications verificaciones: ${totalTime}ms")
        println("Tiempo promedio por verificación: ${averageTime}ms")
    }
    @Test
    fun testCacheClearPerformance() {
        runBlocking {
            sessionManager.getStoredSession()
        }
        val clearTime = measureTimeMillis {
            sessionManager.clearCache()
        }
        assertTrue(
            "Limpieza de cache tomó demasiado tiempo: ${clearTime}ms",
            clearTime < 50L
        )
        println("Tiempo de limpieza de cache: ${clearTime}ms")
    }
    @Test
    fun testStartupTimeSimulation() = runBlocking {
        val startupTime = measureTimeMillis {
            val isValid = sessionManager.isSessionValid(fastMode = true)
            val stats = sessionManager.getCacheStats()
            if (!isValid) {
                sessionManager.clearSession()
            }
        }
        assertTrue(
            "Tiempo de inicio simulado demasiado alto: ${startupTime}ms",
            startupTime < MAX_FAST_MODE_VERIFICATION_TIME_MS
        )
        println("Tiempo de inicio simulado: ${startupTime}ms")
    }
    @Test
    fun testMemoryEfficiency() {
        val initialStats = sessionManager.getCacheStats()
        runBlocking {
            repeat(10) {
                sessionManager.isSessionValid(fastMode = true)
                sessionManager.getStoredSession()
            }
        }
        val finalStats = sessionManager.getCacheStats()
        assertTrue(
            "Estadísticas de cache crecieron demasiado",
            finalStats.size <= initialStats.size + 2
        )
        println("Estadísticas iniciales: ${initialStats.size} campos")
        println("Estadísticas finales: ${finalStats.size} campos")
    }
}
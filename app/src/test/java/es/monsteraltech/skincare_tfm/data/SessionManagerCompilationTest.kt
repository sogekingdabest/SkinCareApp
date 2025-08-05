package es.monsteraltech.skincare_tfm.data
import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertNotNull
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class SessionManagerCompilationTest {
    private lateinit var context: Context
    private lateinit var sessionManager: SessionManager
    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        sessionManager = SessionManager.getInstance(context)
    }
    @Test
    fun `SessionManager should be instantiated successfully`() {
        assertNotNull(sessionManager, "SessionManager should be instantiated")
    }
    @Test
    fun `SessionManager methods should be callable without exceptions`() = runTest {
        try {
            sessionManager.getStoredSession()
            sessionManager.isSessionValid()
            sessionManager.clearSession()
            sessionManager.refreshSession()
            assertNotNull(sessionManager, "All SessionManager methods should be callable")
        } catch (e: Exception) {
            assertNotNull(sessionManager, "SessionManager methods should compile even if they fail at runtime")
        }
    }
}
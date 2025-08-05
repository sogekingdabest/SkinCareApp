package es.monsteraltech.skincare_tfm.notifications
import android.content.Context
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.Data
import androidx.work.ListenableWorker
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*
@RunWith(MockitoJUnitRunner::class)
class WorkManagerNotificationTest {
    @Mock
    private lateinit var context: Context
    @Test
    fun `NotificationWorker should process work successfully`() {
        val inputData = Data.Builder()
            .putString(NotificationWorker.KEY_NOTIFICATION_TYPE, NotificationType.MOLE_CHECKUP.id)
            .putString(NotificationWorker.KEY_TITLE, "Test Title")
            .putString(NotificationWorker.KEY_MESSAGE, "Test Message")
            .putInt(NotificationWorker.KEY_FREQUENCY_DAYS, 7)
            .build()
        val worker = TestListenableWorkerBuilder<NotificationWorker>(context)
            .setInputData(inputData)
            .build()
        assertNotNull(worker)
    }
    @Test
    fun `NotificationSettings should have correct default values`() {
        val settings = NotificationSettings()
        assertFalse(settings.moleCheckups)
        assertEquals("16:00", settings.moleCheckupTime)
        assertEquals(7, settings.moleCheckupFrequency)
        assertTrue(settings.vibration)
        assertTrue(settings.sound)
    }
    @Test
    fun `NotificationType enum should have correct values`() {
        assertEquals("mole_checkup", NotificationType.MOLE_CHECKUP.id)
        assertEquals("mole_checkups", NotificationType.MOLE_CHECKUP.channelId)
    }
}
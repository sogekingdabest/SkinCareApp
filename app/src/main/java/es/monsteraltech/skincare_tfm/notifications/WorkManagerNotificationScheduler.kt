package es.monsteraltech.skincare_tfm.notifications
import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit
class WorkManagerNotificationScheduler(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)
    companion object {
        private const val MOLE_CHECKUP_WORK_NAME = "mole_checkup_periodic"
    }
    fun scheduleNotifications(settings: NotificationSettings) {
        cancelAllNotifications()
        if (settings.moleCheckups) {
            scheduleMoleCheckupNotifications(settings)
        }
    }
    private fun scheduleMoleCheckupNotifications(settings: NotificationSettings) {
        val initialDelay = calculateInitialDelay(settings.moleCheckupTime)
        val inputData = Data.Builder()
            .putString(NotificationWorker.KEY_NOTIFICATION_TYPE, NotificationType.MOLE_CHECKUP.id)
            .putString(NotificationWorker.KEY_TITLE, "Revisión de Lunares")
            .putString(NotificationWorker.KEY_MESSAGE, "Es hora de revisar tus lunares para detectar cambios importantes")
            .putInt(NotificationWorker.KEY_FREQUENCY_DAYS, settings.moleCheckupFrequency)
            .build()
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val periodicWorkRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
            settings.moleCheckupFrequency.toLong(), TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag("mole_checkup")
            .build()
        workManager.enqueueUniquePeriodicWork(
            MOLE_CHECKUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicWorkRequest
        )
        android.util.Log.d("WorkManagerScheduler",
            "Notificaciones programadas cada ${settings.moleCheckupFrequency} días a las ${settings.moleCheckupTime}")
    }
    private fun calculateInitialDelay(time: String): Long {
        val parts = time.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = System.currentTimeMillis()
        var targetTime = calendar.timeInMillis
        if (targetTime <= now) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            targetTime = calendar.timeInMillis
        }
        return targetTime - now
    }
    fun cancelAllNotifications() {
        workManager.cancelUniqueWork(MOLE_CHECKUP_WORK_NAME)
        workManager.cancelAllWorkByTag("mole_checkup")
        android.util.Log.d("WorkManagerScheduler", "Todas las notificaciones canceladas")
    }
}
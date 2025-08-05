package es.monsteraltech.skincare_tfm.notifications
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    companion object {
        const val KEY_NOTIFICATION_TYPE = "notification_type"
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_FREQUENCY_DAYS = "frequency_days"
    }
    override fun doWork(): Result {
        return try {
            val permissionManager = NotificationPermissionManager(applicationContext)
            if (!permissionManager.hasNotificationPermission()) {
                android.util.Log.w("NotificationWorker", "No hay permisos para mostrar notificaciones")
                return Result.retry()
            }
            val notificationType = inputData.getString(KEY_NOTIFICATION_TYPE) ?: NotificationType.MOLE_CHECKUP.id
            val title = inputData.getString(KEY_TITLE) ?: "Revisión de Lunares"
            val message = inputData.getString(KEY_MESSAGE) ?: "Es hora de revisar tus lunares para detectar cambios importantes"
            val notificationManager = NotificationManager(applicationContext)
            val type = when (notificationType) {
                NotificationType.MOLE_CHECKUP.id -> NotificationType.MOLE_CHECKUP
                else -> NotificationType.MOLE_CHECKUP
            }
            notificationManager.showNotification(type, title, message)
            android.util.Log.d("NotificationWorker", "Notificación mostrada exitosamente")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("NotificationWorker", "Error al mostrar notificación: ${e.message}")
            Result.failure()
        }
    }
}
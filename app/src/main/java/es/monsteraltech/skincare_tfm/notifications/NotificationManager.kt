package es.monsteraltech.skincare_tfm.notifications
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import es.monsteraltech.skincare_tfm.MainActivity
import es.monsteraltech.skincare_tfm.R
class NotificationManager(private val context: Context) {
    private val notificationManager = NotificationManagerCompat.from(context)
    private val workManagerScheduler = WorkManagerNotificationScheduler(context)
    init {
        createNotificationChannels()
    }
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(
                                    NotificationType.MOLE_CHECKUP.channelId,
                                    "Revisiones de Lunares",
                                    NotificationManager.IMPORTANCE_HIGH
                            )
                            .apply { description = "Recordatorios para revisar lunares" }
            val systemNotificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            systemNotificationManager.createNotificationChannel(channel)
        }
    }
    fun scheduleNotifications(settings: NotificationSettings) {
        workManagerScheduler.scheduleNotifications(settings)
        android.util.Log.d("NotificationManager", "Notificaciones programadas con WorkManager")
    }
    fun cancelAllNotifications() {
        workManagerScheduler.cancelAllNotifications()
        android.util.Log.d("NotificationManager", "Notificaciones canceladas")
    }
    fun showNotification(type: NotificationType, title: String, message: String) {
        val permissionManager = NotificationPermissionManager(context)
        if (!permissionManager.hasNotificationPermission()) {
            android.util.Log.w("NotificationManager", "No hay permisos para mostrar notificaciones")
            return
        }
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent =
                PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        val notification =
                NotificationCompat.Builder(context, type.channelId)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build()
        try {
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            android.util.Log.e(
                    "NotificationManager",
                    "Error de seguridad al mostrar notificación: ${e.message}"
            )
        }
    }
}
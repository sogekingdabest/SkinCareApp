package es.monsteraltech.skincare_tfm.notifications
import android.app.AlarmManager
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
import java.util.Calendar
class NotificationManager(private val context: Context) {
    private val notificationManager = NotificationManagerCompat.from(context)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    companion object {
        private const val MOLE_CHECKUP_ID = 1002
    }
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
        cancelAllNotifications()
        if (settings.moleCheckups) {
            scheduleMoleCheckup(settings.moleCheckupTime, settings.moleCheckupFrequency)
        }
    }
    private fun scheduleMoleCheckup(time: String, frequencyDays: Int) {
        val intent =
                Intent(context, NotificationReceiver::class.java).apply {
                    putExtra("type", NotificationType.MOLE_CHECKUP.id)
                    putExtra("title", "Revisión de Lunares")
                    putExtra(
                            "message",
                            "Es hora de revisar tus lunares para detectar cambios importantes"
                    )
                }
        val pendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        MOLE_CHECKUP_ID,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        val calendar = getCalendarForTime(time)
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, frequencyDays)
        }
        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                (frequencyDays * 24 * 60 * 60 * 1000).toLong(),
                pendingIntent
        )
    }
    private fun getCalendarForTime(time: String): Calendar {
        val parts = time.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    fun cancelAllNotifications() {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        MOLE_CHECKUP_ID,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        alarmManager.cancel(pendingIntent)
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
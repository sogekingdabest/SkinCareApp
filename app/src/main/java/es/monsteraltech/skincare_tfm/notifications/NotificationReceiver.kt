package es.monsteraltech.skincare_tfm.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: return
        val title = intent.getStringExtra("title") ?: "SkinCare"
        val message = intent.getStringExtra("message") ?: "Tienes una notificación"
        
        val notificationType = when (type) {
            NotificationType.MOLE_CHECKUP.id -> NotificationType.MOLE_CHECKUP
            else -> NotificationType.MOLE_CHECKUP
        }
        
        val notificationManager = NotificationManager(context)
        notificationManager.showNotification(notificationType, title, message)
    }
}
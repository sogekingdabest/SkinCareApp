package es.monsteraltech.skincare_tfm.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class NotificationPermissionManager(private val context: Context) {
    
    companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
    
    /**
     * Verifica si el permiso de notificaciones está concedido
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // En versiones anteriores a Android 13, el permiso se concede automáticamente
            true
        }
    }
    
    /**
     * Solicita el permiso de notificaciones si es necesario
     */
    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission()) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
    
    /**
     * Verifica si se debe mostrar una explicación del permiso
     */
    fun shouldShowRequestPermissionRationale(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            false
        }
    }
}
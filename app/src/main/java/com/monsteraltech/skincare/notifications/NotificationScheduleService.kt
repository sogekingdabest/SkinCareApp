package com.monsteraltech.skincare.notifications

import android.content.Context
import com.monsteraltech.skincare.account.AccountSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface NotificationScheduleService {
    fun apply(settings: AccountSettings)
}

class AndroidNotificationScheduleService @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationScheduleService {
    override fun apply(settings: AccountSettings) {
        val manager = NotificationManager(context)
        if (settings.notificationsEnabled && NotificationPermissionManager(context).hasNotificationPermission()) {
            manager.scheduleNotifications(settings.toNotificationSettings())
        } else manager.cancelAllNotifications()
    }
}

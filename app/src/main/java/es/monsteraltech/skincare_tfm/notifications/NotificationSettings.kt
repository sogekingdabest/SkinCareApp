package es.monsteraltech.skincare_tfm.notifications
import es.monsteraltech.skincare_tfm.account.AccountSettings
data class NotificationSettings(
    val moleCheckups: Boolean = false,
    val moleCheckupTime: String = "16:00",
    val moleCheckupFrequency: Int = 7,
    val vibration: Boolean = true,
    val sound: Boolean = true
)
fun AccountSettings.toNotificationSettings(): NotificationSettings {
    return NotificationSettings(
        moleCheckups = this.moleCheckupsEnabled,
        moleCheckupTime = this.moleCheckupTime,
        moleCheckupFrequency = this.moleCheckupFrequency,
        vibration = this.notificationVibration,
        sound = this.notificationSound
    )
}
fun NotificationSettings.toAccountSettings(currentSettings: AccountSettings): AccountSettings {
    return currentSettings.copy(
        moleCheckupsEnabled = this.moleCheckups,
        moleCheckupTime = this.moleCheckupTime,
        moleCheckupFrequency = this.moleCheckupFrequency,
        notificationVibration = this.vibration,
        notificationSound = this.sound
    )
}
package es.monsteraltech.skincare_tfm.account
import com.google.firebase.firestore.Exclude
data class AccountSettings(
    val userId: String = "",
    val notificationsEnabled: Boolean = true,
    val darkModeEnabled: Boolean = false,
    val language: String = "es",
    val moleCheckupsEnabled: Boolean = false,
    val moleCheckupTime: String = "16:00",
    val moleCheckupFrequency: Int = 7,
    val notificationVibration: Boolean = true,
    val notificationSound: Boolean = true,
    @get:Exclude
    @set:Exclude
    var autoBackup: Boolean = true
)
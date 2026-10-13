package com.monsteraltech.skincare.account

/** Only fields explicitly changed by the user replace the stored values. */
data class AccountSettingsPatch(
    val notificationsEnabled: Boolean? = null,
    val darkModeEnabled: Boolean? = null,
    val moleCheckupsEnabled: Boolean? = null,
    val moleCheckupTime: String? = null,
    val moleCheckupFrequency: Int? = null,
    val notificationVibration: Boolean? = null,
    val notificationSound: Boolean? = null,
    /** null leaves the profile alone; an empty profile explicitly clears its values. */
    val analysisProfile: AnalysisProfile? = null
) {
    fun applyTo(settings: AccountSettings) = settings.copy(
        notificationsEnabled = notificationsEnabled ?: settings.notificationsEnabled,
        darkModeEnabled = darkModeEnabled ?: settings.darkModeEnabled,
        moleCheckupsEnabled = moleCheckupsEnabled ?: settings.moleCheckupsEnabled,
        moleCheckupTime = moleCheckupTime ?: settings.moleCheckupTime,
        moleCheckupFrequency = moleCheckupFrequency ?: settings.moleCheckupFrequency,
        notificationVibration = notificationVibration ?: settings.notificationVibration,
        notificationSound = notificationSound ?: settings.notificationSound,
        analysisProfile = analysisProfile ?: settings.analysisProfile
    )
}

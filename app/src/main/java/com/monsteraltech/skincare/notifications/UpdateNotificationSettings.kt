package com.monsteraltech.skincare.notifications

import com.monsteraltech.skincare.account.AccountProfileRepository
import com.monsteraltech.skincare.account.AccountResult
import com.monsteraltech.skincare.account.AccountSettings
import com.monsteraltech.skincare.account.AccountSettingsPatch
import com.monsteraltech.skincare.data.identity.ActiveProfile
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Patches stored fields atomically and finishes scheduling after a committed edit. */
class UpdateNotificationSettings @Inject constructor(
    private val repository: AccountProfileRepository,
    private val profile: ActiveProfile,
    private val scheduler: NotificationScheduleService
) {
    suspend operator fun invoke(owner: String, patch: AccountSettingsPatch): AccountResult<AccountSettings> =
        withContext(NonCancellable) {
            if (profile.ownerId != owner) return@withContext profileChanged()
            val saved = repository.applySettingsPatch(patch, owner)
            (saved.getErrorOrNull()?.exception as? CancellationException)?.let { throw it }
            val settings = saved.getDataOrNull()
            if (settings != null) {
                if (settings.userId != owner || profile.ownerId != owner) return@withContext profileChanged()
                scheduler.apply(settings)
            }
            saved
        }

    private fun profileChanged(): AccountResult<AccountSettings> = AccountResult.error(
        IllegalStateException("El perfil activo ha cambiado"), "Debes iniciar sesión para actualizar configuraciones",
        AccountResult.ErrorType.AUTHENTICATION_ERROR
    )
}

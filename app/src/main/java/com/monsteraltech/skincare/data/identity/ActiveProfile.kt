package com.monsteraltech.skincare.data.identity

/**
 * Identidad del espacio de datos que está activo en el dispositivo.
 *
 * Una cuenta Firebase y el perfil local son espacios independientes. Cambiar
 * de uno a otro nunca mezcla datos de salud de forma implícita.
 */
interface ActiveProfile {
    val ownerId: String?
    val isLocal: Boolean

    fun requireOwnerId(): String =
        ownerId ?: throw SecurityException("No hay ningún perfil activo")

    fun activateLocalProfile(): String
    fun activateAccountProfile()
    fun clear()
}

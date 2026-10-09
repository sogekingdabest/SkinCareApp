package com.monsteraltech.skincare.body.mole.domain.model

/**
 * Captura inmutable de un lunar en un momento concreto.
 *
 * [imageReference] es opaca para el dominio: la capa de datos decide si
 * representa una ruta local o una referencia remota.
 */
data class MoleObservation(
    val id: String,
    val moleId: String,
    val imageReference: String,
    val capturedAtEpochMillis: Long
)

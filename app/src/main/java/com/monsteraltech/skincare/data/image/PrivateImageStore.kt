package com.monsteraltech.skincare.data.image

import java.io.File
import java.io.InputStream

/** Referencias persistentes relativas al espacio privado de la app, independientes de Firebase. */
interface PrivateImageStore {
    /** Copia completa e independiente del original. Ejecutar en IO. */
    fun save(source: File, ownerId: String): String
    /** Resuelve sin copiar ni eliminar archivos. Admite las referencias locales heredadas. */
    fun resolve(reference: String): File?
    /** Abre la referencia resuelta. El llamador cierra el stream y ejecuta en IO. */
    fun open(reference: String): InputStream
    /** Migra una referencia heredada conservando el original si falla la copia. Ejecutar en IO. */
    fun migrateLegacy(reference: String): File?
    /** Elimina exclusivamente una imagen persistente administrada por el almacén. Ejecutar en IO. */
    fun delete(reference: String)
}

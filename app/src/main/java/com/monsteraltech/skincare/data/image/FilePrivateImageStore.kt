package com.monsteraltech.skincare.data.image

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Implementación de archivos comprobable en JVM. Las lecturas nunca migran implícitamente. */
class FilePrivateImageStore(
    privateDirectory: File,
    legacyDirectory: File,
    captureDirectory: File
) : PrivateImageStore {
    private val privateRoot = privateDirectory.canonicalFile
    private val legacyRoot = legacyDirectory.canonicalFile
    private val captureRoot = captureDirectory.canonicalFile

    override fun save(source: File, ownerId: String): String {
        require(ownerId.isNotBlank() && ownerId !in listOf(".", "..") &&
            ownerId.none { it == '/' || it == '\\' || it == '\u0000' }) {
            "Perfil local no válido"
        }
        require(source.isFile) { "Imagen de origen no disponible" }
        val reference = "mole_images/$ownerId/mole_${UUID.randomUUID()}.jpg"
        val destination = requireNotNull(managedFile(privateRoot, reference))
        copyAtomically(source, destination)
        return reference
    }

    override fun resolve(reference: String): File? {
        val relative = managedReference(reference)
        if (relative != null) {
            val current = managedFile(privateRoot, relative) ?: return null
            if (current.isFile) return current
            val legacy = managedFile(legacyRoot, relative)
            return legacy?.takeIf(File::isFile) ?: current
        }
        // Las capturas temporales pueden llegar como ruta absoluta o file://.
        return localFile(reference)?.takeIf(File::isAbsolute)?.canonicalFile?.takeIf { file ->
            file.toPath().startsWith(captureRoot.toPath())
        }
    }

    @Synchronized
    override fun open(reference: String) =
        (resolve(reference) ?: throw FileNotFoundException("Referencia de imagen no válida"))
            .inputStream()

    @Synchronized
    override fun migrateLegacy(reference: String): File? {
        val relative = managedReference(reference) ?: return resolve(reference)
        val destination = managedFile(privateRoot, relative) ?: return null
        if (destination.isFile) return destination
        val legacy = managedFile(legacyRoot, relative) ?: return null
        if (!legacy.isFile) return destination
        return try {
            copyAtomically(legacy, destination)
            legacy.delete()
            destination
        } catch (_: IOException) {
            // Una falta de espacio no debe convertir una foto existente en ilegible.
            legacy
        }
    }

    override fun delete(reference: String) {
        val relative = managedReference(reference) ?: return
        for (root in listOf(privateRoot, legacyRoot)) {
            val file = managedFile(root, relative) ?: continue
            if (file.isFile && !file.delete()) throw IOException("No se pudo eliminar la imagen")
        }
    }

    private fun managedReference(reference: String): String? {
        if (reference.isBlank()) return null
        val file = localFile(reference) ?: return null
        val relative = if (file.isAbsolute) {
            val canonical = file.canonicalFile.toPath()
            val root = listOf(privateRoot, legacyRoot).firstOrNull {
                canonical.startsWith(it.toPath())
            } ?: return null
            root.toPath().relativize(canonical).toString().replace(File.separatorChar, '/')
        } else reference
        val parts = relative.split('/')
        return relative.takeIf {
            parts.size == 3 && parts[0] == "mole_images" &&
                parts.all { part -> part.isNotBlank() && part !in listOf(".", "..") &&
                    '\\' !in part && '\u0000' !in part }
        }
    }

    private fun managedFile(root: File, reference: String): File? =
        File(root, reference).canonicalFile.takeIf {
            it.toPath().startsWith(File(root, "mole_images").canonicalFile.toPath()) &&
                it.toPath().startsWith(root.toPath())
        }

    private fun localFile(reference: String): File? = when {
        reference.startsWith("file:", ignoreCase = true) ->
            runCatching { File(URI(reference)) }.getOrNull()
        Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(reference) -> null
        else -> File(reference)
    }

    private fun copyAtomically(source: File, destination: File) {
        val directory = destination.parentFile ?: throw IOException("Directorio no válido")
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("No se pudo crear el directorio")
        }
        val temporary = Files.createTempFile(directory.toPath(), ".image_", ".tmp").toFile()
        try {
            source.copyTo(temporary, overwrite = true)
            try {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath())
            }
        } finally {
            temporary.delete()
        }
    }
}

package com.monsteraltech.skincare.camera

import android.content.Context
import com.monsteraltech.skincare.data.identity.ActiveProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Durable, account-scoped drafts; excluded from Android backup and cache eviction. */
@Singleton
class CaptureDraftStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profile: ActiveProfile
) {
    data class Draft(val id: String, val file: File)

    fun create(): Draft {
        val id = UUID.randomUUID().toString()
        return Draft(id, file(id).also { check(it.parentFile!!.mkdirs() || it.parentFile!!.isDirectory) })
    }

    fun file(id: String): File {
        require(UUID.fromString(id).toString() == id) { "Referencia de captura no válida" }
        val owner = MessageDigest.getInstance("SHA-256").digest(profile.requireOwnerId().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(context.noBackupFilesDir, "capture_drafts/$owner/$id.jpg")
    }

    /** Compatibility for old in-app intents. Arbitrary external paths are never imported. */
    fun adoptLegacy(path: String): Draft {
        val source = File(path).canonicalFile
        require(source.isFile && source.toPath().startsWith(context.cacheDir.canonicalFile.toPath()))
        val draft = create()
        try {
            source.copyTo(draft.file)
            markReady(draft.id)
            check(source.delete())
            return draft
        } catch (error: Exception) {
            draft.file.delete()
            throw error
        }
    }

    fun markReady(id: String) {
        val image = file(id)
        check(image.isFile && image.length() > 0)
        File(image.path + ".ready").writeText("ready")
    }

    fun isReady(id: String): Boolean = file(id).let { image ->
        val marker = File(image.path + ".ready")
        image.isFile && marker.isFile && marker.readText() == "ready"
    }

    fun discard(id: String) {
        val image = file(id)
        File(image.path + ".ready").delete()
        image.delete()
    }
}

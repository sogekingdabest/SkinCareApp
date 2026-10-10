package com.monsteraltech.skincare.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.monsteraltech.skincare.data.identity.ActiveProfile
import com.google.gson.Gson
import com.monsteraltech.skincare.body.mole.data.local.AnalysisEntity
import com.monsteraltech.skincare.body.mole.data.local.LegacyObservation
import com.monsteraltech.skincare.body.mole.data.local.MoleEntity
import com.monsteraltech.skincare.body.mole.data.local.SkinCareDatabase
import com.monsteraltech.skincare.data.image.PrivateImageStore
import com.monsteraltech.skincare.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class LocalBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: SkinCareDatabase,
    private val activeProfile: ActiveProfile,
    private val settings: BackupSettingsStore,
    private val imageStore: PrivateImageStore,
    private val coordinator: BackupRestoreCoordinator,
    private val records: BackupRecordRestorer,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val dao = database.moleDao()
    private val gson = Gson()
    private val codec = EncryptedBackupCodec()
    private val operations = Mutex()

    suspend fun exportTo(uri: Uri, password: CharArray): Result<BackupSummary> {
        try {
            return withContext(ioDispatcher) {
                operations.withLock { backupResult { export(uri, password) } }
            }
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun importFrom(uri: Uri, password: CharArray): Result<RestoreReport> {
        try {
            return withContext(ioDispatcher) {
                operations.withLock { backupResult { restore(uri, password) } }
            }
        } finally {
            password.fill('\u0000')
        }
    }

    private suspend fun <T> backupResult(action: suspend () -> T): Result<T> = try {
        Result.success(action())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun export(uri: Uri, password: CharArray): BackupSummary {
        val owner = requireCurrentUserId()
        val caller = currentCoroutineContext()
        val checkActive = { caller.ensureActive() }
        val staging = Files.createTempDirectory(context.cacheDir.toPath(), "skincare_export_").toFile()
        try {
            val (moles, analyses) = database.withTransaction { dao.getAllMoles(owner) to dao.getAllAnalyses(owner) }
            checkActive()
            val entries = buildImageEntries(moles, analyses)
            val payload = BackupPayload(PAYLOAD_VERSION, System.currentTimeMillis(), moles, analyses, entries, settings.read(owner))
            validatePayload(payload)
            val encrypted = File(staging, "backup.skb")
            encrypted.outputStream().use { output ->
                codec.encrypt(output, password) { writePayload(it, payload, entries, checkActive) }
            }
            checkActive()
            requireSameProfile(owner)
            // Source errors cannot truncate the destination: complete encryption locally first.
            val output = context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("No se pudo abrir el destino")
            output.use { destination -> encrypted.inputStream().use { it.copyExactly(destination, encrypted.length(), checkActive) } }
            checkActive()
            return BackupSummary(moles.size, analyses.size, entries.size)
        } finally {
            runCatching { staging.deleteRecursively() }
        }
    }

    private suspend fun restore(uri: Uri, password: CharArray): RestoreReport {
        val owner = requireCurrentUserId()
        val caller = currentCoroutineContext()
        val checkActive = { caller.ensureActive() }
        val staging = Files.createTempDirectory(context.cacheDir.toPath(), "skincare_restore_").toFile()
        var failure: Throwable? = null
        var cleanupFailed = false
        val report: RestoreReport
        try {
            checkActive()
            val extracted = decryptAndExtract(uri, password, staging, checkActive)
            validatePayload(extracted.payload)
            val payload = normalizeLegacyPayload(extracted.payload)
            validatePayload(payload)
            require(payload.imageEntries.values.toSet() == extracted.stagedImages.keys) {
                "La copia no contiene todas las imágenes declaradas"
            }
            val sources = payload.imageEntries.mapValues { (_, entry) -> extracted.stagedImages.getValue(entry) }
            report = coordinator.restore(owner, sources, commit = { references ->
                requireSameProfile(owner)
                records.merge(owner, payload, references)
            }, writeSettings = {
                settings.write(payload.accountSettings.copy(userId = owner, autoBackup = false), owner)
            })
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try {
                if (!staging.deleteRecursively()) {
                    cleanupFailed = true
                    failure?.addSuppressed(IOException("No se pudo eliminar el staging"))
                }
            } catch (error: Exception) {
                cleanupFailed = true
                failure?.addSuppressed(error)
            }
        }
        return if (cleanupFailed) report.copy(warnings = report.warnings + RestoreWarning.TEMPORARY_FILES_REMAIN) else report
    }

    private fun decryptAndExtract(
        uri: Uri,
        password: CharArray,
        stagingDirectory: File,
        checkActive: () -> Unit
    ): ExtractedBackup {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("No se pudo abrir la copia seleccionada")
        val stagedImages = mutableMapOf<String, File>()
        lateinit var payload: BackupPayload

        input.use { rawInput ->
            codec.decrypt(rawInput, password) { decryptedInput ->
                val data = DataInputStream(decryptedInput)
                val payloadSize = data.readInt()
                if (payloadSize !in 1..MAX_PAYLOAD_BYTES.toInt()) {
                    throw InvalidBackupException("Tamaño de metadatos no válido")
                }
                val payloadBytes = ByteArray(payloadSize).also(data::readFully)
                payload = runCatching {
                    gson.fromJson(
                        payloadBytes.toString(Charsets.UTF_8),
                        BackupPayload::class.java
                    )
                }.getOrElse {
                    throw InvalidBackupException("Metadatos no válidos", it)
                }

                val imageCount = data.readInt()
                if (imageCount !in 0..MAX_IMAGES) {
                    throw InvalidBackupException("Número de imágenes no válido")
                }
                var totalImageBytes = 0L
                repeat(imageCount) {
                    checkActive()
                    val entryName = data.readUTF()
                    if (!isValidImageEntry(entryName) || entryName in stagedImages) {
                        throw InvalidBackupException("Entrada de imagen no válida")
                    }
                    val imageSize = data.readLong()
                    if (imageSize !in 1..MAX_IMAGE_BYTES) {
                        throw InvalidBackupException("Tamaño de imagen no válido")
                    }
                    totalImageBytes += imageSize
                    if (totalImageBytes > MAX_TOTAL_IMAGE_BYTES) {
                        throw InvalidBackupException("La copia supera el tamaño máximo permitido")
                    }
                    val destination = File(stagingDirectory, "${UUID.randomUUID()}.image")
                    destination.outputStream().use { output ->
                        data.copyExactly(output, imageSize, checkActive)
                    }
                    stagedImages[entryName] = destination
                }

                // Fuerza a CipherInputStream a consumir y autenticar la etiqueta GCM.
                checkActive()
                if (data.read() != -1) {
                    throw InvalidBackupException("La copia contiene datos inesperados")
                }
            }
        }
        return ExtractedBackup(payload, stagedImages)
    }

    private fun writePayload(
        output: OutputStream,
        payload: BackupPayload,
        imageEntries: Map<String, String>,
        checkActive: () -> Unit
    ) {
        checkActive()
        val data = DataOutputStream(output)
        val payloadBytes = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        require(payloadBytes.size <= MAX_PAYLOAD_BYTES) {
            "Los metadatos de la copia son demasiado grandes"
        }
        data.writeInt(payloadBytes.size)
        data.write(payloadBytes)
        data.writeInt(imageEntries.size)
        imageEntries.forEach { (relativePath, archiveEntry) ->
            checkActive()
            val image = resolvePrivateImage(relativePath) ?: throw IOException("Imagen no disponible")
            val length = image.length()
            require(image.isFile && length in 1..MAX_IMAGE_BYTES) {
                "Una imagen no está disponible o supera el tamaño máximo"
            }
            data.writeUTF(archiveEntry)
            data.writeLong(length)
            image.inputStream().use { input ->
                input.copyExactly(data, length, checkActive)
            }
        }
        data.flush()
    }

    internal suspend fun remapRecordsForCurrentUser(
        userId: String, payload: BackupPayload, imagePathMap: Map<String, String>
    ): BackupRecordRestorer.Records = records.remap(userId, payload, imagePathMap)

    internal fun validatePayload(payload: BackupPayload) {
        if (payload.schemaVersion !in 1..PAYLOAD_VERSION) {
            throw InvalidBackupException(
                "Versión de datos no compatible: ${payload.schemaVersion}"
            )
        }
        require(payload.moles.size <= MAX_MOLES) { "Demasiados lunares en la copia" }
        require(payload.analyses.size <= MAX_ANALYSES) { "Demasiados análisis en la copia" }
        require(payload.imageEntries.size <= MAX_IMAGES) { "Demasiadas imágenes en la copia" }
        require(payload.moles.all { it.id.isNotBlank() }) { "ID de lunar no válido" }
        require(payload.moles.map { it.id }.toSet().size == payload.moles.size) {
            "La copia contiene lunares duplicados"
        }
        val moleIds = payload.moles.mapTo(mutableSetOf()) { it.id }
        require(payload.analyses.all { it.id.isNotBlank() && it.moleId in moleIds }) {
            "El historial contiene referencias no válidas"
        }
        require(payload.analyses.map { it.id }.toSet().size == payload.analyses.size) {
            "La copia contiene observaciones duplicadas"
        }
        if (payload.schemaVersion >= 2) {
            val analyses = payload.analyses.associateBy { it.id }
            require(payload.moles.all { mole ->
                val current = mole.currentAnalysisId
                if (current == null) mole.analysisCount == 0 && mole.analysisResult.isBlank()
                else analyses[current]?.moleId == mole.id
            }) { "La copia contiene referencias a observaciones actuales no válidas" }
        }
        require(payload.imageEntries.keys.all(String::isNotBlank) && payload.imageEntries.values.all(::isValidImageEntry)) {
            "La copia contiene rutas de imagen no válidas"
        }
    }

    internal fun normalizeLegacyPayload(payload: BackupPayload): BackupPayload {
        if (payload.schemaVersion != 1) return payload
        val current = payload.moles.filter { it.analysisCount > 0 || it.analysisResult.isNotBlank() }
            .map(LegacyObservation::snapshot)
        val existingIds = payload.analyses.mapTo(mutableSetOf()) { it.id }
        val analyses = payload.analyses + current.filter { it.id !in existingIds }
        val currentIds = current.associate { it.moleId to it.id }
        return payload.copy(schemaVersion = 2,
            moles = payload.moles.map { it.copy(currentAnalysisId = currentIds[it.id]) }, analyses = analyses)
    }

    private fun buildImageEntries(
        moles: List<MoleEntity>,
        analyses: List<AnalysisEntity>
    ): Map<String, String> {
        val paths = buildSet {
            moles.mapTo(this) { it.imagePath }
            analyses.mapTo(this) { it.imagePath }
        }.filter(String::isNotBlank)
        require(paths.size <= MAX_IMAGES) { "Demasiadas imágenes para la copia" }
        var totalBytes = 0L
        return paths.associateWith { path ->
            val image = resolvePrivateImage(path) ?: throw IOException("Falta una imagen necesaria para la copia")
            require(image.isFile && image.length() in 1..MAX_IMAGE_BYTES) { "Imagen no disponible o demasiado grande" }
            totalBytes += image.length()
            require(totalBytes <= MAX_TOTAL_IMAGE_BYTES) { "La copia supera el tamaño máximo" }
            "images/${sha256(path)}.bin"
        }
    }

    private fun resolvePrivateImage(reference: String): File? = imageStore.migrateLegacy(reference)

    private fun isValidImageEntry(name: String): Boolean =
        IMAGE_ENTRY_REGEX.matches(name)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun requireCurrentUserId(): String =
        activeProfile.requireOwnerId()

    private fun requireSameProfile(owner: String) {
        if (activeProfile.ownerId != owner) throw BackupProfileChangedException()
    }

    private fun InputStream.copyExactly(output: OutputStream, expectedBytes: Long, checkActive: () -> Unit) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (total < expectedBytes) {
            checkActive()
            val requested = minOf(buffer.size.toLong(), expectedBytes - total).toInt()
            val read = read(buffer, 0, requested)
            if (read < 0) throw InvalidBackupException("La copia está truncada")
            total += read
            output.write(buffer, 0, read)
        }
    }

    private data class ExtractedBackup(
        val payload: BackupPayload,
        val stagedImages: Map<String, File>
    )

    companion object {
        const val MIME_TYPE = "application/octet-stream"
        const val FILE_EXTENSION = "skb"
        const val MIN_PASSWORD_LENGTH = EncryptedBackupCodec.MIN_PASSWORD_LENGTH
        private const val PAYLOAD_VERSION = 2
        private const val MAX_MOLES = 10_000
        private const val MAX_ANALYSES = 100_000
        private const val MAX_IMAGES = 100_000
        private const val MAX_PAYLOAD_BYTES = 20L * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 25L * 1024 * 1024
        private const val MAX_TOTAL_IMAGE_BYTES = 2L * 1024 * 1024 * 1024
        private val IMAGE_ENTRY_REGEX = Regex("""images/[a-f0-9]{64}\.bin""")
    }
}

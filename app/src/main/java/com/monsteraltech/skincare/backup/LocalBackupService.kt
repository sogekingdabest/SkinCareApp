package com.monsteraltech.skincare.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.monsteraltech.skincare.data.identity.ActiveProfile
import com.google.gson.Gson
import com.monsteraltech.skincare.body.mole.data.local.AnalysisEntity
import com.monsteraltech.skincare.body.mole.data.local.MoleEntity
import com.monsteraltech.skincare.body.mole.data.local.SkinCareDatabase
import com.monsteraltech.skincare.data.FirebaseDataManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LocalBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: SkinCareDatabase,
    private val activeProfile: ActiveProfile,
    private val localDataManager: FirebaseDataManager
) {
    private val dao = database.moleDao()
    private val gson = Gson()
    private val codec = EncryptedBackupCodec()

    suspend fun exportTo(uri: Uri, password: CharArray): Result<BackupSummary> =
        withContext(Dispatchers.IO) {
            try {
                runCatching {
                    val userId = requireCurrentUserId()
                    val moles = dao.getAllMoles(userId)
                    val analyses = dao.getAllAnalyses(userId)
                    val imageEntries = buildImageEntries(moles, analyses)
                    val payload = BackupPayload(
                        schemaVersion = PAYLOAD_VERSION,
                        createdAtEpochMillis = System.currentTimeMillis(),
                        moles = moles,
                        analyses = analyses,
                        imageEntries = imageEntries,
                        accountSettings = localDataManager.getUserSettings(userId)
                    )

                    val output = context.contentResolver.openOutputStream(uri, "w")
                        ?: throw IOException("No se pudo abrir el archivo de destino")
                    output.use { rawOutput ->
                        codec.encrypt(rawOutput, password) { encryptedOutput ->
                            writePayload(encryptedOutput, payload, imageEntries)
                        }
                    }
                    BackupSummary(moles.size, analyses.size, imageEntries.size)
                }
            } finally {
                password.fill('\u0000')
            }
        }

    suspend fun importFrom(uri: Uri, password: CharArray): Result<BackupSummary> =
        withContext(Dispatchers.IO) {
            val stagingDirectory = Files.createTempDirectory(
                context.cacheDir.toPath(),
                "skincare_restore_"
            ).toFile()
            val createdImages = mutableListOf<File>()
            try {
                runCatching {
                    val userId = requireCurrentUserId()
                    val extracted = decryptAndExtract(uri, password, stagingDirectory)
                    validatePayload(extracted.payload)

                    val imagePathMap = copyImagesToPrivateStorage(
                        userId = userId,
                        payload = extracted.payload,
                        stagedImages = extracted.stagedImages,
                        createdImages = createdImages
                    )
                    val restored = remapRecordsForCurrentUser(
                        userId,
                        extracted.payload,
                        imagePathMap
                    )

                    try {
                        database.withTransaction {
                            dao.upsertMoles(restored.moles)
                            dao.upsertAnalyses(restored.analyses)
                        }
                    } catch (error: Exception) {
                        createdImages.forEach { it.delete() }
                        throw error
                    }

                    localDataManager.updateUserSettings(
                        extracted.payload.accountSettings.copy(
                            userId = userId,
                            autoBackup = false
                        ),
                        userId
                    )
                    BackupSummary(
                        moleCount = restored.moles.size,
                        analysisCount = restored.analyses.size,
                        imageCount = createdImages.size
                    )
                }
            } finally {
                password.fill('\u0000')
                stagingDirectory.deleteRecursively()
            }
        }

    private fun decryptAndExtract(
        uri: Uri,
        password: CharArray,
        stagingDirectory: File
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
                    val entryName = data.readUTF()
                    if (!isValidImageEntry(entryName) || entryName in stagedImages) {
                        throw InvalidBackupException("Entrada de imagen no válida")
                    }
                    val imageSize = data.readLong()
                    if (imageSize !in 0..MAX_IMAGE_BYTES) {
                        throw InvalidBackupException("Tamaño de imagen no válido")
                    }
                    totalImageBytes += imageSize
                    if (totalImageBytes > MAX_TOTAL_IMAGE_BYTES) {
                        throw InvalidBackupException("La copia supera el tamaño máximo permitido")
                    }
                    val destination = File(stagingDirectory, "${UUID.randomUUID()}.image")
                    destination.outputStream().use { output ->
                        data.copyExactly(output, imageSize)
                    }
                    stagedImages[entryName] = destination
                }

                // Fuerza a CipherInputStream a consumir y autenticar la etiqueta GCM.
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
        imageEntries: Map<String, String>
    ) {
        val data = DataOutputStream(output)
        val payloadBytes = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        require(payloadBytes.size <= MAX_PAYLOAD_BYTES) {
            "Los metadatos de la copia son demasiado grandes"
        }
        data.writeInt(payloadBytes.size)
        data.write(payloadBytes)
        data.writeInt(imageEntries.size)
        imageEntries.forEach { (relativePath, archiveEntry) ->
            val image = resolvePrivateImage(relativePath)
            val length = image.length()
            require(image.isFile && length <= MAX_IMAGE_BYTES) {
                "Una imagen no está disponible o supera el tamaño máximo"
            }
            data.writeUTF(archiveEntry)
            data.writeLong(length)
            image.inputStream().use { input ->
                input.copyExactly(data, length)
            }
        }
        data.flush()
    }

    private suspend fun remapRecordsForCurrentUser(
        userId: String,
        payload: BackupPayload,
        imagePathMap: Map<String, String>
    ): RestoredRecords {
        val moleIdMap = mutableMapOf<String, String>()
        payload.moles.forEach { mole ->
            val owner = dao.getMoleOwner(mole.id)
            moleIdMap[mole.id] =
                if (owner != null && owner != userId) UUID.randomUUID().toString() else mole.id
        }
        val moles = payload.moles.map { mole ->
            mole.copy(
                id = moleIdMap.getValue(mole.id),
                userId = userId,
                imagePath = restoredImagePath(mole.imagePath, imagePathMap)
            )
        }
        val analyses = payload.analyses.map { analysis ->
            val owner = dao.getAnalysisOwner(analysis.id)
            analysis.copy(
                id = if (owner != null && owner != userId) {
                    UUID.randomUUID().toString()
                } else {
                    analysis.id
                },
                moleId = moleIdMap.getValue(analysis.moleId),
                userId = userId,
                imagePath = restoredImagePath(analysis.imagePath, imagePathMap)
            )
        }
        return RestoredRecords(moles, analyses)
    }

    private fun validatePayload(payload: BackupPayload) {
        if (payload.schemaVersion != PAYLOAD_VERSION) {
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
        require(payload.imageEntries.values.all(::isValidImageEntry)) {
            "La copia contiene rutas de imagen no válidas"
        }
    }

    private fun buildImageEntries(
        moles: List<MoleEntity>,
        analyses: List<AnalysisEntity>
    ): Map<String, String> {
        val paths = buildSet {
            moles.mapTo(this) { it.imagePath }
            analyses.mapTo(this) { it.imagePath }
        }.filter(String::isNotBlank)
        return paths.mapNotNull { path ->
            val image = resolvePrivateImage(path)
            if (image.isFile) {
                path to "images/${sha256(path)}.bin"
            } else {
                null
            }
        }.toMap()
    }

    private fun copyImagesToPrivateStorage(
        userId: String,
        payload: BackupPayload,
        stagedImages: Map<String, File>,
        createdImages: MutableList<File>
    ): Map<String, String> {
        val destinationDirectory = File(
            context.noBackupFilesDir,
            "mole_images/$userId"
        ).apply { mkdirs() }
        return payload.imageEntries.mapNotNull { (originalPath, archiveEntry) ->
            val source = stagedImages[archiveEntry] ?: return@mapNotNull null
            val fileName = "backup_${UUID.randomUUID()}.jpg"
            val relativePath = "mole_images/$userId/$fileName"
            val destination = File(destinationDirectory, fileName)
            source.copyTo(destination, overwrite = false)
            createdImages += destination
            originalPath to relativePath
        }.toMap()
    }

    private fun restoredImagePath(
        originalPath: String,
        restoredPaths: Map<String, String>
    ): String = originalPath.takeIf(String::isNotBlank)
        ?.let(restoredPaths::get)
        .orEmpty()

    private fun resolvePrivateImage(relativePath: String): File =
        File(localDataManager.getFullImagePath(context, relativePath))

    private fun isValidImageEntry(name: String): Boolean =
        IMAGE_ENTRY_REGEX.matches(name)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun requireCurrentUserId(): String =
        activeProfile.requireOwnerId()

    private fun InputStream.copyExactly(output: OutputStream, expectedBytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (total < expectedBytes) {
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

    private data class RestoredRecords(
        val moles: List<MoleEntity>,
        val analyses: List<AnalysisEntity>
    )

    companion object {
        const val MIME_TYPE = "application/octet-stream"
        const val FILE_EXTENSION = "skb"
        const val MIN_PASSWORD_LENGTH = EncryptedBackupCodec.MIN_PASSWORD_LENGTH
        private const val PAYLOAD_VERSION = 1
        private const val MAX_MOLES = 10_000
        private const val MAX_ANALYSES = 100_000
        private const val MAX_IMAGES = 100_000
        private const val MAX_PAYLOAD_BYTES = 20L * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 25L * 1024 * 1024
        private const val MAX_TOTAL_IMAGE_BYTES = 2L * 1024 * 1024 * 1024
        private val IMAGE_ENTRY_REGEX = Regex("""images/[a-f0-9]{64}\.bin""")
    }
}

package com.monsteraltech.skincare.backup

import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal class EncryptedBackupCodec(
    private val secureRandom: SecureRandom = SecureRandom()
) {
    fun encrypt(
        output: OutputStream,
        password: CharArray,
        writePlaintext: (OutputStream) -> Unit
    ) {
        require(password.size >= MIN_PASSWORD_LENGTH) {
            "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres"
        }
        val salt = ByteArray(SALT_SIZE).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_SIZE).also(secureRandom::nextBytes)
        val header = createHeader(salt, iv)
        output.write(header)

        val cipher = createCipher(Cipher.ENCRYPT_MODE, password, salt, iv)
        cipher.updateAAD(header)
        CipherOutputStream(output, cipher).use(writePlaintext)
    }

    fun decrypt(
        input: InputStream,
        password: CharArray,
        readPlaintext: (InputStream) -> Unit
    ) {
        val header = ByteArray(HEADER_SIZE)
        DataInputStream(input).readFully(header)
        val parsed = parseHeader(header)
        val cipher = createCipher(
            Cipher.DECRYPT_MODE,
            password,
            parsed.salt,
            parsed.iv,
            parsed.iterations
        )
        cipher.updateAAD(header)
        CipherInputStream(input, cipher).use(readPlaintext)
    }

    private fun createCipher(
        mode: Int,
        password: CharArray,
        salt: ByteArray,
        iv: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS
    ): Cipher {
        val keySpec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val keyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(keySpec)
                .encoded
        } finally {
            keySpec.clearPassword()
        }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, iv))
            }
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun createHeader(salt: ByteArray, iv: ByteArray): ByteArray =
        java.io.ByteArrayOutputStream(HEADER_SIZE).use { bytes ->
            java.io.DataOutputStream(bytes).use { data ->
                data.write(MAGIC)
                data.writeInt(CONTAINER_VERSION)
                data.writeInt(PBKDF2_ITERATIONS)
                data.write(salt)
                data.write(iv)
            }
            bytes.toByteArray()
        }

    private fun parseHeader(header: ByteArray): ParsedHeader {
        val data = DataInputStream(header.inputStream())
        val magic = ByteArray(MAGIC.size).also(data::readFully)
        if (!magic.contentEquals(MAGIC)) {
            throw InvalidBackupException("El archivo no es una copia de SkinCare")
        }
        val version = data.readInt()
        if (version != CONTAINER_VERSION) {
            throw InvalidBackupException("Versión de copia no compatible: $version")
        }
        val iterations = data.readInt()
        if (iterations !in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) {
            throw InvalidBackupException("Parámetros criptográficos no válidos")
        }
        val salt = ByteArray(SALT_SIZE).also(data::readFully)
        val iv = ByteArray(IV_SIZE).also(data::readFully)
        return ParsedHeader(iterations, salt, iv)
    }

    private data class ParsedHeader(
        val iterations: Int,
        val salt: ByteArray,
        val iv: ByteArray
    )

    companion object {
        const val MIN_PASSWORD_LENGTH = 12
        private val MAGIC = byteArrayOf(
            'S'.code.toByte(),
            'K'.code.toByte(),
            'C'.code.toByte(),
            'A'.code.toByte(),
            'R'.code.toByte(),
            'E'.code.toByte(),
            'B'.code.toByte(),
            'K'.code.toByte()
        )
        private const val CONTAINER_VERSION = 1
        private const val PBKDF2_ITERATIONS = 310_000
        private const val MIN_ACCEPTED_ITERATIONS = 100_000
        private const val MAX_ACCEPTED_ITERATIONS = 2_000_000
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 12
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val HEADER_SIZE = 8 + 4 + 4 + SALT_SIZE + IV_SIZE
    }
}

class InvalidBackupException(message: String, cause: Throwable? = null) :
    GeneralSecurityException(message, cause)

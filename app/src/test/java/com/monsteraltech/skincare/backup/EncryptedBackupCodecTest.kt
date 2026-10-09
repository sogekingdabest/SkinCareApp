package com.monsteraltech.skincare.backup

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EncryptedBackupCodecTest {
    private val codec = EncryptedBackupCodec()

    @Test
    fun `round trip preserves plaintext`() {
        val plaintext = "lunar-history-and-image-bytes".toByteArray()
        val encrypted = ByteArrayOutputStream()

        codec.encrypt(encrypted, "correct horse battery".toCharArray()) { output ->
            output.write(plaintext)
        }

        val restored = ByteArrayOutputStream()
        codec.decrypt(
            encrypted.toByteArray().inputStream(),
            "correct horse battery".toCharArray()
        ) { input ->
            input.copyTo(restored)
        }

        assertArrayEquals(plaintext, restored.toByteArray())
    }

    @Test
    fun `wrong password cannot decrypt backup`() {
        val encrypted = ByteArrayOutputStream()
        codec.encrypt(encrypted, "correct horse battery".toCharArray()) { output ->
            output.write("private".toByteArray())
        }

        assertThrows(Exception::class.java) {
            codec.decrypt(
                encrypted.toByteArray().inputStream(),
                "this password is wrong".toCharArray()
            ) { input ->
                input.readBytes()
            }
        }
    }

    @Test
    fun `tampering is detected`() {
        val encrypted = ByteArrayOutputStream()
        codec.encrypt(encrypted, "correct horse battery".toCharArray()) { output ->
            output.write(ByteArray(1024) { it.toByte() })
        }
        val corrupted = encrypted.toByteArray().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        }

        assertThrows(Exception::class.java) {
            codec.decrypt(
                corrupted.inputStream(),
                "correct horse battery".toCharArray()
            ) { input ->
                input.readBytes()
            }
        }
    }
}

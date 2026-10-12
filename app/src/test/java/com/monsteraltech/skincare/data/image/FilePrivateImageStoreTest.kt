package com.monsteraltech.skincare.data.image

import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilePrivateImageStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var privateRoot: File
    private lateinit var legacyRoot: File
    private lateinit var cacheRoot: File
    private lateinit var store: FilePrivateImageStore
    private val bytes = byteArrayOf(1, 2, 3, 4)

    @Before fun setUp() {
        privateRoot = temporary.newFolder("private")
        legacyRoot = temporary.newFolder("legacy")
        cacheRoot = temporary.newFolder("cache")
        store = FilePrivateImageStore(privateRoot, legacyRoot, cacheRoot)
    }

    private fun source() = File(cacheRoot, "capture.jpg").apply { writeBytes(bytes) }
    private fun legacy(reference: String) = File(legacyRoot, reference).apply {
        parentFile!!.mkdirs()
        writeBytes(bytes)
    }

    @Test fun `guardar y abrir usa la misma referencia privada sin consumir el original`() {
        val original = source()
        val reference = store.save(original, "owner-1")
        assertTrue(reference.startsWith("mole_images/owner-1/"))
        assertEquals(File(privateRoot, reference).canonicalFile, store.resolve(reference))
        assertArrayEquals(bytes, store.open(reference).use { it.readBytes() })
        assertArrayEquals(bytes, original.readBytes())
    }

    @Test fun `dos capturas conservan archivos independientes`() {
        val original = source()
        val first = store.save(original, "owner-1")
        original.writeBytes(byteArrayOf(9))
        val second = store.save(original, "owner-1")
        assertNotEquals(first, second)
        assertArrayEquals(bytes, store.open(first).use { it.readBytes() })
        assertArrayEquals(byteArrayOf(9), store.open(second).use { it.readBytes() })
    }

    @Test fun `resolver una foto heredada no copia ni elimina durante el binding`() {
        val reference = "mole_images/owner/old.jpg"
        val original = legacy(reference)
        assertEquals(original.canonicalFile, store.resolve(reference))
        assertTrue(original.exists())
        assertFalse(File(privateRoot, reference).exists())
    }

    @Test fun `migracion explicita mantiene legibles referencias relativas y absolutas antiguas`() {
        val reference = "mole_images/owner/photo con espacio.jpg"
        val original = legacy(reference)
        val oldUri = original.toURI().toString()
        val migrated = store.migrateLegacy(reference)
        assertEquals(File(privateRoot, reference).canonicalFile, migrated)
        assertFalse(original.exists())
        assertArrayEquals(bytes, store.open(reference).use { it.readBytes() })
        assertArrayEquals(bytes, store.open(oldUri).use { it.readBytes() })
        assertEquals(migrated, store.migrateLegacy(reference))
    }

    @Test fun `si falla la migracion la imagen heredada sigue disponible`() {
        val reference = "mole_images/owner/old.jpg"
        val original = legacy(reference)
        File(privateRoot, "mole_images").writeText("not a directory")
        assertEquals(original.canonicalFile, store.migrateLegacy(reference))
        assertArrayEquals(bytes, store.open(reference).use { it.readBytes() })
        assertTrue(original.exists())
    }

    @Test fun `una ruta ajena o traversal no permite leer ni eliminar archivos`() {
        val external = temporary.newFile("outside.jpg").apply { writeBytes(bytes) }
        for (reference in listOf(external.absolutePath, "../outside.jpg", "mole_images/../outside.jpg", "https://example.com/photo.jpg")) {
            assertNull(store.resolve(reference))
            store.delete(reference)
        }
        assertTrue(external.exists())
        assertThrows(IllegalArgumentException::class.java) { store.save(source(), "../owner") }
    }

    @Test fun `capturas temporales admiten rutas y file uri pero no se eliminan como persistentes`() {
        val capture = source()
        assertEquals(capture.canonicalFile, store.resolve(capture.absolutePath))
        assertArrayEquals(bytes, store.open(capture.toURI().toString()).use { it.readBytes() })
        store.delete(capture.absolutePath)
        assertTrue(capture.exists())
    }

    @Test fun `eliminar la copia persistida conserva la captura original`() {
        val original = source()
        val reference = store.save(original, "owner")
        store.delete(reference)
        assertFalse(requireNotNull(store.resolve(reference)).exists())
        assertTrue(original.exists())
    }

    @Test fun `guardar en un destino inaccesible falla sin consumir el original`() {
        File(privateRoot, "mole_images").writeText("not a directory")
        val original = source()
        assertThrows(IOException::class.java) { store.save(original, "owner") }
        assertArrayEquals(bytes, original.readBytes())
    }

    @Test fun `guardados concurrentes del mismo perfil mantienen todas sus fotos`() {
        val original = source()
        val start = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(4)
        try {
            val captures = (1..20).map {
                workers.submit<String> { start.await(); store.save(original, "owner") }
            }
            start.countDown()
            val references = captures.map { it.get() }
            assertEquals(20, references.toSet().size)
            references.forEach { assertArrayEquals(bytes, store.open(it).use { stream -> stream.readBytes() }) }
        } finally {
            workers.shutdownNow()
        }
    }
}

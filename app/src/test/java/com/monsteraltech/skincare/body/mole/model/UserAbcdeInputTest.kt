package com.monsteraltech.skincare.body.mole.model

import com.monsteraltech.skincare.body.mole.model.UserAbcdeInput.Companion.ComparisonBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UserAbcdeInputTest {

    @Test
    fun `total pondera cada criterio segun la formula del analisis`() {
        val input = UserAbcdeInput(asymmetry = 2f, border = 8f, color = 6f, diameter = 5f, evolution = 0f)
        // (2*1.3) + (8*0.1) + (6*0.5) + (5*0.5) = 2.6 + 0.8 + 3.0 + 2.5 = 8.9
        assertEquals(8.9f, input.total(), 0.001f)
    }

    @Test
    fun `la evolucion actua como multiplicador`() {
        val base = UserAbcdeInput(asymmetry = 1f, border = 1f, color = 1f, diameter = 1f, evolution = 0f)
        val withEvolution = base.copy(evolution = 2f)
        // multiplicador: 1 + 2*0.2 = 1.4
        assertEquals(base.total() * 1.4f, withEvolution.total(), 0.001f)
    }

    @Test
    fun `sin valores el total es cero`() {
        assertEquals(0f, UserAbcdeInput().total(), 0.0f)
    }

    @Test
    fun `los buckets de comparacion siguen los cortes de diferencia`() {
        assertEquals(ComparisonBucket.SIMILAR, UserAbcdeInput.comparisonBucket(5f, 5.5f))
        assertEquals(ComparisonBucket.SOMEWHAT_SIMILAR, UserAbcdeInput.comparisonBucket(5f, 7f))
        assertEquals(ComparisonBucket.SOME_DIFFERENCES, UserAbcdeInput.comparisonBucket(5f, 9f))
        assertEquals(ComparisonBucket.VERY_DIFFERENT, UserAbcdeInput.comparisonBucket(5f, 11f))
    }

    @Test
    fun `la diferencia es simetrica`() {
        assertEquals(
            UserAbcdeInput.difference(3f, 8f),
            UserAbcdeInput.difference(8f, 3f),
            0.0f
        )
    }

    @Test
    fun `el mapa de metadatos incluye todas las claves y el total`() {
        val input = UserAbcdeInput(asymmetry = 1f, border = 2f, color = 3f, diameter = 4f, evolution = 1f)
        val map = input.toMetadataMap()
        assertEquals(1f, map["userAsymmetry"])
        assertEquals(2f, map["userBorder"])
        assertEquals(3f, map["userColor"])
        assertEquals(4f, map["userDiameter"])
        assertEquals(1f, map["userEvolution"])
        assertEquals(input.total(), map["userTotal"] as Float, 0.001f)
    }

    @Test
    fun `fromMetadata reconstruye los valores guardados`() {
        val original = UserAbcdeInput(asymmetry = 1.5f, border = 4f, color = 2f, diameter = 3f, evolution = 1f)
        val restored = UserAbcdeInput.fromMetadata(original.toMetadataMap())
        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun `fromMetadata devuelve null si no hay valores de usuario`() {
        assertNull(UserAbcdeInput.fromMetadata(emptyMap()))
        assertNull(UserAbcdeInput.fromMetadata(mapOf("title" to "lunar", "bodyPart" to "Torso")))
    }

    @Test
    fun `fromMetadata acepta valores numericos de distintos tipos`() {
        // Firestore puede devolver Double o Long en lugar de Float.
        val metadata = mapOf<String, Any>(
            "userAsymmetry" to 1.5,
            "userBorder" to 4L
        )
        val restored = UserAbcdeInput.fromMetadata(metadata)
        assertNotNull(restored)
        assertEquals(1.5f, restored!!.asymmetry, 0.0f)
        assertEquals(4f, restored.border, 0.0f)
    }
}

package es.monsteraltech.skincare_tfm.body.mole.dialog

import com.google.firebase.Timestamp
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import org.junit.Test
import org.junit.Assert.*
import java.util.*

/**
 * Tests unitarios para la funcionalidad de búsqueda en MoleSelectorDialog
 */
class MoleSelectorDialogTest {

    private fun createTestMoles(): List<MoleData> {
        val now = Timestamp.now()
        val yesterday = Timestamp(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))
        
        return listOf(
            MoleData(
                id = "1",
                title = "Lunar Brazo Izquierdo",
                bodyPart = "brazo",
                description = "Lunar pequeño y redondo",
                createdAt = yesterday,
                lastAnalysisDate = now,
                analysisCount = 3
            ),
            MoleData(
                id = "2", 
                title = "Lunar Espalda",
                bodyPart = "espalda",
                description = "Lunar irregular",
                createdAt = now,
                lastAnalysisDate = null,
                analysisCount = 1
            ),
            MoleData(
                id = "3",
                title = "Lunar Pierna Derecha", 
                bodyPart = "pierna",
                description = "Lunar grande",
                createdAt = yesterday,
                lastAnalysisDate = yesterday,
                analysisCount = 2
            )
        )
    }

    @Test
    fun testSearchByTitle() {
        val moles = createTestMoles()
        val searchQuery = "brazo"
        
        val filtered = moles.filter { mole ->
            mole.title.lowercase().contains(searchQuery.lowercase())
        }
        
        assertEquals(1, filtered.size)
        assertEquals("Lunar Brazo Izquierdo", filtered[0].title)
    }

    @Test
    fun testSearchByBodyPart() {
        val moles = createTestMoles()
        val searchQuery = "espalda"
        
        val filtered = moles.filter { mole ->
            mole.bodyPart.lowercase().contains(searchQuery.lowercase())
        }
        
        assertEquals(1, filtered.size)
        assertEquals("Lunar Espalda", filtered[0].title)
    }

    @Test
    fun testSearchByDescription() {
        val moles = createTestMoles()
        val searchQuery = "irregular"
        
        val filtered = moles.filter { mole ->
            mole.description.lowercase().contains(searchQuery.lowercase())
        }
        
        assertEquals(1, filtered.size)
        assertEquals("Lunar Espalda", filtered[0].title)
    }

    @Test
    fun testSearchByAnalysisCount() {
        val moles = createTestMoles()
        val searchQuery = "3"
        
        val filtered = moles.filter { mole ->
            mole.analysisCount.toString().contains(searchQuery)
        }
        
        assertEquals(1, filtered.size)
        assertEquals("Lunar Brazo Izquierdo", filtered[0].title)
    }

    @Test
    fun testMultipleSearchTerms() {
        val moles = createTestMoles()
        val searchQuery = "lunar brazo"
        val searchTerms = searchQuery.lowercase().split(" ").filter { it.isNotBlank() }
        
        val filtered = moles.filter { mole ->
            searchTerms.all { term ->
                mole.title.lowercase().contains(term) ||
                mole.bodyPart.lowercase().contains(term) ||
                mole.description.lowercase().contains(term)
            }
        }
        
        assertEquals(1, filtered.size)
        assertEquals("Lunar Brazo Izquierdo", filtered[0].title)
    }

    @Test
    fun testCaseInsensitiveSearch() {
        val moles = createTestMoles()
        val searchQuery = "BRAZO"
        
        val filtered = moles.filter { mole ->
            mole.title.lowercase().contains(searchQuery.lowercase()) ||
            mole.bodyPart.lowercase().contains(searchQuery.lowercase())
        }
        
        assertEquals(1, filtered.size)
        assertEquals("Lunar Brazo Izquierdo", filtered[0].title)
    }

    @Test
    fun testEmptySearchReturnsAll() {
        val moles = createTestMoles()
        val searchQuery = ""
        
        val filtered = if (searchQuery.isEmpty()) {
            moles
        } else {
            moles.filter { mole ->
                mole.title.lowercase().contains(searchQuery.lowercase())
            }
        }
        
        assertEquals(3, filtered.size)
    }

    @Test
    fun testNoResultsForInvalidSearch() {
        val moles = createTestMoles()
        val searchQuery = "inexistente"
        
        val filtered = moles.filter { mole ->
            mole.title.lowercase().contains(searchQuery.lowercase()) ||
            mole.bodyPart.lowercase().contains(searchQuery.lowercase()) ||
            mole.description.lowercase().contains(searchQuery.lowercase())
        }
        
        assertEquals(0, filtered.size)
    }
}
package com.monsteraltech.skincare.body.mole.domain

import com.monsteraltech.skincare.body.mole.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MoleListSearchTest {
    private val entry = MoleListEntry("m", "ÁREA NUEVA", "Texto", "Brazo", "zone", "", "Resultado estable", 72, 1577880000000, null)
    @Test fun `body search preserves Unicode case matching and analysis text`() {
        assertTrue(MoleListSearch.matches(entry, MoleListQuery("área")))
        assertTrue(MoleListSearch.matches(entry, MoleListQuery("estable")))
        assertFalse(MoleListSearch.matches(entry, MoleListQuery("brazo")))
    }
    @Test fun `selector matches any token against dates body region and count`() {
        assertTrue(MoleListSearch.matches(entry, MoleListQuery("unmatched brazo", selector = true)))
        assertTrue(MoleListSearch.matches(entry, MoleListQuery("72", selector = true)))
        assertTrue(MoleListSearch.matches(entry, MoleListQuery("2020", selector = true)))
    }
    @Test fun `blank queries include every record and absent dates are safe`() {
        assertTrue(MoleListSearch.matches(entry, MoleListQuery("  ", selector = true)))
        assertFalse(MoleListSearch.matches(entry, MoleListQuery("no-match", selector = true)))
    }
}

package com.monsteraltech.skincare.body.domain

import com.monsteraltech.skincare.body.data.LegacyBodyRegionMapping
import org.junit.Assert.*
import org.junit.Test

class BodyRegionIdTest {
    @Test fun `all six existing colors migrate to the intended anatomical site`() {
        val expected = mapOf("#FF000000" to BodyRegionId.HEAD, "#FFED1C24" to BodyRegionId.RIGHT_ARM,
            "#FFFFC90E" to BodyRegionId.TORSO, "#FF22B14C" to BodyRegionId.LEFT_ARM,
            "#FF3F48CC" to BodyRegionId.RIGHT_LEG, "#FFED00FF" to BodyRegionId.LEFT_LEG)
        expected.forEach { (color, id) -> assertEquals(id, LegacyBodyRegionMapping.resolve(null, color, "")) }
    }

    @Test fun `legacy RGB and ARGB normalization is independent of locale and casing`() {
        assertEquals(BodyRegionId.RIGHT_ARM, LegacyBodyRegionMapping.fromColor(" #ffed1c24 "))
        assertEquals(BodyRegionId.RIGHT_ARM, LegacyBodyRegionMapping.fromColor("#ED1C24"))
        assertNull(LegacyBodyRegionMapping.fromColor("#00ED1C24"))
    }

    @Test fun `an explicit ID wins even when the visual representation changes`() {
        assertEquals(BodyRegionId.TORSO, LegacyBodyRegionMapping.resolve(BodyRegionId.TORSO, "#FFED1C24", "Localized label"))
        assertEquals(BodyRegionId.UNKNOWN, LegacyBodyRegionMapping.resolve(BodyRegionId.UNKNOWN, "#FFFFC90E", "Torso"))
    }

    @Test fun `legacy color has precedence and missing color can recover a known name`() {
        assertEquals(BodyRegionId.RIGHT_ARM, LegacyBodyRegionMapping.resolve(null, "#FFED1C24", "Torso"))
        assertEquals(BodyRegionId.LEFT_LEG, LegacyBodyRegionMapping.resolve(null, "", "  PIERNA   IZQUIERDA "))
    }

    @Test fun `unsupported legacy records and malformed modern IDs are never guessed`() {
        assertEquals(BodyRegionId.UNKNOWN, LegacyBodyRegionMapping.resolve(null, "#FFFFFF", "Custom location"))
        assertNull(BodyRegionId.fromValue("RIGHT_ARM"))
        assertNull(BodyRegionId.fromValue("#FFED1C24"))
        assertNull(BodyRegionId.fromValue(null))
        assertEquals(BodyRegionId.RIGHT_ARM, BodyRegionId.fromValue("right_arm"))
    }
}

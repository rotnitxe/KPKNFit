package com.example.kpkn.domain.nutrition.nutrients

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutrientIdTest {
    @Test
    fun hasThirtyEightEntriesWithConsecutiveIds() {
        assertEquals(38, NutrientId.entries.size)
        assertEquals((1..38).toList(), NutrientId.entries.map { it.id })
    }

    @Test
    fun keysAreUnique() {
        assertEquals(38, NutrientId.entries.map { it.key }.toSet().size)
    }

    @Test
    fun fromKeyAndFromIdRoundTrip() {
        NutrientId.entries.forEach {
            assertEquals(it, NutrientId.fromKey(it.key))
            assertEquals(it, NutrientId.fromId(it.id))
        }
        assertNull(NutrientId.fromKey("no_existe"))
        assertNull(NutrientId.fromId(0))
        assertNull(NutrientId.fromId(39))
    }

    @Test
    fun knownUnitsAndOrder() {
        assertEquals("kcal", NutrientId.ENERGY.unit)
        assertEquals(1, NutrientId.ENERGY.id)
        assertEquals("mg", NutrientId.SODIUM.unit)
        assertEquals("µg", NutrientId.VITAMIN_D.unit)
        assertEquals("g", NutrientId.EPA_DHA.unit)
        assertEquals("g", NutrientId.WATER.unit)
        assertEquals(38, NutrientId.WATER.id)
        assertEquals(NutrientId.VITAMIN_A_RAE, NutrientId.fromKey("vitamin_a_rae"))
        assertEquals(NutrientId.FOLATE_DFE, NutrientId.fromId(31))
    }
}

package com.example.kpkn.data.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandedCatalogIntegrityTest {

    @Test
    fun programmaticCatalog_hasUniqueIds() {
        val items = BrandedEnergyKcalCatalog.buildProgrammaticCatalog()
        assertTrue(items.isNotEmpty())
        assertEquals(items.size, items.distinctBy { it.id }.size)
    }

    @Test
    fun energyDrinks_haveCaffeine() {
        val items = BrandedEnergyKcalCatalog.buildProgrammaticCatalog()
            .filter { it.category == "bebida_energetica" }
        assertTrue(items.isNotEmpty())
        assertTrue(items.all { it.caffeineMg > 0.0 })
    }

    @Test
    fun allItems_havePositiveServing() {
        val items = BrandedEnergyKcalCatalog.buildProgrammaticCatalog()
        assertTrue(items.all { it.servingSize > 0.0 })
    }

    @Test
    fun scoreAndRedBull_exist() {
        val items = BrandedEnergyKcalCatalog.buildProgrammaticCatalog()
        assertTrue(items.any { it.name.contains("Score", ignoreCase = true) })
        assertTrue(items.any { it.name.contains("Red Bull", ignoreCase = true) })
        assertTrue(items.any { it.name.contains("Monster", ignoreCase = true) })
    }
}

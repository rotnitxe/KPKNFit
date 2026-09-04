package com.example.kpkn.data.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandedSnackCatalogIntegrityTest {

    @Test
    fun programmaticCatalog_hasUniqueIds() {
        val items = BrandedSnackCatalog.buildProgrammaticCatalog()
        assertTrue(items.size >= 80)
        assertEquals(items.size, items.distinctBy { it.id }.size)
    }

    @Test
    fun allItems_havePositiveServingAndCalories() {
        val items = BrandedSnackCatalog.buildProgrammaticCatalog()
        assertTrue(items.all { it.servingSize > 0.0 && it.calories > 0.0 })
    }

    @Test
    fun starBrands_areSearchable() {
        val items = BrandedSnackCatalog.buildProgrammaticCatalog()
        fun hasAlias(needle: String) = items.any { food ->
            food.name.contains(needle, ignoreCase = true) ||
                food.searchAliases.any { it.contains(needle, ignoreCase = true) }
        }
        assertTrue(hasAlias("oreo"))
        assertTrue(hasAlias("triton") || hasAlias("tritón"))
        assertTrue(hasAlias("sabritas"))
        assertTrue(hasAlias("field") || hasAlias("casino") || hasAlias("morochas"))
        assertTrue(hasAlias("pepitos") || hasAlias("bagley"))
    }

    @Test
    fun hispanicCountries_haveCoverage() {
        val items = BrandedSnackCatalog.buildProgrammaticCatalog()
        val short = mutableListOf<String>()
        listOf("MX", "GT", "SV", "HN", "NI", "CR", "PA", "CU", "DO", "CO", "VE", "EC", "PE", "BO", "CL", "AR", "UY", "PY")
            .forEach { country ->
                val n = items.count { it.tags.contains(country) }
                if (n < 8) short += "$country=$n"
            }
        assertTrue("countries below 8 curated rows: $short", short.isEmpty())
    }
}

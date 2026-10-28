package com.example.kpkn.domain.nutrition.nutrients

import com.example.kpkn.data.models.LoggedFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogNutrientsEnrichmentTest {
    private class FakePort(
        private val records: Map<String, CatalogRecord> = emptyMap(),
        private val values: Map<String, Map<NutrientId, Double>> = emptyMap(),
    ) : NutritionCatalogPort {
        override val generation = "gen-test"
        override fun record(id: String) = records[id]
        override fun nutrientsPer100(id: String) = values[id].orEmpty()
        override fun portions(id: String) = emptyList<CatalogPortion>()
        override fun byGtin(gtin: String): CatalogRecord? = null
        override fun search(queryNorm: String, limit: Int) = emptyList<CatalogRecord>()
        override fun recordFor(source: String, sourceRecordId: String): CatalogRecord? = null
    }

    private fun record(id: String = "r1", basisAmount: Double = 100.0, basisUnit: String = "g", density: Double? = null) =
        CatalogRecord(id, "USDA", "1", "Leche", null, null, null, "RAW", "NA", basisAmount, basisUnit, density)

    private fun port(record: CatalogRecord, values: Map<NutrientId, Double> = mapOf(NutrientId.CALCIUM to 120.0, NutrientId.VITAMIN_C to 0.0)) =
        FakePort(mapOf(record.id to record), mapOf(record.id to values))

    private fun food(id: String? = "r1") =
        LoggedFood(foodName = "Leche", amount = 250.0, calories = 150.0, protein = 8.0, carbs = 12.0, fats = 8.0, catalogRecordId = id)

    @Test
    fun gramBaseCopiesValuesAndKeepsZero() {
        val out = food().withCatalogNutrients(port(record()))
        assertEquals(120.0, out.nutrientsPer100g!!["calcium"]!!, 1e-9)
        assertEquals(0.0, out.nutrientsPer100g!!["vitamin_c"]!!, 0.0)
        assertNull(out.nutrientsPer100g!!["iron"])
        assertEquals("gen-test", out.catalogGeneration)
    }

    @Test
    fun neverTouchesMacrosAmountOrOtherFields() {
        val input = food()
        val out = input.withCatalogNutrients(port(record()))
        assertEquals(input, out.copy(nutrientsPer100g = null, catalogGeneration = null))
    }

    @Test
    fun basisAmount50DoublesValues() {
        val out = food().withCatalogNutrients(port(record(basisAmount = 50.0)))
        assertEquals(240.0, out.nutrientsPer100g!!["calcium"]!!, 1e-9)
    }

    @Test
    fun mlBaseDividesByDensity() {
        val out = food().withCatalogNutrients(port(record(basisUnit = "ml", density = 1.03)))
        assertEquals(120.0 / 1.03, out.nutrientsPer100g!!["calcium"]!!, 1e-9)
    }

    @Test
    fun mlBaseWithoutDensityOrNonPositiveDensityUsesOne() {
        val noDensity = food().withCatalogNutrients(port(record(basisUnit = "ml", density = null)))
        assertEquals(120.0, noDensity.nutrientsPer100g!!["calcium"]!!, 1e-9)
        val zeroDensity = food().withCatalogNutrients(port(record(basisUnit = "ml", density = 0.0)))
        assertEquals(120.0, zeroDensity.nutrientsPer100g!!["calcium"]!!, 1e-9)
    }

    @Test
    fun mlBaseWith50AndDensityCombines() {
        val out = food().withCatalogNutrients(port(record(basisAmount = 50.0, basisUnit = "ml", density = 1.03)))
        assertEquals(120.0 * 2.0 / 1.03, out.nutrientsPer100g!!["calcium"]!!, 1e-9)
    }

    @Test
    fun returnsSameInstanceWhenNothingApplies() {
        val p = port(record())
        val quick = food().copy(isQuickEntry = true)
        assertSame(quick, quick.withCatalogNutrients(p))
        val withSnapshot = food().copy(nutrientsPer100g = mapOf("calcium" to 1.0), catalogGeneration = "old")
        assertSame(withSnapshot, withSnapshot.withCatalogNutrients(p))
        val noLink = food(id = null)
        assertSame(noLink, noLink.withCatalogNutrients(p))
        val unknown = food(id = "zzz")
        assertSame(unknown, unknown.withCatalogNutrients(p))
        val empty = food()
        assertSame(empty, empty.withCatalogNutrients(port(record(), values = emptyMap())))
        val badBasis = food()
        assertSame(badBasis, badBasis.withCatalogNutrients(port(record(basisAmount = 0.0))))
        assertSame(badBasis, badBasis.withCatalogNutrients(port(record(basisAmount = -5.0))))
        assertSame(badBasis, badBasis.withCatalogNutrients(port(record(basisUnit = "oz"))))
    }

    @Test
    fun snapshotIsReadableAfterwards() {
        val out = food().withCatalogNutrients(port(record()))
        assertTrue(out.hasNutrientSnapshot())
        assertEquals(300.0, out.nutrientAmount(NutrientId.CALCIUM)!!, 1e-9)
    }
}

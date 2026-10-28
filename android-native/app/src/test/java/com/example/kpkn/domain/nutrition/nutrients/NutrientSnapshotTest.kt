package com.example.kpkn.domain.nutrition.nutrients

import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.domain.nutrition.scaleFoodByPortion
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutrientSnapshotTest {
    // Copia local de `dbJson` (data/db/Entities.kt), que el carril JVM excluye.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private fun food(amount: Double = 250.0, unit: String = "g", snapshot: Map<String, Double>? = mapOf("calcium" to 40.0)) =
        LoggedFood(foodName = "Leche", amount = amount, unit = unit, nutrientsPer100g = snapshot)

    @Test
    fun scalesPer100ByAmount() {
        assertEquals(100.0, food().nutrientAmount(NutrientId.CALCIUM)!!, 1e-9)
    }

    @Test
    fun nonGramUnitIsNull() {
        assertNull(food(unit = "ml").nutrientAmount(NutrientId.CALCIUM))
        assertNull(food(amount = 0.0).nutrientAmount(NutrientId.CALCIUM))
    }

    @Test
    fun missingKeyIsNullAndDeclaredZeroIsZero() {
        val f = food(snapshot = mapOf("sodium" to 0.0))
        assertNull(f.nutrientAmount(NutrientId.CALCIUM))
        assertEquals(0.0, f.nutrientAmount(NutrientId.SODIUM)!!, 0.0)
        assertNull(food(snapshot = null).nutrientAmount(NutrientId.SODIUM))
    }

    @Test
    fun hasNutrientSnapshot() {
        assertTrue(food().hasNutrientSnapshot())
        assertFalse(food(snapshot = null).hasNutrientSnapshot())
        assertFalse(food(snapshot = emptyMap()).hasNutrientSnapshot())
    }

    @Test
    fun withNutrientSnapshotDropsNonFiniteValues() {
        val f = LoggedFood(foodName = "x", amount = 100.0).withNutrientSnapshot(
            mapOf(
                NutrientId.IRON to 2.5,
                NutrientId.ZINC to Double.NaN,
                NutrientId.SODIUM to Double.POSITIVE_INFINITY,
                NutrientId.CHOLESTEROL to 0.0,
            ),
            "gen-1",
        )
        assertEquals(mapOf("iron" to 2.5, "cholesterol" to 0.0), f.nutrientsPer100g)
        assertEquals("gen-1", f.catalogGeneration)
        assertNull(f.nutrientAmount(NutrientId.ZINC))
    }

    @Test
    fun jsonRoundTripKeepsSnapshot() {
        val original = LoggedFood(foodName = "Leche", amount = 200.0, catalogRecordId = "usda_171265")
            .withNutrientSnapshot(mapOf(NutrientId.CALCIUM to 113.0, NutrientId.VITAMIN_D to 1.2), "gen-7")
        val decoded = json.decodeFromString<LoggedFood>(json.encodeToString(original))
        assertEquals(original, decoded)
        assertEquals("usda_171265", decoded.catalogRecordId)
        assertEquals(226.0, decoded.nutrientAmount(NutrientId.CALCIUM)!!, 1e-9)
    }

    @Test
    fun oldJsonWithoutNewFieldsReadsAsNull() {
        val old = """{"id":"a","foodName":"Arroz","amount":150.0,"unit":"g","calories":200.0}"""
        val decoded = json.decodeFromString<LoggedFood>(old)
        assertNull(decoded.catalogRecordId)
        assertNull(decoded.nutrientsPer100g)
        assertNull(decoded.catalogGeneration)
        assertNull(decoded.nutrientAmount(NutrientId.IRON))
    }

    @Test
    fun scaleFoodByPortionSetsCatalogRecordId() {
        val item = FoodItem(id = "usda_171265", name = "Pollo", servingSize = 100.0, calories = 165.0, protein = 31.0)
        val logged = scaleFoodByPortion(item, amountGrams = 150.0)
        assertEquals("usda_171265", logged.catalogRecordId)
        val plain = scaleFoodByPortion(FoodItem(id = "custom_1", name = "Mio"), amountGrams = 100.0)
        assertNull(plain.catalogRecordId)
    }
}

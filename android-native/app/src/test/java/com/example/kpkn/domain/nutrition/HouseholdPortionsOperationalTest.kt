package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.models.LoggedFood
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseholdPortionsOperationalTest {

    private fun offPack() = GlobalFoodEntity(
        foodId = "off_hallulla_kg",
        name = "Hallulla Ideal 1kg",
        normalizedName = "hallulla ideal 1kg",
        aliasesJson = Json.encodeToString(listOf("hallulla ideal 1kg")),
        calories = 400.0,
        protein = 8.0,
        carbs = 70.0,
        fats = 8.0,
        source = "OFF Chile",
        sourcePriority = 80,
        portionGrams = 1000.0,
    ).toFoodItem()

    @Test
    fun `template pack grams do not beat a household unit`() {
        val stored = LoggedFood(
            foodName = "Hallulla",
            amount = 1000.0,
            quantity = 1.0,
            calories = 4000.0,
            protein = 80.0,
            carbs = 700.0,
            fats = 80.0,
        )
        val (item, grams) = HouseholdPortions.eatenGramsForTemplateFood(stored, "hallulla")
        assertEquals("cl013", item?.id)
        assertTrue("template clamp $grams", grams in 70.0..90.0)
    }

    @Test
    fun `template kilogram-sized rice without kg is a household plate not a sack`() {
        val stored = LoggedFood(
            foodName = "Arroz Blanco (cocido)",
            amount = 1000.0,
            quantity = 1.0,
            calories = 1300.0,
            protein = 27.0,
            carbs = 280.0,
            fats = 3.0,
        )
        val (item, grams) = HouseholdPortions.eatenGramsForTemplateFood(stored, "arroz")
        assertEquals("gen005", item?.id)
        assertTrue("arroz template clamp $grams", grams in 100.0..140.0)
    }

    @Test
    fun `search pick on a simple query does not persist an OFF pack`() {
        val off = offPack()
        val identity = HouseholdPortions.identityForSearchPick(off, "hallulla")
        assertEquals("cl013", identity?.id)
        val grams = HouseholdPortions.eatenGramsForSearchPick(identity!!, "hallulla", off)
        assertTrue("search grams $grams", grams in 70.0..90.0)
        assertFalse(HouseholdPortions.isHouseholdIdentity(off, brandHint = null))
    }

    @Test
    fun `explicit kilogram query may keep the selected row`() {
        val off = offPack()
        val identity = HouseholdPortions.identityForSearchPick(off, "Hallulla Ideal 1kg")
        assertEquals("off_hallulla_kg", identity?.id)
    }

    @Test
    fun `householdStaticFood never returns a global SKU`() {
        listOf("hallulla", "fideos", "arroz", "queso", "atún", "tomate", "leche", "xyznoexiste123").forEach { q ->
            val food = HouseholdPortions.householdStaticFood(q)
            if (food != null) {
                assertFalse("$q leaked ${food.id} ${food.source}", HouseholdPortions.isGlobalSku(food))
            } else {
                assertNull(food)
            }
        }
    }
}

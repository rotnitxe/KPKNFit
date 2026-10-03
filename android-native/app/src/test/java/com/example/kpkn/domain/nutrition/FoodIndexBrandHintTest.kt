package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.models.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * WP-N1: `FoodIndex.brandHintFor` keeps its normalized brand list between calls; the list must be
 * rebuilt whenever the index changes, and the selection rules must not move.
 */
class FoodIndexBrandHintTest {

    private fun offRow(id: String, name: String, brand: String?) = GlobalFoodEntity(
        foodId = id, name = name, brand = brand,
        normalizedName = FoodIdentity.normalize(name), normalizedBrand = brand?.let(FoodIdentity::normalize),
        calories = 100.0, protein = 5.0, carbs = 20.0, fats = 2.0, source = "OFF", nutritionBasis = "PER_100G_AS_SOLD",
    )

    private fun newIndex() = FoodIndex().apply {
        build(
            listOf(
                offRow("off-1", "Leche descremada Colun", "Colun"),
                offRow("off-2", "Yogurt natural Colun Light", "Colun Light"),
                offRow("off-3", "Vivo PRO BIOTICOS", "Avena"),
                offRow("off-4", "Arroz grado 1", "OFF"),
            ),
            buildFoodDatabase(),
            FOOD_ALIASES,
        )
    }

    @Test
    fun `the longest brand named in the query wins and generic or food-class brands are ignored`() {
        val index = newIndex()
        assertEquals("Colun Light", index.brandHintFor("yogurt colun light"))
        assertEquals("Colun", index.brandHintFor("leche colun"))
        assertNull(index.brandHintFor("avena"))
        assertNull(index.brandHintFor("arroz off"))
        assertNull(index.brandHintFor("leche"))
    }

    @Test
    fun `a brand added after the first lookup is picked up`() {
        val index = newIndex()
        assertNull(index.brandHintFor("barrita marca nueva"))
        index.addStaticFood(
            FoodItem(id = "custom-brand-1", name = "Barrita Test", brand = "Marca Nueva", calories = 400.0, protein = 10.0, carbs = 50.0, fats = 15.0, isCustom = true),
        )
        assertEquals("Marca Nueva", index.brandHintFor("barrita marca nueva"))
        assertEquals("Colun", index.brandHintFor("leche colun"))
    }
}

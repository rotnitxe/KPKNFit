package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*
import com.example.kpkn.data.models.RecipeIngredient as UserRecipeIngredient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RecipeLoggerPipelineTest {
    private fun portion(grams: Boolean = false, uncertain: Boolean = false): LoggedFood {
        val ingredient = LoggedFood(amount = 100.0, calories = 800.0, protein = 80.0,
            potassiumMg = 400.0, waterMl = 200.0,
            micronutrients = listOf(Micronutrient("Calcio", 120.0, "mg")),
            caloriesMin = if (uncertain) 600.0 else null,
            caloriesMax = if (uncertain) 1000.0 else null, isUncertain = uncertain)
        val recipe = RecipeData(listOf(UserRecipeIngredient(ingredient)), servings = 4,
            finalWeightGrams = if (grams) 600.0 else null, version = 3)
        val food = RecipeCalculator.asFood("recipe:one", "Mi receta", recipe)
        return if (grams) RecipeCalculator.portion(food, grams = 150.0, id = "portion")
        else RecipeCalculator.portion(food, servings = 0.5, id = "portion")
    }

    @Test fun `recipe through real logger keeps fractional rations macros provenance and JSON`() {
        val original = portion()
        val tag = loggedFoodToEditableTag(original)
        val interpretation = requireNotNull(tag.interpretationV2)
        assertTrue(interpretation.canFinalize())
        assertFalse(interpretation.isUncertain)
        assertNull(tag.confirmedLearning())
        val saved = interpretation.toLoggedFood(requireNotNull(tag.loggedFood))
        assertEquals("ración", saved.unit)
        assertEquals(0.5, saved.amount, 0.0)
        assertEquals(100.0, saved.calories, 0.0)
        assertEquals(100.0, saved.caloriesMin!!, 0.0)
        assertEquals(original.nutritionReferenceNote, saved.nutritionReferenceNote)
        assertEquals("recipe:one", saved.recipeId)
        assertEquals(3, saved.recipeVersion)
        assertTrue(saved.evidenceJson!!.contains("CUSTOM_CONFIRMED"))
        assertEquals(saved, Json.decodeFromString<LoggedFood>(Json.encodeToString(saved)))
        assertEquals(saved, loggedFoodToEditableTag(saved).loggedFood?.copy(interpretationId = saved.interpretationId))
    }

    @Test fun `recipe grams through logger remain grams and retain exact nutrient snapshot`() {
        val original = portion(grams = true)
        val saved = requireNotNull(loggedFoodToEditableTag(original).loggedFood)
        assertEquals("g", saved.unit)
        assertEquals(150.0, saved.amount, 0.0)
        assertEquals(200.0, saved.calories, 0.0)
        assertEquals(original.micronutrients, saved.micronutrients)
    }

    @Test fun `resizing fractional recipe in logger scales every nutrient and uncertainty interval once`() {
        val initial = loggedFoodToEditableTag(portion(uncertain = true))
        val resized = rescaleEstimatedFood(requireNotNull(initial.baseLoggedFood), 1.5)
        val edited = refreshAfterEdit(initial.copy(amountGrams = 1.5, baseAmountGrams = 1.5,
            portionMinGrams = 1.5, portionMaxGrams = 1.5,
            loggedFood = resized, baseLoggedFood = resized), "portion")
        val saved = requireNotNull(edited.loggedFood)
        assertTrue(requireNotNull(edited.interpretationV2).canFinalize())
        assertEquals("ración", saved.unit)
        assertEquals(1.5, saved.amount, 0.0)
        assertEquals(300.0, saved.calories, 0.0)
        assertEquals(225.0, saved.caloriesMin!!, 0.0)
        assertEquals(375.0, saved.caloriesMax!!, 0.0)
        assertEquals(150.0, saved.potassiumMg, 0.0)
        assertEquals(75.0, saved.waterMl, 0.0)
        assertEquals(45.0, saved.micronutrients.single().amount, 0.0)
        assertTrue(saved.isUncertain)
        assertNull(edited.confirmedLearning())
    }
}

package com.example.kpkn.screens.nutrition.recipes

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class RecipeDraftTest {
    private val ingredient = RecipeIngredientDraft(RecipeIngredient(LoggedFood(foodName = "Arroz", amount = 400.0, calories = 800.0)))
    private val valid = RecipeDraft(name = "Arroz", servings = "4", finalWeight = "600", ingredients = listOf(ingredient))
    @Test fun `el formulario admite coma decimal y escala desde la instantanea original`() {
        val form = valid.copy(finalWeight = "600,5", ingredients = listOf(ingredient.copy(amount = "200")))
        val food = form.food("recipe")
        assertEquals(100.0, food.calories, 0.0)
        assertEquals(600.5, food.recipe!!.finalWeightGrams!!, 0.0)
        assertEquals(800.0, ingredient.ingredient.snapshot.calories, 0.0)
    }
    @Test fun `un texto invalido no conserva silenciosamente la cantidad anterior`() {
        for (amount in listOf("", "x", "0", "-1", "NaN")) {
            assertTrue(runCatching { valid.copy(ingredients = listOf(ingredient.copy(amount = amount))).food("recipe") }.isFailure)
        }
        assertTrue(runCatching { valid.copy(servings = "2.5").food("recipe") }.isFailure)
        assertTrue(runCatching { valid.copy(finalWeight = "abc").food("recipe") }.isFailure)
    }
    @Test fun `editar una receta carga todos sus ingredientes y notas sin resolverlos otra vez`() {
        val food = valid.copy(notes = "Agua al gusto").food("recipe")
        assertEquals(food, RecipeDraft.fromFood(food).food(food.id, food))
    }
}

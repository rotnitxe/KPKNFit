package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*
import com.example.kpkn.data.models.RecipeIngredient as UserRecipeIngredient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RecipeCalculatorTest {
    private fun recipe(weight: Double? = 600.0) = RecipeData(
        ingredients = listOf(
            UserRecipeIngredient(LoggedFood(foodName = "Arroz", amount = 400.0, calories = 400.0, carbs = 80.0,
                fiber = 4.0, sodiumMg = 40.0, micronutrients = listOf(Micronutrient("Hierro", 4.0, "mg"))), "ausente"),
            UserRecipeIngredient(LoggedFood(foodName = "Pollo", amount = 400.0, calories = 400.0, protein = 80.0,
                fats = 8.0, micronutrients = listOf(Micronutrient("Hierro", 2.0, "mg")))),
        ), servings = 4, finalWeightGrams = weight,
    )
    private fun food(weight: Double? = 600.0) = RecipeCalculator.asFood("recipe", "Arroz con pollo", recipe(weight))

    @Test fun `800 kcal en cuatro raciones son 200 por racion`() {
        val result = RecipeCalculator.calculate(recipe())
        assertEquals(800.0, result.batch.calories, 0.0)
        assertEquals(200.0, result.perServing.calories, 0.0)
        assertEquals(20.0, result.perServing.protein, 0.0)
        assertEquals(1.5, result.perServing.micronutrients.single().amount, 0.0)
    }
    @Test fun `150 g de un lote de 600 g equivalen a una de cuatro raciones`() {
        val byWeight = RecipeCalculator.portion(food(), grams = 150.0, id = "weight")
        val byServing = RecipeCalculator.portion(food(), servings = 1.0, id = "serving")
        assertEquals(200.0, byWeight.calories, 0.0)
        assertEquals(byServing.protein, byWeight.protein, 0.0)
        assertEquals(byServing.micronutrients, byWeight.micronutrients)
        assertEquals(10.0, byWeight.sodiumMg, 0.0)
        assertEquals("g", byWeight.unit)
        assertEquals("ración", byServing.unit)
    }
    @Test fun `sin peso final solo se registra por raciones`() {
        assertNull(RecipeCalculator.calculate(recipe(null)).per100g)
        assertEquals(200.0, RecipeCalculator.portion(food(null), servings = 1.0).calories, 0.0)
        assertTrue(runCatching { RecipeCalculator.portion(food(null), grams = 150.0) }.isFailure)
    }
    @Test fun `cambiar el peso cocinado no cambia nutrientes del lote`() {
        assertEquals(RecipeCalculator.calculate(recipe(400.0)).batch, RecipeCalculator.calculate(recipe(900.0)).batch)
        assertEquals(200.0, RecipeCalculator.calculate(recipe(400.0)).per100g!!.calories, 0.0)
    }
    @Test fun `editar la definicion no cambia la instantanea registrada ni depende del catalogo`() {
        val original = food()
        val logged = RecipeCalculator.portion(original, servings = 1.0)
        val history = NutritionLog(id = "history", foods = listOf(logged))
        val json = Json.encodeToString(history)
        val edited = original.copy(recipe = recipe().copy(version = 2, servings = 2))
        assertEquals(400.0, RecipeCalculator.portion(edited, servings = 1.0).calories, 0.0)
        assertEquals(history, Json.decodeFromString<NutritionLog>(json))
        assertEquals(200.0, history.foods.single().calories, 0.0)
        assertEquals("ausente", original.recipe!!.ingredients.first().foodId)
    }
    @Test fun `JSON anterior sin receta sigue siendo un alimento normal y receta nueva hace ida y vuelta`() {
        assertNull(Json.decodeFromString<FoodItem>("{\"id\":\"old\",\"name\":\"Pan\"}").recipe)
        assertEquals(food(), Json.decodeFromString<FoodItem>(Json.encodeToString(food())))
    }
    @Test fun `valida raciones ingredientes cantidades y nutrientes finitos sin ceros ficticios`() {
        val invalid = listOf(recipe().copy(servings = 0), recipe().copy(ingredients = emptyList()),
            recipe(0.0), recipe(-1.0), recipe(Double.NaN), recipe(Double.POSITIVE_INFINITY),
            recipe().copy(ingredients = listOf(UserRecipeIngredient(LoggedFood(amount = 0.0)))),
            recipe().copy(ingredients = listOf(UserRecipeIngredient(LoggedFood(amount = 1.0, calories = Double.NaN)))),
            recipe().copy(ingredients = listOf(UserRecipeIngredient(LoggedFood(amount = 1.0, sodiumMg = -1.0)))))
        invalid.forEach { assertTrue(it.toString(), runCatching { RecipeCalculator.calculate(it) }.isFailure) }
        for (quantity in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertTrue(runCatching { RecipeCalculator.portion(food(), servings = quantity) }.isFailure)
        }
        assertTrue(runCatching { RecipeCalculator.portion(food(), servings = 1.0, grams = 50.0) }.isFailure)
    }
    @Test fun `peso final inusual avisa pero no bloquea`() {
        assertNotNull(RecipeCalculator.calculate(recipe(20.0)).warning)
        assertEquals(200.0, RecipeCalculator.portion(food(20.0), servings = 1.0).calories, 0.0)
    }
    @Test fun `editar cantidad escala la instantanea y conserva su banda de incertidumbre`() {
        val ingredient = UserRecipeIngredient(LoggedFood(foodName = "Plato", amount = 200.0, calories = 400.0,
            caloriesMin = 300.0, caloriesMax = 500.0, isUncertain = true,
            fatBreakdown = FatBreakdown(saturated = 4.0), micronutrients = listOf(Micronutrient("Calcio", 80.0, "mg"))))
        val resized = RecipeCalculator.resizeIngredient(ingredient, 100.0)
        assertEquals(200.0, resized.snapshot.calories, 0.0)
        val data = RecipeData(listOf(resized), servings = 2)
        val result = RecipeCalculator.calculate(data).perServing
        assertEquals(100.0, result.calories, 0.0)
        assertEquals(75.0, result.caloriesMin!!, 0.0)
        assertEquals(125.0, result.caloriesMax!!, 0.0)
        assertEquals(20.0, result.micronutrients.single().amount, 0.0)
        assertEquals(1.0, result.fatBreakdown!!.saturated, 0.0)
        assertTrue(result.isUncertain)
        assertEquals(400.0, ingredient.snapshot.calories, 0.0)
    }
}

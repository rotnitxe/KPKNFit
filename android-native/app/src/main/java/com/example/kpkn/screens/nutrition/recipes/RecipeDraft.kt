package com.example.kpkn.screens.nutrition.recipes

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.RecipeCalculator
import com.example.kpkn.screens.nutrition.customfood.CustomFoodValidator

/** Cada cantidad se calcula desde su instantánea base, sin acumular redondeos ni consultar el catálogo. */
data class RecipeIngredientDraft(val ingredient: RecipeIngredient, val amount: String = ingredient.snapshot.amount.toString())

data class RecipeDraft(
    val name: String = "",
    val servings: String = "1",
    val finalWeight: String = "",
    val notes: String = "",
    val ingredients: List<RecipeIngredientDraft> = emptyList(),
) {
    fun food(id: String, base: FoodItem? = null): FoodItem {
        require(name.isNotBlank() && name.trim().length <= 80) { "Escribe un nombre de hasta 80 caracteres." }
        val count = requireNotNull(servings.trim().toIntOrNull()) { "Las raciones deben ser un número entero." }
        val final = if (finalWeight.isBlank()) null else requireNotNull(CustomFoodValidator.parseNumber(finalWeight)) {
            "Revisa el peso final en gramos."
        }
        val snapshots = ingredients.map { draft ->
            val amount = requireNotNull(CustomFoodValidator.parseNumber(draft.amount)) { "Revisa las cantidades de los ingredientes." }
            RecipeCalculator.resizeIngredient(draft.ingredient, amount)
        }
        return RecipeCalculator.asFood(id, name, RecipeData(
            ingredients = snapshots, servings = count, finalWeightGrams = final,
            version = base?.recipe?.version ?: 1, notes = notes.trim().ifBlank { null },
        ), base)
    }
    companion object {
        fun fromFood(food: FoodItem): RecipeDraft {
            val recipe = requireNotNull(food.recipe)
            return RecipeDraft(food.name, recipe.servings.toString(), recipe.finalWeightGrams?.toString().orEmpty(),
                recipe.notes.orEmpty(), recipe.ingredients.map(::RecipeIngredientDraft))
        }
    }
}

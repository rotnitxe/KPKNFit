package com.example.kpkn.screens.nutrition.recipes

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.RecipeCalculator
import com.example.kpkn.screens.nutrition.customfood.CustomFoodValidator

/** Cada cantidad se calcula desde su instantánea base, sin acumular redondeos ni consultar el catálogo. */
@kotlinx.serialization.Serializable
data class RecipeIngredientDraft(
    val ingredient: RecipeIngredient,
    val amount: String = ingredient.snapshot.amount.toString(),
    /** Identidad estable de la fila repetida; no cambia al reordenar ni rotar. */
    val id: String = java.util.UUID.randomUUID().toString(),
)

@kotlinx.serialization.Serializable
data class RecipeValidationErrors(
    val nameError: String? = null,
    val servingsError: String? = null,
    val finalWeightError: String? = null,
    val ingredientsEmptyError: String? = null,
    val ingredientAmountErrors: Map<String, String> = emptyMap(),
) {
    val hasErrors: Boolean
        get() = nameError != null || servingsError != null || finalWeightError != null ||
            ingredientsEmptyError != null || ingredientAmountErrors.isNotEmpty()
}

@kotlinx.serialization.Serializable
data class RecipeDraft(
    val name: String = "",
    val servings: String = "1",
    val finalWeight: String = "",
    val notes: String = "",
    val ingredients: List<RecipeIngredientDraft> = emptyList(),
) {
    fun validationErrors(): RecipeValidationErrors {
        val nameErr = when {
            name.isBlank() -> "Escribe un nombre de hasta 80 caracteres."
            name.trim().length > 80 -> "Máximo 80 caracteres."
            else -> null
        }
        val servingsCount = servings.trim().toIntOrNull()
        val servingsErr = if (servingsCount == null || servingsCount < 1) {
            "Las raciones deben ser un número entero."
        } else {
            null
        }
        val finalWeightErr = if (finalWeight.isNotBlank()) {
            val parsed = CustomFoodValidator.parseNumber(finalWeight)
            if (parsed == null || parsed <= 0.0) "Revisa el peso final en gramos." else null
        } else {
            null
        }
        val amountErrors = buildMap {
            ingredients.forEach { draft ->
                val parsed = CustomFoodValidator.parseNumber(draft.amount)
                if (parsed == null || parsed <= 0.0) {
                    put(draft.id, "Indica una cantidad mayor que cero.")
                }
            }
        }
        val emptyErr = if (ingredients.isEmpty()) "Añade al menos un ingrediente." else null
        return RecipeValidationErrors(
            nameError = nameErr,
            servingsError = servingsErr,
            finalWeightError = finalWeightErr,
            ingredientsEmptyError = emptyErr,
            ingredientAmountErrors = amountErrors,
        )
    }

    fun food(id: String, base: FoodItem? = null): FoodItem {
        val errors = validationErrors()
        require(errors.nameError == null) { errors.nameError!! }
        val count = requireNotNull(servings.trim().toIntOrNull()?.takeIf { it >= 1 }) {
            errors.servingsError ?: "Las raciones deben ser un número entero."
        }
        val final = if (finalWeight.isBlank()) null else requireNotNull(CustomFoodValidator.parseNumber(finalWeight)) {
            errors.finalWeightError ?: "Revisa el peso final en gramos."
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

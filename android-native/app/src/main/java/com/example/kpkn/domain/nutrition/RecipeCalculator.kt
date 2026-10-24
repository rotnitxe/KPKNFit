package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*
import com.example.kpkn.data.models.RecipeIngredient as UserRecipeIngredient
import java.util.Locale

/** Una receta suma instantáneas: cambiar el agua del lote cambia su densidad, nunca sus nutrientes. */
object RecipeCalculator {
    data class Result(
        val batch: LoggedFood,
        val perServing: LoggedFood,
        val per100g: LoggedFood?,
        val warning: String?,
    )

    fun calculate(recipe: RecipeData): Result {
        require(recipe.servings >= 1) { "Indica al menos una ración." }
        require(recipe.version >= 1) { "La versión de la receta no es válida." }
        require(recipe.ingredients.isNotEmpty()) { "Añade al menos un ingrediente." }
        require(recipe.finalWeightGrams == null || recipe.finalWeightGrams.isFinite() && recipe.finalWeightGrams > 0.0) {
            "El peso final debe ser un número mayor que cero."
        }
        val foods = recipe.ingredients.map { it.snapshot }
        foods.forEach { food ->
            require(food.amount.isFinite() && food.amount > 0.0 && food.quantity.isFinite() && food.quantity > 0.0) {
                "Cada ingrediente necesita una cantidad válida mayor que cero."
            }
            require(nutrients(food).all { it.isFinite() && it >= 0.0 } &&
                food.micronutrients.all { it.amount.isFinite() && it.amount >= 0.0 }) {
                "Los nutrientes deben ser números finitos y no negativos."
            }
        }
        fun sum(value: (LoggedFood) -> Double): Double = foods.sumOf(value).also {
            require(it.isFinite()) { "El total de la receta es demasiado grande." }
        }
        fun bound(value: (LoggedFood) -> Double?, fallback: (LoggedFood) -> Double): Double? =
            if (foods.any { value(it) != null }) sum { value(it) ?: fallback(it) } else null
        val micronutrients = foods.flatMap { it.micronutrients }
            .groupBy { it.name.trim().lowercase(Locale.ROOT) to it.unit.trim().lowercase(Locale.ROOT) }
            .values.map { entries -> entries.first().copy(amount = entries.sumOf { it.amount }.also {
                require(it.isFinite()) { "El total de la receta es demasiado grande." }
            }) }
        val fat = foods.mapNotNull { it.fatBreakdown }.takeIf { it.isNotEmpty() }?.let { parts ->
            FatBreakdown(parts.sumOf { it.saturated }, parts.sumOf { it.monounsaturated },
                parts.sumOf { it.polyunsaturated }, parts.sumOf { it.trans })
        }
        val batch = LoggedFood(
            foodName = "Lote", amount = 1.0, unit = "lote",
            calories = sum { it.calories }, protein = sum { it.protein }, carbs = sum { it.carbs }, fats = sum { it.fats },
            fiber = sum { it.fiber }, sugar = sum { it.sugar }, sodiumMg = sum { it.sodiumMg },
            potassiumMg = sum { it.potassiumMg }, waterMl = sum { it.waterMl },
            caffeineMg = sum { it.caffeineMg }, creatineG = sum { it.creatineG },
            fatBreakdown = fat, micronutrients = micronutrients,
            isUncertain = foods.any { it.isUncertain },
            nutritionReferenceNote = "Receta propia · versión ${recipe.version}",
            analysisSource = AnalysisSource.USER_MEMORY,
            caloriesMin = bound({ it.caloriesMin }, { it.calories }), caloriesMax = bound({ it.caloriesMax }, { it.calories }),
            proteinMin = bound({ it.proteinMin }, { it.protein }), proteinMax = bound({ it.proteinMax }, { it.protein }),
            carbsMin = bound({ it.carbsMin }, { it.carbs }), carbsMax = bound({ it.carbsMax }, { it.carbs }),
            fatsMin = bound({ it.fatsMin }, { it.fats }), fatsMax = bound({ it.fatsMax }, { it.fats }),
        )
        require(nutrients(batch).all { it.isFinite() }) { "El total de la receta es demasiado grande." }
        val ingredientsGrams = foods.filter { it.unit == "g" }.sumOf { it.amount }
        val unusualWeight = recipe.finalWeightGrams?.let { final ->
            ingredientsGrams > 0.0 && (final < ingredientsGrams / 4.0 || final > ingredientsGrams * 4.0)
        } == true
        return Result(
            batch = batch,
            perServing = scaled(batch, 1.0 / recipe.servings).copy(amount = 1.0, unit = "ración"),
            per100g = recipe.finalWeightGrams?.let { scaled(batch, 100.0 / it).copy(amount = 100.0, unit = "g") },
            warning = if (unusualWeight) "Revisa el peso final: difiere mucho del peso de los ingredientes. Puedes guardarlo igual." else null,
        )
    }

    /** Ficha normal del catálogo propio: nutrientes por ración y, si hay rendimiento, masa de esa ración. */
    fun asFood(id: String, name: String, recipe: RecipeData, base: FoodItem? = null): FoodItem {
        require(id.isNotBlank() && name.isNotBlank()) { "Escribe un nombre para la receta." }
        val portion = calculate(recipe).perServing
        val grams = recipe.finalWeightGrams?.div(recipe.servings)
        return (base ?: FoodItem()).copy(
            id = id, name = name.trim(), source = "recipe", sourceRecordId = null,
            isCustom = true, isAiInferred = false, recipe = recipe,
            servingSize = grams ?: 1.0, servingUnit = if (grams != null) "g" else "ración",
            unit = if (grams != null) "g" else "ración", portionGrams = grams,
            nutritionBasis = "PER_SERVING", foodState = "PREPARED",
            calories = portion.calories, protein = portion.protein, carbs = portion.carbs, fats = portion.fats,
            carbBreakdown = CarbBreakdown(portion.fiber, portion.sugar),
            fatBreakdown = portion.fatBreakdown, micronutrients = portion.micronutrients,
            caffeineMg = portion.caffeineMg, creatineG = portion.creatineG,
            normalizedName = null, normalizedBrand = null, qualityFlags = emptyList(),
        )
    }

    /** El borrador conserva esta instantánea aunque se edite o borre la definición después. */
    fun portion(food: FoodItem, servings: Double? = null, grams: Double? = null, id: String = ""): LoggedFood {
        val recipe = requireNotNull(food.recipe) { "El alimento no es una receta." }
        require((servings == null) != (grams == null)) { "Elige raciones o gramos." }
        val quantity = servings ?: requireNotNull(grams)
        require(quantity.isFinite() && quantity > 0.0) { "La cantidad debe ser mayor que cero." }
        val ratio = if (servings != null) servings / recipe.servings else {
            val final = requireNotNull(recipe.finalWeightGrams) { "Añade el peso final para registrar por gramos." }
            requireNotNull(grams) / final
        }
        val result = scaled(calculate(recipe).batch, ratio).copy(
            id = id, foodName = food.name, amount = quantity, unit = if (grams != null) "g" else "ración",
            recipeId = food.id, recipeVersion = recipe.version,
            evidenceJson = """{"source":"CUSTOM_CONFIRMED"}""",
        )
        require(nutrients(result).all { it.isFinite() } && result.micronutrients.all { it.amount.isFinite() }) {
            "La cantidad es demasiado grande."
        }
        return result
    }

    /** Escala una porción guardada en su unidad declarada, con todos los nutrientes y su intervalo. */
    fun resizePortion(food: LoggedFood, amount: Double): LoggedFood {
        require(food.recipeId != null && food.amount.isFinite() && food.amount > 0.0)
        require(amount.isFinite() && amount > 0.0)
        val result = scaled(food, amount / food.amount).copy(amount = amount)
        require(nutrients(result).all { it.isFinite() } && result.micronutrients.all { it.amount.isFinite() })
        return result
    }

    /** Escala la instantánea original una sola vez; no consulta el catálogo al editar cantidades. */
    fun resizeIngredient(ingredient: UserRecipeIngredient, amount: Double): UserRecipeIngredient {
        val original = ingredient.snapshot
        require(amount.isFinite() && amount > 0.0 && original.amount.isFinite() && original.amount > 0.0) {
            "La cantidad del ingrediente debe ser mayor que cero."
        }
        val snapshot = scaled(original, amount / original.amount).copy(amount = amount)
        require(nutrients(snapshot).all { it.isFinite() } && snapshot.micronutrients.all { it.amount.isFinite() }) {
            "La cantidad es demasiado grande."
        }
        return ingredient.copy(snapshot = snapshot)
    }

    private fun nutrients(food: LoggedFood): List<Double> = listOf(
        food.calories, food.protein, food.carbs, food.fats, food.fiber, food.sugar,
        food.sodiumMg, food.potassiumMg, food.waterMl, food.caffeineMg, food.creatineG,
    ) + listOfNotNull(food.caloriesMin, food.caloriesMax, food.proteinMin, food.proteinMax,
        food.carbsMin, food.carbsMax, food.fatsMin, food.fatsMax) + food.fatBreakdown?.let { listOf(it.saturated, it.monounsaturated, it.polyunsaturated, it.trans) }.orEmpty()

    private fun scaled(food: LoggedFood, ratio: Double): LoggedFood = food.copy(
        calories = food.calories * ratio, protein = food.protein * ratio, carbs = food.carbs * ratio, fats = food.fats * ratio,
        fiber = food.fiber * ratio, sugar = food.sugar * ratio, sodiumMg = food.sodiumMg * ratio,
        potassiumMg = food.potassiumMg * ratio, waterMl = food.waterMl * ratio,
        caffeineMg = food.caffeineMg * ratio, creatineG = food.creatineG * ratio,
        caloriesMin = food.caloriesMin?.times(ratio), caloriesMax = food.caloriesMax?.times(ratio),
        proteinMin = food.proteinMin?.times(ratio), proteinMax = food.proteinMax?.times(ratio),
        carbsMin = food.carbsMin?.times(ratio), carbsMax = food.carbsMax?.times(ratio),
        fatsMin = food.fatsMin?.times(ratio), fatsMax = food.fatsMax?.times(ratio),
        micronutrients = food.micronutrients.map { it.copy(amount = it.amount * ratio) },
        fatBreakdown = food.fatBreakdown?.let { FatBreakdown(it.saturated * ratio, it.monounsaturated * ratio,
            it.polyunsaturated * ratio, it.trans * ratio) },
    )
}

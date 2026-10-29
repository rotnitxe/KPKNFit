package com.example.kpkn.screens.nutrition.recipes

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.RecipeCalculator

object RecipeSamples {
    val food = RecipeCalculator.asFood("sample-recipe", "Arroz con pollo", RecipeData(
        ingredients = listOf(
            RecipeIngredient(LoggedFood(foodName = "Arroz cocido", amount = 400.0, calories = 400.0, carbs = 80.0)),
            RecipeIngredient(LoggedFood(foodName = "Pollo cocido", amount = 400.0, calories = 400.0, protein = 80.0, fats = 8.0)),
        ), servings = 4, finalWeightGrams = 600.0,
    ))
    val draft = RecipeDraft.fromFood(food)
}

package com.example.kpkn.screens.nutrition.recipes

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.input.ImeAction
import com.example.kpkn.domain.nutrition.*
import com.example.kpkn.screens.nutrition.design.logger.LoggerFoodItem
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1000dp-mdpi")
class RecipeLoggerUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun rationEditorAcceptsDecimalsAndShowsRationsInsteadOfGrams() {
        var amount: Double? = null
        val food = RecipeCalculator.asFood("no-weight", "Arroz con pollo", RecipeSamples.food.recipe!!.copy(finalWeightGrams = null))
        val tag = loggedFoodToEditableTag(RecipeCalculator.portion(food, servings = 0.5)).copy(isExpanded = true)
        rule.setContent { LoggerFoodItem(tag, false, "", null, null, false, RecipeSamples.loggerActions { amount = it }) }
        rule.onNodeWithText("Raciones de la receta").assertExists()
        rule.onNodeWithText("Grande").assertDoesNotExist()
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("1,5")
        rule.onAllNodes(hasSetTextAction()).onFirst().performImeAction()
        assertEquals(1.5, requireNotNull(amount), 0.0)
    }
    @Test fun recipeWeightEditorNamesGramsAndKeepsDecimalInput() {
        var amount: Double? = null
        val tag = loggedFoodToEditableTag(RecipeCalculator.portion(RecipeSamples.food, grams = 150.0)).copy(isExpanded = true)
        rule.setContent { LoggerFoodItem(tag, false, "", null, null, false, RecipeSamples.loggerActions { amount = it }) }
        rule.onNodeWithText("Gramos de la receta").assertExists()
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("75.5")
        rule.onAllNodes(hasSetTextAction()).onFirst().performImeAction()
        assertEquals(75.5, requireNotNull(amount), 0.0)
    }
}

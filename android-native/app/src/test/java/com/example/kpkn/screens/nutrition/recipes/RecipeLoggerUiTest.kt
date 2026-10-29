package com.example.kpkn.screens.nutrition.recipes

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kpkn.domain.nutrition.*
import com.example.kpkn.screens.nutrition.design.logger.LoggerFoodSheet
import com.example.kpkn.screens.nutrition.design.logger.LoggerFoodSheetState
import com.example.kpkn.screens.nutrition.design.logger.LoggerIcons
import com.example.kpkn.screens.nutrition.design.logger.loggerRecipeUnits
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Una receta en la hoja de ajuste (Registros v4, f-edit): sin peso final se cuenta en raciones (nunca en gramos ni con tamaños) y la
 * cifra admite decimales escritos; pesada, se cuenta en gramos con decimales.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1000dp-mdpi")
class RecipeLoggerUiTest {
    @get:Rule val rule = createComposeRule()

    private fun state(tag: ResolvedTag): LoggerFoodSheetState {
        val logged = requireNotNull(tag.loggedFood)
        return LoggerFoodSheetState(
            name = logged.foodName, icon = LoggerIcons.Bowl, matchLine = "Coincidencia alta · tu receta",
            grams = tag.amountGrams ?: logged.amount, units = loggerRecipeUnits(logged.unit), kcal = logged.calories.toInt(),
            protein = logged.protein, carbs = logged.carbs, fats = logged.fats, portion = null, cooking = null,
        )
    }

    private fun show(tag: ResolvedTag, onAmount: (Double) -> Unit) = rule.setContent {
        LoggerFoodSheet(
            state = state(tag), onGramsChange = onAmount, onPortionChange = {}, onCookingChange = {}, onVariant = {},
            onToggleExcluded = {}, onMacroChange = { _, _ -> }, onChange = {}, onRemove = {}, onDismiss = {},
        )
    }

    @Test fun rationEditorAcceptsDecimalsAndShowsRationsInsteadOfGrams() {
        var amount: Double? = null
        val food = RecipeCalculator.asFood("no-weight", "Arroz con pollo", RecipeSamples.food.recipe!!.copy(finalWeightGrams = null))
        val tag = loggedFoodToEditableTag(RecipeCalculator.portion(food, servings = 0.5))
        show(tag) { amount = it }
        rule.onNodeWithText("ración").assertExists()
        rule.onNodeWithText("Grande").assertDoesNotExist()
        rule.onNodeWithContentDescription("Más ración").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        assertEquals(1.0, requireNotNull(amount), 0.0)
        rule.onNodeWithText("½").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("1,5")
        rule.onAllNodes(hasSetTextAction()).onFirst().performImeAction()
        assertEquals(1.5, requireNotNull(amount), 0.0)
    }

    @Test fun recipeWeightEditorNamesGramsAndKeepsDecimalInput() {
        var amount: Double? = null
        val tag = loggedFoodToEditableTag(RecipeCalculator.portion(RecipeSamples.food, grams = 150.0))
        show(tag) { amount = it }
        rule.onNodeWithText("g").assertExists()
        rule.onNodeWithText("150").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("75.5")
        rule.onAllNodes(hasSetTextAction()).onFirst().performImeAction()
        assertEquals(75.5, requireNotNull(amount), 0.0)
    }

    @Test fun recipeUnitsFollowHowItWasLogged() {
        assertEquals(listOf("raciones"), loggerRecipeUnits("ración").map { it.plural })
        assertEquals(listOf("gramos"), loggerRecipeUnits("g").map { it.plural })
    }
}
